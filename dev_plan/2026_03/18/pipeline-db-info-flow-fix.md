# 파이프라인 실행 시 DB 정보 흐름 정리 및 수정

## 1. DB 정보의 2가지 저장소

### A. Orchestrator DB (`orchestrator` PostgreSQL) — 연결 정보
실제 DB 접속에 필요한 물리적 정보를 관리한다.

```
datasource 테이블:
  datasourceId  = "ext_daejeon"
  host          = "localhost"
  port          = 29000
  databaseName  = "daejeon"
  dbType        = "POSTGRESQL"
  username      = "UX686v..." (암호문, PasswordEncryptor로 암호화)
  password      = "lZWvVh..." (암호문, PasswordEncryptor로 암호화)
  zone          = "EXTERNAL"

agent 테이블:
  agentCode           = "dmz-bojo-rcv-daejeon"
  sourceDatasourceId  = "ext_daejeon"   ← datasource 논리적 ID
  targetDatasourceId  = "dmz"           ← datasource 논리적 ID
  endpointUrl         = "http://localhost:8082"
```

**역할**: "어디에 연결할지" (host/port/자격증명)
**관리**: 프론트 UI에서 등록/수정

### B. Agent YAML (`config/agents/*.yml`) — 동기화 로직
파이프라인이 무엇을 어떻게 동기화할지 정의한다.

```yaml
# dmz-bojo-rcv-daejeon.yml
agent-code: dmz-bojo-rcv-daejeon
type: RCV

jewon:
  source-table: sec_jewon_view       # 외부 DB에서 읽을 테이블
  target-table: if_rsv_sec_jewon     # IF 테이블에 쓸 테이블
  primary-key: obsv_code
  conflict-key: source_refs

obsvdata:
  source-table: sec_obsvdata_view
  target-table: if_rsv_sec_obsvdata
  ...
```

**역할**: "무엇을 어떻게 동기화할지" (테이블명, PK, 증분방식)
**관리**: 코드에 포함, 배포 시 반영

---

## 2. 현재 실행 흐름

```
[1] 프론트/스케줄 → Orchestrator
    POST /api/executions/{agentId}/run

[2] Orchestrator → Agent
    POST http://agent:8082/api/pipeline/execute
    Body: {
      executionId: "dmz-bojo-rcv-daejeon_{uuid}",
      agentCode: "dmz-bojo-rcv-daejeon",
      sourceDatasourceId: "ext_daejeon",    ← ID만 (자격증명 없음)
      sourceDbType: "POSTGRESQL",
      targetDatasourceId: "dmz",            ← ID만 (자격증명 없음)
      targetDbType: "POSTGRESQL",
      sourceTableIds: {...},
      triggeredBy: "MANUAL"
    }

[3] Agent — DB 연결정보 해석 (SyncDataSourceService.resolveFromProxy)
    3-1. 캐시 확인 → 있으면 즉시 사용
         (앱 재시작 시 초기화됨. HikariCP DataSource 내부에도 평문이 유지되므로 캐시 유무와 보안 차이 없음)
    3-2. Proxy에 요청 (필수)
    3-3. 실패 시 예외 발생 (Orchestrator fallback 제거)
    → 응답 수신 → 복호화 → HikariCP DataSource 생성

[4] Agent — 파이프라인 실행
    YAML에서 테이블명/PK/증분방식 읽기
    source DataSource로 SELECT → target DataSource로 UPSERT
```

### Orchestrator ↔ Proxy ↔ Agent 통신 정리

```
Orchestrator → Agent:   파이프라인 실행 트리거 (POST /api/pipeline/execute)
Orchestrator → Proxy:   DB 메타데이터 조회 (search-tables, search-columns, test-connection)
                        실행 데이터 조회 (execution-data)
Agent → Proxy:          connection-info 요청 (자격증명 해석, 암호문 전달)
Agent → Orchestrator:   실행 결과 콜백 (notifyStarted, notifyFinished)
```

**원칙**: Agent가 자격증명을 얻는 경로는 **반드시 Proxy 경유**. Orchestrator 직접 조회는 허용하지 않음.

---

## 3. 현재 문제점

### 문제 1: Orchestrator fallback — Proxy 우회 가능

