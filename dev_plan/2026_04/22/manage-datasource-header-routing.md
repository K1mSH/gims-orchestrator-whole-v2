# Agent 관리 테이블 조회 — 헤더 기반 DataSource 라우팅

> 작성일: 2026-04-22
> 선행 이슈: `dev_plan/2026_04/21/provide-agent-issue-db-split.md`
> 상태: **확정 (구현 대기)**

---

## 배경

어제 이슈 문서(`provide-agent-issue-db-split.md`)에서 제기된 문제를 해결하기 위한 확정 방향 정리.

### 문제 재확인

- sync-agent-provide의 기본 datasource가 PG 29006 → execution/sync_log가 PG에 저장됨
- Proxy Internal(8093)은 기본 datasource(Oracle 29004)에서만 조회
- 결과: Orchestrator 프론트에서 provide Agent의 테이블별 처리현황/상세이력이 표시되지 않음

### 기존 설계 전제의 한계

- "망 하나당 대표 Agent + Proxy는 그 Agent의 DB를 바라봄"
- DMZ는 bojo/others가 같은 dev PG 29001 공유 (collector는 자체 트랙 예외)
- 내부망에는 bojo-int만 있어 Oracle 29004 단일 공유
- **provide 추가 시점부터 "내부망에 서로 다른 기본 DB를 쓰는 Agent 공존"** → 전제 붕괴

---

## 규약 확정 — "Agent는 항상 target 쪽에 둔다"

현재 모든 파이프라인 Agent에서 **target DB = 자기 JPA 기본 datasource = 관리 DB**가 일관되게 성립함 (검증 완료).

| Agent | JPA 기본 datasource (자기 DB) | target-table 위치 |
|-------|------------------------------|-------------------|
| bojo (DMZ) | DMZ dev PG 29001 | DMZ dev PG 29001 (IF 테이블) |
| others (DMZ) | DMZ dev PG 29001 | DMZ dev PG 29001 (IF 테이블) |
| bojo-int (내부망) | Oracle 29004 | Oracle 29004 (GIMS 실테이블) |
| **provide (내부망)** | **PG 29006** | **PG 29006 (api_prv_* 테이블)** |

→ **"Agent의 target DB = 관리 DB"** 를 공식 규약으로 채택. 별도 "관리 DB" 필드 불필요.

---

## 확정안 — target 재활용 + 풀 중복 수용 + JPA→JdbcTemplate 전환

### 동작 원리

```
Orchestrator 백엔드 (ExecutionService)
    ↓ Agent 조회 (findAgentByExecutionId, findById 등 — 기존 로직)
    ↓ 헤더 주입: X-Manage-Datasource-Id = agent.targetDatasourceId
[Proxy Internal (8093)]
    ↓ ExecutionDataController: @RequestHeader로 수신
    ↓ dataSourceProvider.getJdbcTemplate(headerValue)
    ├─ null (기존 Agent 중 targetDatasourceId가 null인 경우) → 기본 DataSource 사용 (Proxy yml)
    └─ 값 있음 → 캐시 조회 → 미스 시 Orchestrator에서 접속정보 받아 신규 생성
    ↓ JdbcTemplate으로 execution/sync_log 직접 SELECT (JPA 미사용)
[해당 DB (PG 29006 or Oracle 29004 등)]
    ↑ 결과
```

### 왜 JPA가 아닌 JdbcTemplate인가

- JPA EntityManager는 Spring 초기화 시점에 DataSource가 **고정** → 런타임 동적 교체 불가
- 헤더 라우팅을 JPA에 적용하려면 `AbstractRoutingDataSource` + ThreadLocal 도입 필요 → 복잡도 급증
- Proxy는 어차피 **경량 SELECT만** 수행 (엔티티 생명주기/더티 체킹 등 JPA 이점 불필요)
- 이미 source/target 조회 로직은 JdbcTemplate 기반으로 구현돼 있어 **일관성 유지**
- Agent 내부 쓰기(recordExecutionStart/Finish)는 JPA save 유지 (자기 DB에 쓰는 로직이라 라우팅 필요 없음)

### 핵심 설계 포인트

1. **규약 기반 자동 판단** — Agent의 `targetDatasourceId`를 헤더로 그대로 주입. 운영자 추가 입력 없음
2. **Agent 엔티티 스키마 변경 없음** — 기존 `targetDatasourceId` 필드 재활용
3. **운영자 UI 변경 없음** — 기존 source/target 등록 화면 그대로
4. **풀 중복 수용** — Proxy의 경량 조회 트래픽 특성상 실질 부담 없음

