# sync-orchestrator/backend (Orchestrator 백엔드)

## [E1] 아키텍처 변경
- [x] ZoneConfig masterAgentUrl → proxyAgentUrl
- [x] AgentType DB_CON_PROXY enum 추가
- [x] DatasourceService/ExecutionService 프록시 라우팅
- [x] V3__rename_master_to_proxy.sql 마이그레이션

## [E1] 실행 엔진
- [x] ExecutionDto conditions 필드 (동적 WHERE)
- [x] ExecutionController conditions 전달 + 빈 조건 400 거부
- [x] ExecutionService 조건 전달
- [x] AgentService.getSelectTables() 프록시
- [x] AgentController GET /api/agents/{id}/select-tables

## [E1] execution-modes 제거
- [x] AgentExecutionMode 엔티티/리포지토리 제거
- [x] Agent.java executionModes 필드 제거
- [x] AgentDto/Controller/Service 관련 코드 제거
- [x] ExecutionService/Dto/Controller executionModeId 제거

## [E2] 보안
- [x] ExecutionService credentials 전송 제거
- [x] DatasourceService 암호문 응답 (ENC 그대로)
- [x] WebConfig API Key 인터셉터
- [x] application.yml Jasypt 암호화

## [E10] 일관성 정비
- [x] ScheduleExecutor 패턴 통일 (@EventListener)
- [x] AgentService, ExecutionService 한글 로그
- [x] ScheduleService cancelSchedule → unregisterSchedule

**진행도: 19/19 = 100%**