Agent의 `resolveFromProxy()`가 Proxy 실패 시 Orchestrator에 직접 connection-info를 요청한다.
Proxy가 자격증명 전달의 유일한 경로여야 하는데, fallback으로 우회 가능하면 Proxy 존재 의미가 희석됨.

```java
// 현재 코드 (SyncDataSourceService.resolveFromProxy)
// 1. 캐시
// 2. Proxy 요청
// 3. Orchestrator 직접 요청 (fallback) ← 제거 대상
```

### 문제 2: URL 경로 불일치

Agent가 Proxy와 Orchestrator에 **같은 URL 패턴**으로 요청하지만, 두 서비스의 경로가 다르다.

| 서비스 | 엔드포인트 경로 | 제공처 |
|--------|----------------|--------|
| Proxy (8083/8093) | `GET /api/datasource/connection-info/{id}` | sync-agent-common의 DatasourceController |
| Orchestrator (8080) | `GET /api/datasources/{id}/connection-info` | Orchestrator의 DatasourceController |

Agent의 `fetchConnectionInfo()`가 하나의 URL 패턴만 사용하므로 둘 중 하나만 맞는다.

**변경 전 Agent 코드 (원본)**:
```java
String url = baseUrl + "/api/datasource/connection-info/" + datasourceId;
// → Proxy에는 맞지만 Orchestrator에는 404
```

**오늘 수정한 Agent 코드**:
```java
String url = baseUrl + "/api/datasources/" + datasourceId + "/connection-info";
// → Orchestrator에는 맞지만 Proxy에는 404
```

### 문제 3: Proxy가 복호화된(평문) 정보를 Agent에게 서빙

현재 Proxy의 connection-info 서빙 흐름:
```
1. DatasourceController.getConnectionInfo(datasourceId) 호출
2. → ProxyDataSourceService.getJdbcTemplate(datasourceId) 내부 호출
3.   → Orchestrator에서 암호문 수신
4.   → Proxy가 즉시 복호화 (PasswordEncryptor.decrypt)
5.   → 복호화된 DataSourceInfo를 캐시에 저장
6. → ConnectionInfoProvider.getDataSourceInfo()로 캐시에서 반환
7. → Agent에게 평문 username/password 전달 ← 보안 문제!
```

의도한 흐름:
```
Proxy → Agent에게 암호문 그대로 전달 → Agent가 직접 복호화
```

### 문제 4: Proxy connection-info의 이중 역할

Proxy의 ProxyDataSourceService는 2가지 목적으로 connection-info를 사용:
1. **자신의 DataSource 생성** — 테이블/컬럼 검색, 데이터 조회 프록시 (복호화 필요)
2. **Agent에게 connection-info 서빙** — Agent가 자체 DataSource 생성 (암호문 전달 필요)

현재는 1번 용도의 복호화된 데이터를 2번 용도로도 쓰고 있어 문제.

---

## 4. 수정 방안

### 방침: Orchestrator fallback 제거 + URL 통일 + Proxy에 암호문 전용 엔드포인트 추가

### 4-1. Agent의 Orchestrator fallback 제거

Agent가 자격증명을 얻는 경로를 Proxy로 한정한다.

```java
// SyncDataSourceService.resolveFromProxy() 수정
public DataSourceInfo resolveFromProxy(String datasourceId) {
    // 1. 캐시 확인
    DataSourceInfo cached = cachedDataSourceInfos.get(datasourceId);
    if (cached != null) return cached;

    // 2. Proxy에 요청 (필수)
    if (proxyUrl == null || proxyUrl.isEmpty()) {
        throw new IllegalStateException("proxy-url 미설정. 자격증명 해석 불가: " + datasourceId);
    }
    DataSourceInfo info = fetchConnectionInfo(proxyUrl, datasourceId);
    if (info != null) {
        cachedDataSourceInfos.put(datasourceId, info);
        return info;
    }

    // 3. Proxy 실패 → 예외 (Orchestrator fallback 없음)
    throw new IllegalStateException("Proxy에서 datasource 해석 실패: " + datasourceId);
}
```

**bojo, bojo-int 양쪽 모두 적용**

기존 `fetchFromOrchestrator()` 메서드도 함께 삭제한다. (getJdbcTemplate()에서 호출하는 부분 포함)

