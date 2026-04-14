# sync-agent-bojo (DMZ Agent)

## [E1] 아키텍처
- [x] 프록시 엔드포인트 ComponentScan 제외
- [x] Agent YAML 14개 table-mappings 추가

## [E1] Source 추적 / SyncLog
- [x] LinkTableUpdateStep SyncLog 기록 (tableType=LINK)
- [x] LINK 타입 SyncLog 제외 (sumCountsByExecutionIdExcludeLink)
- [x] DaejeonLoadStep per-mapping SyncLog
- [x] LinkTableObsvDataFetcher 시간 비교 `>=` → `>` (극점 중복 방지)
- [x] tm_gd970101 Target/SyncLog 제거

## [E1] 파이프라인 실행
- [x] DaejeonLoadStep → DefaultLoadStep 리네이밍
- [x] LoaderStepHelper 신규 (공통 로직)
- [x] PipelineRegistry 복합키 + PipelineService modeId 추출
- [x] DefaultLoadStep conditions 통합 (isResyncExecution, buildMergedConditions)
- [x] TargetRepositoryService conditions 쿼리 (Native SQL)
- [x] execution-modes 전체 제거

## [E2] 보안
- [x] SyncDataSourceService Proxy 경유 + 암호화
- [x] HikariCP 풀 하드닝 (maxPool=10, timeout=10s, leak=60s)
- [x] application.yml Jasypt 암호화
- [x] API Key 인증 (ApiKeyFilter)
- [x] /debug/datasources 제거

## [E10] 일관성 정비
- [x] PipelineService runningAgentCodes Set + finally
- [x] HealthController loaderAgents, runningAgents 추가
- [x] 로그/예외 한글 통일
- [x] PipelineController Map → PipelineDto
- [x] Lombok 줄당 1개 통일

## [E1] 버그 수정
- [x] LoaderStepHelper source_refs 재생성 (외부PK → IF PK)

**진행도: 23/23 = 100%**
