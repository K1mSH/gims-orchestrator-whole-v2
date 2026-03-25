# sync-agent-common (공통 모듈)

## Source 추적 / SyncLog
- [x] ExecutionDataController 추적 로직 (stripIfPrefix, source_refs 폴백)
- [x] SyncLog 엔티티 per-mapping 리디자인 (mappingName, sourceTables, targetTables)
- [x] SyncLogRepository per-mapping 쿼리
- [x] SourceToIfStep saveSyncLogMapping()
- [x] TableMapping 모델 클래스 (YAML table-mappings 파싱)
- [x] 3단계 source 필터 (buildSourceFilter) — RCV/Loader/SND 자동 분기
- [x] execution_id 인덱스 6개 엔티티 추가

## 파이프라인 실행
- [x] PipelineRegistry 복합키 (agentCode, modeId)
- [x] LoaderStepHelper 공통 로직 (processJewon, processObsvdata, saveSyncLog)
- [x] ConditionBuilder / ExecutionCondition / ConditionOperator (동적 WHERE)
- [x] ExecutionOptions + PipelineRunner conditions 필드
- [x] ConditionBuilder 날짜 캐스팅 (castIfDate)
- [x] ExecutionCondition tableName 필드 (조건 → 특정 테이블 바인딩)

## 보안
- [x] PasswordEncryptor 중앙화 (암호문 통신)
- [x] ApiKeyFilter common으로 통합 (@ConditionalOnProperty)

## DTO/인터페이스
- [x] PipelineDto 신규 (Agent 응답 통일)
- [x] OrchestratorClient 콜백 재시도 (3회)

**진행도: 17/17 = 100%**
