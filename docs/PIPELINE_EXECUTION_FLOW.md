# 파이프라인 실행 흐름 — 소스 기반 상세 추적

> 실제 소스코드를 한 줄씩 따라가며 파이프라인이 어디서 시작되고, DB 정보가 어떻게 전달되고, 데이터가 어떻게 동기화되는지 추적한다.

---

## STEP 0. 앱 부팅 시 초기화 — YAML → Runner 등록

파이프라인을 실행하려면 먼저 **어떤 Agent가 어떤 Step을 실행하는지** 등록되어야 한다.
이 등록은 앱 부팅 시 자동으로 이루어지며, 런타임에 YAML을 다시 읽지 않는다.

### 0-1. AgentConfigLoader — YAML 파일 전체 스캔

**파일**: `infolink-agent-bojo-dmz/.../config/AgentConfigLoader.java`

```
[L26-27] @PostConstruct
         public void loadAgentConfigs()
         // ★ 앱 시작 시 자동 실행 (Spring 빈 초기화 단계)

[L29-30] PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver()
         Resource[] resources = resolver.getResources("classpath:config/agents/*.yml")
         // config/agents/ 폴더의 모든 .yml 파일을 스캔
         // DMZ 기준: 12개 파일 (RCV 10 + Loader 1 + SND 1)

[L34-41] for (Resource resource : resources) {
             Map<String, Object> data = yaml.load(is)           // YAML → Map 파싱
             AgentDefinition def = parseAgentDefinition(data)    // Map → AgentDefinition 변환
             agentDefinitions.add(def)                           // 리스트에 추가
         }
```

**parseAgentDefinition()** (L54-132)이 YAML의 각 섹션을 객체로 변환:
```
agent-code: "dmz-bojo-rcv-daejeon"  →  def.setAgentCode("dmz-bojo-rcv-daejeon")
type: "RCV"                         →  def.setType("RCV")
jewon:
  source-table: sec_jewon_view      →  def.getJewon().setSourceTable("sec_jewon_view")
  target-table: if_rsv_sec_jewon    →  def.getJewon().setTargetTable("if_rsv_sec_jewon")
  primary-key: obsv_code            →  def.getJewon().setPrimaryKey("obsv_code")
  conflict-key: source_refs         →  def.getJewon().setConflictKey("source_refs")
  full-copy: true                   →  def.getJewon().setFullCopy(true)
obsvdata:
  source-table: sec_obsvdata_view   →  def.getObsvdata().setSourceTable(...)
  ...
table-mappings:                     →  def.getTableMappings() (프론트 모니터링용)
select-tables:                      →  def.getSelectTables() (WHERE 조건 드롭다운용)
```

**결과**: `agentDefinitions` 리스트에 12개 AgentDefinition 객체가 메모리에 보관됨.

### 0-2. PipelineConfig — AgentDefinition → Runner 조립 → Registry 등록

AgentConfigLoader 초기화가 끝나면, 타입별 PipelineConfig가 @PostConstruct로 실행된다.

**파일**: `infolink-agent-bojo-dmz/.../config/RcvPipelineConfig.java` (예시)

```
[L31-32] @PostConstruct
         public void registerRcvPipelines()

[L33] List<AgentDefinition> rcvDefs = agentConfigLoader.getAgentsByType("RCV")
      // 12개 AgentDefinition 중 type="RCV"인 것만 필터 → 10개

[L36-44] for (AgentDefinition def : rcvDefs) {
             PipelineRunner runner = createRcvRunner(def)      // Step 체인 조립
             List<StepDefinition> stepDefs = buildStepDefinitions(def)  // 메타데이터
             pipelineRegistry.register(def.getAgentCode(), "RCV", runner, stepDefs)
             // ★ PipelineRegistry에 등록
         }
```

**createRcvRunner()** (L48-84) — AgentDefinition에서 Step 객체를 생성:
```
def.getJewon()의 설정값으로 SourceToIfStep 생성:
  ExtractStepConfig.builder()
      .stepId("jewon-extract")
      .sourceTable("sec_jewon_view")      ← YAML에서 온 값
      .targetIfTable("if_rsv_sec_jewon")  ← YAML에서 온 값
      .primaryKeyColumn("obsv_code")      ← YAML에서 온 값
      .conflictKey("source_refs")
      .fullCopy(true)
      .build()
  → SourceToIfStep jewonStep = new SourceToIfStep(config, dataSourceProvider, syncLogRepository)

def.getObsvdata()도 동일하게 SourceToIfStep 생성

return new PipelineRunner(agentCode, List.of(jewonStep, obsvStep))
// ★ 2개 Step이 순서대로 묶인 Runner 완성
```

### 0-3. PipelineRegistry — 라우팅 테이블 완성

**파일**: `infolink-agent-bojo-dmz/.../config/PipelineRegistry.java`

```
[register() L53-55]
    runners.computeIfAbsent(agentCode, k -> new ConcurrentHashMap<>()).put(modeId, runner)
    agentTypes.put(agentCode, agentType)
```

타입별 PipelineConfig가 순차 실행되면서 Registry가 채워짐:
```
RcvPipelineConfig  → RCV 10개 등록
LoaderPipelineConfig → Loader 1개 등록
SndPipelineConfig  → SND 1개 등록
```

**등록 완료 후 PipelineRegistry 상태** (DMZ Agent 기준):
```
runners = {
  "dmz-bojo-rcv-daejeon":          {"default": Runner(jewon-extract, obsvdata-extract, link-table-update)},
  "dmz-bojo-rcv-bytek":            {"default": Runner(jewon-extract, obsvdata-extract, link-table-update)},
  "dmz-bojo-rcv-chungnam":         {"default": Runner(...)},
  "dmz-bojo-rcv-hydronet-ara":     {"default": Runner(...)},
  "dmz-bojo-rcv-hydronet-idc":     {"default": Runner(...)},
  "dmz-bojo-rcv-hydronet-kyungnam":{"default": Runner(...)},
  "dmz-bojo-rcv-hydronet-wonju":   {"default": Runner(...)},
  "dmz-bojo-rcv-infoworld-local":  {"default": Runner(...)},
  "dmz-bojo-rcv-infoworld-seoul":  {"default": Runner(...)},
  "dmz-bojo-rcv-keunsan":          {"default": Runner(...)},
  "dmz-bojo-loader":               {"default": Runner(default-load)},
  "dmz-bojo-snd":                  {"default": Runner(snd-step)},
}
agentTypes = {
  "dmz-bojo-rcv-daejeon": "RCV",
  "dmz-bojo-rcv-bytek": "RCV",
  ...
  "dmz-bojo-loader": "LOADER",
  "dmz-bojo-snd": "SND",
}
```

