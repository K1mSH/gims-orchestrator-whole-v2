# API 제공용 Agent 개발 계획

> 작성일: 2026-04-21
> 모듈: **sync-agent-provide** (신규, 내부망 Agent)
> 포트: 8096
> 소스: Oracle 29004 (Proxy 경유) / 타겟: PG 29006 (자체 DB, ddl-auto)

---

## 배경

레거시 제공 API 3종(MEGOKR, 가뭄119, OPN)을 API Provider 시스템으로 이식.
API Provider는 단순 SELECT만 지원하므로, 복잡 SQL은 전처리 Step이 제공 테이블에 적재.

```
[Oracle 29004 원본 테이블]
     ↓ (sync-agent-provide: Type A 복사 / Type B 전처리)
[PG 29006: api_prv_* 제공 테이블]
     ↓
[API Provider(8095)가 JdbcTemplate 동적 SELECT → 외부 응답]
```

## 모듈 분리 이유

- bojo-int(Oracle→Oracle)과 타겟 DB가 다름 (Oracle vs PG)
- bojo-int에 이미 10개 파이프라인 + 87파일 — 부하/복잡도 분산
- 자기 DB(PG 29006)의 엔티티를 자기가 관리 — ddl-auto + JPA 쓰기
- DMZ의 bojo/others 분리와 동일 패턴

## 내부망 Agent 구성 (변경 후)

```
내부망:
  bojo-int (8092)       → RCV + Loader (IF_RSV → Oracle 적재) — 기존 유지
  sync-agent-provide (8096) → Oracle → PG 제공 테이블 적재 — 신규
  api-provider (8095)   → PG 제공 테이블 읽기 → 외부 응답 — 기존 유지
```

---

## 분류 (4/21 레거시 SQL 재검증)

> 상세 컬럼 정의: `provide-api-column-analysis.md` 참조

### Type A: 단순 복사 — 7건 (데이터 API만, 인증/로그 제외)

단일 테이블 SELECT, Oracle 함수/JOIN 없음. ProvideLoadStep 범용 Step 사용.

| # | SQL ID | API명 | 소스 테이블 | PG 제공 테이블 | merge-key | 컬럼 수 |
|---|--------|-------|-----------|---------------|-----------|:---:|
| A1 | selectNgw08 | 공공관정 가뭄지원 | TM_GD00203 | api_prv_tm_gd00203 | 복합 확인 | 11 |
| A2 | selectNgw09 | 공공관정 상세 | WT_DREAM_PERMWELL_PUBLIC | api_prv_wt_dream_permwell_public | PERM_NT_NO | 34 |
| A3 | selectdroght119 | 가뭄119 | SDE_NGWS.WT_DREAM_PERMWELL_PUBLIC_21033 | api_prv_wt_dream_permwell_public_21033 | OBJECTID | 33 |
| A4 | info_general | 관측망 상세 | TM_GD10001 | api_prv_tm_gd10001 | GENNUM | 5 |
| A5 | info_yhjs_info | 영향조사 상세 | TM_GD50001 | api_prv_tm_gd50001 | YH_SNO | 7 |
| A6 | selectNgw04_01 | 수질검사결과(TMP) | TMP_MEGOKR_API | api_prv_tmp_megokr_api | SN | 127 |
| A7 | selectNgw09_01 | 공공관정 상세(목록) | WT_DREAM_PERMWELL_PUBLIC | (A2와 공유) | PERM_NT_NO | 34 |

> A2/A7은 같은 테이블 → 제공 테이블 1개. A6은 TMP 테이블 존재 확인 필요.

### Type B: 전처리 필요 — 18건

