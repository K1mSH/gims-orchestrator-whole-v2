# HTTP 통신 (HTTP Client & Inter-Service Communication)

> 이 문서는 GIMS 동기화 시스템의 **모듈 간 통신**과 **외부 API 호출** 방식을 설명합니다.
> 서비스들이 서로 어떻게 대화하고, 외부 API를 어떻게 호출하는지를 이해할 수 있도록 작성했습니다.

---

## 전체 그림

이 시스템은 여러 개의 독립된 앱(마이크로서비스)으로 구성되어 있다. 각 앱은 자기만의 포트에서 동작하며, **HTTP REST API**로 서로 통신한다.

```
Frontend (3000)
    ↕ HTTP
Orchestrator (8080)
    ↕ HTTP
Proxy DMZ (8083)          Proxy Internal (8093)
    ↕ HTTP                    ↕ HTTP
Agent DMZ (8082)          Agent Internal (8092)
API Collector (8084)      API Collector (8094)
    ↕ HTTP
외부 API (뉴스, 공공데이터 등)
```

각 화살표가 HTTP 호출이다. 이 호출들을 처리하는 핵심 도구가 **RestTemplate**이다.

---

## 핵심 개념

### 1. RestTemplate — Spring의 HTTP 클라이언트

> `RestTemplate`은 Spring Framework가 제공하는 동기식 HTTP 클라이언트다. 내부적으로 Java 표준의 `HttpURLConnection`을 사용한다.

웹 브라우저가 웹서버에 요청을 보내듯, 서버도 다른 서버에 HTTP 요청을 보낼 수 있다. `RestTemplate`은 Spring에서 이 역할을 하는 도구다.

```java
// 다른 서버에 GET 요청
String result = restTemplate.getForObject("http://localhost:8080/api/agents", String.class);

// 다른 서버에 POST 요청 (데이터 전송)
restTemplate.postForEntity("http://localhost:8082/api/pipeline/execute", requestBody, Void.class);
```

브라우저에서 URL을 입력하는 것과 본질적으로 같다. 다만 사람이 아닌 코드가 요청을 보내고 응답을 받는 것.

---

### 2. 타임아웃 — 무한 대기 방지

외부 서버가 응답을 안 주면? 타임아웃 없이는 **영원히 기다린다**. 이러면 스레드가 묶여서 다른 작업도 못 한다.

```
connectTimeout: 연결 맺기 제한 시간
  "서버야 거기 있니?" → N초 안에 응답 없으면 포기

readTimeout: 응답 대기 제한 시간
  "서버야 데이터 줘" → N초 안에 데이터 안 오면 포기
```

우리 시스템의 타임아웃 설정:

| 모듈 | connectTimeout | readTimeout | 이유 |
|------|---------------|-------------|------|
| Orchestrator | 5초 | 30초 | Agent에 실행 명령 후 응답 대기 |
| API Collector | 10초 | 30초 | 외부 API 응답이 느릴 수 있음 |

타임아웃이 발생하면 예외(Exception)가 던져지고, 호출한 쪽에서 에러 처리를 한다.

> **연관 파일**
> - `infolink-orchestrator-backend/.../config/WebConfig.java` — connect=5s, read=30s
> - `infolink-api-collector/.../config/RestTemplateConfig.java` — connect=10s, read=30s

---

### 3. Interceptor — 모든 요청에 자동으로 뭔가 붙이기

> `ClientHttpRequestInterceptor`는 Spring이 제공하는 인터페이스다. RestTemplate에 등록하면 모든 요청에 자동으로 적용된다.

모든 HTTP 요청에 공통으로 필요한 것(인증 헤더 등)이 있다면, 매번 코드를 쓰는 대신 **Interceptor**를 등록한다.

```
일반 요청:
  코드에서 매번 → headers.set("X-API-Key", key) 추가

Interceptor 사용:
  한 번 등록 → 모든 요청에 자동으로 X-API-Key 헤더 추가
```

Orchestrator의 RestTemplate에는 X-API-Key Interceptor가 등록되어 있다. Agent/Proxy에 보내는 모든 요청에 자동으로 인증 키가 붙는다.