### 풀 중복에 대한 판단

- Agent가 heavy work(수백만 건 UPSERT 등 장시간 커넥션)의 주체
- Proxy는 **경량 조회만** (모니터링 SELECT)
- 같은 DB 가리키는 풀 2개가 공존해도 Proxy 측 커넥션 수 영향 미미
  - HikariCP idle 자동 정리
  - 피크 트래픽 자체가 낮음 (초당 수 건 수준)
  - 메모리 수 MB 미만
- **중복 매칭 로직을 만드는 비용 > 중복 수용 비용**

---

## 조사 결과 — 영향 범위

Proxy가 Agent 관리 테이블(execution/sync_log)을 조회하는 경로는 **`ExecutionDataController` 한 곳**으로 통일돼 있음. 이 컨트롤러의 모든 엔드포인트가 `DataSourceProvider`를 통해 DataSource를 획득 → **Provider 한 지점만 수정하면 자동 전파**.

### 적용되는 13개 엔드포인트

```
GET  /api/execution-data/{executionId}/summary
GET  /api/execution-data/{executionId}
GET  /api/execution-data/recent
GET  /api/execution-data/
GET  /api/execution-data/{executionId}/source
GET  /api/execution-data/{executionId}/target-if
GET  /api/execution-data/{executionId}/target
GET  /api/execution-data/{executionId}/failed
GET  /api/execution-data/{executionId}/tables       ← 테이블별 처리현황 (원인 포인트)
GET  /api/execution-data/{executionId}/tables/{name}
GET  /api/execution-data/{executionId}/tables/{name}/failed
GET  /api/execution-data/{executionId}/trace
GET  /api/execution-data/{executionId}/trace-source
```

### 상세이력 페이지 외 호출처
- `/recent`, `""` — 대시보드/모니터링 **최근 실행 목록**
- `/trace`, `/trace-source` — **정/역 추적** 기능

### 관리 DB를 건드리지 않는 common 컨트롤러 (수정 불필요)
- `DataRetentionController` — Agent가 자기 DB에 직접 접근 (Proxy 라우팅 대상 아님)
- `DatasourceController` — Agent의 외부 소스 DB 메타 조회 (관리 DB ≠ 소스 DB)
- `ExecutionParamsController` / `StepDefinitionController` — yml 기반, DB 조회 없음

---

## 수정 범위

### (1) sync-agent-common — DataSourceProvider 구현체 (기존 코드 활용)

`sync-proxy-internal/.../ProxyDataSourceService`가 **이미 필요한 로직을 전부 구현하고 있음**:
- `getJdbcTemplate(datasourceId)` — 캐시 조회 → miss 시 Orchestrator 호출 → HikariDataSource 생성 → JdbcTemplate 반환
- null/unknown 시 Spring 기본 DataSource fallback
- 캐시 관리 + PreDestroy 정리

→ **수정 불필요**. 컨트롤러에서 헤더 값을 이 메서드에 그대로 넘기면 동작.

### (2) sync-agent-common — ExecutionDataController 재작성 (핵심)

**각 엔드포인트의 JPA 호출을 JdbcTemplate 쿼리로 교체** + 헤더 수신.

교체 대상 JPA 호출 (약 20개 지점):
| JPA 호출 | 교체 쿼리 |
|----------|-----------|
| `executionRepository.findByExecutionId(id)` | `SELECT ... FROM execution WHERE execution_id = ?` |
| `executionRepository.findTop10ByOrderByStartedAtDesc()` | `SELECT ... FROM execution ORDER BY started_at DESC LIMIT 10` |
| `executionRepository.findAllByOrderByStartedAtDesc()` | `SELECT ... FROM execution ORDER BY started_at DESC` |
| `syncLogRepository.sumCountsByExecutionId(id)` | `SELECT SUM(read/write/failed/skip) FROM sync_log WHERE execution_id = ?` |
| `syncLogRepository.findByExecutionId(id)` | `SELECT ... FROM sync_log WHERE execution_id = ?` |
| `syncLogRepository.findFailedByExecutionId(id)` | `SELECT ... FROM sync_log WHERE execution_id = ? AND failed_count > 0` |
| `syncLogRepository.findByExecutionIdAndMappingName(id, name)` | `SELECT ... FROM sync_log WHERE execution_id = ? AND mapping_name = ?` |

