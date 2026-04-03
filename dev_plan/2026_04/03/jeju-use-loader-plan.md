# 제주/이용량 내부망 Loader 계획

> 작성일: 2026-04-03
> 선행: 제주/이용량 RCV 완료 (IF_RSV 적재 확인됨)

## 목적
IF_RSV → GIMS 실테이블 적재. 레거시 로직을 커스텀 Step으로 구현.

## 핵심 결정사항

### 1. 전 Step 커스텀 (source-to-if 미사용)
- I1~I3, I5: 복잡한 로직 (분산/분기/조건부/증분)
- I4: 단순 이관이지만 **컬럼명 변환** 필요
- source-to-if는 동일 컬럼명 전제 → 컬럼 매핑 기능 없음
- column-mapping 확장은 에이전트 로직 복잡도 증가로 비채택

### 2. 컬럼명: 환경부 표준 적용
- **GIMS 타겟 DDL**: 환경부 표준 컬럼명으로 생성
- **IF_RSV**: 이전(레거시) 컬럼명 유지 (이미 생성됨)
- **Loader Step**: IF_RSV(이전) → GIMS(표준) 매핑 변환 처리
- 예: `SPOT_ID → BRNCH_ID`, `OBSRVT_NM → OBSVTR_NM`
- 매핑은 각 Step 내 Java Map/상수로 정의

### 3. 기존 매핑 기능 비채택 사유
- api-collector 매핑: JSON→DB 변환 용도, DB→DB와 다름
- 에이전트 YAML: DB 테이블 등록과 실행 로직 분리 구조 → 매핑 끼우기 부적합

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

### I4: JejuRgetnLoadStep (단순 MERGE + 컬럼명 변환)

| 항목 | 값 |
|------|-----|
| 소스 | IF_RSV_RGETSTGMS01 (이전 컬럼명) |
| 타겟 | RGETSTGMS01 (환경부 표준 컬럼명) |
| 로직 | source_refs 기준 MERGE, 전컬럼 매핑 변환 |
| 레거시 | RgetnDB.java, target.xml `insetRgetstgms01` |
| 난이도 | **낮음** — 1:1 이관 + 컬럼명 치환 |

**참고**: IF_RSV_RGETSTGMS01은 새올/제주 공유 테이블. 새올은 새올 Loader(source-to-if)에서 같은 컬럼명으로 처리, 제주는 이 커스텀 Step에서 표준 변환.

### I1: JejuJewonLoadStep (1→7 분산)

| 항목 | 값 |
|------|-----|
| 소스 | IF_RSV_TB_JEJU_JEWON |
| 타겟 | 5개 테이블 (실제 target.xml 기준) |
| 레거시 | JewonDB.java, target.xml |
| 난이도 | **높음** — 다수 타겟, 고정값, 서브쿼리 |

**타겟 테이블 (표준 컬럼명 적용):**

| # | 테이블 | 11자리 표준명 | 설명 | PK (표준) |
|---|--------|-------------|------|-----------|
| 1 | TM_GD60001 | TM_GD970001 | ODM관측소 | BRNCH_ID |
| 2 | TM_GD10001 | TM_GD120001 | 관정 | GWEL_NO |
| 3 | TM_GD60130 | TM_GD970130 | ODM관정사양 | GWEL_NO + BRNCH_ID |
| 4 | TM_GD60002 | TM_GD970002 | ODM관측소사양 | BRNCH_ID |
| 5 | TM_GD60101 | TM_GD970101 | ODM결과 (센서 3종 등록) | RSLT_ID |

### I2: JejuObsvdataLoadStep (센서 분기)

| 항목 | 값 |
|------|-----|
| 소스 | IF_RSV_TB_JEJU |
| 타겟 | 2개 테이블 × 3항목(GL/WTEMP/SCOND) = 6개 논리 INSERT |
| 레거시 | ObsvrdataDB.java, target.xml `insetPm60201_*`, `insetPm60202_*` |
| 난이도 | **중간** — msn 센서분기 + EAV + RESULT_ID 참조 |

**타겟 테이블:**

| # | 테이블 | 11자리 표준명 | 설명 | PK (표준) |
|---|--------|-------------|------|-----------|
| 1 | PM_GD60201 | PM_GD970201 | ODM관측자료 | OBSRVN_DATA_ID |
| 2 | PM_GD60202 | PM_GD970202 | ODM다심도관측자료 | RSLT_ID + OBSRVN_DATA_ID |

