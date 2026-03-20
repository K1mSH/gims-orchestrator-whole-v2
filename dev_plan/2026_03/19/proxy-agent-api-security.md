# Proxy/Agent API 보안 강화 계획

## 1. 문제

### 1-1. Proxy에 불필요한 common 컨트롤러 노출
Proxy의 `@SpringBootApplication(scanBasePackages)`에 `com.sync.agent.common`이 포함되어,
common의 컨트롤러 5개가 전부 Proxy에서도 활성화됨.

| 컨트롤러 | 엔드포인트 | Proxy에 필요? | 위험도 |
|----------|-----------|:---:|--------|
| DataRetentionController | `POST /api/cleanup/{agentCode}` | **X** | **높음** — DELETE 실행 |
| ExecutionDataController | `GET /api/execution-data/**` | **O** | 낮음 — 읽기 전용 |
| DatasourceController | `POST /api/datasource/**` | **O** | 중간 — DB 연결/스키마 조회 |
| StepDefinitionController | `GET /api/pipeline/step-definitions` | **X** | 낮음 — Agent 전용 메타 |
| ExecutionParamsController | `GET /api/pipeline/execution-params` | **X** | 낮음 — Agent 전용 메타 |

**핵심 위험**: `cleanup` 엔드포인트가 Proxy에서 열려있으면, 캐싱된 JdbcTemplate로 DELETE 쿼리 실행 가능.

### 1-2. Agent에 API Key 인증 없음
Orchestrator → Agent 직접 호출 시 `X-API-Key` 검증이 없음.
ApiKeyFilter는 Proxy(DMZ/Internal)에만 존재하고, Agent(bojo/bojo-int)에는 없음.

| Agent 엔드포인트 | 호출자 | 인증 |
|-----------------|--------|------|
| `POST /api/pipeline/trigger` | Orchestrator | **없음** |
| `POST /api/cleanup/{agentCode}` | Orchestrator | **없음** |
| `GET /api/execution-data/**` | Orchestrator (via Proxy) | **없음** |
| `POST /api/datasource/**` | Orchestrator (via Proxy) | **없음** |

---

## 2. 해결 방안

### 2-1. common 컨트롤러에 `@ConditionalOnProperty` 적용

각 common 컨트롤러에 활성화 조건을 달아서, 모듈별 `application.yml`에서 명시적으로 켜야만 활성화.
**기본값 = false** → 새 컨트롤러가 추가돼도 명시적으로 켜지 않으면 안 뜸.

#### 대상 컨트롤러 (5개)

| 컨트롤러 | property 키 | 기본값 |
|----------|-------------|--------|
| DataRetentionController | `common.controller.cleanup.enabled` | false |
| ExecutionDataController | `common.controller.execution-data.enabled` | false |
| DatasourceController | `common.controller.datasource.enabled` | false |
| StepDefinitionController | (이미 `@ConditionalOnBean` 있음 — 변경 없음) | — |
| ExecutionParamsController | `common.controller.execution-params.enabled` | false |

> StepDefinitionController는 이미 `@ConditionalOnBean(StepDefinitionProvider.class)`로 보호됨.
> Proxy에 StepDefinitionProvider가 없으므로 자동 비활성.

#### 수정 내용

```java
// DataRetentionController.java
@ConditionalOnProperty(name = "common.controller.cleanup.enabled", havingValue = "true")
@RestController
@RequestMapping("/api/cleanup")
public class DataRetentionController { ... }

// ExecutionDataController.java
@ConditionalOnProperty(name = "common.controller.execution-data.enabled", havingValue = "true")
@RestController
@RequestMapping("/api/execution-data")
public class ExecutionDataController { ... }

// DatasourceController.java
@ConditionalOnProperty(name = "common.controller.datasource.enabled", havingValue = "true")
@RestController
@RequestMapping("/api/datasource")
public class DatasourceController { ... }

// ExecutionParamsController.java
@ConditionalOnProperty(name = "common.controller.execution-params.enabled", havingValue = "true")
@RestController
@RequestMapping("/api/pipeline")
public class ExecutionParamsController { ... }
```

#### 모듈별 application.yml 설정

```yaml
# sync-agent-bojo (Agent DMZ) — 전부 활성화
common.controller:
  cleanup.enabled: true
  execution-data.enabled: true
  datasource.enabled: true
  execution-params.enabled: true

# sync-agent-bojo-int (Agent Internal) — 동일
common.controller:
  cleanup.enabled: true
  execution-data.enabled: true
  datasource.enabled: true
  execution-params.enabled: true

# sync-proxy-dmz — execution-data, datasource만 활성화
common.controller:
  execution-data.enabled: true
  datasource.enabled: true

# sync-proxy-internal — 동일
common.controller:
  execution-data.enabled: true
  datasource.enabled: true
```

### 2-2. Agent에 ApiKeyFilter 추가

Proxy에만 있던 ApiKeyFilter를 **common으로 이동**하고, `@ConditionalOnProperty`로 제어.

#### 수정 내용

1. **common에 ApiKeyFilter 생성**: `com.sync.agent.common.config.ApiKeyFilter`
   - Proxy DMZ/Internal의 기존 구현과 동일 로직
   - property: `common.filter.api-key.enabled` (기본 false)
   - `${agent.api-key:}`로 키 값 주입

