# execution-modes 전체 제거 계획

## 배경
- 프론트 실행옵션에서 "실행 방식" 드롭다운 UI가 이미 제거됨
- WHERE 조건 기능으로 실행옵션이 완전 대체됨
- 백엔드에 남아있는 execution-modes 관련 코드를 정리

## 삭제 대상

### 1. YML (1파일)
- `sync-agent-bojo-int/.../agents/internal-bojo-loader.yml` → `execution-modes` 블록 제거

### 2. sync-agent-common (1파일)
- `ExecutionModeDefinition.java` → 파일 삭제

### 3. sync-agent-bojo (1파일)
- `PipelineService.java` → `executionModeId` 추출 로직 제거 (항상 "default"로 처리)

### 4. sync-agent-bojo-int (5파일)
- `AgentDefinition.java` → `ExecutionModeConfig` 내부클래스 + `executionModes` 필드 제거
- `AgentConfigLoader.java` → execution-modes 파싱 로직 제거
- `PipelineController.java` → `GET /{agentCode}/execution-modes` 엔드포인트 제거, trigger에서 executionModeId 전달 제거
- `PipelineRegistry.java` → `executionModes` Map + getter 제거, register 파라미터에서 modes 제거
- `LoaderPipelineConfig.java` → `buildExecutionModes()` 메서드 제거, register 호출에서 modes 파라미터 제거
- `PipelineService.java` → `executionModeId` 추출 로직 제거

### 5. sync-orchestrator backend (6파일)
- `AgentExecutionMode.java` → 파일 삭제 (JPA 엔티티)
- `AgentExecutionModeRepository.java` → 파일 삭제
- `Agent.java` → `executionModes` 필드 제거
- `AgentDto.java` → `ExecutionModeResponse` 클래스 + `DetailResponse.executionModes` 필드 제거
- `AgentController.java` → `GET /{id}/execution-modes`, `POST /{id}/refresh-execution-modes` 엔드포인트 제거
- `AgentService.java` → `fetchExecutionModesFromAgent()`, `getExecutionModes()`, `refreshExecutionModes()` 제거
- `ExecutionService.java` → `executionModeId` 파라미터 제거
- `ExecutionDto.java` → `executionModeId` 필드 제거
- `ExecutionController.java` → `executionModeId` 참조 제거

### 6. Frontend (2파일)
- `types/index.ts` → `ExecutionModeResponse` 인터페이스 + `executionModes` 필드 제거
- `lib/api.ts` → `getExecutionModes`, `refreshExecutionModes` 제거, trigger에서 `executionModeId` 파라미터 제거

### 7. DB
- `agent_execution_mode` 테이블 DROP 필요 (JPA 엔티티 삭제 후 자동 반영 또는 수동)

## 주의사항
- PipelineRegistry의 (agentCode, modeId) 복합키 → agentCode 단일키로 단순화
- PipelineService에서 modeId 분기 제거 → 항상 default Runner 사용
- 기존 실행 이력(execution_log)에 저장된 executionModeId 값은 그대로 유지 (히스토리)

## 영향 범위
- 프론트: 이미 UI 제거됨, API 호출부만 정리
- 백엔드: API 엔드포인트 2개 삭제, 내부 로직 단순화
- Agent: 파이프라인 라우팅이 단순해짐 (모드 분기 없음)
