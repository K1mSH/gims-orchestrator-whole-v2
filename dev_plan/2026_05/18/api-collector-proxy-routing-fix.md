# common 공통화 + api-collector 1차 적용 (Proxy 경유 통일)

> 2026-05-18
> 사이클: 5/18 (제주 retention 완료 후) — API 검증 진입 단계, 첫 데이터 수집 시도 시 발견된 401 회귀 fix
> 참고:
> - 회귀 조사 보고: 본 세션 (2026-05-18, dev_logs/2026_05/2026-05-18.md 에 추후 append)
> - 5/6 dev_log Phase 4: api-collector JWT 통합 — 단, 시스템 간 호출 부분 누락
> - 5/14 dev_log "others-dmz lazy resolve fix" — 시스템 호출 인증 문제의 다른 변종
> - git c0660cf (3/11~3/17): api-collector `OrchestratorClient` 첫 등장 — 다른 6 agent 의 `SyncDataSourceService` 패턴 차용 안 함

---

## 1. 배경 / 회귀 사실

api-collector 에서 endpoint 수동 실행 → **401 AUTH_REQUIRED** 로그:
```
[Collector] Creating DataSource: dmz
[Collector] Fetching connection-info from Orchestrator: dmz
ERROR ... 실행 실패 [...]: 401 : "{"error":"AUTH_REQUIRED"}"
```

회귀 조사 결과 (본 세션):

| 모듈 | connection-info 경로 | X-API-Key | 상태 |
|------|---------------------|:---------:|:----:|
| **infolink-api-collector** | **Orchestrator(backend) 직접** (`OrchestratorClient.getConnectionInfo`) | ❌ | 🔴 깨짐 |
| infolink-agent-others-dmz | Proxy 경유 (`SyncDataSourceService.fetchConnectionInfoFromProxy`) | ✅ | OK |
| infolink-agent-bojo-internal | Proxy 경유 (동일 메서드) | ✅ | OK |
| infolink-agent-bojo-dmz | Proxy 경유 (동일 메서드) | ✅ | OK |
| infolink-agent-provide | Proxy 경유 (동일 메서드) | ✅ | OK |
| infolink-api-provider | Proxy 경유 (`ProviderDataSourceService.createDataSource`) | ✅ | OK |

**관찰** — 7 모듈에 같은 connection-info 호출 코드가 7번 복제됨. 6개는 같은 양식 (Proxy + X-API-Key) 복제, api-collector 만 다른 양식 (Orchestrator 직접). git 히스토리 확인 (c0660cf) — api-collector 가 3월 중순 첫 등장 시 다른 agent 패턴 차용 안 함. 5/6 Phase 4 JWT 통합 때 backend 보호 시작 → 6 모듈은 동시에 정정됐고 api-collector 만 호출 양식이 달라 누락.

수동 실행 + **스케줄 실행 모두 같은 `OrchestratorClient.getConnectionInfo()` 거침** (`ApiScheduleExecutor → ApiExecutionService.run() → DynamicDataSourceService → OrchestratorClient`). 스케줄은 cookie 컨텍스트 자체가 없는 시스템 호출이라 X-API-Key 가 필수.

## 2. 사용자 결정 (2026-05-18)

**B-2 정공 — 공통화 + 단계 적용**

> "공통화 버전을 collector 에 먼저해보고, 이상 없으면 기존 6 모듈에 반영"

- **Phase 1 (본 사이클)**: common 에 새 클래스 추가 + api-collector 가 첫 사용자 → 검증
- **Phase 2 (별 사이클, Phase 1 통과 시)**: 다른 6 모듈도 같은 common 사용으로 교체

회귀 범위 작음 — Phase 1 동안 다른 6 모듈은 **코드 무변경**, JAR 만 갱신 (호출 안 하는 새 클래스가 common 에 추가될 뿐).

## 3. Phase 1 변경 범위 (본 사이클)

### 3-1. common 신규 클래스

