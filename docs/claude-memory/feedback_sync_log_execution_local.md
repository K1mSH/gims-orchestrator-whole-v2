---
name: feedback-sync-log-execution-local
description: sync_log + execution 적재 위치 = agent.history_datasource_id (= 모듈 default JPA primary). agent /health 자동 추출 + DB 등록
metadata: 
  node_type: memory
  type: feedback
  originSessionId: 80ff9d63-9e5b-42fc-933c-7c4483496968
---

`sync_log` 와 `execution` 둘 다 **agent 의 JPA primary datasource** (= 모듈 default) 에 박힘. 36 agent 일관. orchestrator DB `agent.history_datasource_id` 컬럼이 단일 진실원.

**Why**: 5/19 어제 fix (sync-log = agent.target 룰) 가 sync_log 만 target 으로 옮기고 execution 은 JPA primary 그대로 두어 분리 발생 → backend mgmtJdbc=target 이 execution 못 찾아 detail/source/target/trace API 500 회귀. 본 세션 재검증: agent JPA 가 모든 entity 를 primary 에 박는 표준 + sync_log 도 같은 곳에 두어야 일관. 옵션 i 부분 롤백 (어제 fix mirror) 후 추가 발견 — backend → proxy → ExecutionDataController 흐름이라 proxy 가 자기 JPA primary 봄. provide 처럼 module default ≠ proxy 의 jpa 인 경우 깨짐. 해결 = backend 가 `agent.history_datasource_id` 헤더 송신 → proxy 가 동적 datasource 만듦. agent_code prefix/port/zone hardcoded 회피 (작명/인프라 의존), DB 컬럼이 진실원.

**How to apply**:
- sync_log INSERT = JPA `SyncLogRepository.save()` 표준 (동적 JdbcTemplate INSERT 우회 X).
- agent `application.yml` 의 `agent.history-datasource-id` 명시 (모듈 단위, 4 모듈: bojo-dmz/bojo-internal/others-dmz/provide = dmz/internal/dmz/api-provider).
- agent `/health` 응답에 `historyDatasourceId` 포함 (HealthController `@Value` 주입).
- orchestrator `AgentService.discoverAgents` 가 health 응답에서 추출 → `AgentDto.CreateRequest.historyDatasourceId` 로 프론트에 전달.
- 프론트 등록 폼 = **readonly 표시** (운영자 입력 X, 정상 연결 확인 차원).
- `AgentService.create` 가 request.historyDatasourceId 받아 `agent.history_datasource_id` 컬럼 저장.
- backend `ExecutionService.buildHeaders` = `X-Manage-Datasource-Id` 헤더 = `agent.getHistoryDatasourceId()` 송신.
- proxy `ProxyDataSourceService.getJdbcTemplate(헤더값)` → 동적 connection-info 받아 정확한 JdbcTemplate 사용.

새 agent 등록 시 yml 한 줄 박으면 자동. 신규 모듈 추가 시 application.yml + DB UPDATE 또는 자동 등록 흐름 활용.

관련: [[feedback_agent_at_target]] (어제 fix 의 토대 — 본 사이클로 무효화. agent.target 은 데이터 흐름 routing 용, sync_log/execution 적재 위치 = agent.history_datasource_id 별도).
