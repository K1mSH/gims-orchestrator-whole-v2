# 레거시 API → provide 이식 전체 맵

> 작성일: 2026-04-24
> 범위: 레거시 MEGOKR / 가뭄119 / OPN API 25건 전체
> 목적: provide Agent 이식 진행 상황, 처리 결정, 근거를 단일 문서로 관리
> 선행 자료:
>  - `dev_plan/2026_04/21/provide-api-column-analysis.md` (원재료 — 25건 컬럼 정의)
>  - `dev_plan/2026_04/22/provide-source-table-strategy.md` (Type A 이식 전략)
>  - `dev_logs/2026_04/2026-04-23.md` (Type A 이식 완료)

---

## 목차

1. [개요](#1-개요)
2. [처리 결정 카테고리](#2-처리-결정-카테고리)
3. [Type A — 완료 (7건)](#3-type-a--완료-7건)
4. [Type B — 재분류 결과 (18건)](#4-type-b--재분류-결과-18건)
5. [레거시 URL 매핑 · 노출 성격](#5-레거시-url-매핑--노출-성격)
6. [엔티티 현황 · 재검토 대상](#6-엔티티-현황--재검토-대상)
7. [설계 원칙](#7-설계-원칙)
8. [이번 이식 범위 (확정)](#8-이번-이식-범위-확정)
9. [보류 / 장기 과제](#9-보류--장기-과제)

---

## 1. 개요

레거시 `newgims_v2` 에서 외부로 제공하던 API 25건을 **provide Agent** 로 이식하여 PG 제공 테이블(`api_prv_*`) 에 적재, 별도 제공 계층이 읽어 외부에 서빙한다.

- **Type A (7건)**: 단일 테이블 SELECT, 복잡 로직 없음 — 모두 이식 완료
- **Type B (18건)**: JOIN / PIVOT / 함수 / DBLINK / 동적 SQL 등 전처리 필요
- 분류 기준은 4/21 분석서(`provide-api-column-analysis.md`) 에서 확립

2026-04-24 전수 재분석 결과, Type B 18건 중:
- **A 재활용 3건** (B1/B2/B3) — A7 완료로 커버
- **Step 작성 11종** — 중 2종은 엔티티 추가 필요
- **외부층 처리 1건** (B15) — B14 의 DISTINCT 결과
- **보류 3건** (B6/B7/B8)

---

## 2. 처리 결정 카테고리

| 표기 | 의미 | 판정 기준 |
|:--:|---|---|
| ✅ | **완료** | provide Agent 이식 완료, 운영 가능 |
| ♻️ | **재활용** | 다른 Agent 의 타겟으로 커버됨, 별도 이식 불필요 |
| 🔨 | **Step 작성 예정** | 이번 사이클 이식 대상 |
| 🔀 | **외부층 처리** | 별도 Step 없이 제공 API 계층에서 SELECT 가공 |
| ⏸ | **보류** | 선행 조건 미해결 (뷰 정의 / DBLINK 담당자 확인) |

---

## 3. Type A — 완료 (7건)

> 단일 테이블 SELECT, NVL/`||` 등 PG 호환 가능 수준. 4/23 까지 전부 이식 완료.

| A# | SQL ID | 소스 테이블 | 타겟 | Agent Code | 상태 |
|:--:|---|---|---|---|:--:|
| A1 | `megokrapi.selectNgw08` | `TM_GD000203` (표준화) | `api_prv_tm_gd000203` | `provide-tm-gd000203` | ✅ |
| A2 | `megokrapi.selectNgw09` | `TM_GD112002` (← WT_DREAM_PERMWELL_PUBLIC 표준화) | `api_prv_tm_gd112002` | `provide-tm-gd112002` | ✅ |
| A3 | `megokrapi.selectNgw09_01` | ↑ (A2 공유) | ↑ (A2 타겟 재활용) | — | ✅ (외부층 동일 소스) |
| A4 | `drought119api.selectdroght119` | `SDE_NGWS.WT_DREAM_PERMWELL_PUBLIC_21033` | `api_prv_wt_dream_permwell_public_21033` | `provide-wt-dream-permwell-public-21033` | ✅ |
| A5 | `opn.info_general` | `TM_GD120001` (← TM_GD10001 표준화) | `api_prv_tm_gd120001` | `provide-tm-gd120001` | ✅ |
| A6 | `opn.info_yhjs_info` | `TM_GD130001` (← TM_GD50001 표준화) | `api_prv_tm_gd130001` | `provide-tm-gd130001` | ✅ |
| A7 | `megokrapi.selectNgw04_01` | `TMP_MEGOKR_API` (149컬럼, 이미 PIVOT 완료) | `api_prv_tmp_megokr_api` | `provide-tmp-megokr-api` | ✅ |

> **A7 의 특수성**: `TMP_MEGOKR_API` 는 `TM_GD30301` (22 메타) + `TM_GD30302` 를 125 WT_* 컬럼으로 PIVOT 한 결과 = 149 컬럼이 이미 이식된 상태. **Type B 의 B1/B2/B3 를 모두 커버하는 핵심 효과** (아래 4절 참조).

---

## 4. Type B — 재분류 결과 (18건)

### 4.1 전수 매트릭스

| B# | SQL ID | 소스 | 결과 컬럼 요약 | 복잡 요소 | 결정 | Step 클래스 | 엔티티 |
|:--:|---|---|---|---|:--:|---|:--:|
| B1 | `selectNgw03` | `TM_GD30301` | 22 메타 | 2중 서브쿼리 (JOSACODE/INVSTG_YEAR 필터) | ♻️ **A7 재활용** | — | — |
| B2 | `selectNgw03_01` | `TMP_MEGOKR_API` | 22 + RNUM | DECODE→CASE, FETCH 10000 | ♻️ **A7 재활용** | — | — |
| B3 | `selectNgw04` | `TM_GD30302` PIVOT | 125 WT_* + PK | PIVOT 125컬럼 | ♻️ **A7 재활용** | — | — |
| B4 | `info_permwell` | `RGETNPMMS01`+`TC_GD00100` | 13 컬럼 | JOIN + Oracle `FN_GD_GET_GUBUN`/`FN_GD_GET_CMMTNDCODE` | 🔨 Step | `ApiPrvPermwellLoadStep` | ✅ `ApiPrvPermwell` |
| B5 | `info_general_105` | `TM_GD10001` 외 5개 | 17 컬럼 | 5-way LEFT JOIN | 🔨 Step | `ApiPrvGeneral105LoadStep` | ✅ `ApiPrvGeneral105` |
| B6 | `info_general_211215` | `VIEW_GTEST` | 6 컬럼 | Oracle 뷰 의존 | ⏸ **제외** (뷰 정의 확보) | — | ❌ |
| B7 | `info_observation_station1` | `DBLINKUSR.DUBWLOBSIF` 외 4개 | 15 컬럼 | DBLINK + 실시간 10분 맞물림 | ⏸ **제외** (DBLINK) | — | ❌ |
| B8 | `info_observation_station0` | `DUBRFOBSIF` 외 2개 | 11 컬럼 | DBLINK + 실시간 | ⏸ **제외** (DBLINK) | — | ❌ |
| B9 | `linkage_analy_chart_general` | `PM_GD60201`+`60101`+`10001` | 6 컬럼 (`YMD=YYYYMMDD`) | CTE + UNION + PIVOT + 스칼라 | 🔨 Step (**일 단위**) | `ApiPrvLinkageChartLoadStep` | ✅ `ApiPrvLinkageChart` |
| B10 | `observationStationTimeService` | ↑ (B9 동일 소스) | 6 컬럼 (`YMD=YYYYMMDDHH24`) | PIVOT + 스칼라 + `datatype` 분기 + `TRUNC` | 🔨 Step (**시 단위**, B9 와 분리) | **`ApiPrvObsStationTimeLoadStep`** | **❌ 추가 필요** |
| B11 | `waterQualityInfo` | `10001`+`30301`+`30302`+`TC00002` | **14 고정** + 동적 C* | 동적 PIVOT + 3JOIN + 스칼라 | 🔨 Step | `ApiPrvWaterQualityLoadStep` | ✅ `ApiPrvWaterQuality` |
| B12 | `waterQualityInfoDJ` | `10001`+`30301`+`30302` | **12 고정** + 동적 C* (대전 전용, 일부 컬럼 없음) | 동적 PIVOT + 3JOIN | 🔨 Step (**대전 전용**, B11 과 분리) | **`ApiPrvWaterQualityDjLoadStep`** | **❌ 추가 필요** |
| B13 | `waterQualityMfdsInfo` | `70201`+`70202`+`20910` | 20 고정 + 동적 C* | 동적 PIVOT + 2JOIN + 스칼라 | 🔨 Step | `ApiPrvWaterQualityMfdsLoadStep` | ✅ `ApiPrvWaterQualityMfds` |
| B14 | `searchInspection` | `TM_GD30310`+`TC_GD00002` | **4 컬럼** (JOSACODE/YEAR 포함) | LEFT JOIN | 🔨 Step (**파일럿**) | `ApiPrvInspectionLoadStep` | ✅ `ApiPrvInspection` |
| B15 | `searchAllInspection` | ↑ (B14 동일 소스) | **2 컬럼** (GROUP BY) | LEFT JOIN + GROUP BY | 🔀 **외부층 DISTINCT** (B14 커버) | — | — |
| B16 | `actualUseDetailDJ` | `TC00100`+`20930`+`RGETNTGMS02` | 5 컬럼 | CTE 2개 + 3JOIN + `ROW_NUMBER` + DECODE | 🔨 Step | `ApiPrvActualUseDjLoadStep` | ✅ `ApiPrvActualUseDj` |
| B17 | `unRegitsFclySmrize` | `TM_GD00301` | 10 컬럼 | 스칼라 서브쿼리 8개 + UNION ALL | 🔨 Step | `ApiPrvUnregitsFclyLoadStep` | ✅ `ApiPrvUnregitsFcly` |
| B18 | `gnlwtqltinfo_inputsittn` | `10001`+`30301`+`30302` | 7 컬럼 | UNION ALL + 3중 서브쿼리 + 3JOIN (대전 전용) | 🔨 Step | `ApiPrvWqInputStatusDjLoadStep` | ✅ `ApiPrvWqInputStatusDj` |

### 4.2 A7 재활용 근거 (B1/B2/B3)

`TMP_MEGOKR_API` 는 `TM_GD30301` (22 메타) + `TM_GD30302` 의 125 WT_* PIVOT 결과를 **이미 담고 있는** 149컬럼 테이블. 즉:

| 요구 API | 필요 컬럼 | `api_prv_tmp_megokr_api` 에 있는가? |
|---|---|:---:|
| B1 `selectNgw03` | 22 메타 | ✅ (메타 24 에 포함) |
| B2 `selectNgw03_01` | 22 메타 + RNUM + DECODE 보정 | ✅ (메타 24 에 포함, DECODE 는 외부층에서) |
| B3 `selectNgw04` | 125 WT_* + PK | ✅ (WT_* 125 그대로) |

→ **별도 엔티티/Agent/Step 불필요**. 외부 API 제공층에서 `api_prv_tmp_megokr_api` 를 SELECT 할 때 컬럼 선택 + WHERE + DECODE 적용.

### 4.3 분리 필수 쌍 상세

#### B9 vs B10 — 시간 granularity 다름

| 항목 | B9 `linkage_analy_chart_general` | B10 `observationStationTimeService` |
|---|---|---|
| YMD 형식 | `YYYYMMDD` (일) | `YYYYMMDDHH24` (시) |
| TIME_UNIT_ID | 4 (일간) | 3 (시간별) |
| UNION | quality=1 + quality=5 병합 | `datatype` 파라미터 분기 |
| ELEV/WTEMP | 원본 | `TRUNC(2)`, `TRUNC(1)` |
| 레코드 개수 | 일당 1건 | 시간당 1건 (24배) |

→ 레코드 개수 자체가 달라 **타겟 분리 필수**.

#### B11 vs B12 — 필드 구성 다름

| 항목 | B11 `waterQualityInfo` | B12 `waterQualityInfoDJ` |
|---|---|---|
| 고정 컬럼 수 | 14 | 12 |
| `qltwtrInspctSn` | 포함 | **없음** |
| `usrNM` | 포함 (TC_GD00002 JOIN) | **없음** |
| `ugrwtrPrposCode` | CASE WHEN 디코딩 (생활용/공업용/…) | 원본 코드 그대로 |
| `brtcNm` 필터 | 선택적 | **필수 (대전 강제)** |
| 소스 JOIN | `TC_GD00002` 포함 (4테이블) | 3테이블 |

→ API 응답 스펙 자체가 다름. **타겟 분리 필수**.

#### B14 vs B15 — B15 는 B14 의 DISTINCT

| 항목 | B14 `searchInspection` | B15 `searchAllInspection` |
|---|---|---|
| SELECT 컬럼 | 4 (`JOSACODE`, `DTA_STDR_YEAR`, `QLTWTR_INSPCT_IEM_CODE`, `remarkCtnt`) | **2** (`QLTWTR_INSPCT_IEM_CODE`, `remarkCtnt`) |
| GROUP BY | 없음 | `QLTWTR_INSPCT_IEM_CODE, remarkCtnt` |
| WHERE | `josacode=? AND dtaStdrYear=?` | 전체 |

→ B14 결과를 **외부 API 제공층에서 `SELECT DISTINCT QLTWTR_INSPCT_IEM_CODE, remarkCtnt`** 로 B15 완전 대체. 별도 Step 불필요.

> **참고**: B14/B15 는 레거시 OPN 에서 직접 공개 API 로 노출되지 않고, `waterQuality*` API 의 **helper SQL** 및 **관리자 페이지 (manage/*)** 에서만 사용됨. 자세한 성격은 [§5 레거시 URL 매핑](#5-레거시-url-매핑--노출-성격) 참조.

---

## 5. 레거시 URL 매핑 · 노출 성격

레거시 `newgims_v2` 의 Controller URL 매핑 기준으로 각 SQL 이 **어떤 경로로 호출되고 실제 외부에 노출되는 성격인지** 기록.

> 목적: 이식 후 외부 API 제공 계층 구현 시 "어떤 API 를 어떤 방식으로 서빙할지" 판단 근거. 외부 공개 대상과 내부 전용을 구분하여 제공 계층의 라우팅/인증을 설계할 수 있음.

### 5.1 노출 성격 카테고리

| 기호 | 의미 | 예시 |
|:--:|---|---|
| 🌐 | **공공 공개 API** — `openapiList.do` 공개 카탈로그 등록 추정. API 키 인증으로 누구나 사용 가능 | OPN `data/{service}/{operation}` 계열 |
| 🤝 | **B2B 연동 API** — 특정 외부 기관(MEGOKR, 가뭄119 등) 과 계약 연동. 공개 카탈로그엔 미등록 | `/megokrApi/*`, `/drought119Api/*` |
| 🔧 | **내부 helper** — 다른 API 호출 시 전처리 전용, 독립 엔드포인트 없음 | B14 (waterQuality* helper) |
| 🏢 | **관리자/내부 화면** — 사이트 내부 관리 페이지에서만 사용 | B15 등 (manage/*) |

### 5.2 URL 매핑 매트릭스

**MegokrApiController** (`/megokrApi/*`) — 🤝 **전부 B2B 연동**:

| # | URL | SQL | 용도 추정 |
|:--:|---|---|---|
| A1 | `/megokrApi/ngw08` | selectNgw08 | 공공관정 가뭄지원 |
| A2 | `/megokrApi/ngw09` | selectNgw09 | 공공관정 상세 (단건) |
| A3 | `/megokrApi/ngw09_01` | selectNgw09_01 | 공공관정 상세 (목록) |
| A7 | `/megokrApi/ngw04_01` | selectNgw04_01 | 수질검사결과 (TMP 기반) |
| B1 | `/megokrApi/ngw03` | selectNgw03 | 수질검사개요 (단건) |
| B2 | `/megokrApi/ngw03_01` | selectNgw03_01 | 수질검사개요 (목록) |
| B3 | `/megokrApi/ngw04` | selectNgw04 | 수질검사결과 (원본 PIVOT) |

**Drought119ApiController** — 🤝 **B2B 연동**:

| # | URL | SQL | 용도 추정 |
|:--:|---|---|---|
| A4 | `/drought119Api/selectDrought119` | selectdroght119 | 가뭄119 인허가관정 |

**OPNController** (`/api/data/{service}/{operation}`) — 🌐 **공공 공개 API 디스패처**:

| # | service | operation | SQL | 비고 |
|:--:|---|---|---|---|
| A5 | `groundwaterMonitoringNetworkService` | `getNationalGroundwater` | info_general | 국가지하수관측망 |
| A5 | ↑ | `getSeawaterPermeation` | info_general (공유) | 해수침투망 |
| A5 | ↑ | `getRuralGroundwater` | info_general (공유) | 농촌지하수 |
| A5 | `surveyFacilitiesService` | `getBasicSurvey` | info_general (공유) | 기초조사 — **A5 가 총 4 operation 공유** |
| A6 | `surveyFacilitiesService` | `getImpactInvestigation` | info_yhjs_info | 영향조사 |
| B4 | `wellInfoService` | `getWellInfo` | info_permwell | 인허가관정 상세 |
| B5 | `groundwaterMonitoringNetworkService` | `getSupplementaryGroundwater` | info_general_105 | 보조지하수관측망 |
| B6 | `groundwaterMonitoringNetworkService` | `getGroundwaterQualityMeasurement` | info_general_211215 | 수질측정망 (VIEW_GTEST) |
| B7 | `observationStationService` | `getWaterLevelObservationStation` | info_observation_station1 | 수위관측소 (DBLINK) |
| B8 | `observationStationService` | `getRainfallStation` | info_observation_station0 | 우량관측소 (DBLINK) |
| B9 | `observationStationService` | `getGroundwaterMonitoringNetwork` | linkage_analy_chart_general | 관측그래프 (일 단위) |
| B10 | `observationStationTimeService` | `observationStationTimeService` | observationStationTimeService | 관측소 시간서비스 |
| B11 | `waterQualityInfo` | `waterQualityInfo` | waterQualityInfo | 수질정보 (범용) |
| B12 | `waterQualityInfoDJ` | `waterQualityInfoDJ` | waterQualityInfoDJ (brtcNm=대전광역시) | 대전 수질정보 |
| B12 | `waterQualityInfoKB` | `waterQualityInfoKB` | waterQualityInfoDJ (brtcNm=경상북도) | **경북 수질정보 — B12 SQL 공유** |
| B13 | `waterQualityMfdsInfo` | `waterQualityMfdsInfo` | waterQualityMfdsInfo | 식약처 수질 |
| B16 | `actualUseDetailDJ` | `actualUseDetailDJ` | actualUseDetailDJ (brtcNm=대전광역시) | 대전 이용실태 |
| B16 | `actualUseDetailKB` | `actualUseDetailKB` | actualUseDetailDJ (brtcNm=경상북도) | **경북 이용실태 — B16 SQL 공유** |
| B17 | `unRegitsFclySmrize` | `unRegitsFclySmrize` | unRegitsFclySmrize | 대전 미등록시설 (하드코딩) |
| B18 | `gnlwtqltinfo_inputsittn` | `gnlwtqltinfo_inputsittn` | gnlwtqltinfo_inputsittn | 대전 수질입력현황 |

**OPN 직접 URL 매핑 없음**:

| # | SQL | 사용처 | 성격 |
|:--:|---|---|:--:|
| B14 | searchInspection | OPN `waterQualityInfo/DJ/KB/Mfds` 호출 전 검사항목 codeList 조회 (내부 전처리) + `manage/*` 관리자 화면 | 🔧 + 🏢 |
| B15 | searchAllInspection | `manage/*` 관리자 화면 전용 (OPN 에서 호출 안 함) | 🏢 |

### 5.3 노출 성격 집계

| 성격 | 고유 SQL 수 | 해당 B#/A# |
|:--|:--:|---|
| 🌐 공공 공개 (OPN) | **14** | A5, A6, B4, B5, B6, B7, B8, B9, B10, B11, B12, B13, B16, B17, B18 |
| └ 그 중 operation 공유 확장 | A5 (4 op) / B12 (2 op: DJ+KB) / B16 (2 op: DJ+KB) | — |
| 🤝 B2B 연동 (MegokrApi/Drought119) | 8 | A1, A2, A3, A4, A7, B1, B2, B3 |
| 🔧 내부 helper | 1 | B14 |
| 🏢 관리자 화면 전용 | 2 | B14 (겸용), B15 |

> **"사이트에 공개된 API 목록이 11개쯤" 의 배경**: OPN 공공 공개 14건 중 `openapiList.do` 에 실제 등록된 건만 공개 카탈로그에 노출되며, 11개 정도라면 대부분의 공공 API가 등록되어 있다는 의미. 나머지 14건(B2B/helper/관리자)은 공개 카탈로그와 별도로 운영되므로 "사이트 상 보이지 않음" 이 정상.

### 5.4 이식 전략 함의

- **이식 대상 선정은 "공개/비공개" 무관** — 레거시에서 어떤 방식으로든 사용되는 SQL 은 신규 시스템에서도 필요할 가능성 높음.
- **제공 계층 구현 시점**에 위 성격 구분이 라우팅/인증 정책 결정에 활용:
  - 🌐 공공 공개: 공개 API 키 기반, 카탈로그 노출
  - 🤝 B2B: 기관별 API 키 + IP 화이트리스트
  - 🔧 내부 helper: 외부 노출 없음, 다른 API 구현의 내부 조합
  - 🏢 관리자 화면: 관리자 세션 인증
- **B14/B15 의 재분류 결정 재검토 필요 없음** — B14 는 내부 helper 라도 신규 시스템에서 `waterQualityInfo` 구현 시 동일 전처리 필요하니 이식 대상 유지. B15 는 여전히 외부층 DISTINCT 로 대체 가능하나, 실제로는 관리자 화면에서 직접 쿼리하므로 이식 안 해도 관리자 기능 재구현 시 별도 처리.

---

## 6. 엔티티 현황 · 재검토 대상

### 6.1 현재 엔티티 (18개)

**Type A 대응 (7개, 이식 완료)**:
- `ApiPrvTmGd000203` (A1)
- `ApiPrvTmGd112002` (A2/A3)
- `ApiPrvWtDreamPermwellPublic21033` (A4)
- `ApiPrvTmGd120001` (A5)
- `ApiPrvTmGd130001` (A6)
- `ApiPrvTmpMegokrApi` (A7)

**Type B 대응 (10개, 이식 대기)**:
- `ApiPrvPermwell` (B4)
- `ApiPrvGeneral105` (B5)
- `ApiPrvLinkageChart` (B9)
- `ApiPrvWaterQuality` (B11)
- `ApiPrvWaterQualityMfds` (B13)
- `ApiPrvInspection` (B14)
- `ApiPrvActualUseDj` (B16)
- `ApiPrvUnregitsFcly` (B17)
- `ApiPrvWqInputStatusDj` (B18)
- `ApiPrvNgw04` (B3 대응용으로 작성됨 — **재분류로 불필요**)

**⚠️ 재검토 대상 (3개)**:
- `ApiPrvTmGd110301` + YAML `provide-tm-gd110301.yml` + 테이블 `api_prv_tm_gd110301` — **B1/B2 원본 단순 카피. 재분류로 A7 재활용 결론 → 중복**
- `ApiPrvTmGd110302` + YAML `provide-tm-gd110302.yml` + 테이블 `api_prv_tm_gd110302` — **B3 원본 단순 카피. 재분류로 A7 재활용 결론 → 중복**
- `ApiPrvNgw04` — **B3 전처리 결과용. 재분류로 A7 재활용 결론 → 불필요**

> 이들은 4/22 세션에서 "전처리 Step 구현 시 대체 예정" 전제로 작성된 임시 단순 카피. 재분류 결과 **대체 자체가 불필요** (A7 이 모두 커버). 제거 대상으로 판단되나, 실제 제거 전에 사용자 승인 + 실데이터 확인 필요.

### 6.2 추가 작성 필요 (2개)

이번 재분류로 새롭게 **엔티티 분리가 필요**한 건:

- **`ApiPrvObsStationTime`** — B10 시 단위 관측. 타겟 `api_prv_obs_station_time`
- **`ApiPrvWaterQualityDj`** — B12 대전 수질. 타겟 `api_prv_water_quality_dj`

두 엔티티 모두 파일럿 이후 해당 Step 작성 시점에 함께 생성 (일괄 생성 불필요).

---

## 7. 설계 원칙

### 7.1 재활용 판정 기준

한 레거시 API 의 결과 컬럼이 **이미 이식된 다른 타겟의 부분집합** 이면 재활용:
- 컬럼 이름 일치 또는 단순 변환(CASE/DECODE)만 필요
- WHERE 조건은 외부 API 제공층에서 적용 가능
- **예시**: B1/B2/B3 → A7

### 7.2 분리 판정 기준

다음 중 하나라도 해당하면 **타겟 분리 필수**:

| 기준 | 예시 |
|---|---|
| 결과 컬럼 구성이 다름 (필드 수 / 이름) | B11 vs B12 (14 vs 12 고정) |
| 시간 granularity 다름 (일/시/분) | B9 일 vs B10 시 |
| 필수 필터가 스펙상 고정 (지역/조건) | B12 대전 전용, B18 대전 전용 |
| 집계 수준 다름 (원본 vs GROUP BY) | B14 상세 vs B15 DISTINCT |

### 7.3 외부층 위임 기준

다음에 한해 별도 Step 없이 **외부 API 제공 계층**에서 처리:

- 단순 `SELECT DISTINCT` 로 얻어지는 결과 (B15)
- 단순 컬럼 선택 / 이름 변경 (alias)
- 단순 CASE/DECODE 코드 → 문자열 변환
- 단순 WHERE 필터 (특정 지역/연도)

기준 초과(JOIN / 집계 / PIVOT / 복수 테이블 결합)는 Step 작성 대상.

### 7.4 provide 타겟 분리 원칙 (기존 메모리 룰)

`feedback_provide_target_per_api`:
> 레거시 API endpoint 1개당 타겟 1개. 컬럼/소스 공유해도 타겟 공유 금지.

본 재분류는 이 룰을 **엄격 적용한 결과** — "같은 소스" 이유만으로 묶지 않고, 위 6.2 기준에 따라 판정.

### 7.5 메모리 / 정책 근거

- `feedback_provide_target_per_api` — API endpoint 단위 1:1 분리
- `feedback_provide_layer_upsert` — provide 는 항상 UPSERT + UK(source_refs)
- `feedback_no_internal_exposure` — 내부 메타 컬럼(link_status 등)을 외부 제공 테이블에 노출 금지
- `feedback_module_specific_stays` — API 마다 상이한 전처리 로직(JOIN/PIVOT) 은 범용 Step 으로 통합하지 말고 각각 별도 Step 클래스

---

## 8. 이번 이식 범위 (확정)

### 8.1 작성 대상 Step 11종

난이도 오름차순 (파일럿 → 복잡):

| 순서 | B# | Step 클래스 | 난이도 | 비고 |
|:--:|:--:|---|:--:|---|
| 1 (파일럿) | B14 | `ApiPrvInspectionLoadStep` | 저 | LEFT JOIN 1개. 공통코드 NGW_0026 |
| 2 | B17 | `ApiPrvUnregitsFclyLoadStep` | 중 | UNION ALL + 스칼라 집계 8개 |
| 3 | B16 | `ApiPrvActualUseDjLoadStep` | 중 | CTE 2개 + 3JOIN + ROW_NUMBER |
| 4 | B4 | `ApiPrvPermwellLoadStep` | 중 | Oracle 함수(`FN_GD_GET_GUBUN` 등) 대체 필요 |
| 5 | B5 | `ApiPrvGeneral105LoadStep` | 중 | 5-way LEFT JOIN |
| 6 | B13 | `ApiPrvWaterQualityMfdsLoadStep` | 상 | 동적 PIVOT + 2JOIN + 스칼라 |
| 7 | B18 | `ApiPrvWqInputStatusDjLoadStep` | 상 | UNION + 3중 서브 + 3JOIN (대전) |
| 8 | B9 | `ApiPrvLinkageChartLoadStep` | 상 | CTE + UNION + PIVOT (일 단위) |
| 9 | B10 | `ApiPrvObsStationTimeLoadStep` | 상 | PIVOT + 시 단위 + datatype 분기 |
| 10 | B3→대체 → B11 | `ApiPrvWaterQualityLoadStep` | 최상 | 동적 PIVOT + 3JOIN + 스칼라 |
| 11 | B12 | `ApiPrvWaterQualityDjLoadStep` | 최상 | 동적 PIVOT + 3JOIN (대전) |

**공통화 전략** (Rule of Three): 파일럿(1) ~ 2번째 작성까지는 Step 내부 SQL 중복 허용. 3번째(Step #3 혹은 #4) 시점에 공통 UPSERT/소스REFS 패턴 추출 검토.

**이미 공통화된 헬퍼 즉시 재사용**: `SourceRefUtils`, `ConditionBuilder`, `IfTableService`.

### 8.2 추가 엔티티 2종

- `ApiPrvObsStationTime` (B10)
- `ApiPrvWaterQualityDj` (B12)

각각 해당 Step 작업 시점에 함께 생성.

### 8.3 DDL 일괄 생성

원본 Oracle 테이블 DDL 은 초기 파트에서 한 번에 전량 생성 — `scripts/ddl/internal-oracle/provide-source/` 에.

중복 제거 후 필요한 원본 테이블:
- `TM_GD110310` (B14)
- `TC_GD000002` (B14, B11)
- `TC_GD000100` (B4, B16)
- `RGETNPMMS01` (B4)
- `TM_GD120001` (B5 등 — A5 이식 시 이미 있음)
- `TM_GD970001`, `TM_GD970002`, `TM_GD970101`, `TM_GD970130`, `TM_GD980002`, `PM_GD970201` (B5, B9, B10)
- `TM_GD110301`, `TM_GD110302` (B11, B12, B18 — 이미 있음)
- `TM_GD110350`, `TM_GD110351`, `TM_GD010910` (B13)
- `TM_GD010930`, `RGETNTGMS02` (B16)
- `TM_GD023001` (B17, `TM_GD00301` 표준화)

---

## 9. 보류 / 장기 과제

### 9.1 VIEW_GTEST (B6)

- Oracle 뷰. 정의 확보 필요.
- 뷰 기반 Step 은 소스가 물리 테이블 아니라 추적/UK 설계 별도 검토.

### 9.2 DBLINK (B7 / B8)

- `DBLINKUSR.*` 외부 DB Link 경유 테이블.
- 개발 환경 재현 옵션: (1) 별도 스키마 + 동기화 (2) DB Link 재현 (3) 담당자 확인 후 대체
- 4/22 계획서에서 이미 "담당자 확인 후 결정" 보류.

### 9.3 §6.1 의 중복 엔티티 3개 재검토

- `ApiPrvTmGd110301`, `ApiPrvTmGd110302`, `ApiPrvNgw04`
- 재분류 결과 **A7 재활용으로 대체됨** → 중복
- 제거 방향 추정되나 **사용자 승인 + 배포 영향도 확인 후 별도 정리 작업**

### 9.4 외부 API 제공 계층 구현

본 문서는 provide Agent의 **적재 범위** 만 다룬다. `api_prv_*` 테이블을 외부에 서빙하는 **제공 API 구현** (특히 B15 DISTINCT, B1/B2/B3 의 컬럼 선택/DECODE 등) 은 별도 모듈 작업.

---

## 개정 이력

- 2026-04-24: 최초 작성. Type A 7건 완료 현황 + Type B 18건 재분류 결과.
- 2026-04-24: §5 레거시 URL 매핑 · 노출 성격 추가 (공공/B2B/helper/관리자 구분). B14/B15 성격 명시.