**센서 분기**: msn=S11 → PM_GD60201, msn=S21/S22 → PM_GD60202

### I3: JejuFacilityLoadStep (조건부 INSERT)

| 항목 | 값 |
|------|-----|
| 소스 | IF_RSV_RGETSTGMS01 |
| 타겟 | 2개 테이블 |
| 레거시 | JejuInToDB.java, target.xml `insertTmGd31010Gms`, `insertPmGd31022` |
| 난이도 | **중간** — 존재체크 MERGE, 연도별 |

**타겟 테이블:**

| # | 테이블 | 11자리 표준명 | 설명 | PK (표준) |
|---|--------|-------------|------|-----------|
| 1 | TM_GD31010 | TM_GD111010 | 이용량시설 | BRNCH_ID |
| 2 | PM_GD31022 | PM_GD111022 | 이용량일자료 | BRNCH_ID + OBSRVN_YMD |

### I5: UseLoadStep (이용량)

| 항목 | 값 |
|------|-----|
| 소스 | IF_RSV_USE_LEGACY_DATA + IF_RSV_USE_STATUS_DATA |
| 타겟 | 4개 테이블 |
| 레거시 | UseToIn.java, target.xml |
| 난이도 | **중간** — SN 증분, 음수→0, 후처리(lastReceive) |

**타겟 테이블:**

| # | 테이블 | 11자리 표준명 | 설명 | PK (표준) |
|---|--------|-------------|------|-----------|
| 1 | PM_GD31021 | PM_GD111021 | 이용량시간자료 | BRNCH_ID + OBSRVN_DT |
| 2 | PM_GD31022 | PM_GD111022 | 이용량일자료 (I3과 공유) | BRNCH_ID + OBSRVN_YMD |
| 3 | TM_GD31024 | TM_GD111024 | 이용량최근수신현황 | BRNCH_ID |
| 4 | TM_GD31025 | TM_GD111025 | 이용량관측데이터 | SN |

## GIMS 타겟 DDL

**소스**: `dev_plan/2026_04/03/제주_이용량_ddl.txt` (환경부 표준화 문서 추출)
**컬럼명**: `환경부표준` 컬럼 사용 (이전 컬럼명 X)
**대상**: 11개 테이블 (PM_GD31022 공유로 실제 11개)

| # | 테이블 | Step | 컬럼수 |
|---|--------|------|--------|
| 1 | TM_GD60001 | I1 | 16 |
| 2 | TM_GD10001 | I1 | 33 |
| 3 | TM_GD60130 | I1 | 57 |
| 4 | TM_GD60002 | I1 | 21 |
| 5 | TM_GD60101 | I1 | 18 |
| 6 | PM_GD60201 | I2 | 5 |
| 7 | PM_GD60202 | I2 | 6 |
| 8 | TM_GD31010 | I3 | 50 |
| 9 | PM_GD31022 | I3/I5 | 4 |
| 10 | PM_GD31021 | I5 | 4 |
| 11 | TM_GD31024 | I5 | 2 |
| 12 | TM_GD31025 | I5 | 4 |

## 레거시 참조 소스

| Step | 레거시 Java | 레거시 XML | 위치 |
|------|-----------|-----------|------|
| I1 | JewonDB.java (class) | target.xml | `D:\dev\claude\copySource\test\internal\in_use\` |
| I2 | ObsvrdataDB.java | target.xml | 동일 |
| I3 | JejuInToDB.java | target.xml | 동일 |
| I4 | RgetnDB.java | target.xml (`insetRgetstgms01`) | 동일 |
| I5 | UseToIn.java | target.xml | 동일 |

## 구현 순서

```
Phase 1: DDL + 인프라
  - 12개 GIMS 타겟 테이블 DDL (환경부 표준 컬럼명)
  - internal-jeju-loader.yml (4 step: I1~I4)
  - internal-use-loader.yml (1 step: I5)
  - JejuLoadStepFactory, UseLoadStepFactory
  - Orchestrator Agent 등록

Phase 2: I4 (단순 MERGE + 컬럼 변환) → E2E 검증
Phase 3: I5 (이용량) → E2E 검증
Phase 4: I3 (이용시설) → E2E 검증
Phase 5: I2 (관측 센서 분기) → E2E 검증
Phase 6: I1 (제원 1→5 분산) → E2E 검증
```
