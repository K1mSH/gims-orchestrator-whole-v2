# Agent DB Credentials를 Proxy 경유로 변경

## 목적

현재 Orchestrator가 실행 요청 시 DB credentials(username, password)를 **평문으로 Agent에 HTTP 전달**하고 있음.
Proxy를 DB 접근 전담으로 설계한 취지에 맞게, Agent가 **Proxy를 통해 credentials를 자체 해석**하도록 변경.

## 현재 흐름

```
Orchestrator
  → DB에서 credentials 조회 + decrypt
  → HTTP body에 평문(host, port, username, password) 포함
  → POST agent:8082/api/pipeline/execute
Agent
  → params에서 credentials 추출
  → SyncDataSourceService.setCurrentDatasources(sourceInfo, targetInfo)
  → 직접 DB 연결
```

## 변경 후 흐름

```
Orchestrator
  → datasourceId만 전달 (credentials 제거)
  → POST agent:8082/api/pipeline/execute

Agent
  → datasourceId 수신
  → Proxy에 credentials 요청: GET proxy:8083/api/datasource/connection-info/{datasourceId}
  → 응답으로 받은 정보로 DataSource 생성
  → DB 연결
```

Proxy의 기존 패턴 재활용:
- Proxy(ProxyDataSourceService)가 이미 `Orchestrator GET /api/datasources/{id}/connection-info` 를 호출하여 credentials를 해석하는 구조.
- Agent도 같은 Zone의 Proxy에 요청하면, Proxy가 Orchestrator API를 대신 호출해서 credentials를 전달.

## 수정 대상 파일

### 1. Proxy — connection-info API 추가 (신규)

**sync-agent-common** `controller/DatasourceController.java`
- `GET /api/datasource/connection-info/{datasourceId}` 엔드포인트 추가
- ProxyDataSourceService에서 해당 datasourceId의 연결 정보를 조회하여 반환
- 이미 ProxyDataSourceService.resolveDatasourceInfo() 로직이 있으므로 이를 API로 노출

### 2. Agent — Proxy에서 credentials 조회

**sync-agent-bojo** `config/SyncDataSourceService.java`
- 현재: params에서 직접 host/port/username/password 추출하여 DataSourceInfo 생성
- 변경: datasourceId만 받고, Proxy API 호출하여 credentials 획득
- 새 메서드: `resolveFromProxy(String datasourceId)` → `GET proxy:port/api/datasource/connection-info/{datasourceId}`
- Proxy URL은 application.yml의 `agent.proxy-url` (새 설정) 또는 기존 `agent.orchestrator-url`에서 유도

**sync-agent-bojo** `pipeline/PipelineService.java` (executeWithRunner)
- 현재: params에서 sourceHost, sourcePort, sourceUsername, sourcePassword 등 추출
- 변경: params에서 sourceDatasourceId, targetDatasourceId만 추출
- SyncDataSourceService.resolveFromProxy(datasourceId)로 DataSourceInfo 생성

**sync-agent-bojo-int** — 동일 적용 (Internal Agent)

### 3. Orchestrator — 실행 요청에서 credentials 제거

**sync-orchestrator/backend** `execution/ExecutionService.java` (triggerExecutionInternal)
- 현재 (415~425줄): sourceHost, sourcePort, sourceUsername, sourcePassword 등을 request에 put
- 변경: sourceDatasourceId, sourceDbType, sourceZone, sourceZoneShortCode, sourceDatasourceDbId만 전달
- targetDatasource도 동일하게 credentials 제거

### 4. Agent — PipelineController 요청 파싱 수정

**sync-agent-bojo** `controller/PipelineController.java`
- credentials 관련 파라미터 파싱 제거 (더 이상 안 옴)
- datasourceId만 params에 전달

## 영향 범위

- **실행 트리거**: Orchestrator → Agent HTTP 요청 body 변경 (하위 호환 X)
- **Proxy**: 새 API 1개 추가 (기존 로직 재활용)
- **Agent**: DataSource 해석 방식 변경 (Proxy 호출)
- **기존 데이터 조회/Retention/Health**: 변경 없음

## 설정 추가

```yaml
# sync-agent-bojo application.yml
agent:
  proxy-url: http://localhost:8083    # 같은 Zone의 Proxy URL

# sync-agent-bojo-int application.yml
agent:
  proxy-url: http://localhost:8093    # Internal Proxy URL
```

## 주의사항

- **Proxy health 체크**: 파이프라인 시작 전 `GET proxy/health` 호출하여 Proxy 가용성 확인. 실패 시 즉시 에러 반환 (파이프라인 시작 안 함)
- **credentials fallback 금지**: params에 credentials가 포함되어 있어도 무시. 반드시 Proxy 경유만 허용. 보안상 평문 credentials가 HTTP body에 실리는 경로를 완전히 차단

## HikariCP 풀 안정성 강화

### 위험 시나리오

물리 프로세스 1개(sync-agent-bojo)에 논리 Agent 12개가 동작하므로,
connection 풀 문제 → 전체 Agent 먹통 → 재부팅 시 12개 전부 중단되는 구조.