### 4-2. URL 경로 통일

**Orchestrator 경로를 표준으로 채택**: `/api/datasources/{id}/connection-info`

이유:
- Orchestrator가 중앙 관리자 → 표준 경로를 따라가는 것이 자연스러움
- Agent는 Proxy든 Orchestrator든 같은 URL로 요청할 수 있어야 함

수정 대상:
| 파일 | 변경 |
|------|------|
| sync-agent-common `DatasourceController.java` | connection-info 엔드포인트 삭제 (URL을 맞춰도 복호화된 평문을 반환하는 구조라 사용 불가. Proxy 전용 패스스루 Controller로 대체) |
| sync-agent-bojo `SyncDataSourceService.java` | 이미 수정됨 (`/api/datasources/{id}/connection-info`) |
| sync-agent-bojo-int `SyncDataSourceService.java` | 이미 수정됨 |

### 4-3. Proxy에 암호문 전달 엔드포인트 추가

Proxy에 별도 Controller를 추가하여 Agent에게 **암호문 그대로** 전달.

```java
// sync-proxy-dmz/.../controller/ConnectionInfoController.java (신규)
@RestController
@RequestMapping("/api/datasources")
public class ConnectionInfoController {

    private final String orchestratorUrl;
    private final RestTemplate restTemplate;

    /**
     * Agent용 connection-info 프록시
     * Orchestrator의 connection-info를 그대로 전달 (복호화하지 않음)
     */
    @GetMapping("/{datasourceId}/connection-info")
    public ResponseEntity<Map<String, Object>> getConnectionInfo(
            @PathVariable String datasourceId) {
        // Orchestrator에 요청
        String url = orchestratorUrl + "/api/datasources/" + datasourceId + "/connection-info";
        Map<String, Object> response = restTemplate.getForObject(url, Map.class);
        // 암호문 그대로 Agent에게 전달
        return ResponseEntity.ok(response);
    }
}
```

핵심: **Proxy가 복호화하지 않고 Orchestrator 응답을 그대로 패스스루**

> **동기화 주의**: sync-proxy-dmz와 sync-proxy-internal의 ConnectionInfoController는
> 동일한 역할을 하는 코드이다. 한쪽을 수정하면 반드시 다른 쪽도 동일하게 수정해야 한다.
> common 모듈에 두지 않는 이유: Agent 앱에 불필요하게 로딩되는 것을 방지하기 위함.
> 이 내용을 클래스 Javadoc에 명시한다.

```java
/**
 * Agent용 connection-info 프록시 엔드포인트.
 *
 * Agent가 파이프라인 실행 시 DB 자격증명을 얻기 위해 호출한다.
 * Orchestrator의 connection-info API를 그대로 패스스루하며, 복호화하지 않는다.
 * Agent가 직접 PasswordEncryptor로 복호화하여 DataSource를 생성한다.
 *
 * ── 동기화 대상 ──
 * 이 클래스는 sync-proxy-dmz / sync-proxy-internal 양쪽에 동일하게 존재한다.
 * 한쪽을 수정하면 반드시 다른 쪽도 동일하게 수정할 것.
 * common 모듈에 두지 않는 이유: Agent 앱에 불필요하게 로딩되기 때문.
 *
 * ── 관련 코드 ──
 * - Orchestrator: DatasourceController.getConnectionInfo() — 암호문 응답 원본
 * - Agent: SyncDataSourceService.fetchConnectionInfo() — 이 엔드포인트를 호출하는 쪽
 * - Proxy 자체 DataSource 생성: ProxyDataSourceService.fetchFromOrchestrator() — 별도 흐름 (복호화 O)
 */
```

### 4-4. 기존 common 모듈 DatasourceController의 connection-info 엔드포인트

현재 `/api/datasource/connection-info/{id}`는 Proxy 자신이 DataSource를 만들기 위해
내부적으로 사용하는 로직과 연결되어 있고, 복호화된 데이터를 반환한다.

**방안**: 해당 엔드포인트를 삭제한다.
- Proxy 자신의 DataSource 생성: 내부적으로 `ProxyDataSourceService.getJdbcTemplate()` 직접 호출
- Agent에게 서빙: 4-2의 신규 Controller가 담당
- 기존 이 엔드포인트를 실제 호출하는 곳이 없다 (Agent는 URL 불일치로 못 쓰고 있었음)