### 0-4. 부팅 시 초기화 전체 흐름 요약

```
앱 시작 (Spring Boot)
  │
  ▼
[AgentConfigLoader @PostConstruct]
  config/agents/*.yml 12개 스캔
  → List<AgentDefinition> 12개 메모리 보관
  │
  ▼
[RcvPipelineConfig @PostConstruct]
  agentConfigLoader.getAgentsByType("RCV") → 10개
  각 AgentDefinition → Step 객체 생성 (YAML 설정값 주입)
  → PipelineRunner 조립 → pipelineRegistry.register()
  │
[LoaderPipelineConfig @PostConstruct]
  agentConfigLoader.getAgentsByType("LOADER") → 1개
  → Runner 조립 → register()
  │
[SndPipelineConfig @PostConstruct]
  agentConfigLoader.getAgentsByType("SND") → 1개
  → Runner 조립 → register()
  │
  ▼
PipelineRegistry 완성
  runners: 12개 agentCode → Runner 매핑
  ★ 이후 실행 요청 시 agentCode로 Runner를 꺼내 실행
```

### 객체별 역할 정리

| 객체 | 생명주기 | 역할 | 보관 내용 |
|------|---------|------|----------|
| **AgentConfigLoader** | 부팅 시 1회 | YAML 파싱 | `List<AgentDefinition>` — 원본 설정값 (테이블명, PK, 증분방식 등) |
| **RcvPipelineConfig 등** | 부팅 시 1회 | Runner 조립 | AgentDefinition → Step → PipelineRunner 생성 (일회성, 조립 후 역할 끝) |
| **PipelineRegistry** | 부팅~종료 | 라우팅 테이블 | `Map<agentCode, Runner>` — 실행 시 agentCode로 Runner 선택 |
| **PipelineRunner** | 부팅~종료 | Step 체인 실행 | Step 목록 (YAML 설정이 이미 주입된 상태) |

> **핵심**: YAML은 부팅 시 한 번만 읽혀서 객체로 변환되고, 런타임에는 메모리에 올려둔 Runner를 재사용한다.
> YAML을 수정하면 앱을 재시작해야 반영된다.

---

## 시작점: 수동 실행 vs 스케줄 실행

파이프라인 실행이 시작되는 진입점은 2가지다.

### A. 수동 실행 — 프론트엔드에서 버튼 클릭

**파일**: `infolink-orchestrator/.../execution/ExecutionController.java`

```
[L44] @PostMapping("/{id}/run")
      // 프론트에서 POST /api/executions/7/run 호출
      // {id}는 Agent PK (예: 7 = dmz-bojo-rcv-daejeon)

[L47] @RequestBody(required = false) ExecutionDto.TriggerRequest request
      // body 선택사항 — 없으면 증분 동기화, 있으면 재동기화

[L48-60] request 분기:
      if (request == null || 모든 필드 null) {
          // ① 증분 동기화 — body 없이 호출
          executionService.triggerExecution(id)        // → [L62]
      } else {
          // ② 재동기화 — startTime/endTime/filters/conditions 지정
          // conditions가 빈 배열이면 400 Bad Request (전체 데이터 긁어오기 방지)
          executionService.triggerExecution(            // → [L57-60]
              id, startTime, endTime, filters, selectedStepIds, conditions, "MANUAL")
      }
```

**TriggerRequest DTO** (`ExecutionDto.java` L100-126):
```
startTime: LocalDateTime     // 재동기화 시작 시간 (optional)
endTime: LocalDateTime       // 재동기화 종료 시간 (optional)
filters: List<Map>           // 실행 필터 (optional)
selectedStepIds: List<String> // 특정 Step만 실행 (optional)
conditions: List<Map>        // 동적 WHERE 조건 (optional, 지정 시 최소 1개)
```

### B. 스케줄 실행 — Cron 트리거

**파일**: `infolink-orchestrator/.../schedule/ScheduleExecutor.java`

```
[L45-54] @PostConstruct init()
      // 앱 시작 시 DB에서 활성화된 모든 스케줄을 로드
      List<Schedule> schedules = scheduleRepository.findEnabledSchedulesWithAgent()
      // 각 스케줄을 Spring TaskScheduler에 등록

[L69-101] registerSchedule(schedule)
      // CronTrigger 생성 → taskScheduler.schedule(task, trigger)
      // 예: cronExpression = "0 */10 * * * *" → 10분마다 실행

      ─── cron 시간 도달 ───

[L127-155] executeAgent(scheduleId, agentId)
      // ★ Cron에 의해 자동 호출되는 메서드

[L131] Schedule schedule = scheduleRepository.findById(scheduleId)

[L132-144] executionOptions JSON 파싱
      if (schedule.getExecutionOptions() != null) {
          Map options = objectMapper.readValue(executionOptions, Map.class)
          List filters = (List) options.get("filters")
          if (filters != null) {
              // 필터 포함 실행
              executionService.triggerExecution(
                  agentId, null, null, filters, "SCHEDULE")  // [L140-141]
              return
          }
      }

[L150] // 필터 없이 실행 (증분 동기화)
      executionService.triggerExecution(agentId, "SCHEDULE")
```

**Schedule 엔티티** (`Schedule.java`):
```
cronExpression: "0 */10 * * * *"   // Cron 표현식
isEnabled: true                     // 활성화 여부
executionOptions: JSON              // 조건실행 옵션 (optional)
  예: {"filters":[{"paramId":"sido","column":"sido","operator":"EQ","value":"경기도"}]}
agent: Agent (ManyToOne)            // 실행 대상 Agent
```

**수동 vs 스케줄 차이점:**
| 항목 | 수동 실행 | 스케줄 실행 |
|------|----------|------------|
| 진입점 | POST /api/executions/{id}/run | CronTrigger → executeAgent() |
| triggeredBy | "MANUAL" | "SCHEDULE" |
| startTime/endTime | 지정 가능 | 항상 null (증분) |
| conditions | 지정 가능 | null (executionOptions의 filters만) |
| selectedStepIds | 지정 가능 | null (전체 Step) |

---

## STEP 1. Orchestrator — 실행 요청 조립

**파일**: `infolink-orchestrator/.../execution/ExecutionService.java`
**메서드**: `triggerExecutionInternal()` (L369-552)

### triggerExecution 오버로드 구조

수동 실행(Controller)과 스케줄 실행(ScheduleExecutor)은 각각 다른 파라미터 조합으로 호출하지만,
실제 실행 로직은 `triggerExecutionInternal()` **한 곳에만** 존재한다.
오버로드는 호출하는 쪽이 매번 null을 나열하지 않아도 되게 하는 편의 메서드.

