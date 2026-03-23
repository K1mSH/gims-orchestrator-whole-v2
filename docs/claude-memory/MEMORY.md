# GIMS Orchestrator v2 - 작업 메모리
<!-- 최종 동기화: 2026-03-23 -->

- [feedback_run_without_jar.md](feedback_run_without_jar.md) - JAR 대신 gradlew bootRun으로 실행
- [feedback_module_specific_stays.md](feedback_module_specific_stays.md) - 모듈 전용 로직은 common으로 올리지 않기

## 작업 규칙

### ⚠️ 최우선 룰: 코드 수정 전 계획 문서 필수
- **모든 코드 수정 전** `dev_plan/` 폴더에 계획 문서(.md)를 작성
- 사용자가 확인 후 "진행하라"고 할 때까지 코드 수정 금지
- 문서 내용: 수정 목적, 수정 대상 파일, 변경 내용 요약, 영향 범위
- 경로: `orchestrator_v2/dev_plan/2026_MM/DD/`

### 일반 규칙
- 한글 사용 (주석, 터미널 설명, 변수명 혼용 OK)
- 작업 범위: `orchestrator_v2/` 내에서만 진행
- 빌드/테스트: **Gradle** 사용 (`./gradlew clean build -x test`)
- lib(sync-agent-common) 수정 시 → 참조 프로젝트(sync-agent-bojo)에 JAR 복사 필수
- dev_logs 작성: 코드 수정+테스트 완료 후, 수정 목적 기술
- 구조/UI 문서(docs/)는 개발방향 문서 → 보수적 수정, 애매하면 질문
- "테스트 해봐" = 앱 실행 + 로그 모니터링 + 피드백 루프
- MEMORY 관리: 임의 수정 안함, 필요시 건의 → 사용자 승인 후 반영

## 프로젝트 구조
```
orchestrator_v2/
├── sync-agent-common/    # 공통 모듈 (JAR 라이브러리)
├── sync-agent-bojo/      # 통합 Agent (12개 논리적 Agent)
├── sync-agent-bojo-int/  # Internal Agent
├── sync-orchestrator/
│   ├── backend/          # Spring Boot (port 8080)
│   └── frontend/         # Next.js (port 3000)
├── infolink-api-collector/ # API 수집 모듈 (독립, port 8084/8094)
├── docs/                 # ARCHITECTURE.md, UI_GUIDE.md, 클로드 작업 메뉴얼.txt
│   └── claude-memory/    # troubleshooting.md (git 추적)
├── dev_logs/2026_MM/     # 작업 일지 (년월 디렉토리)
├── dev_plan/2026_MM/DD/  # 계획 문서 (년월/일 디렉토리)
├── test_plan/            # 기능별 테스트 문서 (재사용)
└── scripts/              # testdata.sh 등
```

## 빌드 명령어
```bash
# common (수정 시 bojo에 JAR 복사 필요)
cd sync-agent-common && ./gradlew clean build -x test
cp build/libs/sync-agent-common-*.jar ../sync-agent-bojo/libs/

# agent
cd sync-agent-bojo && ./gradlew clean build -x test

# orchestrator backend
cd sync-orchestrator/backend && ./gradlew clean build -x test

# api collector
cd infolink-api-collector && ./gradlew clean build -x test

# frontend 타입체크
cd sync-orchestrator/frontend && npx tsc --noEmit
```

## DB 환경
| 용도 | 타입 | 포트 | DB명 | 비고 |
|------|------|------|------|------|
| Orchestrator | PostgreSQL | 29001 | orchestrator | 중앙 관리 |
| Agent IF | PostgreSQL | 29001 | dev | IF 테이블, link_ngwis |
| 외부 PG | PostgreSQL | 29000 | daejeon,bytek,chungnam,keunsan | 4개 |
| 외부 MySQL | MySQL | 29010 | infoworld_*,hydronet_* | 6개 (Docker) |
- 전체 계정: `k1m` / `1111` (테스트용)
- keunsan만 대문자 테이블/컬럼, 나머지 소문자

