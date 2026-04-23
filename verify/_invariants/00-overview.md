# Invariants — 개요 (Overview)

본 디렉토리는 **프로젝트 전반이 상시 지켜야 하는 불변조건(invariant)** 을 관리한다.
기능 체크리스트(`verify/checklists/`)가 "이 Agent 의 요구사항이 충족되나?" 를 묻는다면, invariant 는 **"전 시스템에서 이 규약이 지켜지나?"** 를 묻는다.

---

## 1. Invariant 정의

**Invariant 의 성립 조건**
- 환경 독립적 구조 규약 — 개발/스테이징/실배포 **어디서든 성립** 해야 함
- 여러 모듈/Agent 에 걸친 **횡단 관심사** (cross-cutting)
- 깨졌을 때 **시스템의 신뢰성 자체가 흔들림**

**Invariant 가 아닌 것**
- 특정 환경의 값 (`localhost`, 포트 번호, 계정) — 이건 `verify/deployment/config-replacement.md`
- 특정 Agent 고유 기능 — 이건 해당 Agent `checklists/*.md`
- 일회성 버그 / 수정 후 소멸하는 이슈 — 이건 `verify/issues/`
- 개발 프로세스 규칙 (계획 문서 선행, scope creep 금지 등) — 이건 CLAUDE.md / MEMORY

---

## 2. Invariant 카테고리 (1~11)

| # | 카테고리 | 요약 | 세부 파일 |
|:-:|--------|------|---------|
| 1 | **아키텍처 / 모듈 경계** | 논리 Agent vs 물리 앱, 공통 JAR 경계, 내 DB/남의 DB 구분 | (예정) |
| 2 | **데이터 접근 / 영속성** | 읽기=JPA, 쓰기=JdbcTemplate batch, provide Loader 는 UPSERT+UK | (예정) |
| 3 | **설정 / 단일 진실원** | Orchestrator DB 등록이 단일 진실원, Agent=target=관리DB, 헤더 라우팅 | `header-routing.md`, `agent-target-manage-db.md` (예정) |
| 4 | **추적 / 감사** | execution_id target 전용, source 역조회 3단계 분기, SyncLog per-mapping | `trace-source-branches.md`, `sync-log-per-mapping.md` (예정) |
| 5 | **운영 UI / 외부 노출** | 내부 개념 노출 금지, 프론트=테이블 단위, Link 테이블 제외 | (예정) |
| 6 | **파이프라인 품질 / 방어** | Retention 4계층 방어, 공통 헬퍼 사용, 회귀 금지, 모드별 Step 교체 | (예정) |
| 7 | **스키마 / DDL 배포** | DB별 분리, @Comment ↔ DB 주석 동기화, 멱등성 | `schema-comment-sync.md` (예정) |
| 8 | **코드 스타일 / 일관성** | 로그 한글, Lombok 줄당 1개, 레이어 패키지, Agent 상태 추적 | (예정 — 검증 가능한 것만) |
| 9 | (개발 프로세스 — invariant 아님) | CLAUDE.md / MEMORY 에서 관리 | — |
| 10 | (코드 스타일 세부 — 일부만 invariant) | — | — |
| 11 | **배포 이식성 / 운영 환경 적합성** | 환경 독립성, 임시값 제거, 장애 격리, 감사/롤백 | `no-tmp-values.md`, `fault-isolation.md` (예정) |

> 번호 9, 10 은 invariant 로 승격하지 않음 (개발 프로세스 / 작업 규칙 성격).
> 실제 세부 파일은 하나씩 검토하며 작성.

---

## 3. 카테고리별 규약 요지

### 1. 아키텍처 / 모듈 경계
- 12 개 논리 Agent, 1 물리 앱 (agentCode 라우팅)
- 3 망 구조: DMZ / Internal / API Provider
- common JAR = 공통 로직만 / **모듈 전용 로직은 common 에 올리지 않음**
- **내 DB = JPA 엔티티 / 남의 DB = JdbcTemplate**
- 새 Agent 모듈은 `entity/` 구조 필수

### 2. 데이터 접근 / 영속성
- **읽기 = JPA / 쓰기 = JdbcTemplate batch** (IDENTITY + 대량 UPSERT 성능)
- **provide Loader 예외 — 항상 UPSERT + UK** (외부 제공 안정성 우선)
- 제원 UK = `source_refs` (obsv_code 는 외부 DB 중복)

### 3. 설정 / 단일 진실원
- yml 에 DB 등록 ID **하드코딩 금지**
- **Orchestrator DB 등록 = 단일 진실원**
- **Agent = target 쪽 = 관리 DB = 자기 JPA 기본 datasource**
- 관리 DB 라우팅 = `X-Manage-Datasource-Id` 헤더 + `agent.targetDatasourceId`

### 4. 추적 / 감사 (Traceability)
- **execution_id 는 항상 target 에만** 존재
- source 역조회: target(execution_id) → source_refs → source
- **buildSourceFilter 3 단계 분기** (RCV / Loader / SND) + 분기 3 `target_tables` 1 순위 매칭
- **SyncLog = per-mapping** (mappingName / sourceTables[] / targetTables[] / readCount / writeCount)
- executionId 인덱스 필수 (IF / Target 6 엔티티)
- **추적 컬럼 규약** (Oracle 측): LINK_STATUS / EXECUTION_ID / SOURCE_REFS / EXTRACTED_AT / UPDATED_AT