```
triggerExecution(id)                                              → Internal(id, null, null, null, null, null, "MANUAL")
triggerExecution(id, triggeredBy)                                  → Internal(id, null, null, null, null, null, triggeredBy)
triggerExecution(id, start, end)                                   → Internal(id, start, end, null, null, null, "MANUAL")
triggerExecution(id, start, end, triggeredBy)                      → Internal(id, start, end, null, null, null, triggeredBy)
triggerExecution(id, start, end, filters, triggeredBy)             → Internal(id, start, end, filters, null, null, triggeredBy)
triggerExecution(id, start, end, filters, stepIds, conds, trigBy)  → Internal(id, start, end, filters, stepIds, conds, trigBy)
```

**호출 예시:**
```
Controller (수동, body 없음)   → triggerExecution(7)
Controller (수동, 재동기화)     → triggerExecution(7, startTime, endTime, filters, stepIds, conditions, "MANUAL")
ScheduleExecutor (필터 없음)   → triggerExecution(7, "SCHEDULE")
ScheduleExecutor (필터 포함)   → triggerExecution(7, null, null, filters, "SCHEDULE")
```

### 동기/비동기 경계

Orchestrator의 `triggerExecutionInternal()`은 Agent에 HTTP POST를 **동기적으로 보내고**,
Agent가 요청을 수락했음(202)을 확인한 뒤 즉시 `TriggerResponse`를 프론트에 반환한다.
실제 파이프라인 실행은 Agent 쪽에서 `@Async`로 별도 스레드에서 진행되며,
결과는 나중에 콜백(POST /api/callback/finished)으로 Orchestrator에 통보된다.

```
프론트 → Orchestrator → Agent (동기 POST, 수락 확인)
프론트 ← TriggerResponse("RUNNING") 즉시 반환
         ...Agent 비동기 실행 중...
         Agent → Orchestrator 콜백 (결과 통보)
```

```
[L374-375] Agent agent = agentRepository.findById(id)
           // Orchestrator DB(agent 테이블)에서 Agent 엔티티 조회
           //   agentCode = "dmz-bojo-rcv-daejeon"
           //   sourceDatasourceId = "ext_daejeon"
           //   targetDatasourceId = "dmz"
           //   endpointUrl = "http://localhost:8082"

[L379-385] Agent 상태 체크
           if (OFFLINE) → 예외: "Agent is offline"
           if (RUNNING) → 예외: "Agent is already running"
           // ONLINE 상태일 때만 실행 가능

[L388] String executionId = agentCode + "_" + UUID.randomUUID()
       // "dmz-bojo-rcv-daejeon_bbc3c4c3-de8c-4992-8d72-1f69c9ad1729"
       // agentCode가 포함되어 콜백 시 Agent 식별 가능

[L391-393] agent.setStatus(AgentStatus.RUNNING)
           agent.setLastExecutedAt(LocalDateTime.now())
           agentRepository.save(agent)
           // Agent를 즉시 RUNNING으로 마킹 (중복 실행 방지)
```

### request Map 조립 (Agent에게 보낼 데이터)

```
[L400-404] 기본 정보
           request.put("executionId", executionId)
           request.put("agentCode", agentCode)        // "dmz-bojo-rcv-daejeon"
           request.put("agentType", "RCV")
           request.put("triggeredBy", "MANUAL")        // 또는 "SCHEDULE"

[L406-413] 시간 범위 (지정된 경우에만)
           if (startTime != null) request.put("startTime", "2026-03-18T00:00:00")
           if (endTime != null)   request.put("endTime",   "2026-03-18T23:59:59")

[L416-419] Source Datasource — ID만 전달
           Datasource src = datasourceRepository.findById("ext_daejeon")
           request.put("sourceDatasourceId", "ext_daejeon")
           request.put("sourceDbType", "POSTGRESQL")

[L421] // ★★★ "credentials는 Agent가 Proxy에서 자체 해석 (보안상 평문 전달 제거)"
       // → username/password는 request에 넣지 않음!

[L422-425] request.put("sourceZone", "EXTERNAL")
           request.put("sourceDatasourceDbId", 8)     // datasource PK (sourceRef 생성용)
           request.put("sourceZoneShortCode", "E")    // Zone 약자

[L427-494] sourceTableIds 구성
           // Agent의 SOURCE 타입 AgentTable에서 테이블명→ID 맵 구성
           // 없으면 Agent에 자동 발견 요청 (GET /api/pipeline/{agentCode}/tables)
           request.put("sourceTableIds", {sec_jewon_view: 26, sec_obsvdata_view: 27})

[L498-503] Target Datasource — 동일하게 ID만
           request.put("targetDatasourceId", "dmz")
           request.put("targetDbType", "POSTGRESQL")
           // ★ 여기도 credentials 전달 안함

[L507-522] 조건실행 파라미터 (있을 때만)
           if (filters != null)        request.put("filters", filters)
           if (selectedStepIds != null) request.put("selectedStepIds", selectedStepIds)
           if (conditions != null)      request.put("conditions", conditions)
```

### Agent에 POST 전송

```
[L397] String url = agent.getEndpointUrl() + "/api/pipeline/execute"
       // "http://localhost:8082/api/pipeline/execute"

[L527] ResponseEntity<Void> response = restTemplate.postForEntity(url, request, Void.class)
       // ★ 이 한 줄이 Agent에 실행 요청을 보내는 실제 지점
       // request Map을 JSON으로 직렬화해서 POST
       // 응답 body는 안 봄 (Void) — 2xx면 수락된 것으로 간주

[L533-540] return TriggerResponse(executionId, agentId, agentCode, "RUNNING", ...)
           // 프론트에 즉시 응답 반환 (비동기 실행)
```

### Agent에 전달되는 request Map 구성

조건에 따라 request에 포함되는 필드가 달라진다.

**항상 포함 (고정 필드):**
```json
{
  "executionId": "dmz-bojo-rcv-daejeon_bbc3c4c3-...",
  "agentCode": "dmz-bojo-rcv-daejeon",
  "agentType": "RCV",
  "triggeredBy": "MANUAL",
  "sourceDatasourceId": "ext_daejeon",
  "sourceDbType": "POSTGRESQL",
  "sourceZone": "EXTERNAL",
  "sourceZoneShortCode": "E",
  "sourceDatasourceDbId": 8,
  "sourceTableIds": {"sec_jewon_view": 26, "sec_obsvdata_view": 27},
  "targetDatasourceId": "dmz",
  "targetDbType": "POSTGRESQL"
}
// ★ username, password 없음!
```

**조건부 포함 (지정된 경우에만 추가):**

