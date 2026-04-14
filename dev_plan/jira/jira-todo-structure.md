# Jira TODO 구조 (Epic → Task → Sub-task)

> 작성일: 2026-04-14
> 목적: Jira 에픽/태스크 계층 구조 확정 → 등록 스크립트 기반
> 상태 범례: DONE / IN_PROGRESS / TODO / BLOCKED

---

## E1. 동기화 파이프라인 핵심 (DMZ)
> 외부 DB → IF → Target 수집/전송 기본 구조

| Task | Sub-tasks | 상태 |
|------|-----------|------|
| T1. Proxy/Agent 모듈 분리 | Proxy DMZ(8083), Proxy Internal(8093), Agent에서 프록시 엔드포인트 제거 | DONE |
| T2. RCV 파이프라인 (10개 업체) | SourceToIfStep 공통화, LinkTable 업데이트, 업체별 YAML 설정 | DONE |
| T3. Loader 파이프라인 | DefaultLoadStep, LoaderStepHelper 공통 로직, 모드별 Step 교체 | DONE |
| T4. SND 파이프라인 | sync-agent-others(8085) 모듈, IF_SND → 외부 전송 | DONE |
| T5. 추적 (Tracing) | source_refs 3단계 분기, PK 파싱, execution_id 인덱스 | DONE |
| T6. SyncLog 리디자인 | per-mapping 방식, SOURCE/TARGET 테이블별 표시 | DONE |
| T7. Retention (보존기간) | 4계층 방어, 음수 방어, 스케줄 실행 | DONE |
| T8. 조건실행/증분실행 | ConditionBuilder 공통 헬퍼, 날짜 기반 필터 | DONE |
| T9. 로컬 E2E 검증 | 전 업체 파이프라인 E2E 통과 | DONE |
| T10. 실서버 배포 | (?) 일정 미정 | TODO |

## E2. 보안 체계
> 암호화, API Key 인증, 프록시 보안

| Task | Sub-tasks | 상태 |
|------|-----------|------|
| T1. Jasypt 암호화 키 외부화 | 환경변수 분리, Docker 전달 | DONE |
| T2. API Key 인증 | ApiKeyFilter, pipeline/info 인증 예외 | DONE |
| T3. Proxy 보안 | Agent↔Proxy 간 인증 | DONE |
| T4. 실서버 보안 검증 | (?) 배포 시 확인 | TODO |

## E3. 외부 API 수집기 (infolink-api-collector)
> 코드 변경 없이 UI 등록만으로 외부 API 수집

| Task | Sub-tasks | 상태 |
|------|-----------|------|
| T1. 기본 구조 | Entity/CRUD/UI, 테스트 호출, JSON 트리 뷰 | DONE |
| T2. 필드 매핑 + 실행 엔진 | DataTransformer, INSERT/UPSERT, Savepoint 에러 격리 | DONE |
| T3. 나라장터 연동 | 공사/용역/외자/물품 4개 유형 | DONE |
| T4. 커스텀 실행기 | D1~D5 전부 E2E | DONE |
| T5. API Key 참조 | isApiKeyRef 플래그, resolveApiKey() | DONE |
| T6. LOOKUP 파생 컬럼 | isDerived, 정규식 키 추출, 공통코드 API 매칭 | DONE |
| T7. 스케줄 실행 | ApiScheduleExecutor, cron 기반 | DONE |
| T8. Docker 배포 테스트 | 컨테이너 기동, 외부 연동 확인 | DONE |

## E4. 내부망 Oracle 전환
> Internal Agent 자체 DB + Target을 Oracle로 전환

| Task | Sub-tasks | 상태 |
|------|-----------|------|
| T1. Oracle Docker 환경 | gvenzl/oracle-xe:21-slim, 29004포트 | DONE |
| T2. DDL 생성 (Target 테이블) | GIMS Target, IF 테이블, 인덱스 | DONE |
| T3. JDBC + JPA Oracle 전환 | bojo-int datasource 설정, 쿼리 호환성 | DONE |
| T4. E2E 검증 | 전구간 파이프라인 동작 확인 | DONE |

## E5. 새올 SND 파이프라인
> 새올 Tibero(→Oracle 대체) 16개 테이블 전송

| Task | Sub-tasks | 상태 |
|------|-----------|------|
| T1. 새올 Oracle Docker 환경 | gims_dmz_saeol_oracle(29005) | DONE |
| T2. DMZ SND 16테이블 구현 | IF_SND 엔티티, YAML 설정 | DONE |
| T3. Internal Loader 연결 | SND → RCV → Loader 전구간 | DONE |
| T4. E2E + 추적 검증 | 건수 + 역추적 정상 | DONE |

## E6. 제주/이용량 파이프라인 (Internal)
> 내부망 제주 보조관측망 + 이용량 Loader 커스텀 Step

