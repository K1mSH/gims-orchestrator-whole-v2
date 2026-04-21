# 전처리 Loader Step 개발 가이드

> 작성일: 2026-04-16
> 대상: orchestrator_v2 시스템에 전처리 Loader Step을 구현할 개발자
> 전제: Spring Boot, JPA, JdbcTemplate 기본 지식

---

## 목차

1. [배경 — 왜 전처리가 필요한가](#1-배경--왜-전처리가-필요한가)
2. [레거시 API 분류](#2-레거시-api-분류)
3. [시스템 아키텍처](#3-시스템-아키텍처)
4. [핵심 인터페이스](#4-핵심-인터페이스)
5. [구현 순서](#5-구현-순서)
6. [전처리 대상별 구현 가이드](#6-전처리-대상별-구현-가이드)
7. [빌드/테스트/실행](#7-빌드테스트실행)
8. [주의사항](#8-주의사항)
9. [원본 참조 인덱스](#9-원본-참조-인덱스)

---

## 1. 배경 — 왜 전처리가 필요한가

기존 GIMS에는 외부에 데이터를 제공하는 레거시 API가 3종(MEGOKR, 가뭄119, OPN) 있습니다.
이 API들을 새로운 **API Provider** 시스템으로 이식하는데, API Provider는 **단순 SELECT만** 지원합니다.

- 단순 SQL인 API → Provider에 바로 등록 (코드 작업 없음)
- 복잡한 SQL(PIVOT, JOIN, DBLINK)인 API → **전처리 Loader**가 필요

```
[전처리 Loader Step — 주기적 실행]
  내부망 Oracle에서 복잡 SQL 실행
       ↓
  결과를 api-provider 전용 PG의 정제 테이블(TM_PROVIDE_*)에 적재
       ↓
  API Provider가 정제 테이블에서 단순 SELECT → 외부 응답
```

**이 문서의 범위**: 전처리 Loader Step 구현 (= Agent 코드 개발)

---

## 2. 레거시 API 전체 목록 및 관여 테이블

레거시 제공 API는 3종(MEGOKR, 가뭄119, OPN)이며, 총 **18개 API 오퍼레이션**이 있습니다.
각 API가 참조하는 테이블을 모두 정리합니다.

### 공통 인프라 테이블

모든 레거시 API가 공통으로 사용하는 인증/이력 테이블:

| 테이블 | 용도 | 사용 API |
|--------|------|---------|
| **TM_GD21301** | API Key 저장/검증 | MEGOKR, 가뭄119, OPN 전체 |
| **TH_GD21301** | API 호출 이력 로그 | MEGOKR, 가뭄119, OPN 전체 |

---

### MEGOKR API (환경부 데이터 제공)

> 📎 SQL: `sql_megokrapi.xml` / DAO: `MegokrApiDAO.java`

| # | SQL ID | API명 | 관여 테이블 | 분류 |
|---|--------|------|-----------|:---:|
| 1 | selectNgw03 | 수질측정망검사 개요 | **TM_GD30301** | Type A |
| 2 | selectNgw03_01 | 수질측정망검사 개요 (페이징) | **TM_GD30301**, TM_GD10001 | Type A |
| 3 | selectNgw04 | 수질측정망검사 결과 | **TM_GD30302** (PIVOT 125컬럼) | **Type B** |
| 4 | selectNgw04_01 | 수질측정망검사 결과 (페이징) | **TM_GD30302**, TM_GD30301, TM_GD10001 | **Type B** |
| 5 | selectNgw08 | 공공지하수 관정/가뭄지원 | **TM_GD00203** | Type A |
| 6 | selectNgw09 | 공공지하수 관정 상세 | **WT_DREAM_PERMWELL_PUBLIC** | Type A |
| 7 | selectNgw09_01 | 공공지하수 관정 상세 (페이징) | **WT_DREAM_PERMWELL_PUBLIC** | Type A |

---

### 가뭄119 API (긴급 관정 데이터 제공)

> 📎 SQL: `sql_drought119api.xml` / DAO: `Drought119ApiDAO.java`

| # | SQL ID | API명 | 관여 테이블 | 분류 |
|---|--------|------|-----------|:---:|
| 8 | selectdroght119 | 가뭄지원관정 조회 | **SDE_NGWS.WT_DREAM_PERMWELL_PUBLIC_21033** | Type A |

---

### OPN API (공공 데이터 서비스)

> 📎 SQL: `sql_opn.xml` / Controller: `OPNController.java` / DAO: `OPNDAO.java`

| # | SQL ID | API명 | 관여 테이블 | 분류 |
|---|--------|------|-----------|:---:|
| 9 | info_permwell | 인허가관정 상세 | **RGETNPMMS01**, TC_GD00100 | Type A |
| 10 | info_general | 관측망 조회 (국가/해수침투/농어촌/기초조사) | **TM_GD10001** | Type A |
| 11 | info_general_105 | 보조지하수관측망 상세 | **TM_GD10001**, TM_GD60101, TM_GD60130, TM_GD60001, TM_GD60002, TM_GD70002 | **Type B** |
| 12 | info_general_211215 | 수질측정망 상세 | **VIEW_GTEST** (뷰) | Type A |
| 13 | info_yhjs_info | 영향조사 상세 | **TM_GD50001** | Type A |
| 14 | info_observation_station1 | 수위관측소 상세 | **DBLINKUSR.DUBWLOBSIF**, DBLINKUSR.DUBMMWL, DBLINKUSR.V_WP_WKSDAMSBSN, DBLINKUSR.V_WR_HACHEON_MST | **Type B** |
| 15 | info_observation_station0 | 우량관측소 상세 | **DUBRFOBSIF**, DUBMMRF, DBLINKUSR.V_WP_WKSDAMSBSN | **Type B** |
| 16 | waterQualityInfo | 수질정보 (전국) | **TM_GD10001**, TM_GD30301, TM_GD30302, TC_GD00002 | **Type B** |
| 17 | waterQualityInfoDJ | 수질정보 (대전/강원) | **TM_GD10001**, TM_GD30301, TM_GD30302 | **Type B** |
| 18 | waterQualityMfdsInfo | 수질정보 (MFDS) | **TM_GD70201**, TM_GD70202, TM_GD20910 | 확인 필요 |
| 19 | linkage_analy_chart_general | 관측소 시계열 그래프 | **PM_GD60201**, TM_GD60101, TM_GD10001 | **Type B** |
| 20 | observationStationTimeService | 관측소 시간별 서비스 | **PM_GD60201**, TM_GD60101, TM_GD10001 | **Type B** |
| 21 | searchInspection | 수질검사 항목 조회 | **TM_GD30310**, TC_GD00002 | Type A |
| 22 | searchMaxDtaStdrYear | 최대 기준연도 조회 | **TM_GD30310** | Type A |
| 23 | actualUseDetailDJ | 이용실태 상세 (대전) | **TC_GD00100**, TM_GD20930, RGETNTGMS02 | 확인 필요 |
| 24 | unRegitsFclySmrize | 미등록시설 조회 (대전) | **TM_GD00301** | 확인 필요 |
| 25 | gnlwtqltinfo_inputsittn | 수질측정망 입력현황 (대전) | **TM_GD10001**, TM_GD30301, TM_GD30302 | 확인 필요 |

> **"확인 필요"**: SQL 복잡도는 중간 수준이나, 실제 이식 여부를 담당자와 확인해야 합니다.

---

### 분류 요약

| 분류 | 건수 | 설명 |
|------|:---:|------|
| **Type A** (단순 복사) | 12개 | 단순 SELECT — 기존 copy Step으로 Oracle→PG 복사, **엔티티+YAML만 추가** |
| **Type B** (전처리 필요) | 9개 | 복잡 SQL — 새 Loader Step 코드 구현 필요 |
| **확인 필요** | 4개 | 담당자 확인 후 분류 확정 |

> **중요**: Type A도 api-provider 전용 PG에 **제공용 테이블(TM_PROVIDE_*)을 생성**해야 합니다.
> 원본 Oracle 테이블을 직접 조회하는 것이 아니라, PG에 복사된 테이블을 Provider가 SELECT합니다.
> 다만 복사 로직은 기존 copy Step(simple-load 등)을 그대로 사용하므로 **새 Step 코드는 불필요**합니다.

---

### Type A: 단순 복사 대상 — 제공용 테이블

기존 copy Step으로 Oracle → PG 복사. **엔티티 + YAML만 추가**, 새 Step 코드 불필요.

| # | 레거시 API | Oracle 소스 테이블 | PG 제공용 테이블 | merge-key (안) |
|---|-----------|-------------------|-----------------|---------------|
| A1 | 가뭄119 | SDE_NGWS.WT_DREAM_PERMWELL_PUBLIC_21033 | TM_PROVIDE_DROUGHT119 | OBJECTID |
| A2 | MEGOKR NGW_03 | TM_GD30301 | TM_PROVIDE_NGW03 | QLTWTR_INSPCT_SN |
| A3 | MEGOKR NGW_08 | TM_GD00203 | TM_PROVIDE_NGW08 | (PK 확인 필요) |
| A4 | MEGOKR NGW_09 | WT_DREAM_PERMWELL_PUBLIC | TM_PROVIDE_NGW09 | (PK 확인 필요) |
| A5 | OPN info_permwell | RGETNPMMS01 | TM_PROVIDE_PERMWELL | (PK 확인 필요) |
| A6 | OPN info_general | TM_GD10001 | TM_PROVIDE_GENERAL | GENNUM |
| A7 | OPN info_general_211215 | VIEW_GTEST | TM_PROVIDE_GTEST | (PK 확인 필요) |
| A8 | OPN info_yhjs_info | TM_GD50001 | TM_PROVIDE_YHJS | (PK 확인 필요) |
| A9 | OPN searchInspection | TM_GD30310 | TM_PROVIDE_INSPECTION | (PK 확인 필요) |

> A3~A9의 merge-key는 Oracle 원본 테이블의 PK를 확인하여 확정 필요.
> YAML에서 기존 `factory-key: simple-load` + `merge-key` 설정으로 처리.

**테이블 2계층 구조**:

```
[실서버 Oracle]
     ↓ (별도 경로로 복제)
[우리 DB: 원본 테이블] ← LINK_STATUS로 처리 상태 추적
     ↓ (copy Step / 전처리 Step이 LINK_STATUS 보고 처리)
[우리 DB: TM_PROVIDE_*] ← 제공용 테이블
     ↓
[API Provider가 SELECT]
```

| 구분 | 테이블 예시 | 관리 방식 | LINK_STATUS | 비고 |
|------|-----------|---------|:---:|------|
| **원본** | TM_GD10001, TM_GD30301 등 | **DDL 스크립트** (수동 생성) | **O** | 다른 곳에서 관리, JPA 엔티티 아님 |
| **제공** | TM_PROVIDE_NGW03 등 | **JPA 엔티티** (ddl-auto) | X | 우리가 관리 |

**원본 테이블 추가 컬럼** (DDL에 포함):

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `LINK_STATUS` | VARCHAR(20) | 처리 상태 (PENDING → SUCCESS / FAILED) |
| `EXECUTION_ID` | VARCHAR(100) | 어떤 실행에서 적재했는지 |
| `SOURCE_REFS` | VARCHAR(4000) | 원본 데이터 참조 |
| `EXTRACTED_AT` | TIMESTAMP | 추출 시각 |
| `UPDATED_AT` | TIMESTAMP | 최종 갱신 시각 |

> 원본 테이블 DDL은 실서버 스키마 기준으로 작성 + 위 5개 컬럼 추가.
> `scripts/ddl/` 하위에 DDL 파일로 관리. JPA `ddl-auto`로 건드리면 안 됨.

**제공 테이블(TM_PROVIDE_*) 추적 컬럼** (JPA 엔티티에 포함):

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `EXECUTION_ID` | VARCHAR(100) | 어떤 실행에서 적재했는지 |
| `SOURCE_REFS` | VARCHAR(4000) | 원본 데이터 참조 |
| `UPDATED_AT` | TIMESTAMP | 최종 갱신 시각 |

> 📎 **기존 IF 테이블 패턴 참조**: `sync-agent-bojo-int/.../entity/iftable/IfRsvSecJewon.java`

---

### Type B: 전처리 대상 정리

| # | 레거시 API | 복잡도 | 관여 테이블 (Oracle 소스) | 정제 테이블 (PG 타겟) |
|---|-----------|-------|------------------------|---------------------|
| B1 | MEGOKR NGW_04 (selectNgw04) | PIVOT 125컬럼 | TM_GD30302 | TM_PROVIDE_NGW04 |
| B2 | MEGOKR NGW_04 (selectNgw04_01) | PIVOT + 서브쿼리 | TM_GD30302, TM_GD30301, TM_GD10001 | TM_PROVIDE_NGW04 (B1과 공유) |
| B3 | OPN waterQualityInfo | 3단계 중첩 + 동적 CASE | TM_GD10001, TM_GD30301, TM_GD30302, TC_GD00002 | TM_PROVIDE_WATER_QUALITY |
| B4 | OPN waterQualityInfoDJ | B3과 동일 구조 (지역 필터) | TM_GD10001, TM_GD30301, TM_GD30302 | TM_PROVIDE_WATER_QUALITY (B3과 공유) |
| B5 | OPN info_general_105 | 5개 LEFT JOIN | TM_GD10001, TM_GD60101, TM_GD60130, TM_GD60001, TM_GD60002, TM_GD70002 | TM_PROVIDE_GENERAL_105 |
| B6 | OPN observation_station1 | DBLINK + 동적 CASE | DBLINKUSR.DUBWLOBSIF, DBLINKUSR.DUBMMWL, DBLINKUSR.V_WP_WKSDAMSBSN, DBLINKUSR.V_WR_HACHEON_MST | TM_PROVIDE_OBSERVATION |
| B7 | OPN observation_station0 | DBLINK + 동적 CASE | DUBRFOBSIF, DUBMMRF, DBLINKUSR.V_WP_WKSDAMSBSN | TM_PROVIDE_RAINFALL (B6과 분리) |
| B8 | OPN linkage_analy_chart | CTE + PIVOT + UNION | PM_GD60201, TM_GD60101, TM_GD10001 | TM_PROVIDE_LINKAGE_CHART |
| B9 | OPN observationStationTime | PIVOT | PM_GD60201, TM_GD60101, TM_GD10001 | TM_PROVIDE_LINKAGE_CHART (B8과 공유 가능) |

> B1/B2, B3/B4, B8/B9는 SQL 구조가 유사하여 정제 테이블 공유 가능 → **실제 정제 테이블은 5~6개**

---

### 관여 테이블 전체 목록

이번 작업에서 관여하는 **Oracle 소스 테이블** 전체:

| 테이블 | 설명 | 사용 API |
|--------|------|---------|
| **TM_GD10001** | 관정 기본정보 | NGW_03_01, info_general, general_105, waterQuality, linkage_chart 등 |
| **TM_GD30301** | 수질측정망 검사 개요 | NGW_03, NGW_04_01, waterQuality |
| **TM_GD30302** | 수질측정망 검사 결과 | NGW_04 (PIVOT), waterQuality |
| **TM_GD30310** | 수질검사 항목 기준 | searchInspection |
| **TM_GD00203** | 공공지하수 관정 | NGW_08 |
| **TM_GD50001** | 영향조사 | info_yhjs_info |
| **TM_GD60001** | 관측점 마스터 | info_general_105 |
| **TM_GD60002** | 관측점 상세 | info_general_105 |
| **TM_GD60101** | 관측 지점/항목 | info_general_105, linkage_chart |
| **TM_GD60130** | 관측 지점 상세 | info_general_105 |
| **TM_GD70002** | 검색 DB 접속정보 | info_general_105 |
| **TM_GD70201** | MFDS 수질검사 본 | waterQualityMfds |
| **TM_GD70202** | MFDS 수질검사 결과 | waterQualityMfds |
| **TM_GD20910** | 사용자 정보 | waterQualityMfds |
| **TM_GD20930** | (이용실태 관련) | actualUseDetailDJ |
| **TM_GD00301** | 미등록시설 | unRegitsFcly |
| **TM_GD21301** | API Key 관리 | 전체 공통 |
| **TH_GD21301** | API 호출 이력 | 전체 공통 |
| **PM_GD60201** | 관측 데이터 (측정값) | linkage_chart, observationStationTime |
| **TC_GD00100** | 행정구역 코드 | info_permwell, actualUseDetailDJ |
| **TC_GD00002** | 공통 코드 | searchInspection, waterQuality |
| **WT_DREAM_PERMWELL_PUBLIC** | 공공관정 (허가) | NGW_09 |
| **SDE_NGWS.WT_DREAM_PERMWELL_PUBLIC_21033** | 가뭄지원관정 (SDE) | 가뭄119 |
| **RGETNPMMS01** | 인허가관정 마스터 | info_permwell |
| **RGETNTGMS02** | 이용실태 조사 | actualUseDetailDJ |
| **VIEW_GTEST** | 수질측정망 뷰 | info_general_211215 |
| **DBLINKUSR.DUBWLOBSIF** | 수위관측소 (DBLINK) | observation_station1 |
| **DBLINKUSR.DUBMMWL** | 수위측정 (DBLINK) | observation_station1 |
| **DBLINKUSR.V_WP_WKSDAMSBSN** | 유역 뷰 (DBLINK) | observation_station0/1 |
| **DBLINKUSR.V_WR_HACHEON_MST** | 하천 마스터 (DBLINK) | observation_station1 |
| **DUBRFOBSIF** | 우량관측소 | observation_station0 |
| **DUBMMRF** | 강우량 측정 | observation_station0 |

> 총 **32개 테이블** (GIMS 로컬 26 + DBLINK 4 + 로컬 DB링크 2)

---

### DB 구조

| DB | 컨테이너 | 포트 | 역할 |
|----|---------|------|------|
| Oracle XE | gims_orchestrator_inner_oracle | **29004** | 전처리 **소스** (GIMS 원본 테이블) + Agent 로컬 관리 |
| PG Orchestrator | gims_orchestrator_dmz | **29001** | Orchestrator 중앙 관리 |
| **PG API Provider** | **gims_api_provider_pg** | **29006** | 전처리 **타겟** (TM_PROVIDE_* 제공 테이블) |

> **주의: 소스 테이블 존재 여부**
>
> 현재 개발용 내부망 Oracle(29004)에는 보조관측/제주/이용량/새올 테이블만 있습니다.
> 제공 API가 참조하는 GIMS 본체 테이블은 **실서버 Oracle에만 존재**합니다.
>
> | 개발 DB(29004)에 **있음** | 개발 DB(29004)에 **없음** — 실서버만 존재 |
> |--------------------------|---------------------------------------|
> | RGETNPMMS01, RGETNTGMS02 | TM_GD10001, TM_GD30301, TM_GD30302, TM_GD30310 |
> | TM_GD970xxx, PM_GD970xxx | TM_GD00203, TM_GD50001, TM_GD00301 |
> | TM_GD111xxx, PM_GD111xxx | TM_GD60001/60002/60101/60130, TM_GD70002/70201/70202 |
> | (보조관측/제주/이용량/새올) | PM_GD60201, TM_GD20910/20930, TC_GD00100/00002 |
> | | WT_DREAM_*, VIEW_GTEST, TM_GD21301, TH_GD21301 |
> | | DBLINKUSR.* (외부 DB), DUBRFOBSIF, DUBMMRF |
>
> 전처리 Step 개발/테스트는 **실서버 접속이 확보**되거나, **개발 DB에 해당 테이블을 추가 생성**한 후 진행해야 합니다.

```
[내부망 Oracle 29004]                          [api-provider 전용 PG (신규)]
 │                                              │
 │  ── Type A: 기존 copy Step으로 단순 복사 ──     │  ── Type A: 제공용 테이블 ──
 ├── TM_GD30301 (수질검사 개요)    ──────────→   ├── TM_PROVIDE_NGW03
 ├── TM_GD00203 (공공지하수)       ──────────→   ├── TM_PROVIDE_NGW08
 ├── WT_DREAM_PERMWELL_PUBLIC    ──────────→   ├── TM_PROVIDE_NGW09
 ├── SDE_NGWS.WT_DREAM_...21033  ──────────→   ├── TM_PROVIDE_DROUGHT119
 ├── TM_GD10001 (관정 기본)       ──────────→   ├── TM_PROVIDE_GENERAL
 ├── RGETNPMMS01 (인허가관정)     ──────────→   ├── TM_PROVIDE_PERMWELL
 ├── VIEW_GTEST (수질측정망 뷰)   ──────────→   ├── TM_PROVIDE_GTEST
 ├── TM_GD50001 (영향조사)        ──────────→   ├── TM_PROVIDE_YHJS
 ├── TM_GD30310 (수질검사항목)    ──────────→   ├── TM_PROVIDE_INSPECTION
 │                                              │
 │  ── Type B: 새 전처리 Step 필요 ──             │  ── Type B: 정제 테이블 ──
 ├── TM_GD30302 (수질 결과, PIVOT) ─────────→   ├── TM_PROVIDE_NGW04
 ├── TM_GD10001+30301+30302 (JOIN) ─────────→  ├── TM_PROVIDE_WATER_QUALITY
 ├── TM_GD10001+60xxx (5 JOIN)    ─────────→   ├── TM_PROVIDE_GENERAL_105
 ├── DBLINKUSR.* (외부 DBLINK)    ─────────→   ├── TM_PROVIDE_OBSERVATION
 ├── DUBRFOBSIF/DUBMMRF (강우)    ─────────→   ├── TM_PROVIDE_RAINFALL
 └── PM_GD60201+60101 (PIVOT)     ─────────→   └── TM_PROVIDE_LINKAGE_CHART
```

---

## 3. 시스템 아키텍처

### 전처리 Step의 위치

전처리 Step과 단순 복사 모두 **기존 내부망 Agent(sync-agent-bojo-int, 포트 8092)**에 추가합니다.
별도 모듈 분리하지 않음 — 실행 주기가 길고(일 1~2회), Step 자체 부하가 낮으며(쿼리는 Oracle이 처리), 인수인계 복잡도를 줄이기 위함.

- bojo-int는 이미 내부망 Oracle에 직접 접근 가능
- SyncLog, 실행이력 등 기존 인프라를 그대로 재사용
- DMZ에서도 bojo/others가 같은 DB 공유하는 검증된 패턴

### 실행 흐름

```
[Orchestrator에서 실행 트리거 (수동 or 스케줄)]
    ↓
[bojo-int: PipelineService.executeAsync()]
    ↓ agentCode = "internal-provide-preprocessor"
[PipelineRegistry.getRunner("internal-provide-preprocessor")]
    ↓
[PipelineRunner.run() → Step 순차 실행]
    ↓
[PreprocessNgw04LoadStep.execute(StepContext)]
    ↓ Oracle에서 복잡 SQL → PG 정제 테이블에 UPSERT
[완료 → SyncLog 기록]
```

> 📎 **참조 (실행 흐름 코드)**:
> - `sync-agent-bojo-int/.../pipeline/PipelineService.java` — `executeAsync()` 진입점
> - `sync-agent-bojo-int/.../config/pipeline/PipelineRegistry.java` — agentCode → PipelineRunner 라우팅
> - `sync-agent-common/.../pipeline/PipelineRunner.java` — Step 순차 실행 루프

### YAML → Factory → Step 자동 등록 원리

```
[앱 기동 시]
  1. AgentConfigLoader가 config/agents/*.yml 스캔
  2. StepFactoryRegistry가 모든 @Component StepFactory를 수집
  3. YAML의 factory-key → Factory 매핑 생성
  4. Factory.create(config) → StepExecutor 인스턴스 생성
  5. PipelineRegistry에 agentCode → PipelineRunner 등록

→ @Component만 붙이면 자동 등록. 추가 설정 코드 불필요.
```

> 📎 **참조**:
> - `sync-agent-bojo-int/.../config/pipeline/AgentConfigLoader.java` — YAML 스캔 (`@PostConstruct`)
> - `sync-agent-common/.../pipeline/StepFactoryRegistry.java` — `List<StepFactory>` 자동 수집

### 기존 Loader Step과의 차이

| 항목 | 기존 Loader Step | 전처리 Step |
|------|----------------|------------|
| **소스** | IF_RSV 테이블 (중간 테이블) | **원본 Oracle 테이블 직접** |
| **SQL** | 단순 SELECT from IF | **복잡 SQL (PIVOT, JOIN 등)** |
| **타겟** | 같은 Oracle DB | **별도 PG** |
| **IF 상태 업데이트** | `ifTableService.batchMarkAsProcessed()` | **불필요** |
| **ConditionBuilder** | `buildIfTableQuery()` 사용 | **불필요** |

> 📎 **기존 Step 참조 (패턴 이해용)**:
> - 가장 단순: `sync-agent-bojo-int/.../loader/step/SimpleLoadStep.java` — IF 조회 → MERGE → IF 상태 갱신 → SyncLog
> - 복잡 (다중 테이블): `sync-agent-bojo-int/.../loader/step/UseLoadStep.java` — 2소스 → 4타겟
> - 순차 FK 체인: `sync-agent-bojo-int/.../loader/step/JejuJewonLoadStep.java` — 1소스 → 5타겟

---

## 4. 핵심 인터페이스

### 4.1 StepExecutor — Step 공통 계약

```java
public interface StepExecutor {
    String getStepId();
    default String getStepName() { return getStepId(); }
    StepResult execute(StepContext context);    // ← 여기에 전처리 로직 구현
}
```

> 📎 `sync-agent-common/src/main/java/com/sync/agent/common/step/StepExecutor.java`

### 4.2 StepContext — 실행 컨텍스트

| 속성 | 타입 | 전처리에서 사용 | 설명 |
|------|------|:---:|------|
| `executionId` | String | **필수** | 실행 고유 ID |
| `sourceDatasourceId` | String | **필수** | 소스 DB ID (Oracle) |
| `targetDatasourceId` | String | **필수** | 타겟 DB ID (PG) |
| `executionOptions` | ExecutionOptions | 선택 | 조건실행 옵션 |
| `params` | Map | 선택 | 추가 파라미터 |

> 📎 `sync-agent-common/src/main/java/com/sync/agent/common/step/StepContext.java`

### 4.3 StepResult — 실행 결과

```java
// 성공
StepResult.builder()
    .stepId("preprocess-ngw04").status(Status.SUCCESS)
    .readCount(1500).writeCount(1500).skipCount(0).durationMs(3200).build();

// 실패
StepResult.failed("preprocess-ngw04", "ORA-01017: invalid username/password", 120);

// 건너뜀
StepResult.skipped("preprocess-ngw04", "소스 데이터 없음");
```

> 📎 `sync-agent-common/src/main/java/com/sync/agent/common/step/StepResult.java`

### 4.4 StepFactory — Step 생성기

```java
public interface StepFactory {
    String getFactoryKey();                    // YAML factory-key와 매칭
    default List<String> getFactoryKeys() { ... }  // 하나의 Factory가 여러 key 처리 가능
    StepExecutor create(Map<String, Object> stepConfig);
}
```

> 📎 `sync-agent-common/src/main/java/com/sync/agent/common/pipeline/StepFactory.java`
> 📎 기존 Factory 예시:
> - 단일 키: `sync-agent-bojo-int/.../loader/factory/SimpleLoadStepFactory.java`
> - 다중 키: `sync-agent-bojo-int/.../loader/factory/JejuLoadStepFactory.java`

### 4.5 SyncLog — 실행 이력 (필수 기록)

```java
syncLogRepository.save(SyncLog.builder()
    .executionId(executionId)
    .stepId("preprocess-ngw04")
    .mappingName("preprocess-ngw04")
    .sourceTables("[\"TM_GD30302\"]")         // JSON 배열 문자열
    .targetTables("[\"TM_PROVIDE_NGW04\"]")
    .readCount(1500L).writeCount(1500L).failedCount(0L).skipCount(0L)
    .build());
```

> 📎 엔티티: `sync-agent-common/src/main/java/com/sync/agent/common/entity/SyncLog.java`
> 📎 Repository: `sync-agent-common/src/main/java/com/sync/agent/common/repository/SyncLogRepository.java`

---

## 5. 구현 순서

### 추가할 파일

```
sync-agent-bojo-int/src/main/java/com/sync/agent/bojoint/
├── entity/target/
│   │  ── Type A: 제공용 엔티티 (기존 simple-load Step 사용, 새 코드 불필요) ──
│   ├── TmProvideDrought119.java         ← A1: 가뭄119
│   ├── TmProvideNgw03.java              ← A2: 수질검사 개요
│   ├── TmProvideNgw08.java              ← A3: 공공지하수
│   ├── TmProvideNgw09.java              ← A4: 공공지하수 상세
│   ├── TmProvidePermwell.java           ← A5: 인허가관정
│   ├── TmProvideGeneral.java            ← A6: 관측망 기본
│   ├── TmProvideGtest.java              ← A7: 수질측정망 (뷰)
│   ├── TmProvideYhjs.java               ← A8: 영향조사
│   ├── TmProvideInspection.java         ← A9: 수질검사 항목
│   │  ── Type B: 전처리용 엔티티 (새 Step 구현 필요) ──
│   ├── TmProvideNgw04.java              ← B1/B2: 수질검사 결과 PIVOT
│   ├── TmProvideWaterQuality.java       ← B3/B4: 수질정보 (전국/지역)
│   ├── TmProvideGeneral105.java         ← B5: 보조관측망 관정
│   ├── TmProvideObservation.java        ← B6: 수위관측소 (DBLINK)
│   ├── TmProvideRainfall.java           ← B7: 우량관측소 (DBLINK)
│   └── TmProvideLinkageChart.java       ← B8/B9: 관측소 시계열
├── loader/
│   ├── factory/
│   │   └── PreprocessLoadStepFactory.java   ← Factory (1개, Type B 전용)
│   └── step/
│       ├── PreprocessNgw04LoadStep.java          ← B1/B2
│       ├── PreprocessWaterQualityLoadStep.java   ← B3/B4
│       ├── PreprocessGeneral105LoadStep.java     ← B5
│       ├── PreprocessObservationLoadStep.java    ← B6
│       ├── PreprocessRainfallLoadStep.java       ← B7
│       └── PreprocessLinkageChartLoadStep.java   ← B8/B9

sync-agent-bojo-int/src/main/resources/config/agents/
├── internal-provide-copy.yml            ← Type A: 기존 simple-load로 단순 복사
└── internal-provide-preprocessor.yml    ← Type B: 새 전처리 Step
```

총: **엔티티 15개** (A: 9 + B: 6) + **Step 6개** (B만) + **Factory 1개** + **YAML 2개** = **24개 파일**

### Type A용 YAML 예시 (기존 Step 재사용)

**파일**: `internal-provide-copy.yml`

```yaml
agent-code: internal-provide-copy
type: LOADER

steps:
  # A1: 가뭄119 — 단순 복사
  - id: copy-drought119
    name: 가뭄119 관정 복사
    factory-key: simple-load
    source-table: SDE_NGWS.WT_DREAM_PERMWELL_PUBLIC_21033
    target-table: TM_PROVIDE_DROUGHT119
    merge-key: OBJECTID

  # A2: MEGOKR NGW_03 — 단순 복사
  - id: copy-ngw03
    name: NGW_03 수질검사 개요 복사
    factory-key: simple-load
    source-table: TM_GD30301
    target-table: TM_PROVIDE_NGW03
    merge-key: QLTWTR_INSPCT_SN

  # A3: MEGOKR NGW_08 — 단순 복사
  - id: copy-ngw08
    name: NGW_08 공공지하수 복사
    factory-key: simple-load
    source-table: TM_GD00203
    target-table: TM_PROVIDE_NGW08
    merge-key: (PK 확인 필요)

  # A4: MEGOKR NGW_09 — 단순 복사
  - id: copy-ngw09
    name: NGW_09 공공지하수 상세 복사
    factory-key: simple-load
    source-table: WT_DREAM_PERMWELL_PUBLIC
    target-table: TM_PROVIDE_NGW09
    merge-key: (PK 확인 필요)

  # A5: OPN 인허가관정 — 단순 복사
  - id: copy-permwell
    name: 인허가관정 복사
    factory-key: simple-load
    source-table: RGETNPMMS01
    target-table: TM_PROVIDE_PERMWELL
    merge-key: (PK 확인 필요)

  # A6: OPN 관측망 기본 — 단순 복사
  - id: copy-general
    name: 관측망 기본정보 복사
    factory-key: simple-load
    source-table: TM_GD10001
    target-table: TM_PROVIDE_GENERAL
    merge-key: GENNUM

  # A7: OPN 수질측정망 — 단순 복사
  - id: copy-gtest
    name: 수질측정망 복사
    factory-key: simple-load
    source-table: VIEW_GTEST
    target-table: TM_PROVIDE_GTEST
    merge-key: (PK 확인 필요)

  # A8: OPN 영향조사 — 단순 복사
  - id: copy-yhjs
    name: 영향조사 복사
    factory-key: simple-load
    source-table: TM_GD50001
    target-table: TM_PROVIDE_YHJS
    merge-key: (PK 확인 필요)

  # A9: OPN 수질검사 항목 — 단순 복사
  - id: copy-inspection
    name: 수질검사 항목 복사
    factory-key: simple-load
    source-table: TM_GD30310
    target-table: TM_PROVIDE_INSPECTION
    merge-key: (PK 확인 필요)
```

> **주의**: `(PK 확인 필요)` 부분은 Oracle 원본 테이블의 PK를 확인하여 채워야 합니다.

---

### Step 1: 정제 테이블 엔티티 생성

**위치**: `sync-agent-bojo-int/.../entity/target/`

```java
@Entity
@Table(name = "TM_PROVIDE_NGW04")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TmProvideNgw04 {

    @Id
    @Column(name = "QLTWTR_INSPCT_SN", length = 50)
    private String qltwtrInspctSn;    // PK: 수질검사일련번호

    @Column(name = "WT_TOT_COL_CNTS", length = 100)
    private String wtTotColCnts;       // 0001: 일반세균
    // ... 125개 항목 컬럼 ...

    // ── 필수 추적 컬럼 (LINK_STATUS는 원본 테이블에만, 제공 테이블에는 불필요) ──
    @Column(name = "EXECUTION_ID", length = 100)
    private String executionId;

    @Column(name = "SOURCE_REFS", length = 4000)
    private String sourceRefs;

    @Column(name = "UPDATED_AT")
    private java.time.LocalDateTime updatedAt;
}
```

**규칙**:
- 어노테이션 순서: `@Entity` → `@Table` → `@Getter` → `@Setter` → `@NoArgsConstructor` → `@AllArgsConstructor` → `@Builder`
- PK: `@Id`만 (업무키 사용, 자동채번 아님)
- `EXECUTION_ID`, `SOURCE_REFS`, `UPDATED_AT` **반드시 포함**
- DDL 직접 실행 불필요 — JPA `ddl-auto` 설정으로 자동 생성

> 📎 기존 엔티티 참조: `sync-agent-bojo-int/.../entity/target/TmGd111010.java`

---

### Step 2: LoadStep 구현

**위치**: `sync-agent-bojo-int/.../loader/step/`

```java
@Slf4j
public class PreprocessNgw04LoadStep implements StepExecutor {

    private final String stepId;
    private final String stepName;
    private final String targetTable;
    private final List<String> sourceTables;
    private final List<String> targetTables;
    private final SyncDataSourceService dataSourceService;
    private final SyncLogRepository syncLogRepository;

    // 생성자 — Factory에서 호출
    public PreprocessNgw04LoadStep(
            String stepId, String stepName, String targetTable,
            List<String> sourceTables, List<String> targetTables,
            SyncDataSourceService dataSourceService,
            SyncLogRepository syncLogRepository) {
        this.stepId = stepId;
        this.stepName = stepName;
        this.targetTable = targetTable;
        this.sourceTables = sourceTables;
        this.targetTables = targetTables;
        this.dataSourceService = dataSourceService;
        this.syncLogRepository = syncLogRepository;
    }

    @Override
    public String getStepId() { return stepId; }

    @Override
    public String getStepName() { return stepName; }

    @Override
    public StepResult execute(StepContext context) {
        long startTime = System.currentTimeMillis();
        String executionId = context.getExecutionId();
        int readCount = 0, writeCount = 0, failedCount = 0;
        String firstError = null;

        try {
            // ── 1. DB 연결 ──
            JdbcTemplate oracleJdbc = dataSourceService
                    .getJdbcTemplate(context.getSourceDatasourceId());
            JdbcTemplate targetPgJdbc = dataSourceService
                    .getJdbcTemplate(context.getTargetDatasourceId());

            log.info("[{}] 전처리 시작: Oracle → {}", stepId, targetTable);

            // ── 2. 원본 복잡 SQL 실행 (Oracle) ──
            String sourceSql = buildSourceQuery();
            List<Map<String, Object>> rows = oracleJdbc.queryForList(sourceSql);
            readCount = rows.size();
            log.info("[{}] Oracle 조회 완료: {}건", stepId, readCount);

            if (rows.isEmpty()) {
                saveSyncLog(executionId, 0, 0, 0, null);
                return StepResult.skipped(stepId, "소스 데이터 없음");
            }

            // ── 3. PG 정제 테이블에 UPSERT ──
            String upsertSql = buildUpsertSql();

            for (Map<String, Object> row : rows) {
                try {
                    String sourceRef = String.format("[\"P:%s:%s\"]",
                            "TM_GD30302", row.get("QLTWTR_INSPCT_SN"));
                    Object[] params = buildUpsertParams(row, executionId, sourceRef);
                    targetPgJdbc.update(upsertSql, params);
                    writeCount++;
                } catch (Exception e) {
                    failedCount++;
                    if (firstError == null) firstError = e.getMessage();
                    log.warn("[{}] UPSERT 실패: {}", stepId,
                            row.get("QLTWTR_INSPCT_SN"), e);
                }
            }

            // ── 4. SyncLog 기록 ──
            saveSyncLog(executionId, readCount, writeCount, failedCount, firstError);

            long durationMs = System.currentTimeMillis() - startTime;
            log.info("[{}] 완료: read={}, write={}, failed={}, {}ms",
                    stepId, readCount, writeCount, failedCount, durationMs);

            return StepResult.builder()
                    .stepId(stepId)
                    .status(failedCount > 0 && writeCount == 0
                            ? Status.FAILED : Status.SUCCESS)
                    .readCount(readCount).writeCount(writeCount)
                    .skipCount(0).durationMs(durationMs)
                    .errorMessage(firstError)
                    .build();

        } catch (Exception e) {
            log.error("[{}] Step 실패", stepId, e);
            saveSyncLog(executionId, readCount, writeCount, failedCount, e.getMessage());
            return StepResult.failed(stepId, e.getMessage(),
                    System.currentTimeMillis() - startTime);
        }
    }

    // ── 원본 복잡 SQL (Oracle PIVOT) ──
    private String buildSourceQuery() {
        return """
            SELECT * FROM TM_GD30302
            PIVOT(
                MAX(RESULT_VALUE) FOR WLTTS_ID_CODE
                IN ('0001' AS WT_TOT_COL_CNTS, '0002' AS WT_TOT_CLF,
                    '0003' AS WT_FCL_CFS, '0004' AS WT_ESC_COL,
                    '0005' AS WT_PLB
                    -- ... 0006 ~ 0125 (sql_megokrapi.xml selectNgw04 참조) ...
                )
            )
            """;
    }

    // ── PG UPSERT (INSERT ... ON CONFLICT) ──
    private String buildUpsertSql() {
        return """
            INSERT INTO TM_PROVIDE_NGW04
                (QLTWTR_INSPCT_SN, WT_TOT_COL_CNTS, WT_TOT_CLF,
                 EXECUTION_ID, SOURCE_REFS, UPDATED_AT)
            VALUES (?, ?, ?, ?, ?, NOW())
            ON CONFLICT (QLTWTR_INSPCT_SN) DO UPDATE SET
                WT_TOT_COL_CNTS = EXCLUDED.WT_TOT_COL_CNTS,
                WT_TOT_CLF = EXCLUDED.WT_TOT_CLF,
                EXECUTION_ID = EXCLUDED.EXECUTION_ID,
                SOURCE_REFS = EXCLUDED.SOURCE_REFS,
                UPDATED_AT = NOW()
            """;
    }

    private Object[] buildUpsertParams(Map<String, Object> row,
                                        String executionId, String sourceRef) {
        return new Object[]{
                row.get("QLTWTR_INSPCT_SN"),
                row.get("WT_TOT_COL_CNTS"),
                row.get("WT_TOT_CLF"),
                executionId,
                sourceRef
        };
    }

    private void saveSyncLog(String executionId, int readCount, int writeCount,
                              int failedCount, String errorSummary) {
        try {
            syncLogRepository.save(SyncLog.builder()
                    .executionId(executionId)
                    .stepId(stepId)
                    .mappingName("preprocess-ngw04")
                    .sourceTables("[\"TM_GD30302\"]")
                    .targetTables("[\"TM_PROVIDE_NGW04\"]")
                    .readCount((long) readCount)
                    .writeCount((long) writeCount)
                    .failedCount((long) failedCount)
                    .skipCount(0L)
                    .errorSummary(errorSummary)
                    .build());
        } catch (Exception e) {
            log.error("[{}] SyncLog 저장 실패", stepId, e);
        }
    }
}
```

> 📎 **기존 Step 참조 (execute 패턴)**:
> - `sync-agent-bojo-int/.../loader/step/SimpleLoadStep.java` — 기본 흐름
> - `sync-agent-bojo-int/.../loader/step/UseLoadStep.java` — 다중 Phase 패턴

---

### Step 3: Factory 구현

**위치**: `sync-agent-bojo-int/.../loader/factory/`

```java
@Component    // ← 이것만으로 자동 등록
@RequiredArgsConstructor
public class PreprocessLoadStepFactory implements StepFactory {

    private final SyncDataSourceService dataSourceService;
    private final SyncLogRepository syncLogRepository;

    private static final List<String> FACTORY_KEYS = Arrays.asList(
            "preprocess-ngw04",
            "preprocess-water-quality",
            "preprocess-general-105",
            "preprocess-observation",
            "preprocess-rainfall",
            "preprocess-linkage-chart"
    );

    @Override
    public String getFactoryKey() { return "preprocess-ngw04"; }

    @Override
    public List<String> getFactoryKeys() { return FACTORY_KEYS; }

    @Override
    @SuppressWarnings("unchecked")
    public StepExecutor create(Map<String, Object> config) {
        String factoryKey = (String) config.getOrDefault("factory-key", "preprocess-ngw04");
        String stepId = (String) config.getOrDefault("id", factoryKey);
        String stepName = (String) config.getOrDefault("name", "전처리");

        List<String> sourceTables = toList(config.get("source-table"));
        List<String> targetTables = toList(config.get("target-table"));
        String targetTable = targetTables.isEmpty() ? "" : targetTables.get(0);

        // factory-key에 따라 다른 Step 생성
        switch (factoryKey) {
            case "preprocess-ngw04":
                return new PreprocessNgw04LoadStep(
                        stepId, stepName, targetTable,
                        sourceTables, targetTables,
                        dataSourceService, syncLogRepository);
            case "preprocess-water-quality":
                return new PreprocessWaterQualityLoadStep(/* ... */);
            case "preprocess-general-105":
                return new PreprocessGeneral105LoadStep(/* ... */);
            case "preprocess-observation":
                return new PreprocessObservationLoadStep(/* ... */);
            case "preprocess-rainfall":
                return new PreprocessRainfallLoadStep(/* ... */);
            case "preprocess-linkage-chart":
                return new PreprocessLinkageChartLoadStep(/* ... */);
            default:
                throw new IllegalArgumentException("알 수 없는 factory-key: " + factoryKey);
        }
    }

    private List<String> toList(Object raw) {
        if (raw instanceof List) return (List<String>) raw;
        if (raw instanceof String) return Collections.singletonList((String) raw);
        return Collections.emptyList();
    }
}
```

> 📎 **기존 Factory 참조 (다중 키 패턴)**:
> - `sync-agent-bojo-int/.../loader/factory/JejuLoadStepFactory.java` — switch 분기

---

### Step 4: YAML 설정

**위치**: `sync-agent-bojo-int/src/main/resources/config/agents/internal-provide-preprocessor.yml`

```yaml
agent-code: internal-provide-preprocessor
type: LOADER

steps:
  # B1/B2: MEGOKR NGW_04 — PIVOT 125컬럼
  - id: preprocess-ngw04
    name: NGW_04 수질검사결과 전처리
    factory-key: preprocess-ngw04
    source-table: [TM_GD30302, TM_GD30301, TM_GD10001]
    target-table: TM_PROVIDE_NGW04

  # B3/B4: OPN waterQualityInfo — 3단계 중첩 + 동적 CASE WHEN
  - id: preprocess-water-quality
    name: 수질측정망 전처리
    factory-key: preprocess-water-quality
    source-table: [TM_GD10001, TM_GD30301, TM_GD30302, TC_GD00002]
    target-table: TM_PROVIDE_WATER_QUALITY

  # B5: OPN info_general_105 — 5개 LEFT JOIN
  - id: preprocess-general-105
    name: 보조관측망 관정 전처리
    factory-key: preprocess-general-105
    source-table: [TM_GD10001, TM_GD60101, TM_GD60130, TM_GD60001, TM_GD60002, TM_GD70002]
    target-table: TM_PROVIDE_GENERAL_105

  # B6: OPN observation_station1 — 수위관측소 (DBLINK)
  - id: preprocess-observation
    name: 수위관측소 전처리
    factory-key: preprocess-observation
    source-table: [DBLINKUSR.DUBWLOBSIF, DBLINKUSR.DUBMMWL, DBLINKUSR.V_WP_WKSDAMSBSN, DBLINKUSR.V_WR_HACHEON_MST]
    target-table: TM_PROVIDE_OBSERVATION

  # B7: OPN observation_station0 — 우량관측소 (DBLINK)
  - id: preprocess-rainfall
    name: 우량관측소 전처리
    factory-key: preprocess-rainfall
    source-table: [DUBRFOBSIF, DUBMMRF, DBLINKUSR.V_WP_WKSDAMSBSN]
    target-table: TM_PROVIDE_RAINFALL

  # B8/B9: OPN linkage_analy_chart + observationStationTime — CTE + PIVOT + UNION
  - id: preprocess-linkage-chart
    name: 관측소 시계열 전처리
    factory-key: preprocess-linkage-chart
    source-table: [PM_GD60201, TM_GD60101, TM_GD10001]
    target-table: TM_PROVIDE_LINKAGE_CHART
```

**YAML 속성**:

| 속성 | 필수 | 설명 |
|------|:---:|------|
| `agent-code` | O | Orchestrator에서 이 Agent를 식별하는 코드 |
| `type` | O | `LOADER` (고정) |
| `steps[].id` | O | Step 고유 ID (로그/이력에 표시) |
| `steps[].name` | O | Step 표시명 (한글 OK) |
| `steps[].factory-key` | O | Factory의 `getFactoryKey()`와 매칭 |
| `steps[].source-table` | O | 원본 테이블 (단일 문자열 or 배열) |
| `steps[].target-table` | O | 정제 테이블 (단일 문자열 or 배열) |

> 📎 **기존 YAML 참조**:
> - 다중 Step: `sync-agent-bojo-int/.../config/agents/internal-jeju-loader.yml`
> - 1:1 MERGE 16개: `sync-agent-bojo-int/.../config/agents/internal-saeol-loader.yml`
> - 복합 소스/타겟: `sync-agent-bojo-int/.../config/agents/internal-use-loader.yml`

---

## 6. 전처리 대상별 구현 가이드

### B1/B2: MEGOKR NGW_04 (PIVOT 125컬럼)

**대상**: `selectNgw04` + `selectNgw04_01` (페이징 버전)

**원본 SQL** (Oracle PIVOT):
```sql
SELECT * FROM TM_GD30302
PIVOT(
    MAX(RESULT_VALUE) FOR WLTTS_ID_CODE
    IN ('0001' AS WT_TOT_COL_CNTS, '0002' AS WT_TOT_CLF, ... '0125' AS WT_WTL)
)
WHERE QLTWTR_INSPCT_SN = ?
```
- `selectNgw04_01`은 TM_GD30301, TM_GD10001을 서브쿼리로 추가 참조

**관여 테이블**: TM_GD30302, (TM_GD30301, TM_GD10001)
**정제 테이블**: `TM_PROVIDE_NGW04` / PK: `QLTWTR_INSPCT_SN`

**전처리 전략**:
- WHERE 제거 → 전체 PIVOT 결과를 PG에 UPSERT
- 페이징 버전(04_01)의 서브쿼리 JOIN은 전처리에서 불필요 (전체 적재 후 Provider가 필터)
- **난이도**: 중 (SQL 단순, 컬럼 수가 많을 뿐)

> 📎 `sql_megokrapi.xml` → `selectNgw04` (105~243행)

---

### B3/B4: OPN waterQualityInfo (3단계 중첩 + 동적 CASE WHEN)

**대상**: `waterQualityInfo` (전국) + `waterQualityInfoDJ` (대전/강원)

**원본 SQL 구조** (간소화):
```sql
SELECT T1.GENNUM, T1.SPOT_NM, ...,
       MAX(CASE WHEN WLTTS_ID_CODE = '0001' THEN RESULT_VALUE END) AS C0001,
       MAX(CASE WHEN WLTTS_ID_CODE = '0002' THEN RESULT_VALUE END) AS C0002,
       -- 항목별 반복 (레거시: iBATIS <iterate> 매크로로 동적 생성)
FROM TM_GD10001 T1
    INNER JOIN TM_GD30301 T3 ON T1.GENNUM = T3.GENNUM
    LEFT JOIN TM_GD30302 T32 ON T3.QLTWTR_INSPCT_SN = T32.QLTWTR_INSPCT_SN
GROUP BY T1.GENNUM, T1.SPOT_NM, ...
```
- `waterQualityInfoDJ`는 동일 구조에 지역 필터만 추가

**관여 테이블**: TM_GD10001, TM_GD30301, TM_GD30302, TC_GD00002
**정제 테이블**: `TM_PROVIDE_WATER_QUALITY` / 복합 PK: `GENNUM + QLTWTR_INSPCT_SN`

**전처리 전략**:
- `<iterate>` 동적 SQL → **고정 CASE WHEN SQL**로 변환 (항목 코드 목록이 고정)
- 전국/지역 구분 없이 전체 적재, Provider에서 지역 필터
- **난이도**: 상 (3테이블 JOIN + 항목별 PIVOT)

> 📎 `sql_opn.xml` → `waterQualityInfo` (622~779행)
> `<iterate property="qltwtrInspctIemCodes">` 부분이 동적 CASE WHEN 매크로

---

### B5: OPN info_general_105 (5개 LEFT JOIN)

**대상**: `info_general_105`

**원본 SQL 핵심**:
```sql
SELECT TB1.GENNUM, TB3.OBSRVT_NM, TB1.BRTC_NM, TB1.LA_VALUE, TB1.LO_VALUE,
       TB2.INSTL_DPH_VALUE, TB4.RDNM_ADDR, ...
FROM TM_GD10001 TB1
    LEFT JOIN (SELECT DISTINCT TAG_CTNT, SPOT_ID FROM TM_GD60101) TB6
        ON TB1.GENNUM = TB6.TAG_CTNT
    LEFT JOIN TM_GD60130 TB2 ON TB6.TAG_CTNT = TB2.GENNUM
    LEFT JOIN TM_GD60001 TB3 ON TB2.SPOT_ID = TB3.SPOT_ID
    LEFT JOIN TM_GD60002 TB4 ON TB2.SPOT_ID = TB4.SPOT_ID
    LEFT JOIN TM_GD70002 TB5 ON TB2.SPOT_ID = TB5.SPOT_ID
WHERE TB3.SPOT_TY_MNG_WORD_NM IN ('보조지하수관측망', '수동보조지하수관측망')
```

**관여 테이블**: TM_GD10001, TM_GD60101, TM_GD60130, TM_GD60001, TM_GD60002, TM_GD70002
**정제 테이블**: `TM_PROVIDE_GENERAL_105` / PK: `GENNUM`

**전처리 전략**:
- JOIN SQL 그대로 실행 → flat 결과 UPSERT
- **난이도**: 중 (JOIN 많지만 로직 직관적)

> 📎 `sql_opn.xml` → info_general_105 관련 (57~107행)

---

### B6: OPN observation_station1 — 수위관측소 (DBLINK)

**대상**: `info_observation_station1`

**원본 SQL 핵심**:
```sql
SELECT A.OBSNM, A.ADDR, B.WL, B.FLW, ...
FROM DBLINKUSR.DUBWLOBSIF A
    LEFT JOIN DBLINKUSR.DUBMMWL B ON B.WLOBSCD = A.WLOBSCD
        AND B.OBSDHM = CASE WHEN TO_CHAR(SYSDATE, 'MI') LIKE '0%' 
            THEN ... ELSE ... END || (CASE WHEN ... END)
    LEFT JOIN DBLINKUSR.V_WP_WKSDAMSBSN C ON ...
    LEFT JOIN DBLINKUSR.V_WR_HACHEON_MST D ON ...
```

**관여 테이블**: DBLINKUSR.DUBWLOBSIF, DBLINKUSR.DUBMMWL, DBLINKUSR.V_WP_WKSDAMSBSN, DBLINKUSR.V_WR_HACHEON_MST
**정제 테이블**: `TM_PROVIDE_OBSERVATION` / PK: `WLOBSCD`

**전처리 전략**:
- **전제**: 내부망 Oracle에 DBLINK 구성 필요 (미구성 시 별도 배치 선행)
- 동적 CASE WHEN 타임스탬프는 SQL에 고정 포함
- **난이도**: 상 (DBLINK 환경 의존)

> 📎 `sql_opn.xml` → `info_observation_station1` (142~179행)

---

### B7: OPN observation_station0 — 우량관측소 (DBLINK)

**대상**: `info_observation_station0`

**원본 SQL**: B6과 유사 구조, 강우 테이블 참조

**관여 테이블**: DUBRFOBSIF, DUBMMRF, DBLINKUSR.V_WP_WKSDAMSBSN
**정제 테이블**: `TM_PROVIDE_RAINFALL` / PK: `RFOBSCD`

**전처리 전략**: B6과 동일 (DBLINK + 동적 타임스탬프)
**난이도**: 상

> 📎 `sql_opn.xml` → `info_observation_station0` (182~215행)

---

### B8: OPN linkage_analy_chart (CTE + PIVOT + UNION)

**대상**: `linkage_analy_chart_general`

**원본 SQL 핵심**:
```sql
WITH TB AS (
    SELECT GENNUM, YMD, "5" AS ELEV, "163" WTEMP,
           ROUND((SELECT AL_VALUE FROM TM_GD10001 WHERE GENNUM = ?) - "5", 2) AS LEV
    FROM ( ... PIVOT (MAX(OBSR_DTA_VALUE) FOR OBSR_IEM_ID IN (5, 163, 52, 333)) ... )
    UNION
    SELECT ... (동일 구조, 다른 날짜 범위)
)
SELECT * FROM TB WHERE YMD BETWEEN ? AND ?
```

**관여 테이블**: PM_GD60201, TM_GD60101, TM_GD10001
**정제 테이블**: `TM_PROVIDE_LINKAGE_CHART` / 복합 PK: `GENNUM + YMD`

**전처리 전략**:
- **주의**: `GENNUM` 파라미터에 의존 → 관측소별 결과가 다름
- 전체 관측소 목록 먼저 조회 → 관측소별 반복 실행 → UPSERT
- **난이도**: 상 (파라미터 의존성 해결 필요)

> 📎 `sql_opn.xml` → `linkage_analy_chart_general` (218~269행)

---

### B9: OPN observationStationTimeService (PIVOT)

**대상**: `observationStationTimeService`

**관여 테이블**: PM_GD60201, TM_GD60101, TM_GD10001 (B8과 동일)
**정제 테이블**: `TM_PROVIDE_LINKAGE_CHART` (B8과 공유 가능)

**전처리 전략**: B8과 동일한 데이터를 시간 단위로 조회 — B8 전처리에 포함 가능

> 📎 `sql_opn.xml` → `observationStationTimeService`

---

## 7. 빌드/테스트/실행

### 빌드

```bash
cd sync-agent-bojo-int
./gradlew clean build -x test
```

### 기동

```bash
./gradlew bootRun
```

기동 시 로그 확인:
```
[BojoInt] Agent 설정 로드: internal-provide-preprocessor (type=LOADER, steps=5)
```

> 📎 기동 설정: `sync-agent-bojo-int/src/main/resources/application.yml`

### Orchestrator에서 실행

1. 프론트(http://localhost:3000) → Agent 관리 → 등록
2. URL: `http://localhost:8092` → auto-discover → `internal-provide-preprocessor` 선택
3. 등록 후 **실행** 버튼 → 이력에서 readCount/writeCount 확인

### 스케줄 등록

Agent 상세 → 스케줄 탭 → cron: `0 0 3 * * ?` (매일 03:00 권장)

---

## 8. 주의사항

### 2계층 테이블 — 컬럼 구분
- **원본 테이블** (DDL 수동 생성): `LINK_STATUS`, `EXECUTION_ID`, `SOURCE_REFS`, `EXTRACTED_AT`, `UPDATED_AT`
  - Step은 `LINK_STATUS = 'PENDING'` 건을 읽어서 처리 후 `SUCCESS`/`FAILED`로 갱신
- **제공 테이블 TM_PROVIDE_*** (JPA 엔티티): `EXECUTION_ID`, `SOURCE_REFS`, `UPDATED_AT`
  - LINK_STATUS 불필요 — API Provider가 단순 SELECT만 함

### SyncLog 필수
빠뜨리면 Orchestrator 대시보드에 이력이 안 보입니다.

### PG UPSERT 패턴
타겟이 PostgreSQL이므로 Oracle MERGE 대신 `INSERT ... ON CONFLICT` 사용:
```sql
INSERT INTO {정제테이블} (..., EXECUTION_ID, SOURCE_REFS, UPDATED_AT)
VALUES (?, ..., ?, ?, NOW())
ON CONFLICT ({PK}) DO UPDATE SET
    {컬럼} = EXCLUDED.{컬럼}, ...,
    EXECUTION_ID = EXCLUDED.EXECUTION_ID,
    SOURCE_REFS = EXCLUDED.SOURCE_REFS,
    UPDATED_AT = NOW()
```

### 에러 처리
- 개별 레코드 실패 → skip하고 나머지 계속
- 전부 실패(`writeCount==0 && readCount>0`) → `Status.FAILED`
- 부분 성공 → `Status.SUCCESS`

### DBLINK (B4 한정)
- 내부망 Oracle에 DBLINK 구성 여부 사전 확인 필요
- 미구성 시 대안 별도 협의

---

## 9. 원본 참조 인덱스

### 시스템 코드 (orchestrator_v2/ 기준)

| 분류 | 파일 | 설명 |
|------|------|------|
| **인터페이스** | | |
| StepExecutor | `sync-agent-common/.../step/StepExecutor.java` | Step 계약 (getStepId, execute) |
| StepContext | `sync-agent-common/.../step/StepContext.java` | 실행 컨텍스트 |
| StepResult | `sync-agent-common/.../step/StepResult.java` | 실행 결과 (success/failed/skipped) |
| StepFactory | `sync-agent-common/.../pipeline/StepFactory.java` | Factory 인터페이스 |
| DataSourceProvider | `sync-agent-common/.../controller/DataSourceProvider.java` | DB 연결 인터페이스 |
| **자동 등록** | | |
| StepFactoryRegistry | `sync-agent-common/.../pipeline/StepFactoryRegistry.java` | @Component Factory 자동 수집 |
| AgentConfigLoader | `sync-agent-bojo-int/.../config/pipeline/AgentConfigLoader.java` | YAML 스캔 |
| PipelineRegistry | `sync-agent-bojo-int/.../config/pipeline/PipelineRegistry.java` | agentCode 라우팅 |
| **실행** | | |
| PipelineService | `sync-agent-bojo-int/.../pipeline/PipelineService.java` | 비동기 실행 진입점 |
| PipelineRunner | `sync-agent-common/.../pipeline/PipelineRunner.java` | Step 순차 실행 |
| **기존 Step** | | |
| SimpleLoadStep | `sync-agent-bojo-int/.../loader/step/SimpleLoadStep.java` | 가장 단순한 패턴 |
| UseLoadStep | `sync-agent-bojo-int/.../loader/step/UseLoadStep.java` | 다중 Phase |
| JejuJewonLoadStep | `sync-agent-bojo-int/.../loader/step/JejuJewonLoadStep.java` | 순차 FK 체인 |
| InternalBojoLoadStep | `sync-agent-bojo-int/.../loader/step/InternalBojoLoadStep.java` | EAV 확장 |
| **기존 Factory** | | |
| SimpleLoadStepFactory | `sync-agent-bojo-int/.../loader/factory/SimpleLoadStepFactory.java` | 단일 키 |
| JejuLoadStepFactory | `sync-agent-bojo-int/.../loader/factory/JejuLoadStepFactory.java` | 다중 키 + switch |
| **기존 YAML** | | |
| internal-saeol-loader | `sync-agent-bojo-int/.../config/agents/internal-saeol-loader.yml` | 16 Step, 1:1 |
| internal-jeju-loader | `sync-agent-bojo-int/.../config/agents/internal-jeju-loader.yml` | 3 Step, 다중 |
| internal-use-loader | `sync-agent-bojo-int/.../config/agents/internal-use-loader.yml` | 복합 소스/타겟 |
| **기존 엔티티** | | |
| TmGd111010 | `sync-agent-bojo-int/.../entity/target/TmGd111010.java` | EXECUTION_ID + SOURCE_REFS |
| IfRsvSecJewon | `sync-agent-bojo-int/.../entity/iftable/IfRsvSecJewon.java` | IF 메타 (LINK_STATUS) |
| **공통 서비스** | | |
| SyncLog | `sync-agent-common/.../entity/SyncLog.java` | 실행 이력 엔티티 |
| SyncLogRepository | `sync-agent-common/.../repository/SyncLogRepository.java` | JPA Repository |
| SyncDataSourceService | `sync-agent-bojo-int/.../config/SyncDataSourceService.java` | getJdbcTemplate() |
| IfTableService | `sync-agent-common/.../service/IfTableService.java` | IF 상태 갱신 (**전처리에서 미사용**) |
| ConditionBuilder | `sync-agent-common/.../step/ConditionBuilder.java` | 동적 WHERE (**전처리에서 미사용**) |

### 레거시 SQL 원본 (newgims_v2/ 기준)

| 파일 | 관련 API |
|------|---------|
| `src/egovframework/sqlmap/com/gims/sql_megokrapi.xml` | NGW_03(9행), NGW_04(105행), NGW_08, NGW_09 |
| `src/egovframework/sqlmap/com/gims/sql_drought119api.xml` | 가뭄119(9행) |
| `src/egovframework/sqlmap/com/gims/sql_opn.xml` | waterQuality(622행), general_105(57행), observation(142행), linkage_chart(218행) |
| `src/gims/dao/MegokrApiDAO.java` | MEGOKR DAO |
| `src/gims/web/OPNController.java` | OPN Controller |

### 설계/분석 문서

| 문서 | 경로 |
|------|------|
| API Provider 전체 설계 | `dev_plan/2026_04/15/gims-api-provider-plan.md` |
| 레거시 제공 API 분석 (v2) | `dev_plan/2026_04/14/gims-legacy-provide-api-analysis.md` |
| 레거시 제공 API 분석 (v3) | `dev_plan/2026_04/14/gims-v3-provide-api-analysis.md` |
| 시스템 아키텍처 | `docs/ARCHITECTURE.md` |
| TODO 현황 | `todo/system/05-api-provide.md` |