| 필드 | 추가 조건 | 예시 값 | 설정 주체 |
|------|----------|---------|----------|
| `startTime` | 수동 재동기화 시 지정 | `"2026-03-18T00:00:00"` | Controller (TriggerRequest) |
| `endTime` | 수동 재동기화 시 지정 | `"2026-03-18T23:59:59"` | Controller (TriggerRequest) |
| `filters` | 수동 실행 또는 스케줄의 executionOptions | `[{"paramId":"sido","column":"sido","operator":"EQ","value":"경기도"}]` | Controller 또는 ScheduleExecutor |
| `selectedStepIds` | 수동 실행에서 특정 Step만 선택 시 | `["jewon-extract"]` | Controller (TriggerRequest) |
| `conditions` | 수동 실행에서 WHERE 조건 지정 시 | `[{"column":"obsv_code","operator":"LIKE","value":"GPM-305%"}]` | Controller (TriggerRequest) |

**실행 시나리오별 request 차이:**

| 시나리오 | triggeredBy | startTime/endTime | filters | conditions | selectedStepIds |
|----------|------------|-------------------|---------|------------|-----------------|
| 수동 증분 (body 없음) | MANUAL | - | - | - | - |
| 수동 재동기화 (시간범위) | MANUAL | O | - | - | - |
| 수동 조건실행 (WHERE) | MANUAL | - | - | O | - |
| 수동 Step 선택 | MANUAL | - | - | - | O |
| 수동 복합 (전부 지정) | MANUAL | O | O | O | O |
| 스케줄 증분 | SCHEDULE | - | - | - | - |
| 스케줄 필터 | SCHEDULE | - | O | - | - |

**실패 시** (L542-551):
```
Agent 상태 복구: RUNNING → ONLINE, lastExecutionStatus = "FAILED"
RuntimeException 발생 → 프론트에 500 응답
```

---

## STEP 2. Agent 수신 — PipelineController

**파일**: `infolink-agent-bojo-dmz/.../controller/PipelineController.java`

```
[L41-42] @PostMapping("/execute")
         public ResponseEntity<Map<String, Object>> execute(@RequestBody Map<String, Object> request)
         // Orchestrator가 보낸 request Map을 수신

[L48-49] String executionId = (String) request.get("executionId")
         String agentCode = (String) request.get("agentCode")
         // "dmz-bojo-rcv-daejeon"

[L63-67] pipelineRegistry.getRegisteredAgentCodes().contains(agentCode)
         // 등록된 agentCode인지 확인
         // config/agents/dmz-bojo-rcv-daejeon.yml 기반으로 부팅 시 등록됨

[L70-71] Map<String, Object> params = new HashMap<>(request)
         // request 복사 → params로 사용

[L74-86] startTime/endTime 처리
         // 문자열 → LocalDateTime 파싱
         // 24시간 범위 이내 검증

[L89] pipelineService.executeAsync(executionId, params)
      // ★ 비동기 실행 — 즉시 202 Accepted 응답 반환
      // 이후 별도 스레드(Bojo-Pipeline-1)에서 실행
```

---

## STEP 3. 파이프라인 시작 — PipelineService

**파일**: `infolink-agent-bojo-dmz/.../pipeline/PipelineService.java`

### executeAsync() (L53-95)

```
[L53] @Async("pipelineExecutor")
      // 별도 스레드풀에서 실행

[L59] String agentCode = (String) params.get("agentCode")
      // "dmz-bojo-rcv-daejeon"

[L78] PipelineRunner baseRunner = pipelineRegistry.getRunner(agentCode)
      // PipelineRegistry에서 이 agentCode에 매핑된 Runner(Step 체인) 조회
      // dmz-bojo-rcv-daejeon.yml → RCV Runner (jewon-extract, obsvdata-extract, link-table-update)

[L81-83] OrchestratorClient orchestratorClient = new OrchestratorClient(orchestratorUrl, agentCode)
         CompositeStepCallback callback = new CompositeStepCallback(orchestratorClient)
         PipelineRunner runner = baseRunner.withProgressCallback(callback)
         // 실행 결과 콜백용 클라이언트 생성 (실행마다 새로 생성, stateless)

[L85] executeWithRunner(runner, orchestratorClient, executionId, agentCode, params)
```

### executeWithRunner() (L97-171) — DB 연결 해석 핵심

```
[L102-103] String sourceDatasourceId = (String) params.get("sourceDatasourceId")  // "ext_daejeon"
           String targetDatasourceId = (String) params.get("targetDatasourceId")  // "dmz"

[L109-110] sourceInfo = syncDataSourceService.resolveFromProxy(sourceDatasourceId)
           // ★★★ Proxy에 connection-info 요청 → STEP 4로

[L115-116] targetInfo = syncDataSourceService.resolveFromProxy(targetDatasourceId)
           // ★★★ target도 동일하게 Proxy에서 해석

[L121] syncDataSourceService.setCurrentDatasources(sourceInfo, targetInfo)
       // ThreadLocal에 source/target DataSourceInfo 저장
       // 이후 Step들이 getJdbcTemplate()으로 접근 가능

[L122-124] 로그: "Pipeline datasource configured - source: ext_daejeon (localhost:29000), target: dmz (localhost:29001)"

[L127-134] Connection 풀 상태 검사
           String sourcePoolIssue = syncDataSourceService.checkPoolHealth(sourceDatasourceId)
           // 기존 풀이 있으면 active/waiting/closed 상태 확인
           // 문제 시 즉시 예외 (풀 고갈 방지)

[L137] executionService.startExecution(executionId, agentCode, ...)
       // Agent 로컬 DB(execution 테이블)에 실행 시작 기록

[L140-141] String triggeredBy = params.get("triggeredBy")  // "MANUAL" or "SCHEDULE"
           orchestratorClient.notifyStarted(executionId, triggeredBy)
           // ★ Orchestrator에 "시작됨" 콜백 → STEP 8A

[L144-145] Map enrichedParams = new HashMap<>(params)
           enrichedParams.put("agentZone", agentZone)  // "DMZ"

[L148] result = runner.run(executionId, enrichedParams)
       // ★★★ 파이프라인 Step 실행 시작 → STEP 7로

[L150] executionService.finishExecution(executionId, result)
       // Agent 로컬 DB에 결과 기록

[L152] log.info("Pipeline completed: {} with status {}", executionId, result.getStatus())

[L164-168] finally 블록:
           orchestratorClient.notifyFinished(result)
           // ★ Orchestrator에 "완료됨" 콜백 → STEP 8B

[L169] syncDataSourceService.clearCurrentDatasources()
       // ThreadLocal 정리 (메모리 누수 방지)
```

---

## STEP 4. Proxy에 자격증명 요청 — SyncDataSourceService

**파일**: `infolink-agent-bojo-dmz/.../config/SyncDataSourceService.java`

### resolveFromProxy() (L158-178)