### 5. 운영 UI / 외부 노출
- common 구조 / 관리 테이블 같은 **내부 개념 운영자 UI 에 노출 금지**
- 프론트엔드 = SOURCE/TARGET 테이블 단위 표시 (매핑은 백엔드 내부)
- **Link 테이블** (link_ngwis / tm_gd970101 / tm_gd980002) → SyncLog / 모니터링 / 매핑 제외

### 6. 파이프라인 품질 / 방어
- **Retention 음수 방어 4 계층** (프론트 / Orchestrator API / Agent Controller / Agent Service)
- Retention cleanup body 에 `targetDatasourceId` 필수
- **커스텀 Step 필수기능 = 조건실행 + Retention 공통 헬퍼 사용**
- **모든 수정은 기존 기능 회귀 없어야 함** (유기적 구조 — 단일 케이스 땜질 금지)
- Loader 모드별 Step 교체 = PipelineRegistry 복합키 `(agentCode, modeId)` + default fallback

### 7. 스키마 / DDL 배포
- DB 별 분리 (`scripts/ddl/saeol-tibero`, `internal-oracle`, `dmz-pg`)
- **@Comment ↔ DB 주석 동기화** (ddl-auto update 는 주석 미반영 → drop 재생성 필요)
- DDL 멱등성 패턴 (ORA-00955 / ORA-01430 무시 / NULL→PENDING UPDATE)

### 8. 코드 스타일 / 일관성 (검증 가능한 항목만)
- 로그 언어 한글 통일 (프리픽스 `[Bojo]` / `[BojoInt]` 등 유지)
- Exception 메시지 한글
- Lombok 줄당 1 개
- ScheduleExecutor 패턴 (`@EventListener(ContextRefreshedEvent)` + register/unregister/getActive)
- PipelineController 응답 = `PipelineDto` (common)
- Agent 상태 추적 = `runningAgentCodes` Set + finally
- 패키지 구조 = 레이어 기반 통일 (controller/dto/entity/repository/service/scheduler/config)

### 11. 배포 이식성 / 운영 환경 적합성

**A. 환경 분리 / 독립성**
- 환경 프로파일 분리 (dev/staging/prod)
- credentials 외부화 (yml 하드코딩 금지)
- 경로 독립성 (Windows ↔ Linux)
- 타임존 / 인코딩 명시

**B. 임시값(tmp) 제거**
- 금지 패턴: `tmp_*`, `dummy`, `placeholder`, `TEST_*`, `임시`, `fake`, `example.com`
- 허용: 명시적 치환 지점 (`${...}`)
- 예외 선언: 주석에 `// TMP: reason / remove-by: YYYY-MM-DD` 명시

**C. 장애 격리 / 복원력** ⭐
- **통합 서비스 구조 리스크 인지**: 한 물리 앱에 다수 논리 Agent → 단일 장애 → 다수 기능 정지
- 격리: 한 Agent 예외가 다른 Agent 로 전파 금지
- 부분 실패 허용: per-mapping 단위 (SyncLog 단위 성공/실패)
- 외부 의존 장애 시 graceful degradation (타임아웃 / 재시도)
- 복원: 재시도 멱등성, 부분 재실행 가능, 복구 절차 문서화
- 관측성: 장애 지점이 로그/모니터링에서 즉시 식별 가능

**D. 감사 / 롤백**
- 감사 로그 보존 (Retention 이 감사 로그 미삭제)
- DDL 롤백 스크립트
- JAR / 설정 롤백 시나리오

---

## 4. Invariant 문서 구조 (개별 파일 템플릿)

각 세부 invariant 파일은 다음 구조를 따른다.

```markdown
# Invariant: {이름}

## 1. 규약 정의
(규약 자체의 명확한 정의)

## 2. 적용 범위
(어떤 모듈 / 어떤 상황에서 지켜져야 하는지)

## 3. 준수 확인 절차
- [ ] (재실행 가능한 검증 절차)

## 4. 적용 Agent 현황표 (감시)
| Agent/모듈 | 준수 | 최종 확인 | 확인자 |
|-----------|:-:|---------|-------|
| ... | ✅/❌/⚠️ | YYYY-MM-DD | ... |

## 5. 위반 이력
- (발견된 위반과 대응)

## 6. 관련 문서 / 메모리
- dev_plan / MEMORY / 관련 이슈 번호
```

---

## 5. 갱신 규칙

- **새 Agent / 새 모듈 추가** 시 → 각 invariant 의 § 4 현황표에 행 추가 (verifier 책임)
- **규약 위반 발견** 시 → § 5 위반 이력 + `issues/OPEN/VER-NNN` 생성 + § 4 표에 ❌
- **규약 변경 / 신설** 시 → dev_plan 작성 → 본 overview § 2~3 갱신 → 세부 파일 작성

---

## 6. 검증 우선순위 (가이드)

invariant 위반은 **수정 범위(파트)** 에 따라 **상대적** 으로 다룬다 (절대 우선순위는 두지 않음).

- common(P1) 수정 → 모든 invariant 재검증
- Proxy(P2/P3) 수정 → § 3 헤더 라우팅 / § 4 추적 재검증
- 특정 Agent 수정 → 해당 Agent 의 § 4 현황표 갱신 + 관련 invariant 재검증
- 실배포 진입 직전 → **모든 invariant + 전 deployment 체크리스트** 통과 필수

`map/feature-dependency.md` 의 파트-invariant 매핑 참조.