| Task | Sub-tasks | 상태 |
|------|-----------|------|
| T1. 내부망 RCV 파이프라인 | 6개 IF_RSV 테이블, YAML 2개 Agent | DONE |
| T2. I1 제주 제원 Loader | 5개 타겟 순차 MERGE, 고정값, BRNCH_ID 확보 | DONE |
| T3. I2 제주 관측 Loader | MSN 분기(S11/S2X), EAV, BRNCH_ID 캐싱 | DONE |
| T4. I3 제주 이용시설 Loader | 조건부 MERGE, 계획 수립 완료 | TODO |
| T5. I5 이용량 Loader | Legacy+Status 2소스→4타겟, 일집계 후처리 | DONE |
| T6. 기존 파이프라인 영향 확인 | internal-bojo-loader 기존 Step 정상 동작 확인 | TODO |

## E7. 환경부 표준 컬럼명 전환
> bojo Target 테이블을 환경부 표준 컬럼명으로 통일

| Task | Sub-tasks | 상태 |
|------|-----------|------|
| T1. 매핑 정의 | 4개 테이블 컬럼 매핑 (이전→표준) | DONE |
| T2. DDL 재생성 | DROP → 표준 컬럼명 CREATE | DONE |
| T3. Java 코드 적용 | Repository, Step 변수명 변경 | DONE |
| T4. E2E 재검증 | bojo-loader 전체 재테스트 | DONE |

## E8. Orchestrator UI/기능
> 중앙 관리 화면 + 운영 기능

| Task | Sub-tasks | 상태 |
|------|-----------|------|
| T1. 실행 상세 화면 | Agent 헤더, 탭 네비게이션, SOURCE/TARGET 분리 표시 | DONE |
| T2. 추적 화면 (trace) | source_refs 역추적, 파생 데이터 안내 | DONE |
| T3. Agent auto-discover | pipeline/info 조회 → 자동 등록 | DONE |
| T4. agent_table 자동 동기화 | OFFLINE→ONLINE 시 자동 갱신, 수동 버튼 | DONE |
| T5. Agent 수정 UI | 읽기 전용 테이블, pass/fail 검증 | DONE |
| T6. Zone 드롭다운 | Agent 등록 시 zone 필수 선택 | DONE |
| T7. 처리현황 개선 | 컬럼 재수집, 미등록 상단 정렬, description 통일 | DONE |
| T8. 실행 카운트 개선 | (?) 신규/갱신/스킵 3분류 | DONE |

## E9. bojo-int Entity 전환
> Internal Agent의 JPA Entity 체계 구축

| Task | Sub-tasks | 상태 |
|------|-----------|------|
| T1. 원본 DDL 백업 | internal-oracle 2048줄 + saeol 778줄 | DONE |
| T2. DynamicEntityManager + NamingStrategy | CaseAwareNamingStrategy, 동적 EM | DONE |
| T3. Entity 자동 생성 | gen_entities.py, 55개 엔티티 | DONE |
| T4. DDL 검증 | ddl-auto:create → 원본 diff, 52개 100% 일치 | DONE |
| T5. Step JPA 전환 | Factory 3개 DynamicEm 주입, Step 4개 JPA 읽기 전환 | IN_PROGRESS |
| T6. E2E 검증 | internal-bojo-loader 전체 실행 | TODO |

## E10. 인프라/리팩토링
> 모듈 구조, 패키지, 설정 일원화 등 횡단 작업

| Task | Sub-tasks | 상태 |
|------|-----------|------|
| T1. 패키지 구조 통일 | 전 모듈 레이어 기반 (controller/dto/entity/...) | DONE |
| T2. 디렉토리 구조 통일 | loader/factory, rcv/factory, snd/factory | DONE |
| T3. Step config 일원화 | YAML single source of truth, 22개 YAML 정리 | DONE |
| T4. 모듈 간 일관성 | 로그 한글, Lombok 스타일, PipelineDto 응답 | DONE |
| T5. DDL 관리 전략 | scripts/ddl/ DB별 분리 (saeol-tibero, internal-oracle, dmz-pg) | DONE |

## E11. Jira 연동
> dev_plan/dev_logs → Jira 이슈 자동 등록

| Task | Sub-tasks | 상태 |
|------|-----------|------|
| T1. Jira 환경 준비 | 계정, 프로젝트(GIMS), API 토큰 | DONE |
| T2. TODO 구조 설계 | Epic/Task/Sub-task 계층 정의 (이 문서) | IN_PROGRESS |
| T3. 기존 240개 이슈 삭제 | bulk delete 스크립트 | TODO |
| T4. 등록 스크립트 개발 | Epic 생성 → Task 생성 → Sub-task 연결 | TODO |
| T5. 대시보드 구성 | 타임라인 뷰, 에픽별 진행률 | TODO |
| T6. 상급자 시연 | (?) 일정 미정 | TODO |

---

## 확인 필요 사항

1. **E1 T10 (실서버 배포)** — 일정이나 선행조건이 있는지?
2. **E6 T4 (I3 제주 이용시설)** — Entity 전환(E9) 완료 후 착수 맞는지?
3. **E6 T6 (기존 파이프라인 영향)** — 별도 Task로 둘지, E9에 포함할지?
4. **빠진 에픽이나 Task** — 단위테스트 결과서 작성, 문서화 작업 등은 에픽으로 넣을지?
5. **에픽 우선순위** — 상급자에게 보여줄 때 순서가 중요한지?