```
[L160-163] 캐시 확인
           DataSourceInfo cached = cachedDataSourceInfos.get("ext_daejeon")
           if (cached != null) {
               log.debug("DataSource resolved from cache: ext_daejeon")
               return cached
           }
           // 앱 재시작 전까지 메모리에 유지 — 반복 실행 시 Proxy 호출 생략

[L167-168] Proxy URL 검증
           if (proxyUrl == null || proxyUrl.isEmpty()) {
               throw IllegalStateException("proxy-url 미설정. 자격증명 해석 불가")
           }
           // ★ proxy-url 미설정이면 즉시 실패 (Orchestrator fallback 없음)

[L171] DataSourceInfo info = fetchConnectionInfoFromProxy("ext_daejeon")
       // → STEP 4-1로

[L172-174] if (info != null) {
               cachedDataSourceInfos.put("ext_daejeon", info)  // 캐시 저장
               return info
           }

[L177] throw IllegalStateException("Proxy에서 datasource 해석 실패: ext_daejeon")
```

### fetchConnectionInfoFromProxy() (L180-217) — HTTP 요청

```
[L182] String url = proxyUrl + "/api/datasources/" + datasourceId + "/connection-info"
       // "http://localhost:8083/api/datasources/ext_daejeon/connection-info"

[L185-189] HTTP 요청 준비
           RestTemplate restTemplate = new RestTemplate()
           HttpHeaders headers = new HttpHeaders()
           if (proxyApiKey != null && !proxyApiKey.isEmpty()) {
               headers.set("X-API-Key", proxyApiKey)
           }
           // API Key 인증 헤더 첨부 (Proxy의 ApiKeyFilter가 검증)

[L190-192] ResponseEntity<Map> responseEntity = restTemplate.exchange(
               url, HttpMethod.GET, new HttpEntity<>(headers), Map.class)
           Map<String, Object> response = responseEntity.getBody()
           // ★ Proxy에 GET 요청 전송 → STEP 5로

[L194-197] 응답 유효성 검사
           if (response == null || response.isEmpty()) {
               log.warn("Empty response from Proxy")
               return null
           }
```

---

## STEP 5. Proxy 패스스루 — ConnectionInfoController

**파일**: `infolink-proxy-dmz/.../controller/ConnectionInfoController.java`

```
[L37-39] @GetMapping("/{datasourceId}/connection-info")
         // Agent로부터 GET /api/datasources/ext_daejeon/connection-info 수신
         // (ApiKeyFilter가 X-API-Key 헤더 먼저 검증 → 통과 후 여기 도달)

[L40] String url = orchestratorUrl + "/api/datasources/" + datasourceId + "/connection-info"
      // "http://localhost:8080/api/datasources/ext_daejeon/connection-info"
      // ★ baseUrl만 Orchestrator로 교체, 경로는 동일 (패스스루)

[L44-45] RestTemplate restTemplate = new RestTemplate()
         Map<String, Object> response = restTemplate.getForObject(url, Map.class)
         // ★ Orchestrator에 GET 요청 → STEP 6으로

[L54] return ResponseEntity.ok(response)
      // ★★★ Orchestrator 응답을 그대로 Agent에게 반환 (복호화 안함!)
      // username/password는 암호문 상태 그대로 전달
```

---

## STEP 6. Orchestrator가 암호문 응답 — DatasourceService

**파일**: `infolink-orchestrator/.../datasource/DatasourceController.java`
**파일**: `infolink-orchestrator/.../datasource/DatasourceService.java`

```
[DatasourceController L72-74]
      @GetMapping("/{datasourceId}/connection-info")
      return ResponseEntity.ok(datasourceService.getConnectionInfo(datasourceId))
      // Proxy로부터 GET /api/datasources/ext_daejeon/connection-info 수신
```

### getConnectionInfo() (L58-70)

```
[L59-60] Datasource ds = datasourceRepository.findById("ext_daejeon")
         // Orchestrator DB(datasource 테이블)에서 조회
         // username/password는 PasswordEncryptor.encrypt()로 암호화 저장되어 있음

[L61-69] return DatasourceDto.ConnectionInfo.builder()
             .datasourceId("ext_daejeon")
             .dbType("POSTGRESQL")
             .host("localhost")
             .port(29000)
             .databaseName("daejeon")
             .username(ds.getUsername())   // ★ "UX686v..." (암호문 그대로!)
             .password(ds.getPassword())   // ★ "lZWvVh..." (암호문 그대로!)
             .build()

// ★★★ decrypt() 호출 없음 — DB에 저장된 암호문을 그대로 반환
// 주석(L55-57): "Agent 내부용 연결 정보 조회 (암호문 그대로 전달)"
```

**응답 JSON** (Proxy → Agent로 전달):
```json
{
  "datasourceId": "ext_daejeon",
  "dbType": "POSTGRESQL",
  "host": "localhost",
  "port": 29000,
  "databaseName": "daejeon",
  "username": "UX686vTXxVDGh6V3QRQNwOkdwnXnzlobH/QkTv0BHL4EesP0ea8c7IRXljUgExfw",
  "password": "lZWvVh41UtYMaH3ZT5TxK4IYsW/ftnJ8ccvO5HZ2/oLQwpHP8HSrECxmK9Ty1Y5o"
}
```

---

## STEP 7-1. Agent가 암호문 복호화 → DataSource 생성

**파일**: `infolink-agent-bojo-dmz/.../config/SyncDataSourceService.java`
(STEP 4의 fetchConnectionInfoFromProxy 계속)

### 암호문 복호화 + DataSourceInfo 빌드 (L199-212)

```
[L199-208] DataSourceInfo info = DataSourceInfo.builder()
               .datasourceId("ext_daejeon")
               .dbType("POSTGRESQL")
               .host("localhost")
               .port(29000)
               .databaseName("daejeon")
               .username(passwordEncryptor.decrypt("UX686v..."))  // ★★★ 복호화! → "k1m"
               .password(passwordEncryptor.decrypt("lZWvVh..."))  // ★★★ 복호화! → "1111"
               .build()

// PasswordEncryptor: Jasypt PBEWithHMACSHA512AndAES_256
// 키: jasypt.encryptor.password (모든 모듈 동일 키 공유)

[L210-211] log.info("Fetched datasource from Proxy: ext_daejeon (localhost:29000)")
```

### 캐시 저장 (resolveFromProxy로 돌아와서)

```
[L173] cachedDataSourceInfos.put("ext_daejeon", info)
       // 복호화된 DataSourceInfo를 메모리 캐시에 저장
       // 다음 실행 시 Proxy 호출 생략 (앱 재시작 시 초기화)
```

### ThreadLocal 설정 (executeWithRunner로 돌아와서)