| # | SQL ID | API명 | 복잡 요소 | PG 정제 테이블 | 난이도 |
|---|--------|-------|---------|---------------|:---:|
| B1 | selectNgw03 | 수질검사 개요 | 2중 서브쿼리 | api_prv_ngw03 | 중 |
| B2 | selectNgw03_01 | 수질검사 개요(TMP) | DECODE→CASE | (B1과 공유 가능) | 저 |
| B3 | selectNgw04 | 수질검사 결과 | PIVOT 125컬럼 | api_prv_ngw04 | 상 |
| B4 | info_permwell | 인허가관정 | JOIN+사용자정의함수 | api_prv_permwell | 중 |
| B5 | info_general_105 | 보조관측망 | 5테이블 LEFT JOIN | api_prv_general_105 | 상 |
| B6 | info_general_211215 | 수질측정망 | Oracle 뷰 의존 | api_prv_view_gtest | 중 |
| B7 | observation_station1 | 수위관측소 | DBLINK+실시간매칭 | api_prv_observation | 최상 |
| B8 | observation_station0 | 우량관측소 | DBLINK+실시간매칭 | api_prv_rainfall | 최상 |
| B9 | linkage_analy_chart | 관측그래프 | CTE+UNION+PIVOT | api_prv_linkage_chart | 상 |
| B10 | observationStationTime | 관측소 시간서비스 | PIVOT+스칼라 | (B9와 공유) | 상 |
| B11 | waterQualityInfo | 수질정보(범용) | 동적PIVOT+3JOIN | api_prv_water_quality | 최상 |
| B12 | waterQualityInfoDJ | 수질정보(대전) | 동적PIVOT+3JOIN | (B11과 공유) | 최상 |
| B13 | waterQualityMfdsInfo | 식약처 수질 | 동적PIVOT+2JOIN | api_prv_water_quality_mfds | 상 |
| B14 | searchInspection | 검사항목 | LEFT JOIN(공통코드) | api_prv_inspection | 저 |
| B15 | searchAllInspection | 전체 검사항목 | LEFT JOIN(공통코드) | (B14와 공유) | 저 |
| B16 | actualUseDetailDJ | 대전 이용실태 | CTE 2개+3JOIN | api_prv_actual_use_dj | 중 |
| B17 | unRegitsFclySmrize | 대전 미등록시설 | 스칼라8개+UNION | api_prv_unregits_fcly | 중 |
| B18 | gnlwtqltinfo_inputsittn | 대전 수질입력현황 | UNION+3중서브+3JOIN | api_prv_wq_input_status_dj | 상 |

---

## 증분 처리 전략

전체 재적재 불가 (측정 데이터 대량). 전 패턴 증분 처리.

### 전략 A: LINK_STATUS 기반 — 소스 테이블에 컬럼 추가

기존 bojo-int Loader와 동일 패턴. 소스 테이블을 우리가 관리할 수 있는 경우.

적용 대상:
- **Type A 전체** (1:1 단순 복사)
- **Type B 패턴1** (기준 테이블 + LEFT JOIN 보강): B1, B4, B5, B13, B14/B15
- **Type B 패턴2** (PIVOT — 기존 EAV→제원 N:1 패턴): B3, B9/B10, B11/B12
- **Type B 패턴4** (집계 — 메인 테이블 LINK_STATUS 따라감): B16, B17, B18

원본 테이블 추가 컬럼 (DDL에 포함):
- `LINK_STATUS` VARCHAR(20) DEFAULT 'PENDING' — PENDING → SUCCESS / FAILED
- `EXECUTION_ID` VARCHAR(100)
- `SOURCE_REFS` VARCHAR(4000)
- `EXTRACTED_AT` TIMESTAMP
- `UPDATED_AT` TIMESTAMP

### 보류 — B6/B7/B8 (3건)

실서버 확인 후 결정. 담당자 협의 필요.

| # | API | 소스 | 보류 사유 |
|---|-----|------|---------|
| B6 | info_general_211215 | VIEW_GTEST (Oracle 뷰) | 뷰 정의는 알고 있으나 수정 가능 여부 불명. LINK_STATUS 추가 또는 원본 테이블 직접 조회 등 방법 협의 필요 |
| B7 | observation_station1 | DBLINKUSR.DUBWLOBSIF 외 3개 | DB Link 테이블이 실서버에 존재하는지 불확실. 표준화자료/실DB에서 미확인 |
| B8 | observation_station0 | DUBRFOBSIF 외 2개 | B7과 동일 사유 |

> 담당자가 방향 결정 후 알려줄 예정. 그때 설계 반영.

### 패턴별 정리 (보류 3건 제외)

| 패턴 | 건수 | 증분 전략 | 기존 사례 |
|------|:---:|:---:|---------|
| Type A (1:1) | 7건 | LINK_STATUS | bojo-int Loader |
| B 기준+JOIN | 7건 | 기준 테이블 LINK_STATUS | bojo-int Loader |
| B PIVOT (N:1) | 5건 | 소스 LINK_STATUS → 변경 그룹 키만 재처리 | bojo-int EAV→제원 |
| B 집계 | 3건 | 메인 테이블 LINK_STATUS → 해당 조건만 재집계 | bojo-int Loader |

