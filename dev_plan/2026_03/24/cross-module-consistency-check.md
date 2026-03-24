# 모듈 간 코드 일관성 점검 결과

## 점검 대상
- sync-orchestrator/backend (Orchestrator)
- sync-agent-bojo (DMZ Agent)
- sync-agent-bojo-int (Internal Agent)
- sync-agent-common (공통 라이브러리)
- infolink-api-collector (API 수집 모듈)

---

## A. 수정 권장 (일관성 영향 큼) >> 모두 시행토록

### 1. 로그 언어 혼재
- **현황**: Agent 모듈은 영어 로그, API Collector는 한글, Orchestrator는 한영 혼재
- **예시**:
  - Agent: `[Bojo] Pipeline execute request: {}`, `[Bojo] Pipeline execution failed to start`
  - API Collector: `스케줄 실행: scheduleId={}, endpointId={}`
  - Orchestrator AgentService: `"이미 등록된 agentCode입니다"` vs `"Retention config JSON 파싱 실패"`
- **위치**: Agent PipelineController, PipelineService 전반 / Orchestrator AgentService
- **제안**: 전체 한글 통일

### 2. Internal Agent `runningAgentCodes` 추적 누락
- **현황**: DMZ Agent는 현재 실행 중인 agentCode를 Set으로 추적, Internal Agent에는 없음
- **위치**:
  - DMZ: `sync-agent-bojo/.../pipeline/PipelineService.java` (L232-234)
  - Internal: `sync-agent-bojo-int/.../pipeline/PipelineService.java` — 없음
- **영향**: Internal HealthController에서 실행 중인 Agent 정보 제공 불가

### 3. Internal Agent finally 블록 누락
- **현황**: DMZ는 파이프라인 실행 후 finally에서 `runningAgentCodes.remove()` 수행, Internal은 finally 자체가 없음
- **위치**:
  - DMZ: `sync-agent-bojo/.../pipeline/PipelineService.java` (L93) — `finally { runningAgentCodes.remove(finalAgentCode); }`
  - Internal: `sync-agent-bojo-int/.../pipeline/PipelineService.java` — finally 없음
- **영향**: #2와 연동, runningAgentCodes가 없으니 finally도 없는 상태

### 4. HealthController 응답 구조 차이
- **현황**:
  - DMZ: `rcvAgents`, `loaderAgents`, `sndAgents`, `runningAgents` 모두 반환
  - Internal: `rcvAgents` 필수, `loaderAgents` 조건부, `sndAgents`/`runningAgents` 없음
- **위치**:
  - DMZ: `sync-agent-bojo/.../controller/HealthController.java` (L43-49)
  - Internal: `sync-agent-bojo-int/.../controller/HealthController.java` (L40-46)
- **제안**: Internal도 보유한 에이전트 타입 전부 반환하는 구조로 통일

### 5. Exception 메시지 언어 혼재
- **현황**: Orchestrator AgentService에서 한영 섞여 있음
- **예시**:
  - `throw new IllegalArgumentException("이미 등록된 agentCode입니다: " + agentCode);`
  - `throw new IllegalArgumentException("Retention config JSON 파싱 실패: " + e.getMessage());`
- **위치**: `sync-orchestrator/backend/.../agent/AgentService.java` (L62, L337)
- **제안**: 한글 통일

---

## B. 선택적 (코드 스타일)

### 6. Agent 응답 패턴: Map vs DTO
- **현황**: Agent PipelineController는 `Map<String, Object>` 수동 구성, Orchestrator/API Collector는 전용 DTO 사용
- **예시**:
  ```java
  // Agent — 수동 Map
  response.put("accepted", true);
  response.put("executionId", executionId);
  return ResponseEntity.accepted().body(response);

  // API Collector — DTO
  return ResponseEntity.status(HttpStatus.CREATED).body(endpointService.create(request));
  ```
- **위치**: `sync-agent-bojo/.../controller/PipelineController.java` (L99, 149, 166)
- **참고**: Agent 응답은 단순 구조라 Map이 실용적일 수 있음 >> 기능적으로 이게 이렇게 구성된 이유가 있을까?

### 7. Async ThreadPool 설정 차이
- **현황**: DMZ `core=6, max=15, queue=50`, Internal `core=4, max=8, queue=20`
- **위치**: 각 모듈 `AsyncConfig.java`
- **참고**: 의도적 차이일 가능성 있음 (DMZ가 더 많은 Agent 처리)

### 8. RestTemplate 타임아웃
- **현황**: API Collector만 명시적 설정 (connect=10s, read=30s), 나머지 모듈은 기본값
- **위치**: `infolink-api-collector/.../config/RestTemplateConfig.java` (L14-19)
- **제안**: Orchestrator/Proxy에서도 타임아웃 명시 검토

### 9. Lombok 어노테이션 스타일
- **현황**: Orchestrator는 줄당 1개씩, API Collector는 한 줄에 여러 개
- **예시**:
  ```java
  // Orchestrator
  @Getter
  @Setter
  @NoArgsConstructor >> 한줄씩가자

  // API Collector
  @Getter @Setter
  @NoArgsConstructor @AllArgsConstructor
  ```
- **참고**: 기능 차이 없음, 취향 차이

---

## C. 의도적 차이 (수정 불필요)

| 항목 | 비고 |
|------|------|
| 로그 프리픽스 `[Bojo]`/`[BojoInt]` | 모듈 구분 목적 |
| Orchestrator `refreshSchedule()` / API Collector에 없음 | 도메인 복잡도 차이 |
| DMZ SND Agent 포함 / Internal SND 없음 | 아키텍처상 Internal에 SND 없음 |
