# execution-mode / execution-param / step-definition 완전 제거

## 목적
- 3/12에 execution-modes → WHERE 조건 대체 완료
- 코드에서 제거했지만 DB 테이블 3개 + 일부 잔여 코드(param, step)가 남아 있음
- 사용되지 않는 엔티티/DTO/서비스 로직 + DB 테이블 완전 정리

## DB 테이블 DROP (3개)

```sql
-- orchestrator DB (localhost:29001/orchestrator)
DROP TABLE IF EXISTS agent_execution_mode CASCADE;
DROP TABLE IF EXISTS agent_execution_param CASCADE;
DROP TABLE IF EXISTS agent_step_definition CASCADE;
```

- agent_execution_mode: 데이터 0건, 참조 코드 이미 제거됨 (3/12)
- agent_execution_param: 데이터 0건, 한번도 사용 안됨
- agent_step_definition: 데이터 3건, 참조 UI 제거됨 (3/12)

## 삭제 파일 (2개)

| 파일 | 이유 |
|------|------|
| `sync-orchestrator/backend/.../agent/AgentExecutionParam.java` | 엔티티 전체 미사용 |
| `sync-orchestrator/backend/.../agent/AgentStepDefinition.java` | 엔티티 전체 미사용 |

## 수정 파일 (5개)

### 1. Agent.java
- `executionParams` 필드 + `@OneToMany` 제거
- `stepDefinitions` 필드 + `@OneToMany` 제거

### 2. AgentDto.java
- `ExecutionParamInput` 내부 클래스 제거
- `ExecutionParamResponse` 내부 클래스 제거
- `StepDefinitionResponse` 내부 클래스 제거
- `AgentResponse`에서 executionParams/stepDefinitions 필드 + from() 매핑 제거
- `CreateRequest`/`UpdateRequest`에서 executionParams 필드 제거

### 3. AgentService.java
- executionParam 동기화 로직 제거 (등록/수정 시)
- stepDefinition 동기화 로직 제거 (동기화 시)

### 4. 프론트 types/index.ts
- `executionParams` 필드 제거
- `stepDefinitions` 필드 제거
- `ExecutionParamResponse`/`ExecutionParamInput`/`StepDefinitionResponse` 인터페이스 제거

### 5. 프론트 components/agent/InfoTab.tsx
- executionParams 상태 및 스케줄 필터 관련 코드 제거

### 6. 프론트 app/agents/[id]/page.tsx
- executionParams 상태 및 초기화 로직 제거

## 영향 범위
- Orchestrator backend + frontend만 해당
- Agent(bojo, bojo-int) 코드 변경 없음
- 기능 영향 없음 (이미 사용되지 않는 코드/테이블 정리)

## 빌드/검증
1. `sync-orchestrator/backend` — `./gradlew clean build -x test`
2. `sync-orchestrator/frontend` — `npx tsc --noEmit`
3. 서버 기동 후 Agent 상세 페이지 정상 로딩 확인