```java
// com.sync.agent.common.config.ApiKeyFilter
@Slf4j
@Component
@Order(1)
@ConditionalOnProperty(name = "common.filter.api-key.enabled", havingValue = "true")
public class ApiKeyFilter implements Filter {
    private static final String API_KEY_HEADER = "X-API-Key";

    @Value("${agent.api-key:}")
    private String apiKey;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String path = httpRequest.getRequestURI();

        if (path.equals("/health") || path.startsWith("/health/")) {
            chain.doFilter(request, response);
            return;
        }

        if (path.startsWith("/api/")) {
            if (apiKey == null || apiKey.isEmpty()) {
                chain.doFilter(request, response);
                return;
            }
            String requestApiKey = httpRequest.getHeader(API_KEY_HEADER);
            if (requestApiKey == null || !requestApiKey.equals(apiKey)) {
                log.warn("[ApiKey] 인증 실패: path={}, remoteAddr={}", path, httpRequest.getRemoteAddr());
                HttpServletResponse httpResponse = (HttpServletResponse) response;
                httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                httpResponse.setContentType("application/json");
                httpResponse.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Invalid or missing API Key\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
```

2. **Proxy의 자체 ApiKeyFilter 삭제** — common 것으로 대체

3. **모듈별 application.yml 설정**

```yaml
# sync-agent-bojo — 활성화
common.filter:
  api-key.enabled: true

# sync-agent-bojo-int — 활성화
common.filter:
  api-key.enabled: true

# sync-proxy-dmz — 활성화 (기존 자체 필터 대체)
common.filter:
  api-key.enabled: true

# sync-proxy-internal — 활성화
common.filter:
  api-key.enabled: true
```

4. **Orchestrator WebConfig 주석 수정**: "Agent는 필터 없으므로 무시됨" → 삭제

### 2-3. `/debug/datasources` 엔드포인트 제거

디버깅용으로 만든 엔드포인트로, 캐싱된 DataSource의 호스트/포트/DB명을 노출함.
로그에 충분히 기록되고 있어 별도 엔드포인트 불필요. 4개 모듈 전부 제거.

| 모듈 | 파일 |
|------|------|
| sync-agent-bojo | `controller/HealthController.java` |
| sync-agent-bojo-int | `controller/HealthController.java` |
| sync-proxy-dmz | `controller/HealthController.java` |
| sync-proxy-internal | `controller/HealthController.java` |

각 HealthController에서 `debugDatasources()` 메서드 + 관련 import/의존성 삭제.

---

## 3. 수정 파일 목록

| 모듈 | 파일 | 작업 |
|------|------|------|
| sync-agent-common | `controller/DataRetentionController.java` | `@ConditionalOnProperty` 추가 |
| sync-agent-common | `controller/ExecutionDataController.java` | `@ConditionalOnProperty` 추가 |
| sync-agent-common | `controller/DatasourceController.java` | `@ConditionalOnProperty` 추가 |
| sync-agent-common | `controller/ExecutionParamsController.java` | `@ConditionalOnProperty` 추가 |
| sync-agent-common | `config/ApiKeyFilter.java` (신규) | common용 API Key 필터 |
| sync-agent-bojo | `application.yml` | controller 활성화 + api-key 필터 활성화 |
| sync-agent-bojo-int | `application.yml` | 동일 |
| sync-proxy-dmz | `config/ApiKeyFilter.java` | **삭제** (common으로 대체) |
| sync-proxy-dmz | `application.yml` | controller 선택 활성화 + api-key 필터 활성화 |
| sync-proxy-internal | `config/ApiKeyFilter.java` | **삭제** (common으로 대체) |
| sync-proxy-internal | `application.yml` | 동일 |
| sync-orchestrator/backend | `config/WebConfig.java` | 주석 수정 |
| sync-agent-bojo | `controller/HealthController.java` | `/debug/datasources` 제거 |
| sync-agent-bojo-int | `controller/HealthController.java` | `/debug/datasources` 제거 |
| sync-proxy-dmz | `controller/HealthController.java` | `/debug/datasources` 제거 |
| sync-proxy-internal | `controller/HealthController.java` | `/debug/datasources` 제거 |

---

## 4. 검증

### 4-1. Proxy 검증
- [ ] `POST /api/cleanup/...` → 404 (컨트롤러 비활성)
- [ ] `GET /api/pipeline/step-definitions` → 404
- [ ] `GET /api/pipeline/execution-params` → 404
- [ ] `GET /api/execution-data/...` → 200 (활성)
- [ ] `POST /api/datasource/test-connection` → 200 (활성)
- [ ] X-API-Key 없는 요청 → 401

### 4-2. Agent 검증
- [ ] X-API-Key 포함 요청 → 정상 동작
- [ ] X-API-Key 없는 요청 → 401
- [ ] X-API-Key 틀린 요청 → 401
- [ ] `/health` → 인증 없이 200
- [ ] `/debug/datasources` → 404 (제거됨)

### 4-3. E2E 검증
- [ ] Orchestrator → Agent 파이프라인 실행 (api-key 자동 전달) → SUCCESS
- [ ] Orchestrator → Proxy → execution-data 조회 → 정상
- [ ] Orchestrator → Agent cleanup → 정상