## 테이블 구조

| 구분 | 예시 | 관리 방식 | LINK_STATUS |
|------|------|---------|:---:|
| **원본** (Oracle, 우리 관리) | TM_GD10001, TM_GD30301 | DDL 스크립트 (수동) | O (전략 A) |
| **제공** (PG 29006) | api_prv_* | JPA 엔티티 (ddl-auto) | X |
| **보류** | VIEW_GTEST, DBLINKUSR.* | 미정 | B6/B7/B8 담당자 협의 후 |

제공 테이블 추적 컬럼 (JPA 엔티티):
- `EXECUTION_ID` VARCHAR(100)
- `SOURCE_REFS` VARCHAR(4000)
- `UPDATED_AT` TIMESTAMP

---

## 실행 흐름

```
[Orchestrator 실행 트리거]
    ↓
[sync-agent-provide(8096): PipelineService.executeAsync()]
    ↓ agentCode = "provide-copy" or "provide-preprocessor"
[PipelineRegistry → PipelineRunner → Step 순차 실행]
    ↓
[Step: Oracle(Proxy 경유) 조회 → PG 29006 UPSERT (INSERT ... ON CONFLICT)]
    ↓
[SyncLog 기록]
```

---

## 모듈 구조 — sync-agent-provide (신규)

bojo-int 복제 기반, others와 동일한 생성 패턴.

```
sync-agent-provide/
├── build.gradle                          ← common JAR 의존 + PG + Oracle 드라이버
├── src/main/java/com/sync/agent/provide/
│   ├── ProvideAgentApplication.java      ← @SpringBootApplication
│   ├── config/
│   │   ├── SyncDataSourceService.java    ← Proxy 경유 Oracle + 자체 PG
│   │   ├── DynamicEntityManagerService.java
│   │   ├── CaseAwareNamingStrategy.java
│   │   └── pipeline/
│   │       ├── AgentConfigLoader.java
│   │       └── PipelineRegistry.java
│   ├── controller/
│   │   └── PipelineController.java
│   ├── entity/target/                    ← 제공 테이블 엔티티 (ddl-auto, PG 29006)
│   │   │  ── Type A ──
│   │   ├── ApiPrvWtDreamPermwellPublic21033.java
│   │   ├── ApiPrvTmGd30301.java
│   │   ├── ApiPrvTmGd00203.java
│   │   ├── ApiPrvWtDreamPermwellPublic.java
│   │   ├── ApiPrvRgetnpmms01.java
│   │   ├── ApiPrvTmGd10001.java
│   │   ├── ApiPrvViewGtest.java
│   │   ├── ApiPrvTmGd50001.java
│   │   ├── ApiPrvTmGd30310.java
│   │   │  ── Type B ──
│   │   ├── ApiPrvTmGd30302.java
│   │   ├── ApiPrvWaterQuality.java
│   │   ├── ApiPrvGeneral105.java
│   │   ├── ApiPrvObservation.java
│   │   ├── ApiPrvRainfall.java
│   │   └── ApiPrvLinkageChart.java
│   ├── pipeline/
│   │   └── PipelineService.java
│   └── loader/
│       ├── factory/
│       │   ├── SimpleLoadStepFactory.java       ← Type A용 (common 또는 복제)
│       │   └── PreprocessLoadStepFactory.java   ← Type B용
│       └── step/
│           ├── PreprocessNgw04LoadStep.java
│           ├── PreprocessWaterQualityLoadStep.java
│           ├── PreprocessGeneral105LoadStep.java
│           ├── PreprocessObservationLoadStep.java
│           ├── PreprocessRainfallLoadStep.java
│           └── PreprocessLinkageChartLoadStep.java
├── src/main/resources/
│   ├── application.yml                   ← port 8096, PG 29006, Proxy URL
│   └── config/agents/                    ← 테이블별 개별 YAML (추가 시 파일만 추가)
│       ├── provide-drought119.yml        ← A1: 단순 복사
│       ├── provide-tm-gd30301.yml        ← A2: 단순 복사
│       ├── provide-tm-gd00203.yml        ← A3: 단순 복사
│       ├── provide-rgetnpmms01.yml       ← A5: 단순 복사
│       ├── provide-tm-gd30302.yml        ← B1/B2: 전처리 (PIVOT)
│       ├── provide-water-quality.yml     ← B3/B4: 전처리 (JOIN+CASE)
│       ├── ...                           ← 이하 동일 패턴
└── libs/
    └── sync-agent-common-1.0.0-SNAPSHOT.jar
```