헤더 수신 패턴:
```java
@GetMapping("/{executionId}")
public ResponseEntity<?> getExecution(
    @PathVariable String executionId,
    @RequestHeader(value = "X-Manage-Datasource-Id", required = false) String datasourceId
) {
    JdbcTemplate jt = dataSourceProvider.getJdbcTemplate(datasourceId);
    Execution execution = queryExecution(jt, executionId);  // JdbcTemplate 쿼리
    // 기존 로직 그대로
}
```

**RowMapper**: Execution / SyncLog 엔티티 클래스는 그대로 두고 RowMapper로 변환. 엔티티 클래스 자체에 손대지 않음.

**유의**: `ExecutionService`의 쓰기 메서드(`recordExecutionStart`/`recordExecutionFinish`)는 JPA 그대로 유지. Agent가 자기 DB에 쓰는 로직이라 DataSource 고정이 정상.

### (3) sync-orchestrator/backend — ExecutionService 헤더 주입

공통 헬퍼 추가 + 8개 API 호출 수정 (`getForEntity` → `exchange`):

```java
private HttpEntity<Void> buildHeaders(Agent agent) {
    HttpHeaders headers = new HttpHeaders();
    if (agent.getTargetDatasourceId() != null) {
        headers.set("X-Manage-Datasource-Id", agent.getTargetDatasourceId());
    }
    return new HttpEntity<>(headers);
}

// 각 API 호출 수정
HttpEntity<Void> entity = buildHeaders(agent);
ResponseEntity<Map> response = restTemplate.exchange(
    url, HttpMethod.GET, entity, Map.class
);
```

수정 지점 (ExecutionService.java):
- `findByAgentIdFromAgent` (line 66)
- `getExecutionDetail` (line 114)
- `getExecutionData` (line 134)
- `getTableStats` (line 175)
- `getTableDetail` (line 195)
- `getTableFailed` (line 215)
- `getTrace` (line 257)
- `getTraceSource` (line 291)

### (4) sync-agent-provide — **수정 없음**

- 어제 만든 모듈 구조 그대로 유지 (기본 datasource = PG 29006, 엔티티 16개 유지)

### (5) sync-orchestrator/frontend — **수정 없음**

- Agent 등록/수정 UI 그대로 (기존 target 필드만 사용)

---

## 기존 Agent 영향도

| Agent | targetDatasourceId | Proxy 동작 |
|-------|-------------------|-----------|
| bojo (DMZ) | dev-pg-29001 (등록됨) | 헤더 주입 → 캐시된 JdbcTemplate 사용 (dev PG 풀 1개 공존) |
| bojo-int (내부망) | oracle-29004 (등록됨) | 헤더 주입 → 캐시된 JdbcTemplate 사용 (Oracle 풀 1개 공존) |
| others (DMZ) | dev-pg-29001 (등록됨) | 헤더 주입 → 캐시된 JdbcTemplate 사용 (dev PG 풀 1개 공존) |
| collector (DMZ) | — | 자체 트랙, common 미사용 — 영향 없음 |
| **provide (내부망)** | PG 29006 datasource ID | **헤더 주입 → provide 관리 DB 라우팅** |

**모든 Agent 코드 변경 0건**. Agent 측 yml도 변경 없음.

### 풀 중복 실질 영향
- Proxy에 Oracle 29004 풀 2개 (yml 기본 + 헤더 라우팅)가 공존 — 합산해도 메모리/커넥션 영향 미미
- Proxy는 경량 조회만 수행 → idle 커넥션은 HikariCP가 자동 정리

---

## 확장성

향후 "자기 DB가 다른" 새 Agent가 추가되는 경우:
- Agent 등록 시 `targetDatasourceId`만 정확히 지정하면 Proxy가 자동 라우팅
- **추가 common/Proxy 수정 불필요**
- 규약: "Agent = target 쪽" 유지하는 한 자연스럽게 흡수

---

## 구현 순서

1. **sync-agent-common — DataSourceProvider 구현체**
   - **기존 코드 활용** — ProxyDataSourceService가 이미 구현돼 있음, 수정 불필요

2. **sync-agent-common — ExecutionDataController**
   - JPA 호출 20개 지점을 JdbcTemplate 쿼리로 교체 (Execution/SyncLog RowMapper 작성)
   - 13개 엔드포인트에 `@RequestHeader("X-Manage-Datasource-Id")` 추가
   - 각 엔드포인트 시작부에서 `dataSourceProvider.getJdbcTemplate(headerValue)` 호출하여 JdbcTemplate 획득
   - 이후 `executionService`/`syncLogRepository` 호출을 JdbcTemplate 쿼리로 대체