> **연관 파일**
> - `infolink-orchestrator-backend/.../config/WebConfig.java` — X-API-Key Interceptor 등록
> - `infolink-agent-common/.../config/ApiKeyFilter.java` — 수신 측 X-API-Key 검증 필터

---

### 4. 콜백 (Callback) — "끝나면 알려줘" 패턴

Orchestrator가 Agent에 "동기화 실행해줘" 명령을 보내면, Agent는 즉시 "알겠다"만 응답하고 실제 작업은 백그라운드에서 수행한다. 작업이 끝나면 Agent가 Orchestrator에 **결과를 알려주는** 요청을 보낸다. 이것이 콜백이다.

```
① Orchestrator → Agent: POST /api/pipeline/execute
   Agent 응답: 200 OK (즉시 반환, "접수했다")

② Agent: 백그라운드에서 동기화 수행 (수 분 소요)

③ Agent → Orchestrator: POST /api/callback/finished
   내용: {executionId, status, readCount, writeCount, ...}
   Orchestrator: 이력 테이블 업데이트
```

**왜 이렇게 하나?**: 동기화는 수 분이 걸릴 수 있다. Orchestrator가 그동안 응답을 기다리고 있으면 타임아웃이 나거나, 그 사이에 다른 요청을 처리 못 한다.

**콜백 재시도**: 콜백 전송에 실패하면 (네트워크 문제 등) 최대 3회 재시도한다. 간격은 2초, 4초, 6초로 점점 늘린다.

```
시도 1: 실패 → 2초 대기
시도 2: 실패 → 4초 대기
시도 3: 실패 → 포기 (로그에 기록)
```

**콜백이 유실되면?**: AgentHealthScheduler가 30초마다 Agent 상태를 확인한다. Agent는 이미 끝났는데 Orchestrator에 "RUNNING"으로 남아있는 실행 이력을 자동으로 복구한다.

> **연관 파일**
> - `infolink-agent-common/.../client/OrchestratorClient.java` — notifyStarted(), notifyFinished(), 재시도 로직
> - `infolink-orchestrator-backend/.../execution/ExecutionCallbackController.java` — 콜백 수신
> - `infolink-orchestrator-backend/.../agent/AgentHealthScheduler.java` — 콜백 유실 자동 복구

---

## 서비스 간 통신 패턴

### Orchestrator → Agent: 실행 명령 [ExecutionService → PipelineController]

```
POST http://agent:8082/api/pipeline/execute
Headers: X-API-Key: {proxyApiKey}
Body: {
  executionId: "daejeon_abc123",
  agentCode: "daejeon",
  sourceDatasourceId: "ds-daejeon-001",
  targetDatasourceId: "ds-if-001",
  filters: [...],
  conditions: [...]
}
```

### Agent → Orchestrator: 실행 결과 콜백 [OrchestratorClient (common) → ExecutionCallbackController]

```
POST http://orchestrator:8080/api/callback/finished
Body: {
  executionId: "daejeon_abc123",
  status: "SUCCESS",
  stepResults: [
    {stepId: "rcv-daejeon", readCount: 150, writeCount: 148, skipCount: 2},
    ...
  ],
  totalDurationMs: 45000
}
```

### Orchestrator → Agent: 헬스체크 [AgentHealthScheduler → HealthController]

```
GET http://agent:8082/actuator/health
응답: {
  status: "UP",
  runningAgents: ["daejeon", "bytek"]  // 현재 실행 중인 Agent 코드
}
```

30초마다 자동 호출. Agent가 살아있는지, 어떤 작업이 돌고 있는지 확인.

### Agent → Proxy → Orchestrator: DataSource 정보 요청 [SyncDataSourceService → ConnectionInfoController → DatasourceController]

```
GET http://proxy:8083/api/datasources/ds-daejeon-001/connection-info
Headers: X-API-Key: {apiKey}
응답: {
  datasourceId: "ds-daejeon-001",
  dbType: "POSTGRESQL",
  host: "192.168.1.100",
  port: 29000,
  databaseName: "daejeon",
  username: "ENC(암호화된값)",
  password: "ENC(암호화된값)"
}
```