---

## DB 현황 (개발 환경)

| Oracle 29004에 **있음** | Oracle 29004에 **없음** (실서버만) |
|--------------------------|---------------------------------------|
| RGETNPMMS01, RGETNTGMS02 | TM_GD10001, TM_GD30301, TM_GD30302, TM_GD30310 |
| TM_GD970xxx, PM_GD970xxx | TM_GD00203, TM_GD50001, TM_GD00301 |
| TM_GD111xxx, PM_GD111xxx | TM_GD60001/60002/60101/60130, TM_GD70002/70201/70202 |
| (보조관측/제주/이용량/새올) | PM_GD60201, TM_GD20910/20930, TC_GD00100/00002 |
| | WT_DREAM_*, VIEW_GTEST, DBLINKUSR.* |

---

## 관리 테이블

`execution`, `sync_log`은 PG 29006에 같이 둔다 (DMZ bojo/others가 같은 PG 공유하는 것과 동일 패턴).
`agent_id` 필드로 어떤 Agent 실행인지 구분 가능.

---

## api-provider 기존 엔티티 이관

api-provider `entity/provide/`에 있는 4개 엔티티를 sync-agent-provide로 이관 후 삭제.
api-provider의 DynamicQueryService는 순수 JdbcTemplate이라 엔티티 불필요.

| 기존 위치 (api-provider) | 이관 위치 (sync-agent-provide) | 테이블 |
|---|---|---|
| ApiPrvTmGd000203.java | entity/target/ApiPrvTmGd00203.java | api_prv_tm_gd000203 |
| ApiPrvTmGd110301.java | entity/target/ApiPrvTmGd110301.java | api_prv_tm_gd110301 |
| ApiPrvTmGd110302.java | entity/target/ApiPrvTmGd110302.java | api_prv_tm_gd110302 |
| ApiPrvTmGd112002.java | entity/target/ApiPrvTmGd112002.java | api_prv_tm_gd112002 |

---

## 개발용 Oracle DDL 대상

Oracle 29004에 없는 소스 테이블 — DDL + 샘플 데이터로 생성 필요 (엔티티 아닌 스크립트).
위치: `scripts/ddl/internal-oracle/provide-source/`

| 테이블 | 사용 API | Type | 비고 |
|--------|---------|:---:|------|
| TM_GD30301 | NGW_03 | A | |
| TM_GD30302 | NGW_04 | B | PIVOT 소스 |
| TM_GD00203 | NGW_08 | A | |
| TM_GD10001 | 관측망/수질 등 다수 | A+B | 여러 API에서 공통 사용 |
| TM_GD50001 | 영향조사 | A | |
| TM_GD30310 | 수질검사 항목 | A | |
| WT_DREAM_PERMWELL_PUBLIC | NGW_09 | A | |
| SDE_NGWS.WT_DREAM_PERMWELL_PUBLIC_21033 | 가뭄119 | A | SDE 스키마 |
| VIEW_GTEST | 수질측정망 | A | 뷰 |
| TC_GD00002 | 공통코드 | B | waterQuality 참조 |
| TC_GD00100 | 행정구역 | B | permwell 참조 |
| TM_GD60001 | 관측점 마스터 | B | general_105 |
| TM_GD60002 | 관측점 상세 | B | general_105 |
| TM_GD60101 | 관측 지점/항목 | B | general_105, linkage_chart |
| TM_GD60130 | 관측 지점 상세 | B | general_105 |
| TM_GD70002 | 검색 DB 접속정보 | B | general_105 |
| PM_GD60201 | 관측 데이터 | B | linkage_chart |
| DBLINKUSR.* (4개) | 수위/우량관측소 | B | DBLINK — 별도 협의 |

