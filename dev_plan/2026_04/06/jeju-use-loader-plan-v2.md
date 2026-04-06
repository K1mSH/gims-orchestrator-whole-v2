# 제주/이용량 내부망 Loader 계획 (v2)

> 작성일: 2026-04-06 (v1: 04/03 → v2 갱신)
> 선행: 제주/이용량 RCV 완료 (IF_RSV 적재 확인됨)

## v1 대비 변경사항
- **I4(RGETSTGMS01) 제외**: 새올 Loader가 이미 처리 중 (컬럼명 동일, 표준화 불필요)
- **컬럼명 정책 확정**: RGETSTGMS 계열은 표준화 안 함 (원래 이름 유지)
- I1~I3, I5만 커스텀 Step 구현 대상

## 남은 Step 요약

| Step | 클래스명 | 소스 | 타겟 수 | 난이도 |
|------|---------|------|---------|--------|
| I1 | JejuJewonLoadStep | IF_RSV_TB_JEJU_JEWON | 5 | 높음 |
| I2 | JejuObsvdataLoadStep | IF_RSV_TB_JEJU | 2 | 중간 |
| I3 | JejuFacilityLoadStep | IF_RSV_RGETSTGMS01 | 2 | 중간 |
| I5 | UseLoadStep | IF_RSV_USE_LEGACY_DATA + IF_RSV_USE_STATUS_DATA | 4 | 중간 |

## 아키텍처

```
sync-agent-bojo-int/src/main/java/com/sync/agent/bojoint/
├── config/pipeline/
│   ├── InternalBojoLoadStepFactory.java     ← 기존
│   ├── JejuLoadStepFactory.java             ← 신규 (I1~I3 통합 팩토리)
│   └── UseLoadStepFactory.java              ← 신규 (I5 팩토리)
├── jeju/step/                               ← 신규 패키지
│   ├── JejuJewonLoadStep.java               (I1)
│   ├── JejuObsvdataLoadStep.java            (I2)
│   └── JejuFacilityLoadStep.java            (I3)
└── use/step/                                ← 신규 패키지
    └── UseLoadStep.java                     (I5)
```

## Step별 상세

### I1: JejuJewonLoadStep (1→5 분산)

| 항목 | 값 |
|------|-----|
| 소스 | IF_RSV_TB_JEJU_JEWON |
| 타겟 | 5개 테이블 |
| 레거시 | JewonDB.java, target.xml |
| 난이도 | **높음** — 다수 타겟, 고정값, 서브쿼리 |

**타겟 테이블 (이전 컬럼명 → 환경부 표준 변환 필요):**

| # | 이전 테이블명 | 11자리 표준명 | 설명 | PK (표준) |
|---|-------------|-------------|------|-----------|
| 1 | TM_GD60001 | TM_GD970001 | ODM관측소 | BRNCH_ID |
| 2 | TM_GD10001 | TM_GD120001 | 관정 | GWEL_NO |
| 3 | TM_GD60130 | TM_GD970130 | ODM관정사양 | GWEL_NO + BRNCH_ID |
| 4 | TM_GD60002 | TM_GD970002 | ODM관측소사양 | BRNCH_ID |
| 5 | TM_GD60101 | TM_GD970101 | ODM결과 (센서 3종 등록) | RSLT_ID |

**컬럼 매핑 예시 (TM_GD970001):**
- SPOT_ID → BRNCH_ID, OBSRVT_NM → OBSVTR_NM, LO_VALUE → LOT, LA_VALUE → LAT
- 매핑 전체: `제주_이용량_ddl.txt`의 `이전COLUMN_NAME` → `환경부표준` 컬럼 참조

### I2: JejuObsvdataLoadStep (센서 분기 EAV)

| 항목 | 값 |
|------|-----|
| 소스 | IF_RSV_TB_JEJU |
| 타겟 | 2개 테이블 × 3항목(GL/WTEMP/SCOND) = 6개 논리 INSERT |
| 레거시 | ObsvrdataDB.java, target.xml |
| 난이도 | **중간** — msn 센서분기 + EAV + RESULT_ID 참조 |

**타겟 테이블 (환경부 표준 변환):**

| # | 이전 | 표준 | 설명 | PK |
|---|------|------|------|-----|
| 1 | PM_GD60201 | PM_GD970201 | ODM관측자료 | OBSRVN_DATA_ID |
| 2 | PM_GD60202 | PM_GD970202 | ODM다심도관측자료 | RSLT_ID + OBSRVN_DATA_ID |