### 4-5. Agent의 복호화 흐름 (변경 없음, 현재 정상)

```java
// SyncDataSourceService.fetchConnectionInfo() - 현재 코드
DataSourceInfo info = DataSourceInfo.builder()
    .datasourceId((String) response.get("datasourceId"))
    // ... host, port, databaseName ...
    .username(passwordEncryptor.decrypt((String) response.get("username")))  // 암호문 수신 → 복호화
    .password(passwordEncryptor.decrypt((String) response.get("password")))  // 암호문 수신 → 복호화
    .build();
```

이미 암호문을 받아서 복호화하는 구조 → 변경 불필요

---

## 5. 수정 후 흐름

```
[1] Orchestrator → Agent
    POST /api/pipeline/execute
    Body: { sourceDatasourceId: "ext_daejeon", ... }

[2] Agent → Proxy (필수, fallback 없음)
    GET /api/datasources/ext_daejeon/connection-info
    (URL 패턴 Orchestrator와 통일)

[3] Proxy — Orchestrator에서 암호문 조회 후 패스스루
    Agent ──GET /api/datasources/{id}/connection-info──→ Proxy (8083)
                                                          │
                                                  Proxy의 ConnectionInfoController
                                                  Orchestrator에 동일 요청 전달
                                                  (패스스루, 복호화 안함)
                                                          │
    Agent ←── { host, port, username(암호문), password(암호문) } ── Proxy
      │
      └─ PasswordEncryptor.decrypt() → 평문 → HikariCP DataSource

    ※ Proxy 실패 시 → 예외 발생 (Orchestrator 직접 조회 불가)

[4] Agent 파이프라인 실행
    YAML 참조: source-table, target-table, PK, 증분방식
    source DataSource → SELECT
    target DataSource → UPSERT
```

---

## 6. 수정 대상 파일

| 모듈 | 파일 | 작업 |
|------|------|------|
| sync-agent-bojo | `SyncDataSourceService.java` | Orchestrator fallback 제거, proxy-url 필수 검증, fetchFromOrchestrator() 삭제 |
| sync-agent-bojo-int | `SyncDataSourceService.java` | 동일 |
| sync-proxy-dmz | `controller/ConnectionInfoController.java` (신규) | Orchestrator 패스스루 엔드포인트 (`/api/datasources/{id}/connection-info`) |
| sync-proxy-internal | `controller/ConnectionInfoController.java` (신규) | 동일 |
| sync-agent-common | `DatasourceController.java` | connection-info 엔드포인트 삭제 + ConnectionInfoProvider 인터페이스 삭제 + ConnectionInfoResponse DTO 삭제 |
| sync-proxy-dmz | `ProxyDataSourceService.java` | ConnectionInfoProvider implements 제거 + getDataSourceInfo() 삭제 |
| sync-proxy-internal | `ProxyDataSourceService.java` | 동일 |
| sync-agent-common | JAR 리빌드 | build → 4개 모듈 libs/ 복사 |

## 7. 빌드/테스트 순서

1. sync-agent-common 수정 → JAR 빌드 → 4개 모듈에 복사
2. sync-proxy-dmz 수정 → 빌드
3. sync-proxy-internal 수정 → 빌드
4. 전체 기동: Orchestrator(8080) + Proxy DMZ(8083) + Agent bojo(8082)
5. RCV 파이프라인 실행 → Agent 로그에서:
   - Proxy에 `/api/datasources/{id}/connection-info` 요청 성공 확인
   - 암호문 수신 → 복호화 → DataSource 생성 → 파이프라인 SUCCESS

## 8. 참고: Proxy의 기존 역할 (변경 없음)

Proxy가 자신의 DataSource를 만드는 기존 흐름은 유지:
- Orchestrator가 `POST /api/datasource/search-tables` 등을 Proxy에 요청할 때
- ProxyDataSourceService.getJdbcTemplate()이 내부적으로 Orchestrator에서 connection-info 가져옴
- Proxy가 복호화 → 자신의 HikariCP DataSource 생성 → 테이블/컬럼 조회
- 이 흐름은 Agent와 무관, 변경 불필요