**ThreadLocal을 쓰는 이유**:
Agent는 하나의 앱이지만 `@Async`로 파이프라인을 실행하므로, 2개 Agent가 동시 실행될 수 있다.
```
Bojo-Pipeline-1 스레드: dmz-bojo-rcv-daejeon 실행 중 (source=ext_daejeon)
Bojo-Pipeline-2 스레드: dmz-bojo-rcv-bytek 실행 중 (source=ext_bytek)
```
`ThreadLocal`은 스레드마다 독립적인 변수 공간이므로, 각 스레드가 `.set()`한 값은 해당 스레드에서만 보인다.
덕분에 Step 코드에서 별도 인덱싱(executionId나 agentCode 전달) 없이 `getSourceDatasourceId()`만 호출하면
자기 파이프라인의 datasource가 나온다. Step이 아무리 깊어져도 파라미터로 끌고 다닐 필요가 없다.

```
파이프라인 시작 → set() 한 번
  → Step 체인 전체에서 get() (자기 스레드 값만 반환)
  → 끝나면 clear() (스레드풀 재사용 시 이전 데이터 잔류 방지)
```

```
[L121] syncDataSourceService.setCurrentDatasources(sourceInfo, targetInfo)

[setCurrentDatasources L267-278]
       currentSourceDatasource.set(sourceInfo)   // ThreadLocal 저장 (이 스레드 전용)
       cachedDataSourceInfos.put("ext_daejeon", sourceInfo)  // 캐시에도 저장 (전체 공유)
       currentTargetDatasource.set(targetInfo)   // ThreadLocal 저장 (이 스레드 전용)
       cachedDataSourceInfos.put("dmz", targetInfo)
```

### HikariCP DataSource 생성 (Step 실행 중 getJdbcTemplate 최초 호출 시)

```
[getJdbcTemplate L109-110]
       findDataSourceInfo("ext_daejeon") → 캐시에서 발견
       return jdbcTemplates.computeIfAbsent("ext_daejeon", this::createJdbcTemplate)
       // 첫 호출 → createJdbcTemplate → createDataSource

[createDataSource L123-147]
       HikariConfig hikariConfig = new HikariConfig()
       hikariConfig.setPoolName("BojoPool-ext_daejeon")
       hikariConfig.setJdbcUrl("jdbc:postgresql://localhost:29000/daejeon")  // DataSourceInfo.getJdbcUrl()
       hikariConfig.setUsername("k1m")       // 평문 (복호화 완료)
       hikariConfig.setPassword("1111")       // 평문 (복호화 완료)
       hikariConfig.setMaximumPoolSize(10)
       hikariConfig.setMinimumIdle(2)
       hikariConfig.setConnectionTimeout(10_000)       // 10초
       hikariConfig.setMaxLifetime(600_000)             // 10분
       hikariConfig.setKeepaliveTime(120_000)           // 2분
       hikariConfig.setConnectionTestQuery("SELECT 1")
       hikariConfig.setLeakDetectionThreshold(60_000)   // 60초
       HikariDataSource ds = new HikariDataSource(hikariConfig)
       // ★ 실제 DB 커넥션 풀 생성 완료
```

---

## STEP 7-2. 파이프라인 Step 실행 — PipelineRunner

**파일**: `infolink-agent-common/.../pipeline/PipelineRunner.java`
**메서드**: `run()` (L42-247)

```
[L55-59] StepContext context 생성
         context.executionId = executionId
         context.pipelineId = pipelineId
         context.params = params

[L61-88] params에서 정보 추출 → context에 설정
         context.sourceDatasourceId = "ext_daejeon"
         context.targetDatasourceId = "dmz"
         context.sourceZone = "EXTERNAL"
         context.sourceZoneShortCode = "E"
         context.sourceDatasourceDbId = 8
         context.agentZone = "DMZ"

[L89-105] sourceTableIds 변환
          context.sourceTableIds = {sec_jewon_view: 26, sec_obsvdata_view: 27}

[L107-124] startTime/endTime 파싱
           String → LocalDateTime 변환 (있는 경우만)

[L126-128] ExecutionOptions 생성
           filters, conditions, timeRange를 구조화된 객체로 변환
```

### Step 구현체가 결정되는 시점 — 부팅 시 PipelineConfig

Runner의 `steps` 리스트에는 **부팅 시 이미 구체적인 Step 객체**가 들어가 있다.
런타임에 if/switch로 분기하는 게 아니라, PipelineConfig에서 `new SourceToIfStep(...)` 또는
`new InternalLoadStep(...)`으로 생성한 객체가 리스트에 담긴다.

**RCV (DMZ) — RcvPipelineConfig.createRcvRunner():**
```
[L60-70] ExtractStepConfig jewonConfig = ... .sourceTable("sec_jewon_view") ...
         // YAML의 jewon 섹션 값으로 config 생성

[L71-72] SourceToIfStep jewonStep = new SourceToIfStep(jewonConfig, dataSourceProvider, ...)
         // ★ 구체적 Step 객체 생성 (StepExecutor 구현체)

[L84-93] obsvConfig = ... .extractType(CUSTOM_STAGING) .customDataFetcher(fetcher) ...
[L107]   SourceToIfStep obsvStep = new SourceToIfStep(obsvConfig, ...)
         // ★ 같은 클래스, 다른 config

[L114]   LinkTableUpdateStep linkStep = new LinkTableUpdateStep(...)
         // ★ 별도 Step 구현체

[L116]   steps = List.of(jewonStep, obsvStep, linkStep)
         // ★★★ 이 리스트가 Runner에 들어감 — 순서 고정

[L121]   return new PipelineRunner(agentCode, steps)
         // Runner.steps = [SourceToIfStep, SourceToIfStep, LinkTableUpdateStep]
```

**Loader (Internal) — LoaderPipelineConfig:**
```
[L55-66] InternalLoadStep loadStep = new InternalLoadStep(stepId, ifObsvTable, targetJewonTable, ...)
         // ★ InternalLoadStep 객체 (StepExecutor 구현체)

[L68]    PipelineRunner runner = new PipelineRunner(agentCode, List.of(loadStep))
         // Runner.steps = [InternalLoadStep]
```

**각 Runner의 steps 내용물:**
```
dmz-bojo-rcv-daejeon의 Runner.steps = [
    SourceToIfStep(jewon config),        // 0번 — SIMPLE_COPY
    SourceToIfStep(obsvdata config),     // 1번 — CUSTOM_STAGING
    LinkTableUpdateStep(link config)     // 2번
]

internal-bojo-loader의 Runner.steps = [
    InternalLoadStep(loader config)      // 0번
]

dmz-bojo-snd의 Runner.steps = [
    SourceToIfStep(jewon config),        // 0번 — SIMPLE_COPY
    SourceToIfStep(obsvdata config)      // 1번 — SIMPLE_COPY
]
```