#### `infolink-agent-common/.../client/ProxyConnectionInfoClient.java` (신규)

```java
package com.infolink.agent.common.client;

/**
 * Proxy 경유 connection-info 조회 클라이언트 (시스템 간 호출 통일 진실원).
 *
 * <p>Agent / API 모듈이 Orchestrator 의 datasource 자격증명을 얻기 위해 사용한다.
 * Proxy(/api/datasources/{id}/connection-info) 가 backend 의 응답을 패스스루하며,
 * 호출 측은 X-API-Key 헤더로 인증 (Proxy → backend 흐름에서 ApiKeyFilter soft-mode 통과).
 *
 * <p>응답 username/password 는 ENC 암호문 그대로 → 호출 측이 PasswordEncryptor 로 복호화.
 *
 * <p>도입 배경: 7 모듈이 동일 패턴 복제 (api-collector / api-provider / 4 agent / api-provider).
 * 본 클래스가 단일 진실원 — 신규 모듈은 본 클래스 import 만 하면 됨.
 *
 * @see com.infolink.agent.common.datasource.PasswordEncryptor
 */
public class ProxyConnectionInfoClient {

    private final RestTemplate restTemplate;
    private final String proxyUrl;
    private final String apiKey;

    public ProxyConnectionInfoClient(RestTemplate restTemplate, String proxyUrl, String apiKey) {
        this.restTemplate = restTemplate;
        this.proxyUrl = proxyUrl;
        this.apiKey = apiKey;
    }

    /**
     * Proxy 경유로 datasource 연결 정보 조회 (암호문 그대로 반환).
     * 복호화는 호출자 책임.
     *
     * @throws IllegalStateException 빈 응답
     * @throws HttpClientErrorException 인증 실패 등 HTTP 에러
     */
    public Map<String, Object> fetchEncrypted(String datasourceId) {
        String url = proxyUrl + "/api/datasources/" + datasourceId + "/connection-info";
        HttpHeaders headers = new HttpHeaders();
        if (apiKey != null && !apiKey.isEmpty()) {
            headers.set("X-API-Key", apiKey);
        }
        ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        Map<String, Object> body = response.getBody();
        if (body == null || body.isEmpty()) {
            throw new IllegalStateException("Empty connection-info response: " + datasourceId);
        }
        return body;
    }
}
```

**의도된 분담:**
- common = HTTP 호출 + 헤더 박기 + ENC 그대로 반환 (단일 진실원)
- 호출 측 = `PasswordEncryptor` 로 username/password 복호화 + Hikari config 구성 (모듈마다 풀 이름·옵션 다를 수 있어 호출 측 책임)

### 3-2. api-collector 적용

#### `OrchestratorClient.java` — 삭제 또는 thin wrapper 로 축소
- 옵션 A (권장): **삭제**. `DynamicDataSourceService` 가 common 의 `ProxyConnectionInfoClient` 를 직접 사용.
- 옵션 B: 클래스 유지 + 내부에서 common 호출. 다만 wrapper 의미 약함 → A 권장.

#### `DynamicDataSourceService.java` — 정정
```java
// 변경 후
private final ProxyConnectionInfoClient proxyClient;
private final PasswordEncryptor passwordEncryptor;

public DynamicDataSourceService(
        @Value("${agent.proxy-url}") String proxyUrl,
        @Value("${agent.api-key:}") String apiKey,
        @Value("${jasypt.encryptor.password}") String jasyptKey) {
    this.proxyClient = new ProxyConnectionInfoClient(new RestTemplate(), proxyUrl, apiKey);
    this.passwordEncryptor = new PasswordEncryptor(jasyptKey);
}

private HikariDataSource createDataSource(String datasourceId) {
    log.info("[Collector] Creating DataSource: {}", datasourceId);
    Map<String, Object> response = proxyClient.fetchEncrypted(datasourceId);

    String dbType = (String) response.get("dbType");
    String host = (String) response.get("host");
    int port = ((Number) response.get("port")).intValue();
    String databaseName = (String) response.get("databaseName");
    String username = passwordEncryptor.decrypt((String) response.get("username"));
    String password = passwordEncryptor.decrypt((String) response.get("password"));

    // ... Hikari config (기존 유지)
}
```