Agent가 외부 DB에 연결하려면 접속 정보가 필요한데, 보안상 Agent에 직접 저장하지 않고 Proxy를 통해 Orchestrator에서 받아온다.

---

## 외부 API 호출 (API Collector)

### 범용 HTTP 호출 엔진 — ApiCallService [infolink-api-collector]

API Collector는 **어떤 외부 API든** 호출할 수 있는 범용 엔진을 갖고 있다. URL, 파라미터, 인증 방식이 모두 DB 설정으로 관리된다.

```
ApiCallService.call(endpoint, params)

1. URL 조립
   템플릿: "https://api.example.com/data/{category}"
   PATH 파라미터: category=news
   결과: "https://api.example.com/data/news"

2. 쿼리 파라미터 추가
   ?page=1&date=20260324
   결과: "https://api.example.com/data/news?page=1&date=20260324"

3. 인증 헤더 추가
   BASIC → Authorization: Basic base64(user:pass)
   BEARER → Authorization: Bearer eyJhbG...

4. 요청 전송 → 응답 수신
   성공: CallResult(200, JSON본문, null)
   실패: CallResult(null, null, "Connection refused")
```

**API Key 처리**: 외부 API 키는 Orchestrator에서 관리한다. 파라미터에 `isApiKeyRef=true`로 설정하면, staticValue에 저장된 키 ID로 실제 키를 조회한다.

```
파라미터: {paramName: "serviceKey", isApiKeyRef: true, staticValue: "key-001"}

실행 시:
  key-001로 Orchestrator에서 실제 키 조회 → "abc123xyz"
  요청에 serviceKey=abc123xyz 추가
```

키는 첫 조회 후 메모리에 캐싱하여 매번 API를 호출하지 않는다.

> **연관 파일**
> - `infolink-api-collector/.../service/ApiCallService.java` — call(), URL 조립, 인증, API Key 캐싱
> - `infolink-api-collector/.../service/DynamicParamResolver.java` — 동적 파라미터 치환 (TODAY/NOW)

---

## CORS — 프론트엔드 연동

> CORS는 W3C 웹 표준이다. 브라우저가 강제하는 보안 정책이며, 서버 측에서 허용 헤더를 설정하여 제어한다. Spring은 `@CrossOrigin` 애노테이션이나 `WebMvcConfigurer`로 설정을 지원한다.

브라우저에서 `localhost:3000`(프론트엔드)이 `localhost:8080`(백엔드)에 요청을 보내면, 브라우저가 **다른 출처(Origin)**라고 판단하여 차단한다. 이것이 CORS(Cross-Origin Resource Sharing) 정책이다.

이를 허용하기 위해 백엔드에 CORS 설정을 한다:

```
허용 출처: http://localhost:3000
허용 메서드: GET, POST, PUT, DELETE
허용 헤더: 전부
자격 증명(쿠키): 허용
```

API Collector와 Orchestrator 백엔드 모두 이 설정이 되어 있어서, Next.js 프론트엔드에서 자유롭게 API를 호출할 수 있다.

> **연관 파일**
> - `infolink-orchestrator-backend/.../config/WebConfig.java` — CORS 설정
> - `infolink-api-collector/.../config/WebConfig.java` — CORS 설정

---

## 에러 처리 요약

| 상황 | 처리 방식 |
|------|----------|
| 타임아웃 | 예외 발생 → 호출자에서 catch |
| 서버 다운 (Connection refused) | CallResult에 에러 메시지 담아 반환 |
| HTTP 4xx/5xx 응답 | 상태 코드 기록, 에러 메시지 보존 |
| 콜백 전송 실패 | 최대 3회 재시도 (2초, 4초, 6초 간격) |
| 콜백 유실 | 헬스체크 스케줄러가 30초마다 자동 복구 |
| CORS 차단 | 백엔드 WebConfig에서 프론트엔드 출처 허용 |