## DB 환경 (추가)
| 용도 | 타입 | 포트 | DB명 | 비고 |
|------|------|------|------|------|
| API Collector | PostgreSQL | 29001 | api_collector | 독립 DB |

## 서버 포트
| 서비스 | 포트 |
|--------|------|
| Orchestrator Backend | 8080 |
| Agent DMZ (sync-agent-bojo) | 8082 |
| Proxy DMZ (sync-proxy-dmz) | 8083 |
| API Collector DMZ | 8084 |
| Agent Internal (sync-agent-bojo-int) | 8092 |
| Proxy Internal (sync-proxy-internal) | 8093 |
| API Collector Internal | 8094 |
| Frontend (Next.js) | 3000 |
| 외부 PG | 29000 |
| 내부 PG | 29001 |
| 외부 MySQL | 29010 |

## 핵심 아키텍처
- 12개 논리적 Agent = RCV 10(업체별) + Loader 1 + SND 1
- 하나의 물리적 앱(sync-agent-bojo:8082)에서 agentCode로 라우팅
- 파이프라인: Source(외부) →[RCV]→ IF_RSV →[Loader]→ Target →[SND]→ IF_SND →[Int RCV]→ IF_RSV →[Int Loader]→ GIMS
- Agent 설정: `config/agents/*.yml` (파일 기반, 업체명 네이밍, table-mappings 포함)
- Multi-DB: PostgreSQL + MySQL 자동 분기 (isMysql()/qi() 헬퍼)
- **데이터 접근**: 읽기=JPA, 쓰기=JDBCTemplate batch (IDENTITY+대량UPSERT 성능 문제)
- **제원 UK**: `obsv_code` 아닌 `source_refs` (외부 DB에 obsv_code 중복 존재)
- **SyncLog**: per-mapping 방식 (mappingName/sourceTables[]/targetTables[]/readCount/writeCount)
- **프론트**: 매핑 개념은 백엔드 내부, 프론트에서는 SOURCE/TARGET 테이블별 행으로 표시
- **Link 테이블**: link_ngwis, tm_gd970101, tm_gd980002 → SyncLog/모니터링/매핑에서 제외

## 주요 파일 위치
- Agent YAML: `sync-agent-bojo/src/main/resources/config/agents/`
- 파이프라인 라우팅: `sync-agent-bojo/.../config/PipelineRegistry.java`
- RCV 핵심: `LinkTableObsvDataFetcher.java`, `LinkTableUpdateStep.java`
- Loader 핵심: `DefaultLoadStep.java`, `LoaderStepHelper.java`, `TargetRepositoryService.java`
- 공통 추출: `sync-agent-common/.../step/SourceToIfStep.java`
- Tracing: `sync-agent-common/.../controller/ExecutionDataController.java`
- Orchestrator 실행: `sync-orchestrator/backend/.../execution/ExecutionService.java`