기존 `OrchestratorClient.ConnectionInfo` 의 `getJdbcUrl()` / `getDriverClassName()` 헬퍼는 `DynamicDataSourceService` 안으로 흡수 (또는 common 의 별 util 로 — 향후 결정). 본 사이클은 단순 inline 가능.

### 3-3. application.yml

#### `infolink-api-collector/.../resources/application.yml`

```yaml
# 변경 전
orchestrator:
  url: ENC(xnxfkstDgp9riQKlSListH6OelTJ2wApZe1KoKpTflTGSVjEfbji9sEwXDzStFew8wJgPEhKP+yawGahOCubqw==)

# 변경 후 (다른 6 모듈과 키 일관)
agent:
  proxy-url: ENC(ZdOrUviyTKn8YvU+Vs725joK12w5RyS01fYyA5p8zN0jPVJVpDxiZjRRQWf3AnkWQQ2R7wKjhM6AScv+OveAaQ==)  # DMZ proxy 8083 — others-dmz yml 그대로 복제
  api-key: ENC(YzTJ0UGnUBS+pOEw5EEu7jEp/rRjAANV95og3onvTTuV4bsTdh7jWP/RUx/jv2vTLojWfJ8QltB9oknwS63RXA==)    # 다른 모듈과 동일
  zone: DMZ
```

`orchestrator.url` 키 제거 — api-collector 가 더 이상 Orchestrator 직접 호출 안 함.

### 3-4. Internal 인스턴스 (8094) — 본 사이클 범위 외

사용자 결정 (Q3, 2026-05-18) = **DMZ 8084 만**. Internal 8094 는 별 트랙으로 미룸.

본 사이클에서 application-internal.yml 신규 추가 없음. test_plan §9 (Internal Collector) 진입 시 별도 결정.

## 3-5. 세 실행 흐름 정합 (수동 / 스케줄 / 테스트)

| 흐름 | 진입점 | DB connection-info 경유? | 본 fix 영향 |
|------|--------|:------------------------:|:----------:|
| 테스트 호출 | `POST /api/endpoints/{id}/test` → `ApiTestService.testCall` | ❌ 외부 API 만 호출 (grep 확인 — DynamicDataSourceService 참조 0건) | 영향 0 — 이미 동작 |
| **수동 실행** | `POST /api/endpoints/{id}/run` → `ApiExecutionService.run` → `dynamicDataSourceService.getDataSource` (line 266) | ✅ 거침 | 본 fix 로 401 해소 |
| **스케줄 실행** | `ApiScheduleExecutor.executeScheduled` → 같은 `ApiExecutionService.run` | ✅ 거침 (같은 경로) | 본 fix 로 401 해소 |

### 스레드 컨텍스트 고려

| 흐름 | 스레드 | cookie 컨텍스트 |
|------|--------|:---------------:|
| 수동 실행 | nio-8084-exec (backend 호출 받는 web thread) | 있음 (운영자 cookie) — 다만 본 fix 의 호출 측은 별개 |
| 스케줄 실행 | TaskScheduler (별 스레드) | 없음 |

→ `ProxyConnectionInfoClient` 가 instance field (`apiKey`) + 동기 `RestTemplate.exchange` 만 사용 → **스레드 컨텍스트 무관**. 스케줄 스레드에서도 정상 X-API-Key 박힘 + 호출 동기 완료.

## 4. 영향 범위 / 회귀

