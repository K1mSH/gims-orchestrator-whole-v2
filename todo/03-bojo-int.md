# sync-agent-bojo-int (Internal Agent)

## Source 추적 / SyncLog
- [x] InternalLoadStep LINK SyncLog (tm_gd980002)
- [x] InternalLoadStep RESYNC 지원
- [x] pm_gd970201 execution_id/source_refs DDL 추가
- [x] InternalLoadStep per-mapping SyncLog
- [x] tm_gd970101 Target 제거
- [x] InternalLoadStep 3단계 분기 (buildSourceFilter)

## 파이프라인 실행
- [x] PipelineRegistry 복합키
- [x] PipelineService modeId 추출
- [x] InternalLoadStep conditions 통합
- [x] execution-modes 전체 제거

## 보안
- [x] SyncDataSourceService 암호화 + Proxy
- [x] HikariCP 풀 하드닝
- [x] application.yml Jasypt 암호화
- [x] API Key 인증
- [x] /debug/datasources 제거
- [x] InternalBojoLoadStepFactory 기본값 수정

## 일관성 정비
- [x] PipelineService runningAgentCodes
- [x] HealthController 정보 추가
- [x] 로그/예외 한글 통일
- [x] PipelineController DTO 응답

## Target DB Oracle 전환 (신규 추가 3/25)
- [ ] Oracle(Tibero) JDBC 드라이버 의존성 추가
- [ ] InternalLoadStep SQL → Oracle 호환 (MERGE INTO 등)
- [ ] ConditionBuilder Oracle 문법 대응 (날짜 함수, 캐스팅 등)
- [ ] TargetRepositoryService Native SQL Oracle 호환
- [ ] common의 Multi-DB 분기(isMysql/qi) → Oracle 분기 추가
- [ ] DDL 검증 (tm_gd970001, pm_gd970201, tm_gd980002 등)
- [ ] Oracle 환경 연동 테스트

**진행도: 20/27 = 74%**