**센서 분기**: msn=S11 → PM_GD970201, msn=S21/S22 → PM_GD970202

### I3: JejuFacilityLoadStep (조건부 INSERT)

| 항목 | 값 |
|------|-----|
| 소스 | IF_RSV_RGETSTGMS01 |
| 타겟 | 2개 테이블 |
| 레거시 | JejuInToDB.java, target.xml |
| 난이도 | **중간** — 존재체크 MERGE, 연도별 |

**타겟 테이블 (환경부 표준 변환):**

| # | 이전 | 표준 | 설명 | PK |
|---|------|------|------|-----|
| 1 | TM_GD31010 | TM_GD111010 | 이용량시설 | BRNCH_ID |
| 2 | PM_GD31022 | PM_GD111022 | 이용량일자료 | BRNCH_ID + OBSRVN_YMD |

### I5: UseLoadStep (이용량)

| 항목 | 값 |
|------|-----|
| 소스 | IF_RSV_USE_LEGACY_DATA + IF_RSV_USE_STATUS_DATA |
| 타겟 | 4개 테이블 |
| 레거시 | UseToIn.java, target.xml |
| 난이도 | **중간** — SN 증분, 음수→0, 후처리(lastReceive) |

**타겟 테이블 (환경부 표준 변환):**

| # | 이전 | 표준 | 설명 | PK |
|---|------|------|------|-----|
| 1 | PM_GD31021 | PM_GD111021 | 이용량시간자료 | BRNCH_ID + OBSRVN_DT |
| 2 | PM_GD31022 | PM_GD111022 | 이용량일자료 (I3과 공유) | BRNCH_ID + OBSRVN_YMD |
| 3 | TM_GD31024 | TM_GD111024 | 이용량최근수신현황 | BRNCH_ID |
| 4 | TM_GD31025 | TM_GD111025 | 이용량관측데이터 | SN |

## GIMS 타겟 DDL (Oracle 29004)

**대상**: 11개 테이블 (PM_GD111022 공유로 중복 제외)
**컬럼명**: 환경부 표준 사용 — `제주_이용량_ddl.txt` 참조
**생성 위치**: `scripts/oracle-init-internal-jeju-use-target.sql` (신규)

| # | 테이블 (표준) | Step | 컬럼수 |
|---|--------------|------|--------|
| 1 | TM_GD970001 | I1 | 16 |
| 2 | TM_GD120001 | I1 | 33 |
| 3 | TM_GD970130 | I1 | 57 |
| 4 | TM_GD970002 | I1 | 21 |
| 5 | TM_GD970101 | I1 | 18 |
| 6 | PM_GD970201 | I2 | 5 |
| 7 | PM_GD970202 | I2 | 6 |
| 8 | TM_GD111010 | I3 | 50 |
| 9 | PM_GD111022 | I3/I5 | 4 |
| 10 | PM_GD111021 | I5 | 4 |
| 11 | TM_GD111024 | I5 | 2 |
| 12 | TM_GD111025 | I5 | 4 |

**참고**: 기존 `gims-target-ddl.sql`의 4개 테이블(tm_gd970001 등)은 **PG용 bojo-loader** 전용이므로 별개.
이번 DDL은 **Oracle 29004** 용으로 신규 작성.

## 레거시 참조 소스

| Step | 레거시 Java | 레거시 XML | 위치 |
|------|-----------|-----------|------|
| I1 | JewonDB.java (class) | target.xml | `D:\dev\claude\copySource\test\internal\in_use\` |
| I2 | ObsvrdataDB.java | target.xml | 동일 |
| I3 | JejuInToDB.java | target.xml | 동일 |
| I5 | UseToIn.java | target.xml | 동일 |

## 구현 순서

```
Phase 1: DDL + 인프라
  - 11개 GIMS 타겟 테이블 DDL (환경부 표준 컬럼명, Oracle)
  - internal-jeju-loader.yml (3 step: I1~I3)
  - internal-use-loader.yml (1 step: I5)
  - JejuLoadStepFactory, UseLoadStepFactory
  - Orchestrator Agent 등록 SQL

Phase 2: I5 (이용량 — SN증분) → E2E 검증
Phase 3: I3 (이용시설 — 조건부) → E2E 검증
Phase 4: I2 (관측 센서 분기 EAV) → E2E 검증
Phase 5: I1 (제원 1→5 분산) → E2E 검증
```
