# I1: JejuJewonLoadStep 구현 계획

> 작성일: 2026-04-06
> 선행: bojo Target 표준화 완료, IF_RSV_TB_JEJU_JEWON 데이터 있음

## 목적
IF_RSV_TB_JEJU_JEWON (1건) → GIMS Target 5개 테이블에 분산 MERGE.
레거시 JewonDB.java + target.xml의 insetTM_GD60001~insetTM_GD60002 로직 이식.

## 소스 → 타겟 매핑

```
IF_RSV_TB_JEJU_JEWON (제주 관측점 마스터, 이전 컬럼명)
    ↓ JejuJewonLoadStep
    ├→ TM_GD970001 (ODM관측소)        — MERGE on OBSVTR_ID, 이미 존재
    ├→ TM_GD120001 (관정)             — MERGE on GRNDS_GWEL_NO+UGWTR_EXMN_CD, 신규 DDL 필요
    ├→ TM_GD970130 (ODM관정사양)       — MERGE on GWEL_NO(FK), 신규 DDL 필요
    ├→ TM_GD970002 (ODM관측소사양)     — MERGE on BRNCH_ID(FK), 신규 DDL 필요
    └→ TM_GD970101 (ODM결과)          — MERGE × 3항목(GL/WTEMP/EC), 이미 존재
```

## 컬럼 매핑 (소스 → 타겟, 이전→표준)

### 1. TM_GD970001 (ODM관측소) — 이미 존재, 표준 컬럼
| 소스 (IF_RSV) | 타겟 (표준) | 비고 |
|--------------|-----------|------|
| OBSRVT_ID | OBSVTR_ID | MERGE key |
| OBSRVT_NM | OBSVTR_NM | |
| LO_VALUE | LOT | |
| LA_VALUE | LAT | |
| LEGALDONG_CODE | STDG_CD | |
| (고정) '제주도보조관측망' | BRNCH_TYPE_MNG_TRM_NM | 레거시 v1 |
| (고정) '해발' | VTCL_CRLPT_MNG_TRM_NM | 레거시 v2 |
| (고정) 'Point' | SPCEDATA_TYPE_MNG_TRM_NM | |
| 조합(시군구+읍면동+리+번지+호) | ADDR | |
| (고정) '제주특별자치도 보조관측소' | RMRK_CN | 레거시 v4 |

### 2. TM_GD120001 (관정) — 신규 DDL
| 소스 (IF_RSV) | 타겟 (표준) | 비고 |
|--------------|-----------|------|
| OBSRVT_ID | GRNDS_GWEL_NO | MERGE key (SPT_GENNUM→GRNDS_GWEL_NO) |
| (고정) '105' | UGWTR_EXMN_CD | MERGE key (JOSACODE→UGWTR_EXMN_CD) |
| OBSRVT_NM | BRNCH_NM | |
| (시도 추출) | CTPV_NM | 레거시 v3 |
| SIGUN_NM | SGG_NM | |
| EMD_NM | EMD_NM | |
| LI_NM | LI_NM | |
| BUNJI+HO | ADDR | |
| LO_VALUE | LOT | |
| LA_VALUE | LAT | |
| TMX_VALUE | XCRD | |
| TMY_VALUE | YCRD | |
| AL_VALUE | ALTD_VL | |
| USE_AT | USE_YN | |
| (고정) '제주도보조관측망' | PRMTV_DATA_NM | 레거시 v1→SDTA_NM |
| (고정) '제주특별자치도 보조관측소' | PRMTV_DATA_INST_NM | 레거시 v4→SDTA_INSTT_NM |
| LEGALDONG_CODE | STDG_CD | |
| (고정) '관측용도...' | RMRK | 레거시 v5 |

### 3. TM_GD970130 (ODM관정사양) — 신규 DDL
| 소스 (IF_RSV) | 타겟 (표준) | 비고 |
|--------------|-----------|------|
| (FK) TM_GD120001.GWEL_NO | GWEL_NO | MERGE key |
| (FK) TM_GD970001.BRNCH_ID | BRNCH_ID | |
| EXTN_CSNG_CALBR | OTSD_CSNG_CALBR | |
| DRNK_AT | DKPP_YN | |
| WAL | WTLV | |
| UGRWTR_PRPOS_CODE | UGWTR_DTL_USG_CD | |
| (고정) 관측항목명 | OBSRVN_ARTCL_NM | 레거시 v6 |
| (고정) 관측주기 | OBSRVN_CYCL_CN | 레거시 v7 |