### Step 순차 실행 루프 (L153-233)

Runner는 Step의 내부 구현을 구분하지 않는다. `StepExecutor` 인터페이스의 `execute(context)`만 호출.
`step.execute(context)` 호출 시 Java 다형성에 의해 해당 객체의 메서드가 실행된다.
SourceToIfStep이면 SourceToIfStep.execute(), InternalLoadStep이면 InternalLoadStep.execute().
SIMPLE_COPY / CUSTOM_STAGING 분기도 Step 내부(SourceToIfStep L160-170)에서 처리된다.

```
for (StepExecutor step : steps) {
    // ① dmz-bojo-rcv-daejeon의 경우 3개 Step:
    //    jewon-extract (SourceToIfStep)      — 내부에서 SIMPLE_COPY
    //    obsvdata-extract (SourceToIfStep)   — 내부에서 CUSTOM_STAGING (Link 기반)
    //    link-table-update (LinkTableUpdateStep) — Link 테이블 갱신

    [L154-174] selectedStepIds 확인
               // 사용자가 특정 Step만 선택했으면 나머지 SKIP

    [L176-185] 진행 콜백
               callback.onStepStarted(executionId, step.getStepId(), stepIndex, totalSteps)
               log.info("Step [{}] started. ({}/{})", stepId, stepIndex, totalSteps)

    [L188-189] ★ Step 실행
               StepResult result = step.execute(context)
               // → STEP 7-3로 (SourceToIfStep의 경우)

    [L191-203] 완료 콜백
               callback.onStepFinished(executionId, step.getStepId(), result, stepIndex, totalSteps)
               log.info("Step [{}] completed. status={}, read={}, write={}, duration={}ms",
                   stepId, status, readCount, writeCount, durationMs)
               stepResults.add(result)

    [L205-210] 실패 감지
               if (result.getStatus() == FAILED) {
                   finalStatus = FAILED
                   break  // ★ First-failure-stop: 한 Step 실패 시 전체 중단
               }
}
```

### 결과 반환 (L235-246)

```
return PipelineResult.builder()
    .executionId(executionId)
    .pipelineId(pipelineId)
    .status(finalStatus)           // SUCCESS 또는 FAILED
    .stepResults(stepResults)       // 모든 Step 결과 목록
    .totalDurationMs(totalDuration)
    .errorMessage(errorMessage)
    .build()
```

---

## STEP 7-3. 데이터 추출/적재 — SourceToIfStep

**파일**: `infolink-agent-common/.../step/SourceToIfStep.java`
**메서드**: `execute()` (L121-417)

### Phase 0: 준비 (L130-153)

```
[L131] resolveColumns(context)
       // YAML config OR DB 메타데이터에서 컬럼 목록 조회

[L137-139] SELECT용 컬럼 결정
           // IF 메타 컬럼(source_refs, link_status, extracted_at 등) 제외
           // id는 포함 (PK 추적용)

[L142-144] INSERT용 컬럼 결정
           // SELECT 컬럼에서 auto-increment PK(id) 제외 (IF 테이블 중복 방지)
           // + source_refs, link_status, extracted_at, updated_at, execution_id 추가

[L149-152] conditions 감지 시 skipLinkUpdate 플래그 설정
           // 조건실행에서는 link 테이블 업데이트 불필요
```

### Phase 1: Source 데이터 조회 (L155-173)

```
[L160-170] 분기:
           if (CUSTOM_STAGING && !useSimpleCopy) {
               records = config.getCustomDataFetcher().fetch(context)
               // Link 테이블 기반 증분 동기화 (LinkTableObsvDataFetcher 등)
           } else {
               records = fetchSimpleCopy(context, selectColumns)
               // 직접 SQL 조회 (전체 복사 / 조건 실행 / 시간 범위)
           }

[fetchSimpleCopy L566-567]
           String sourceDsId = context.getSourceDatasourceId()  // "ext_daejeon"
           JdbcTemplate sourceJdbc = dataSourceProvider.getJdbcTemplate(sourceDsId)
           // ★ 여기서 STEP 7-1의 HikariCP DataSource가 사용됨

[L576-598] WHERE 조건 구성
           // fullCopy가 아니면: WHERE link_status IN ('PENDING','RESYNC','FAILED')
           // startTime/endTime 있으면: WHERE obsv_date BETWEEN ? AND ?
           // conditions 있으면: 사용자 지정 WHERE 조건

[L618-620] String sql = "SELECT " + columnList + " FROM " + table + where.toWhereSql()
           List<Map<String, Object>> records = sourceJdbc.queryForList(sql, where.getParamsArray())
           // ★ Source DB에서 데이터 조회 실행!
```

### Phase 1.1: 파라미터 사전 준비 (L241-286)

```
for (Map<String, Object> record : records) {
    [L249] params.add(record.get(column))  // 각 컬럼 값 수집

    [L256] sourceRef = SourceRefUtils.build(context, sourceTable, pkValue)
           // 형식: "E:8:26:{pk값}" (zone:dsDbId:tableId:pk)
           // Source 추적용 고유 식별자

    [L269-275] linkStatus = isResyncExecution ? "RESYNC" : "PENDING"

    [L278-281] extracted_at, updated_at, execution_id 추가
}
```

### Phase 2: Target IF 테이블에 배치 UPSERT (L289-356)

```
[L211] JdbcTemplate targetJdbc = dataSourceProvider.getJdbcTemplate(targetDsId)
       // Target DataSource (dmz) 사용 — "dmz"는 Agent 자체 로컬 DB

[L225] String insertSql = buildUpsertSql(table, columns, dbType, useUpsert)
       // INSERT INTO "if_rsv_sec_jewon" (...) VALUES (?, ?, ...)
       //   ON CONFLICT ("source_refs") DO UPDATE SET ...

[L290-301] 배치 단위 실행 (기본 1000건씩)
           for (batchStart = 0; batchStart < totalCount; batchStart += batchSize) {
               int[] results = targetJdbc.batchUpdate(insertSql, batchParams)
               // ★ IF 테이블에 데이터 적재!
           }

[L320-350] 배치 실패 시 개별 실행 fallback
           for (each record) {
               try { targetJdbc.update(insertSql, params) }
               catch { failedPkValues.add(key) }
           }
```

### Phase 3: Source link_status 업데이트 (L361-383)

```
[L364] JdbcTemplate sourceJdbc = dataSourceProvider.getJdbcTemplate(sourceDsId)
       // ★ Source DB(ext_daejeon)에 다시 접근

[L370-371] 성공 레코드: UPDATE source_table SET link_status = 'SUCCESS' WHERE pk IN (...)
[L376-377] 실패 레코드: UPDATE source_table SET link_status = 'FAILED' WHERE pk IN (...)
```