3. **sync-orchestrator/backend — ExecutionService**
   - `buildHeaders(Agent)` 공통 헬퍼 추가
   - 8개 API 호출을 `restTemplate.exchange(...)` 로 수정

4. **빌드 + 배포**
   - common JAR 빌드 → Proxy/bojo-int/others/provide 등 적용 모듈 libs/ 복사
   - Orchestrator backend 빌드 + 재기동
   - Proxy 재기동

5. **E2E 검증**
   - 상세이력 페이지에서 provide 실행 이력 표시 확인
   - 테이블별 처리현황 정상 표시 확인
   - 대시보드 최근 실행 목록에 provide 이력 표시 확인
   - 기존 bojo-int 이력도 여전히 정상 조회되는지 회귀 확인

---

## 리스크 / 주의사항

- **Orchestrator datasource 조회 API** 필요: 헤더 ID → 접속정보(url/user/pw) 반환. 기존 API가 있으면 재활용, 없으면 신규 추가 (내부망 노출 전용, 인증 필요)
- **캐시 무효화**: 접속정보 변경 시 Proxy 재기동 필요 (TTL 캐시 도입 가능하지만 재기동이 단순)
- **targetDatasourceId가 null인 Agent** (혹시 Proxy가 Agent로 등록되어 조회에 쓰이는 경우 등): 헤더 미주입 → 기본 DataSource → 기존 동작
- **풀 중복 수용**: 같은 DB 가리키는 풀 2개 공존 가능, Proxy 특성상 실질 부담 없음 (위 영향도 섹션 참조)

---

## 검토한 다른 방향 (기록)

### 옵션 A — 신규 필드 `manageDatasourceId` 추가
- Agent 엔티티에 관리 DB 전용 필드 신규
- **기각 사유**: 운영자에게 "관리 DB 위치" 개념 노출은 내부 구조 요구 → 부적절. 또한 DB 등록 변경 시 운영자가 재입력해야 하는 부담

### 옵션 A' — 시스템 자동 채움 (Agent가 기동 시 보고)
- Agent가 기동 시 자기 JPA datasource URL 보고 → Orchestrator가 매칭해 자동 저장
- **기각 사유**: URL 정규화 매칭 불확실. yml 명시 ID 방식은 "개발 설정이 DB 등록보다 선순위"가 되어 부적절

### 옵션 D — 중복 탈락 로직 (Proxy 기본 vs 헤더 ID 비교)
- 같은 DB면 기본 DataSource 재사용
- **기각 사유**: 기존 로직으로는 "Proxy yml의 datasource ↔ Orchestrator datasources 테이블 row" 매칭 수단이 없음. 구현하려면 yml에 명시 ID 필요한데, 이는 DB 등록 변경 시 yml 수정 전파 문제 발생

### 옵션 R — AbstractRoutingDataSource (JPA 동적 라우팅)
- Proxy의 DataSource를 RoutingDataSource로 교체, ThreadLocal 기반 라우팅
- JPA Repository 그대로 사용 가능
- **기각 사유**: Spring 초기화 구조 변경 + 트랜잭션 경계 주의 + 디버깅 난이도↑. JPA를 유지하려는 이점 대비 복잡도가 큼. Proxy는 경량 SELECT만이라 JdbcTemplate 교체가 더 단순하고 적절

### 결론
운영자 UI/스키마/yml 명시 모두 건드리지 않으면서 구현 가능한 유일한 방향이 **"target 재활용 + 풀 중복 수용"**. Proxy 특성상 풀 중복 비용이 실질적으로 0에 가까워 수용 가능.

---

## 참고 파일

| 파일 | 역할 |
|------|------|
| `sync-agent-common/.../controller/DataSourceProvider.java` | **수정 대상 인터페이스** (구현체 확장) |
| `sync-agent-common/.../controller/ExecutionDataController.java` | **수정 대상** (13개 엔드포인트에 헤더 수신) |
| `sync-orchestrator/backend/.../service/ExecutionService.java` | **수정 대상** (8개 API에 헤더 주입) |
| `sync-orchestrator/backend/.../entity/Agent.java` | 수정 불필요 (`targetDatasourceId` 재활용) |
| `gims-api-provider/.../service/ProviderDataSourceService.java` | 재활용 패턴 참조 (Proxy 경유 DataSource 획득) |
| `dev_plan/2026_04/21/provide-agent-issue-db-split.md` | 선행 이슈 문서 |