### 4. TM_GD970002 (ODM관측소사양) — 신규 DDL
| 소스 (IF_RSV) | 타겟 (표준) | 비고 |
|--------------|-----------|------|
| (FK) TM_GD970001.BRNCH_ID | BRNCH_ID | MERGE key |
| (고정) 'O' | USE_YN | |
| (고정) 시공기관 | CNST_INST_CN | 레거시 v8 |

### 5. TM_GD970101 (ODM결과) — 이미 존재, 3항목 MERGE
GL(수위=5), WTEMP(수온=163), EC(전기전도도=52) 각각 MERGE.
기존 bojo-loader의 ensureResultId와 동일 패턴이지만, 추가 컬럼(RSLT_DT, UNIT_ID 등) 채움.

| 항목 | OBSRVN_ARTCL_ID | UNIT_ID | 비고 |
|------|----------------|---------|------|
| GL (수위) | 5 | 5 | |
| WTEMP (수온) | 163 | 58 | |
| EC (전기전도도) | 52 | 52 | |

## 선행 작업: DDL 3개 테이블 생성

표준화_컬럼매핑.md 기준으로 DDL 생성:
- TM_GD120001 (관정, 33컬럼 + 추적 2컬럼)
- TM_GD970130 (ODM관정사양, 56컬럼 + 추적 2컬럼)
- TM_GD970002 (ODM관측소사양, 20컬럼 + 추적 2컬럼)

파일: `scripts/oracle-init-jeju-target.sql`

## 아키텍처

```
sync-agent-bojo-int/src/main/java/com/sync/agent/bojoint/
├── config/pipeline/
│   └── JejuLoadStepFactory.java         ← 신규
└── jeju/step/
    └── JejuJewonLoadStep.java           ← 신규
```

## 실행 흐름

1. IF_RSV_TB_JEJU_JEWON에서 PENDING 조회
2. 건별 처리:
   a. TM_GD970001 MERGE (OBSVTR_ID 기준) → BRNCH_ID 확보
   b. TM_GD120001 MERGE (GRNDS_GWEL_NO 기준) → GWEL_NO 확보
   c. TM_GD970130 MERGE (GWEL_NO FK 기준)
   d. TM_GD970002 MERGE (BRNCH_ID FK 기준)
   e. TM_GD970101 MERGE × 3 (GL/WTEMP/EC)
3. IF 상태 SUCCESS 업데이트
4. SyncLog 기록

## 레거시에서 사용하는 고정값 (v1~v8)

JewonDB.class 디컴파일 확인 완료:
- v1: `보조지하수관측망` → BRNCH_TYPE_MNG_TRM_NM, PRMTV_DATA_NM
- v2: `제주도평균해수면` → VTCL_CRLPT_MNG_TRM_NM
- v3: `제주특별자치도` → CTPV_NM
- v4: `제주도연계데이터` → RMRK_CN, PRMTV_DATA_INST_NM
- v5: `연계` → GNRL_CTGRY_MNG_TRM_NM (TM_GD970101), RMRK (TM_GD120001)
- v6: `수위, 온도, 전기전도도` → DATA_TYPE_MNG_TRM_NM (TM_GD970101), OBSRVN_ARTCL_NM (TM_GD970130)
- v7: `1시간 간격` → OBSRVN_CYCL_CN
- v8: `제주도지자체` → CNST_INST_CN
- v9: `제주도` → (미사용 여분)
- v10: `순시` → (미사용 여분)

실행 순서 (JewonDB.jewon() 메서드):
1. select_Tb_jeju_jewon (소스 전건 조회)
2. 건별 v1~v10 세팅
3. insetTM_GD60001 (관측소)
4. insetTM_GD10001 (관정)
5. insetTM_GD60130 (관정사양)
6. insetTM_GD60002 (관측소사양)
7. insetGd60101_Gl (결과-수위)
8. insetGd60101_Wtemp (결과-수온)
9. insetGd60101_Scond (결과-전기전도도)

## 주의사항
- BRNCH_ID는 IDENTITY(자동 시퀀스) — INSERT 시 자동 생성, SELECT로 조회
- GWEL_NO도 IDENTITY — TM_GD120001 INSERT 후 조회
- 순서 중요: TM_GD970001 → TM_GD120001 → TM_GD970130/TM_GD970002 → TM_GD970101
- IF_RSV_TB_JEJU_JEWON에 없는 컬럼(TMX_VALUE, TMY_VALUE)은 소스에 있음 확인 필요
