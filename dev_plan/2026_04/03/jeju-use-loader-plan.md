# 제주/이용량 내부망 Loader 계획

> 작성일: 2026-04-03
> 선행: 제주/이용량 RCV 완료 (IF_RSV 적재 확인됨)

## 목적
IF_RSV → GIMS 실테이블 적재. 레거시 로직을 커스텀 Step으로 구현.

## 아키텍처

```
sync-agent-bojo-int/src/main/java/com/sync/agent/bojoint/
├── config/pipeline/
│   ├── InternalBojoLoadStepFactory.java     ← 기존
│   ├── JejuLoadStepFactory.java             ← 신규 (I1~I4 통합 팩토리)
│   └── UseLoadStepFactory.java              ← 신규 (I5 팩토리)
├── loader/step/
│   └── InternalBojoLoadStep.java            ← 기존
├── jeju/step/                               ← 신규 패키지
│   ├── JejuJewonLoadStep.java               (I1)
│   ├── JejuObsvdataLoadStep.java            (I2)
│   ├── JejuFacilityLoadStep.java            (I3)
│   └── JejuRgetnLoadStep.java               (I4)
└── use/step/                                ← 신규 패키지
    └── UseLoadStep.java                     (I5)
```

## Step별 상세

### I4: JejuRgetnLoadStep (가장 단순 — 먼저 구현)

| 항목 | 값 |
|------|-----|
| 소스 | IF_RSV_RGETSTGMS01 (제주 데이터) |
| 타겟 | RGETSTGMS01 (내부망 Oracle) |
| 로직 | source_refs 기준 MERGE (전컬럼) |
| 난이도 | **낮음** — 단순 1:1 이관 |

### I1: JejuJewonLoadStep (1→7 분산)

| 항목 | 값 |
|------|-----|
| 소스 | IF_RSV_TB_JEJU_JEWON |
| 타겟 | 7개 테이블 |
| 로직 | 관측점 1건 → 7개 타겟에 분산 INSERT/UPDATE |

**타겟 테이블 매핑 (레거시 JewonDB 기준):**

| # | 타겟 | 내용 | 매핑 특징 |
|---|------|------|----------|
| 1 | TM_GD60001 | 제주 관측점 마스터 | obsrvt_id → OBSV_CODE, 좌표/주소 매핑 |
| 2 | TM_GD10001 | 관측소 정보 | OBSV_CODE 기준 MERGE |
| 3 | TM_GD60130 | 관측점 시설 | 고정값 다수 (V1~V10) |
| 4 | TM_GD60002 | 관측점 상세 | 심도/구경 등 |
| 5 | GD60101_Gl | 수위 센서 등록 | 고정값 (ITEM_CODE='GL') |
| 6 | GD60101_Wtemp | 수온 센서 등록 | 고정값 (ITEM_CODE='WTEMP') |
| 7 | GD60101_Scond | 전기전도도 센서 등록 | 고정값 (ITEM_CODE='SCOND') |

**난이도: 높음** — 타겟 7개 DDL 필요, 고정값 매핑 복잡

### I2: JejuObsvdataLoadStep (센서 분기)

| 항목 | 값 |
|------|-----|
| 소스 | IF_RSV_TB_JEJU |
| 타겟 | Pm60201 (3개 항목) + Pm60202 (3개 항목) = 6개 논리 타겟 |
| 로직 | msn(센서코드) 분기: S11→60201, S21/S22→60202 |

**컬럼 매핑:**
- gl → 수위값, wtemp → 수온값, scond → 전기전도도값
- 각 항목별 별도 row INSERT (EAV 패턴)
- lc_sn(관측소일련번호) 조회 필요

**난이도: 중간** — 센서 분기 + EAV 변환

### I3: JejuFacilityLoadStep (조건부 INSERT)

| 항목 | 값 |
|------|-----|
| 소스 | IF_RSV_RGETSTGMS01 |
| 타겟 | TmGd31010Gms, TmGd31010, PmGd31022 |
| 로직 | 존재체크 후 INSERT (없으면만), 연도별 처리 |

**난이도: 중간**

### I5: UseLoadStep (이용량)

| 항목 | 값 |
|------|-----|
| 소스 | IF_RSV_USE_LEGACY_DATA + IF_RSV_USE_STATUS_DATA |
| 타겟 | PM_GD31021, PM_GD31022, TM_GD31025 |
| 로직 | SN 증분, 음수→0 보정, legacy+status 합산 |

**난이도: 중간**

## GIMS 타겟 테이블 DDL

내부망 Oracle(29004)에 생성 필요. **실서버 스키마 확인 필수.**

### I4 타겟 (이미 존재)
- RGETSTGMS01 — 새올 Loader에서 이미 생성됨 ✓

### I1 타겟 (7개 — DDL 필요)
- TM_GD60001, TM_GD10001, TM_GD60130, TM_GD60002
- GD60101_Gl, GD60101_Wtemp, GD60101_Scond
- → **실서버 스키마 필요** (txt 파일로 받기)

### I2 타겟 (2개 — DDL 필요)
- PM_GD60201, PM_GD60202
- → **실서버 스키마 필요**

### I3 타겟 (3개 — DDL 필요)
- TM_GD31010_GMS, TM_GD31010, PM_GD31022
- → **실서버 스키마 필요**

### I5 타겟 (3개 — DDL 필요)
- PM_GD31021, PM_GD31022 (I3과 공유?), TM_GD31025
- → **실서버 스키마 필요**

## 구현 순서 (추천)

```
Phase 1: 인프라 (YAML + Factory)
  - internal-jeju-loader.yml (4 step: I1~I4)
  - internal-use-loader.yml (1 step: I5)
  - JejuLoadStepFactory, UseLoadStepFactory

Phase 2: I4 (단순 이관) — 가장 빠르게 E2E 검증
  - RGETSTGMS01 이미 존재
  - 단순 MERGE SQL

Phase 3: I1 (제원 분산) — 실서버 DDL 받은 후
Phase 4: I2 (관측 센서 분기)
Phase 5: I3 (이용시설)
Phase 6: I5 (이용량)
```

## 선행 조건

1. **GIMS 타겟 테이블 DDL** — I1(7개), I2(2개), I3(3개), I5(3개) = 총 ~15개
   - 실서버 스키마 txt 파일 필요 (RGETSTGMS01.txt 처럼)
   - 또는 레거시 소스에서 CREATE TABLE 추출

2. **레거시 Java 소스** — 각 Step의 상세 매핑 로직
   - JewonDB.java, ObsvrdataDB.java, JejuInToDB.java, RgetnDB.java, UseToIn.java
   - `D:\dev\claude\copySource\test\` 에 있다고 문서에 기재됨

## 오늘 가능한 범위

**Phase 1 + Phase 2 (I4)** — YAML + Factory 인프라 + 가장 단순한 I4 구현 + E2E
- 코드 수정: bojo-int 내 3~4개 파일 신규
- DDL: 불필요 (RGETSTGMS01 이미 존재)
- 테스트: Mock → D3 → SND → RCV → Loader(I4) → RGETSTGMS01 검증