## 참고 문서 (필요시 읽기)
| 문서 | 언제 읽나 | 경로 |
|------|----------|------|
| ARCHITECTURE.md | 아키텍처/파이프라인/테이블 설계 작업 시 | `docs/ARCHITECTURE.md` |
| UI_GUIDE.md | 프론트엔드/화면 작업 시 | `docs/UI_GUIDE.md` |
| 클로드 작업 메뉴얼 | 작업 규칙 재확인 시 | `docs/클로드 작업 메뉴얼.txt` |
| troubleshooting.md | 이슈 해결 중 기존 해결법 참고 시 | `docs/claude-memory/troubleshooting.md` |
| dev_logs/*.md | 이전 작업 이어서 할 때 | `dev_logs/2026_MM/` |
| test_plan/*.md | 기능 테스트 시 | `test_plan/bojo-test.md` 등 |

## Source 추적 설계 (3/10 확정, E2E 검증 완료)
- **execution_id는 항상 target에만 존재** — Agent 유형 무관
- source 조회: target(execution_id) → source_refs 수집 → source 테이블 조회
- 3단계 분기 (buildSourceFilter):
  1. source에 source_refs 없음 → PK 파싱 매칭 (RCV — 외부 DB)
  2. source_refs 있고 target 값과 동일 → source_refs IN 매칭 (Loader — IF→Target 복사 패턴)
  3. source_refs 있지만 target 값과 다름 → PK 파싱 매칭 (SND — PK 기반 새 source_refs 생성)
- 판별: 샘플 1건 `SELECT COUNT(*) WHERE source_refs = ?` 체크
- execution_id 인덱스: IF/Target 6개 엔티티에 추가 완료
- bojo에서 ExecutionDataController는 excludeFilter → Proxy 경유만 가능
- tm_gd970101: Internal Loader의 target/SyncLog에서 완전 제거됨

## Retention 주의사항 (3/10 테스트로 확인)
- Internal Agent cleanup 시 `targetDatasourceId` body에 필수 (`"internal"`)
- pm_gd970201 날짜 컬럼: `obsrvn_dt` (obsv_date 아님)
- jewon 테이블: obsv_date 없어 Retention 대상 제외
- retentionDays 음수 방어 완료 (3/11) — 4계층 다중 방어 (프론트 min=1, Orchestrator API 검증, Agent Controller 예외, Agent Service skip)

## Loader 모드별 Step 교체 구조 (3/11 완료)
- PipelineRegistry: `(agentCode, modeId)` 복합키, default fallback
- PipelineService: params에서 `executionModeId` 추출 → Runner 선택
- LoaderStepHelper: 공통 로직 추출 (processJewon/processObsvdata/saveSyncLog)
- DaejeonLoadStep → DefaultLoadStep 리네이밍 (stepId: `default-load`)
- DMZ bojo + Internal bojo-int 양쪽 적용 완료
- 새 모드 추가: Step 구현체 + LoaderPipelineConfig에 등록만 하면 됨

## infolink-api-collector (3/13 신규, 3/23 LOOKUP 추가)
- 외부 API 수집 독립 모듈 (Spring Boot 2.7.12, Java 17, Gradle)
- **설계 원칙**: 코드 변경 없이 UI 등록만으로 새 API 수집 가능
- **플로우**: 기본정보 등록 → 테스트 호출 → JSON 트리에서 데이터 루트 선택 → 타겟 테이블/매핑 설정 → 수동/스케줄 실행
- **엔티티**: ApiEndpoint, ApiParam, ApiFieldMapping, ApiSchedule, ApiExecutionHistory
- **핵심 서비스**: ApiCallService(HTTP), ResponseParser(JSON→Tree/Records), ApiExecutionService(범용 엔진), ApiScheduleExecutor(cron)
- **LOOKUP 파생 컬럼** (3/23): ApiFieldMapping에 통합, isDerived 플래그로 구분
  - 소스필드 → 정규식 키 추출 → 공통코드 API(설정 기반 URL + 그룹코드) → 매칭 → 치환
  - LookupService: 내부 API 호출 + Map 캐싱 + 정규식 추출
  - LOOKUP API URL: `application.yml`의 `lookup.common-code-url` (UI에서 그룹코드만 입력)
  - `lookupMatchType` 예비 (향후 CONTAINS 등 확장 — 금칙어 등)
- **TransformType**: NONE, DATE_FORMAT, NUMBER, SUBSTRING, TRIM, REPLACE, DEFAULT_VALUE, LOOKUP
- **MockApiController**: 뉴스 API + 공통코드 API(NGW_0118 언론사 65건) 시뮬레이션
- **프론트**: `/api-collect` 경로, Next.js proxy `/collector-api/*` → `localhost:8084/api/*`
- **DB**: api_collector (PG 29001), 기존 소스에 영향 없음

## 미완료 작업 (다음 세션)
- Loader 지역별 모드 구현체: **잠정 중단** (사용자가 먼저 언급하기 전까지 진행 안함)