| 영역 | 영향 | 비고 |
|------|:----:|------|
| **common JAR** | 변경 — 새 클래스 1 추가 | api-collector 만 import. 다른 모듈 코드 무변경. |
| **9 모듈 libs/ 복사** | 필수 (CLAUDE.md 룰) | api-collector 외 모듈은 새 클래스 import 안 함 → 회귀 0 |
| **api-collector 수동 실행** | ✅ 401 해소 | 본 사이클 검증 목표 |
| **api-collector 스케줄 실행** | ✅ 401 해소 | 같은 흐름. 스케줄 0건이라 §7 시점에 등록·검증 |
| **api-collector 목록·단건 조회** | 무관 | DynamicDataSourceService 안 거침 |
| **api-collector mock self-call** | 무관 (DMZ) / URL 정정 (Internal) | Internal profile 시 8094 |
| **6 다른 모듈** | 코드 무변경, JAR 만 갱신 | 회귀 사실상 0 — 단 boot 정상 확인 권장 |
| **Backend / Proxy / Auth** | 변경 없음 | |

## 5. 검증 계획

### Phase 1-A — common 추가
- `ProxyConnectionInfoClient.java` 신규
- `./gradlew clean build -x test` (common)
- JAR 9 모듈 libs/ 복사 — bojo-dmz / bojo-internal / others-dmz / provide-dmz / orchestrator-backend / api-collector / api-provider / proxy-dmz / proxy-internal

### Phase 1-B — api-collector 정정
- `DynamicDataSourceService.java` 정정
- `OrchestratorClient.java` 삭제 (옵션 A)
- `application.yml` 정정
- `application-internal.yml` 신규 (3-4-A)
- `./gradlew clean build -x test` (api-collector)

### Phase 1-C — 기동 & 검증

| # | 시나리오 | 기대 |
|---|---------|------|
| 1 | api-collector DMZ 8084 `bootRun` | health 200 + 시작 로그에서 yml load 정상 |
| 2 | (선택) api-collector Internal 8094 `bootRun --args=--spring.profiles.active=internal` | health 200 |
| 3 | 다른 6 모듈 회귀 확인 — 5개 (others-dmz / bojo-internal / bojo-dmz / api-provider) 기동 상태 확인 | 이미 떠있는 상태, 새 JAR 적용은 다음 재기동 시 (= 회귀 검증은 Phase 2 진입 시 자연스럽게) |
| 4 | 사용자 cookie 로그인 → `/api-collect` → 약수터-제원 (26) **수동 실행** | 401 사라짐, DB 적재 SUCCESS |
| 5 | backend log | `[Proxy] Connection-info passthrough success: dmz` 출현 |
| 6 | `api_execution_history` row | status=SUCCESS, read/write count > 0 |
| 7 | 다른 endpoint (나라장터 16) 도 같은 흐름 | OK |

### Phase 1-D — 사용자 확인 + 다음 결정
- 위 Phase 1-C 통과 → Phase 2 (다른 6 모듈 common 교체) 별 사이클 진입 결정
- NG → 디버깅 후 Phase 1 보강

## 6. 리스크

| # | 리스크 | 완화 |
|---|--------|------|
| R1 | common JAR 갱신으로 다른 모듈 boot 깨짐 | 새 클래스 추가만, 기존 클래스 무변경 → boot 영향 0. JAR 복사 후 다른 모듈 재기동은 자연 갱신. |
| R2 | `OrchestratorClient` 삭제 시 다른 reference 깨짐 | `DynamicDataSourceService` 1개만 import. grep 으로 확인 후 삭제. |
| R3 | Internal profile 8094 가 backend(8080) JWKS 호출 못 함 | application.yml 의 `auth.jwks-url=http://localhost:8096/...` 가 profile 상속 — 정상 |
| R4 | mock self-call URL 정정 누락 | application-internal.yml 에 명시 (3-4-A) |
| R5 | Phase 2 진입 시 다른 6 모듈 회귀 | 별 사이클 + 점진 (1개씩) 적용 권장 |
| R6 | Hikari config 옵션 차이 (api-collector 풀 = 5, others-dmz 풀 = ? ) | `DynamicDataSourceService` 안에서 모듈별로 옵션 유지 가능 (common 책임은 connection-info HTTP 만) |
| R7 | `ProxyConnectionInfoClient` 응답 양식 변경 시 7 모듈 동시 영향 | Phase 2 진입 후 발생할 시나리오 — 진입 전 사용자 결정 |
| R8 | api-collector Internal 8094 운영 룰 미확정 | 본 사이클은 DMZ 만 검증, Internal 은 선택 (Q3 결정 대기) |