```
sync-agent-bojo (프로세스 1개)
  ├─ RCV 10개 (각각 다른 source, 전부 같은 target IF DB)
  ├─ Loader 1개
  └─ SND 1개
  → target datasourceId 풀 1개를 12개 Agent가 공유
```

| 시나리오 | 원인 | 증상 |
|----------|------|------|
| 풀 고갈 | 동시 실행 시 target IF DB 풀(max 5) 부족 | connectionTimeout 대기 → 전체 느려짐/에러 |
| long-running 점유 | 배치 UPSERT 수만건 → connection 수분 점유 | 다른 Agent가 같은 풀에서 대기 |
| 죽은 connection | DB 순간 장애 → 풀에 invalid connection 잔존 | validation 전 사용 시 에러 |
| connection leak | 예외 발생 시 connection 미반환 | 풀 점진적 고갈 → 재부팅 필요 |

### 설정 변경 (기존 → 변경)

```java
// SyncDataSourceService.createDataSource() — Agent
// ProxyDataSourceService.createDataSource() — Proxy (동일 적용)

hikariConfig.setMaximumPoolSize(10);            // 5 → 10 (12개 Agent 동시 실행 대비)
hikariConfig.setMinimumIdle(2);                 // 1 → 2 (cold start 방지)
hikariConfig.setConnectionTimeout(10_000);      // 30초 → 10초 (빠른 실패, 먹통 방지)
hikariConfig.setMaxLifetime(600_000);           // 30분 → 10분 (죽은 connection 빠르게 교체)
hikariConfig.setKeepaliveTime(120_000);         // 없음 → 2분 (주기적 validation)
hikariConfig.setConnectionTestQuery("SELECT 1"); // 없음 → 추가 (사용 전 validation)
hikariConfig.setLeakDetectionThreshold(60_000); // 없음 → 60초 (leak 경고 로그)
```

### 적용 대상

| 파일 | 위치 |
|------|------|
| `SyncDataSourceService.java` | sync-agent-bojo/.../config/ |
| `SyncDataSourceService.java` | sync-agent-bojo-int/.../config/ (동일 적용) |
| `ProxyDataSourceService.java` | sync-proxy-dmz/.../config/ |
| `ProxyDataSourceService.java` | sync-proxy-internal/.../config/ |

## 실행 전 Connection 상태 검사

### 개요

파이프라인 시작 **전에** 관련 datasource의 HikariCP 풀 상태를 검사하여,
connection이 부족하면 실행 자체를 거부하고 **사유를 상태에 기록**.

실행 도중 먹통되는 것보다 시작 전에 거부하고 이유를 알려주는 게 낫다.

### 검사 항목

```java
HikariPoolMXBean pool = hikariDataSource.getHikariPoolMXBean();

int active   = pool.getActiveConnections();    // 현재 사용 중
int idle     = pool.getIdleConnections();      // 유휴 대기 중
int waiting  = pool.getThreadsAwaitingConnection(); // 대기 스레드 수
int total    = pool.getTotalConnections();      // 전체 (active + idle)
int max      = hikariDataSource.getMaximumPoolSize();
```

### 거부 조건

| 조건 | 의미 | 거부 사유 메시지 |
|------|------|-----------------|
| `waiting > 0` | 이미 connection 대기 중인 스레드 존재 | "Connection pool 대기열 존재 ({waiting}건)" |
| `active >= max * 0.8` | 풀의 80% 이상 사용 중 | "Connection pool 사용률 과다 ({active}/{max})" |
| `total == 0` | 풀이 비정상 (DB 연결 불가) | "Connection pool 비활성 (DB 연결 확인 필요)" |

### 실행 흐름

```
PipelineService.executeWithRunner()
  │
  ├─ [1] Proxy health 체크 (GET proxy/health)
  │      실패 → FAILED "Proxy 연결 불가"
  │
  ├─ [2] Proxy에서 credentials 해석 → DataSource 생성/캐시
  │      실패 → FAILED "Datasource 해석 실패: {datasourceId}"
  │
  ├─ [3] Connection 풀 상태 검사 ← 신규
  │      ├─ source datasource 풀 검사
  │      └─ target datasource 풀 검사
  │      거부 → FAILED "{사유 메시지}"
  │
  ├─ [4] executionService.startExecution()
  ├─ [5] orchestratorClient.notifyStarted()
  └─ [6] runner.run() — 여기서부터 실제 실행
```

거부 시:
- 파이프라인 실행하지 않음
- `orchestratorClient.notifyFinished(FAILED, 사유 메시지)` 호출
- Orchestrator에 ExecutionHistory가 FAILED + 사유 메시지로 기록됨
- 프론트엔드 실행 이력에서 사유 확인 가능

### 수정 대상

| 파일 | 변경 |
|------|------|
| `SyncDataSourceService.java` | `checkPoolHealth(String datasourceId)` 메서드 추가. HikariPoolMXBean 조회 → 거부 조건 검사 → 통과/거부 결과 반환 |
| `PipelineService.java` (executeWithRunner) | credentials 해석 후, runner.run() 전에 checkPoolHealth() 호출. 거부 시 notifyFinished(FAILED) 후 return |