### Phase 4: SyncLog 저장 (L385-389)

```
saveSyncLogMapping(executionId, readCount, writeCount, skipCount, failedKeys, error, pkColumn)
// Agent 로컬 DB(sync_log 테이블)에 매핑별 동기화 결과 기록
```

### StepResult 반환 (L391-400)

```
return StepResult.builder()
    .stepId("jewon-extract")
    .status(SUCCESS)
    .readCount(402)
    .writeCount(402)
    .skipCount(0)
    .durationMs(1448)
    .build()
// → PipelineRunner의 Step 루프로 반환 → 다음 Step 실행
```

---

## STEP 8. Orchestrator 콜백 — 결과 기록

**파일 (Agent)**: `infolink-agent-common/.../client/OrchestratorClient.java`
**파일 (Orchestrator)**: `infolink-orchestrator/.../callback/CallbackController.java`
**파일 (Orchestrator)**: `infolink-orchestrator/.../callback/CallbackService.java`

### 8A. 시작 콜백

```
[Agent — OrchestratorClient L56-69]
      POST {orchestratorUrl}/api/callback/started
      Body: { executionId, agentId, startedAt, triggeredBy }
      // 3회 재시도, 지연: 2s × 시도번호

[Orchestrator — CallbackService L38-66]
      handleStarted(request):
      [L45-46] Agent agent = agentRepository.findByAgentCode(agentCode)
      [L49-51] agent.setStatus(RUNNING), save
      [L54-63] ExecutionHistory INSERT
               executionId, agentCode, status=RUNNING, startedAt, triggeredBy
```

### 8B. 완료 콜백

```
[Agent — OrchestratorClient L72-106]
      POST {orchestratorUrl}/api/callback/finished
      Body: {
          executionId, agentId, status, finishedAt,
          totalReadCount, totalWriteCount, totalSkipCount, durationMs,
          errorMessage,
          stepResults: [
              { stepId, status, readCount, writeCount, skipCount, durationMs, errorMessage, stepOrder }
          ]
      }

[Orchestrator — CallbackService L73-126]
      handleFinished(request):
      [L83-87] agent.setStatus(ONLINE), lastExecutionStatus = "SUCCESS", save
               // RUNNING → ONLINE 복원
      [L90-104] ExecutionHistory UPDATE
                status=SUCCESS, totalReadCount/Write/Skip, durationMs, finishedAt
      [L107-121] ExecutionStepHistory INSERT (Step별)
                 각 Step의 stepId, status, readCount, writeCount, durationMs, stepOrder
```

---

## 전체 시퀀스 요약

```
[프론트/스케줄]
   POST /api/executions/7/run (수동)
   또는 CronTrigger (스케줄)
        │
        ▼
[STEP 1] Orchestrator — ExecutionService.triggerExecutionInternal()
         agent 테이블에서 sourceDatasourceId="ext_daejeon" 조회
         Agent 상태 → RUNNING
         POST /api/pipeline/execute → Agent에 전달 (ID만, 자격증명 없음)
              │
              ▼
[STEP 2] Agent — PipelineController.execute()
         request 수신, params 복사
         pipelineService.executeAsync() 비동기 호출
              │
              ▼
[STEP 3] Agent — PipelineService.executeWithRunner()
         params에서 sourceDatasourceId 추출
         resolveFromProxy("ext_daejeon") 호출
              │
              ▼
[STEP 4] Agent — SyncDataSourceService.resolveFromProxy()
         캐시 miss → Proxy에 GET 요청
         GET http://proxy:8083/api/datasources/ext_daejeon/connection-info
         (X-API-Key 헤더 포함)
              │
              ▼
[STEP 5] Proxy — ConnectionInfoController
         Orchestrator에 동일 경로로 GET 요청 (패스스루)
         GET http://orchestrator:8080/api/datasources/ext_daejeon/connection-info
              │
              ▼
[STEP 6] Orchestrator — DatasourceService.getConnectionInfo()
         datasource 테이블에서 조회
         username/password 암호문 그대로 JSON 응답
              │
              ▼
         Proxy → Agent에 암호문 그대로 전달 (복호화 안함)
              │
              ▼
[STEP 7-1] Agent — SyncDataSourceService.fetchConnectionInfoFromProxy()
           PasswordEncryptor.decrypt()로 username/password 복호화
           DataSourceInfo 생성 → 캐시 저장
           HikariCP DataSource 생성 (평문 자격증명으로 DB 연결)
              │
              ▼
[STEP 7-2] Agent — PipelineRunner.run()
           StepContext 생성 (datasourceId, sourceTableIds, conditions 등)
           Step 순차 실행 루프 (first-failure-stop)
              │
              ▼
[STEP 7-3] Agent — SourceToIfStep.execute()
           Source DB에서 SELECT (ext_daejeon → sec_jewon_view)
           Target IF 테이블에 UPSERT (dmz → if_rsv_sec_jewon)
           Source link_status 업데이트
           SyncLog 저장
              │
              ▼
[STEP 8A] Agent → Orchestrator 시작 콜백
          POST /api/callback/started
          ExecutionHistory INSERT (RUNNING)

[STEP 8B] Agent → Orchestrator 완료 콜백
          POST /api/callback/finished
          Agent 상태 → ONLINE
          ExecutionHistory UPDATE (SUCCESS/FAILED)
          ExecutionStepHistory INSERT (Step별 결과)
```

---

## 자격증명의 상태 변화 추적

```
Orchestrator DB     : 암호문 저장 (PasswordEncryptor.encrypt 시점)
        ↓ getConnectionInfo() — decrypt 안함
Orchestrator 응답   : 암호문 그대로
        ↓ HTTP
Proxy 수신          : 암호문 그대로
        ↓ 패스스루 — decrypt 안함
Agent 수신          : 암호문 그대로
        ↓ PasswordEncryptor.decrypt()
Agent DataSourceInfo: 평문 (메모리 캐시)
        ↓
HikariCP            : 평문으로 DB 연결
```

---

## 통신 경로 정리

```
Orchestrator → Agent:    실행 트리거 (POST /api/pipeline/execute)
Agent → Proxy:           자격증명 요청 (GET /api/datasources/{id}/connection-info)
Proxy → Orchestrator:    패스스루 (GET /api/datasources/{id}/connection-info)
Agent → Orchestrator:    실행 콜백 (POST /api/callback/started, /finished)
Orchestrator → Proxy:    DB 메타데이터 조회 (search-tables, search-columns)
                         실행 데이터 조회 (execution-data)
```

**원칙**: Agent가 자격증명을 얻는 경로는 반드시 Proxy 경유. Orchestrator 직접 조회 불가.