## 7. 산출물 예상

- **common 신규 (1)**: `ProxyConnectionInfoClient.java`
- **api-collector**: `DynamicDataSourceService.java` 정정 + `OrchestratorClient.java` 삭제
- **yml (1)**: `application.yml` 정정 (Q3=DMZ 만 결정. Internal 8094 는 별 트랙)
- **JAR**: common clean build + 9 모듈 libs/ 복사
- **dev_log**: `dev_logs/2026_05/2026-05-18.md` 에 본 사이클 append
- **빌드**: common + api-collector 2 모듈 clean build

## 8. 커밋 메시지 (예정)

```
feat(common): ProxyConnectionInfoClient 신규 + api-collector 첫 적용

- common: 시스템 간 connection-info 호출 단일 진실원 (Proxy 경유 + X-API-Key)
- api-collector: OrchestratorClient(자체) 삭제, common 클래스 사용으로 정정
- application.yml: orchestrator.url 제거, agent.proxy-url/api-key/zone 추가 (다른 6 모듈과 키 일관)
- (선택) application-internal.yml 신규 — 8094 Internal profile
- JAR 9 모듈 libs/ 일괄 복사

원인: 5/6 Phase 4 JWT 통합 시 api-collector 만 시스템 간 호출 흐름 정정 누락.
수동·스케줄 실행 모두 같은 OrchestratorClient.getConnectionInfo() 거치며 401 AUTH_REQUIRED.
git c0660cf (3/11~3/17) 첫 등장 시점부터 다른 agent 패턴 차용 안 함.

Phase 2 (별 사이클) — 다른 6 모듈 (others-dmz/bojo-internal/bojo-dmz/provide-dmz/api-provider)
도 같은 common 사용으로 교체 예정 (Phase 1 검증 통과 후).
```

## 9. 결정 사항 (사용자 확인 완료, 2026-05-18)

| Q | 결정 |
|---|------|
| Q1 common 클래스명 | **`ProxyConnectionInfoClient`** |
| Q2 `OrchestratorClient.java` | **삭제** (의미 약한 wrapper 회피) |
| Q3 본 사이클 범위 | **DMZ 8084 만** (Internal 8094 별 트랙) |
| Q4 Internal profile 양식 | N/A (Q3 결정으로 미적용) |

## 10. Phase 2 (별 사이클, 검증 통과 후)

다른 6 모듈에서 자체 `fetchConnectionInfoFromProxy` / `createDataSource` 의 HTTP 호출 부분을 common `ProxyConnectionInfoClient` 사용으로 교체.

대상 (6 메서드):
- `infolink-agent-others-dmz/.../SyncDataSourceService.fetchConnectionInfoFromProxy`
- `infolink-agent-bojo-internal/.../SyncDataSourceService.fetchConnectionInfoFromProxy`
- `infolink-agent-bojo-dmz/.../SyncDataSourceService.fetchConnectionInfoFromProxy`
- `infolink-agent-provide/.../SyncDataSourceService.fetchConnectionInfoFromProxy`
- `infolink-api-provider/.../ProviderDataSourceService.createDataSource` (의 HTTP 호출 부분)

회귀 검증 범위 — 04-others 5종 (use/saeol/yaksoter/api-collect/jeju) + bojo + api-provider. 점진 적용 (1개씩) 권장.

## 11. 다음 사이클

Phase 1 통과 후 — test_plan/05 §4 (범용 실행기) → §5 (LOOKUP) → §6 (커스텀) → §7 (스케줄) 진입.