> RGETNPMMS01은 이미 존재 — DDL 불필요, LINK_STATUS 컬럼 ALTER TABLE 추가만 필요

---

## 확인 필요 사항

### Q1. RGETNPMMS01 LINK_STATUS 컬럼 추가
이미 Oracle 29004에 있는 테이블 (bojo-int이 적재). ALTER TABLE로 LINK_STATUS 등 추적 컬럼 추가 시 기존 bojo-int 동작에 영향 없는지?
> **답변**: 기본값이 pending이 되긴 해야할듯? 그 전과정에서 upsert가 되었다면 어쨌든 재추적 대상은 맞아

### Q2. 포트 8096
기존 포트 목록에서 8096 사용 가능한지?
> **답변**: 확인 완료 — 8096 사용 가능 (기존 8080~8095 사이 빈 포트)

### Q3. Oracle 소스 접근 방식
내부망이므로 Oracle 29004 직접 연결도 가능. Proxy 경유 vs 직접 연결?
> **답변**: 직접 연결하면 yml에서 2개의 datasource를 정의해야하지 않나? 배포 환경에서 이게 자연스럽게 되는지가 우려스러워서 원래 패턴대로 db에서 가져와서 쓰는걸 생각중임



### YAML 구조 — 테이블별 개별 파일

기능별(copy/preprocess) 묶음이 아닌 **테이블별 개별 YAML**로 분리.
새 제공 대상 추가 시 YAML 파일 하나만 추가, 기존 파일 수정 불필요.

YAML 상단에 주석으로 패턴 구분:
```yaml
# ──────────────────────────────────────
# 패턴: 단순 복사 (Oracle 원본 → PG 제공 테이블 1:1)
# 소스: RGETNPMMS01 (인허가관정)
# 타겟: api_prv_rgetnpmms01
# ──────────────────────────────────────
agent-code: provide-rgetnpmms01
type: LOADER
steps:
  - id: copy-rgetnpmms01
    ...
```

```yaml
# ──────────────────────────────────────
# 패턴: 전처리 (Oracle 복잡 SQL → PG 정제 테이블)
# 소스: TM_GD30302 + TM_GD30301 + TM_GD10001 (PIVOT 125컬럼)
# 타겟: api_prv_tm_gd30302
# ──────────────────────────────────────
agent-code: provide-tm-gd30302
type: LOADER
steps:
  - id: preprocess-tm-gd30302
    ...
```

---

## 진행 순서

1. **모듈 생성** — bojo-int 복제 기반, 불필요 코드 제거, application.yml(8096, PG 29006)
2. **엔티티 + YAML** — A5(RGETNPMMS01) 먼저 1개로 E2E 검증
3. **빌드 + 기동 테스트** — Orchestrator에서 Agent 등록 → 실행 → PG 29006 확인
4. **api-provider 연동 테스트** — 제공 테이블에서 오퍼레이션 등록 → 동적 SELECT → 외부 응답
5. 나머지 Type A 확장
6. Type B Step 구현 (실서버 접근 확보 후)

---

## api-provider 영향

- api-provider의 `DynamicQueryService`는 순수 JdbcTemplate — 엔티티 불필요
- 기존 `entity/provide/` 4개 엔티티는 sync-agent-provide로 이관 후 삭제
- api-provider 코드 변경 없음

---

## 참조 파일

| 분류 | 파일 |
|------|------|
| StepExecutor | `sync-agent-common/.../step/StepExecutor.java` |
| StepContext | `sync-agent-common/.../step/StepContext.java` |
| StepResult | `sync-agent-common/.../step/StepResult.java` |
| StepFactory | `sync-agent-common/.../pipeline/StepFactory.java` |
| 복제 베이스 (bojo-int) | `sync-agent-bojo-int/` 전체 |
| 분리 패턴 참조 (others) | `sync-agent-others/` 전체 |
| 기존 제공 엔티티 | `gims-api-provider/.../entity/provide/` |
| 레거시 SQL (MEGOKR) | `newgims_v2/src/egovframework/sqlmap/com/gims/sql_megokrapi.xml` |
| 레거시 SQL (OPN) | `newgims_v2/src/egovframework/sqlmap/com/gims/sql_opn.xml` |
| 레거시 SQL (가뭄119) | `newgims_v2/src/egovframework/sqlmap/com/gims/sql_drought119api.xml` |
