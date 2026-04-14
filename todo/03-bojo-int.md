# sync-agent-bojo-int (Internal Agent)

## [E1] Source 추적 / SyncLog
- [x] InternalLoadStep LINK SyncLog (tm_gd980002)
- [x] InternalLoadStep RESYNC 지원
- [x] pm_gd970201 execution_id/source_refs DDL 추가
- [x] InternalLoadStep per-mapping SyncLog
- [x] tm_gd970101 Target 제거
- [x] InternalLoadStep 3단계 분기 (buildSourceFilter)

## [E1] 파이프라인 실행
- [x] PipelineRegistry 복합키
- [x] PipelineService modeId 추출
- [x] InternalLoadStep conditions 통합
- [x] execution-modes 전체 제거

## [E2] 보안
- [x] SyncDataSourceService 암호화 + Proxy
- [x] HikariCP 풀 하드닝
- [x] application.yml Jasypt 암호화
- [x] API Key 인증
- [x] /debug/datasources 제거
- [x] InternalBojoLoadStepFactory 기본값 수정

## [E10] 일관성 정비
- [x] PipelineService runningAgentCodes
- [x] HealthController 정보 추가
- [x] 로그/예외 한글 통일
- [x] PipelineController DTO 응답

## [E4] Target DB Oracle 전환
- [x] Oracle(Tibero) JDBC 드라이버 의존성 추가
- [x] InternalLoadStep SQL → Oracle 호환 (MERGE INTO 등)
- [x] ConditionBuilder Oracle 문법 대응 (날짜 함수, 캐스팅 등)
- [x] TargetRepositoryService Native SQL Oracle 호환
- [x] common의 Multi-DB 분기(isMysql/qi) → Oracle 분기 추가
- [x] DDL 검증 (tm_gd970001, pm_gd970201, tm_gd980002 등)
- [x] Oracle 환경 연동 테스트

## [E5] 새올 16개 테이블 (DMZ→내부망)
- [x] IF 테이블 구조 결정
- [x] Agent YAML 설정 + Step 구현
- [x] 16개 테이블 MERGE 로직
- [x] E2E 테스트

## [E6] 제주 보조망 Loader (내부망)
- [x] I1 JejuJewonLoadStep — 제원 (1소스→5타겟 순차 MERGE, 고정값, BRNCH_ID 확보)
- [x] I2 JejuObsvdataLoadStep — 관측 (MSN 분기 S11/S2X, EAV, BRNCH_ID 캐싱)
- [ ] I3 JejuFacilityLoadStep — 이용시설 (조건부 MERGE, 계획 수립 완료)

## [E6] 제주/이용량 Loader (내부망)
- [x] I5 UseLoadStep — 이용량 (Legacy+Status 2소스→4타겟, 일집계 후처리)
- [x] 내부망 RCV 파이프라인 (6개 IF_RSV 테이블, YAML 2개 Agent)

## [E9] Entity 전환 (JPA 체계 구축)
- [x] Phase 1: 원본 DDL 백업 (internal-oracle 2048줄 + saeol 778줄)
- [x] Phase 2: DynamicEntityManagerService + CaseAwareNamingStrategy
- [x] Phase 3: gen_entities.py → 55개 엔티티 자동 생성
- [x] Phase 4: ddl-auto:create → 원본 diff, 52개 100% 일치
- [ ] Phase 5: Step 4개 JPA 읽기 전환 (Factory DynamicEm 주입)
- [ ] Phase 6: E2E 검증 (internal-bojo-loader 전체 실행)

## [E1] 기존 파이프라인 영향 확인
- [ ] internal-bojo-loader 기존 Step 정상 동작 확인 (Entity 전환 후)

**진행도: 30/36 = 83%**
