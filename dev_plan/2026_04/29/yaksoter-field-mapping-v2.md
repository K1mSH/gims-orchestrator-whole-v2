# 약수터 API 필드 매핑 v2

> **작성일**: 2026-04-29
> **상위 계획서**: `dev_plan/2026_04/29/yaksoter-resume-replace-api.md`
> **베이스**: 4/15 §4-5/§4-6 매핑표 + 표준화 자료 + 4/29 §A.2 호출 검증
> **갱신 사유**: 응답 필드명 = **UPPER_SNAKE_CASE** (4/15 의 camelCase / lower_snake 가정 모두 깨짐)

---

## 0. 출처 / 검증 근거

| 출처 | 위치 | 용도 |
|------|------|------|
| 응답 실측 (yyyy=2020) | `C:\Users\podo\AppData\Local\Temp\yaksoter_sample.json` | 필드명/형식/값 예시 |
| 표준화 자료 (제원) | `docs/Standardizedtable/TM_GD20310.txt` 24 컬럼 | 타겟 컬럼명/타입 |
| 표준화 자료 (수질) | `docs/Standardizedtable/TD_GD20310.txt` 67 컬럼 | 타겟 컬럼명/타입 |
| 레거시 분배 규칙 | `AdminBatchServiceImpl.waterQualityParamMapping():192-228` | 필드 → 제원/수질 분류 기준 |

**레거시 분배 규칙 (`waterQualityParamMapping`)**:
- `ITEM_*` / `INSP_*` / `FAIL_*` / `SAMP_*` / `UN_*` / `ACCEPT_*` / `YYYY` / `PERIOD` / `SUIT` / `UNSUIT` → 수질
- `LEGACY_CODE_NO` / `SPOT_STD_CODE` → **양쪽 (제원+수질 공통)**
- 나머지 → 제원

---

## 1. 응답 89 필드 → 두 테이블 분포

| 분포 | 갯수 | 비고 |
|------|------|------|
| 제원 (`tm_gd010310`) | 24 | LEGACY_CODE_NO + SPOT_STD_CODE 공통 2 포함 |
| 수질 (`td_gd010310`) | 67 | LEGACY_CODE_NO + SPOT_STD_CODE 공통 2 포함 — 응답에서 떼면 65 |
| 응답 합 | 89 | 24 + 65 (공통 중복 빼고) |

> 같은 응답 1건이 두 테이블에 동시 적재 — `LoaderStepHelper.processItem()` 패턴으로 한 루프에서 분배.

---

## 2. 제원 매핑표 (`tm_gd010310` / `TM_GD010310`) — 24 컬럼

| # | API 응답 (UPPER) | 소스 PG (lower) | Target Oracle (UPPER) | 타입 | 길이 | NULL | 변환 | 응답 예시 |
|--:|----------------|---------------|-------------------|-----|----:|:----:|------|---------|
| 1 | `ROWNO` | `seq` | `SEQ` | NUMBER | 22 | N | NUMBER 직변환 | `1` |
| 2 | `LEGACY_CODE_NO` | `brnch_no` | `BRNCH_NO` | VARCHAR2 | 10 | N | 그대로 | `PUB_2671` |
| 3 | `SPOT_NM` | `brnch_nm` | `BRNCH_NM` | VARCHAR2 | 100 | N | 그대로 | `마지막골약수터` |
| 4 | `SPOT_STD_CODE` | `brnch_std_cd` | `BRNCH_STD_CD` | VARCHAR2 | 20 | N | 그대로 | `47280E060000003` |
| 5 | `INFO_CREAT_INSTT_NM` | `info_crt_inst_nm` | `INFO_CRT_INST_NM` | VARCHAR2 | 50 | N | 그대로 | `경북` |
| 6 | `CL_MIDDLE_NM` | `chrtc_mclsf` | `CHRTC_MCLSF` | VARCHAR2 | 4 | Y | 그대로 | null |
| 7 | `CL_SMALL_NM` | `chrtc_sclsf` | `CHRTC_SCLSF` | VARCHAR2 | 4 | Y | 그대로 | null |
| 8 | `DO_NM` | `ctpv_nm` | `CTPV_NM` | VARCHAR2 | 40 | Y | 그대로 | `경상북도` |
| 9 | `CTY_NM` | `sgg_nm` | `SGG_NM` | VARCHAR2 | 30 | Y | 그대로 | `문경시` |
| 10 | `ADRES` | `addr` | `ADDR` | VARCHAR2 | 250 | N | **잘림** (응답 500→250) | `경상북도 문경시 불정동 339-8` |
| 11 | `ADMCODE` | `stdg_cd` | `STDG_CD` | VARCHAR2 | 10 | N | 그대로 | `4728010700` |
| 12 | `CRDNT_X` | `xcrd` | `XCRD` | VARCHAR2 | 20 | Y | 그대로 | null |
| 13 | `CRDNT_Y` | `ycrd` | `YCRD` | VARCHAR2 | 20 | Y | 그대로 | null |
| 14 | `ABL_AT` | `abl_yn` | `ABL_YN` | VARCHAR2 | 1 | N | 첫 글자 (응답 'N' OK) | `N` |
| 15 | `ABL_DE` | `abl_ymd` | `ABL_YMD` | VARCHAR2 | 8 | Y | **점/하이픈 제거** | null |
| 16 | `DAY_AVG` | `day01_avg_usr_cnt` | `DAY01_AVG_USR_CNT` | NUMBER | 22 | N | 문자→NUMBER (실패 시 null) | `250` |
| 17 | `CHARGE` | `pic` | `PIC` | VARCHAR2 | 60 | Y | 그대로 | `경상북도담당자` |
| 18 | `INS_DATE` | `instl_ymd` | `INSTL_YMD` | VARCHAR2 | 8 | N | **점/하이픈 제거** | `19950816` |
| 19 | `DEL_YN` | `del_yn` | `DEL_YN` | CHAR | 1 | N | 첫 글자 | `N` |
| 20 | `OFFICE` | `pic_nm` | `PIC_NM` | VARCHAR2 | 60 | N | 그대로 | `수도사업소` |
| 21 | `OFFICE_TEL` | `pic_cnpl` | `PIC_CNPL` | VARCHAR2 | 50 | N | 그대로 | `054-550-8344` |
| 22 | `BUILDING_NO` | `bno` | `BNO` | VARCHAR2 | 30 | Y | 그대로 | null |
| 23 | `LOC_JIBUN` | `lctn_lotno` | `LCTN_LOTNO` | VARCHAR2 | 20 | Y | 그대로 | null |
| 24 | `COMMT` | `rmrk` | `RMRK` | VARCHAR2 | 500 | Y | 그대로 | null |

> **PIC vs PIC_NM 충돌** (4/15 §4-5 미결): 표준화 자료 보면 `CHARGE → PIC_NM`, `OFFICE → PIC_NM` **둘 다 PIC_NM 에 매핑**. 우리 4/15 엔티티는 `pic` (CHARGE) + `pic_nm` (OFFICE) 로 분리해뒀음. 4/29 결정: **엔티티 정의대로 분리 유지** (`pic` ← CHARGE, `pic_nm` ← OFFICE) — 표준화 자료의 1:1 충돌은 우리쪽 분리로 해소.

---

## 3. 수질 매핑표 (`td_gd010310` / `TD_GD010310`) — 67 컬럼

### 3-1. 기본 (12 + 공통 2 = 12 컬럼)

| # | API 응답 | 소스 PG | Target | 타입 | 길이 | NULL | 변환 | 예시 |
|--:|---------|--------|-------|-----|----:|:----:|------|------|
| 1 | `LEGACY_CODE_NO` | `brnch_no` | `BRNCH_NO` | VARCHAR2 | 10 | N | 그대로 (공통) | `PUB_2671` |
| 2 | `SPOT_STD_CODE` | `brnch_std_cd` | `BRNCH_STD_CD` | VARCHAR2 | 20 | N | 그대로 (공통) | `47280E060000003` |
| 3 | `YYYY` | `yr` | `YR` | VARCHAR2 | 4 | Y | 그대로 | `2020` |
| 4 | `PERIOD` | `qtr` | `QTR` | VARCHAR2 | 10 | N | 그대로 (`'9월'` `'2/4'` 혼재 OK) | `9월` |
| 5 | `INSP_CHECK` | `insp_yn` | `INSP_YN` | VARCHAR2 | 20 | N | 그대로 | `검사` |
| 6 | `UN_INSP_DESC` | `un_insp_rsn` | `UN_INSP_RSN` | VARCHAR2 | 500 | N | 그대로 | null |
| 7 | `ACCEPT_YN` | `stblt_yn` | `STBLT_YN` | VARCHAR2 | 10 | Y | 그대로 | `적합` |
| 8 | `SUIT` | `stblt` | `STBLT` | VARCHAR2 | 10 | N | 그대로 | `78` |
| 9 | `UNSUIT` | `icpt` | `ICPT` | VARCHAR2 | 10 | N | 그대로 | `0` |
| 10 | `INSP_RST` | `icpt_artcl` | `ICPT_ARTCL` | VARCHAR2 | 100 | Y | **잘림** (500→100) | null |
| 11 | `FAIL_DESC` | `icpt_actn_mttr` | `ICPT_ACTN_MTTR` | VARCHAR2 | 100 | Y | **잘림** (500→100) | null |
| 12 | `SAMP_DATE` | `wtsmp_ymd` | `WTSMP_YMD` | VARCHAR2 | 8 | N | **하이픈 제거** ('2020-09-22' → '20200922') | `2020-09-22` |

### 3-2. 측정항목 55 (모두 VARCHAR2 길이 20, 예외 PH=10/COLOR=25)

> 변환: 모두 그대로 적재 (응답 string 형태 유지 — `'불검출'` `'6'` `'0.9'` 등 혼재).

| # | API | 소스 PG | Target | 길이 | # | API | 소스 PG | Target | 길이 |
|--:|-----|--------|-------|----:|--:|-----|--------|-------|----:|
| 13 | `ITEM_GENBACLOW` | `gnrl_germ_lowtmp` | `GNRL_GERM_LOWTMP` | 20 | 41 | `ITEM_DCM` | `dcmt` | `DCMT` | 20 |
| 14 | `ITEM_GENBACMID` | `gnrl_germ_mesph` | `GNRL_GERM_MESPH` | 20 | 42 | `ITEM_BENZENE` | `bnzn` | `BNZN` | 20 |
| 15 | `ITEM_TOTBAC` | `cfbctr` | `CFBCTR` | 20 | 43 | `ITEM_TOLUENE` | `tln` | `TLN` | 20 |
| 16 | `ITEM_BAC` | `clbcl` | `CLBCL` | 20 | 44 | `ITEM_ETILBEN` | `etbz` | `ETBZ` | 20 |
| 17 | `ITEM_FESTR` | `fcfs` | `FCFS` | 20 | 45 | `ITEM_XYLENE` | `xln` | `XLN` | 20 |
| 18 | `ITEM_BRANFUNGUS` | `fcstrcci` | `FCSTRCCI` | 20 | 46 | `ITEM_DCE` | `dcty` | `DCTY` | 20 |
| 19 | `ITEM_GRGUNGUS` | `paergns` | `PAERGNS` | 20 | 47 | `ITEM_CCL4` | `ccl4` | `CCL4` | 20 |
| 20 | `ITEM_SALMOL` | `slmn` | `SLMN` | 20 | 48 | `ITEM_DBCP` | `dbcp` | `DBCP` | 20 |
| 21 | `ITEM_SEGEL` | `shigla` | `SHIGLA` | 20 | 49 | `ITEM_C4H8O2` | `diox14` | `DIOX14` | 20 |
| 22 | `ITEM_SULFUNGUS` | `sfsra` | `SFSRA` | 20 | 50 | `ITEM_GRADIENT` | `tds` | `TDS` | 20 |
| 23 | `ITEM_YERSINIA` | `yersna` | `YERSNA` | 20 | 51 | `ITEM_KMN` | `ptpm_cnsm_qnt` | `PTPM_CNSM_QNT` | 20 |
| 24 | `ITEM_PB` | `pmbm` | `PMBM` | 20 | 52 | `ITEM_SMELL` | `smll` | `SMLL` | 20 |
| 25 | `ITEM_F` | `flrn` | `FLRN` | 20 | 53 | `ITEM_COLOR` | `crmty` | `CRMTY` | **25** |
| 26 | `ITEM_GAS` | `asnc` | `ASNC` | 20 | 54 | `ITEM_CU` | `cppr` | `CPPR` | 20 |
| 27 | `ITEM_SE` | `se` | `SE` | 20 | 55 | `ITEM_ABS` | `abs` | `ABS` | 20 |
| 28 | `ITEM_HG` | `mrcr` | `MRCR` | 20 | 56 | `ITEM_PH` | `ph` | `PH` | **10** |
| 29 | `ITEM_CN` | `cyn` | `CYN` | 20 | 57 | `ITEM_ZN` | `zn` | `ZN` | 20 |
| 30 | `ITEM_CR6` | `chrm` | `CHRM` | 20 | 58 | `ITEM_CL` | `crrd` | `CRRD` | 20 |
| 31 | `ITEM_NO3AM` | `amng` | `AMNG` | 20 | 59 | `ITEM_FE` | `iron` | `IRON` | 20 |
| 32 | `ITEM_NO3N` | `ntng` | `NTNG` | 20 | 60 | `ITEM_MN` | `mngn` | `MNGN` | 20 |
| 33 | `ITEM_CD` | `cdmm` | `CDMM` | 20 | 61 | `ITEM_MUDDY` | `trbt` | `TRBT` | 20 |
| 34 | `ITEM_BORON` | `bor` | `BOR` | 20 | 62 | `ITEM_SO42` | `eccbt_ion` | `ECCBT_ION` | 20 |
| 35 | `ITEM_BRO3` | `bro3` | `BRO3` | 20 | 63 | `ITEM_AL` | `almn` | `ALMN` | 20 |
| 36 | `ITEM_PHENOL` | `phnl` | `PHNL` | 20 | 64 | `ITEM_URAN` | `uran` | `URAN` | 20 |
| 37 | `ITEM_DIAZN` | `dznn` | `DZNN` | 20 | | | | | |
| 38 | `ITEM_PARAT` | `parat` | `PARAT` | 20 | | | | | |
| 39 | `ITEM_PENITRO` | `fenitro` | `FENITRO` | 20 | | | | | |
| 40 | `ITEM_CARBARYL` | `carbaryl` | `CARBARYL` | 20 | | | | | |
| 41a | `ITEM_TCET` | `tcrn` | `TCRN` | 20 | | | | | |
| 41b | `ITEM_TECE` | `ttrt` | `TTRT` | 20 | | | | | |
| 41c | `ITEM_TCE` | `tcrt` | `TCRT` | 20 | | | | | |

> 측정항목 = 55개 (인덱스 13~67 중 일부 부여, 위 표는 좌우 분할).
> 표준화 자료의 응답 길이 25 → Target 20 으로 **잘림** 가능 — 측정값 string 이 20자 초과하는 케이스는 미관측, 발생 시 잘림.

---

## 4. 타입 변환 처리 요약

| 변환 종류 | 대상 컬럼 | 입력 → 출력 |
|----------|---------|-----------|
| **NUMBER 변환** | `ROWNO` → `SEQ`, `DAY_AVG` → `DAY01_AVG_USR_CNT` | string → number, 실패 시 null |
| **첫 글자 추출** | `ABL_AT` → `ABL_YN`, `DEL_YN` → `DEL_YN` | "N" → "N" / 4글자 들어와도 첫 1글자 |
| **하이픈/점 제거 (날짜 8자리)** | `INS_DATE`/`ABL_DE`/`SAMP_DATE` → `INSTL_YMD`/`ABL_YMD`/`WTSMP_YMD` | "2020-09-22" → "20200922" / "1995.08.16" → "19950816" / "19950816" → "19950816" (이미 8) |
| **그대로 잘림 주의** | `ADRES` (500→250), `INSP_RST` (500→100), `FAIL_DESC` (500→100), `ITEM_*` (25→20) | 길이 초과 시 substring |
| **그대로** | 위 외 모든 컬럼 | 무변환 |

> API Collector 의 `TransformType` 활용: `DATE_FORMAT` (하이픈 제거), `NUMBER`, `SUBSTRING`, `DEFAULT_VALUE` — 4/24 LOOKUP 추가 후 패턴 그대로.

---

## 5. ApiFieldMapping 등록 가이드

### Endpoint 1 — 약수터 제원 (`tm_gd010310`)

| sourceField | targetColumn | transformType | transformParam |
|-------------|--------------|---------------|----------------|
| `ROWNO` | `seq` | `NUMBER` | — |
| `LEGACY_CODE_NO` | `brnch_no` | `NONE` | — |
| `SPOT_NM` | `brnch_nm` | `NONE` | — |
| ... (24 매핑 — §2 표대로) | | | |
| `ABL_AT` | `abl_yn` | `SUBSTRING` | `0,1` |
| `INS_DATE` | `instl_ymd` | `DATE_FORMAT` | `(strip dot/hyphen → 8자리)` |
| `DAY_AVG` | `day01_avg_usr_cnt` | `NUMBER` | — |
| ... | | | |

### Endpoint 2 — 약수터 수질 (`td_gd010310`)

> §3 표대로 67 매핑. SAMP_DATE 만 `DATE_FORMAT` (`-` 제거). 나머지 측정항목 모두 `NONE`.

> **`ServiceKey` (소문자 s)** Param 등록:
> - `name`: `serviceKey`
> - `isApiKeyRef`: `true`
> - `staticValue`: api-collector 자체 키 관리에 등록한 키 ID

> **공통 Param**:
> - `pageNo`: 1 (페이징 자동)
> - `numOfRows`: 1000 (운영 가정)
> - `resultType`: `JSON`
> - `yyyy`: 동적 (스케줄 트리거 시점 기준)

### dataRootPath

```
getSgisDrinkWaterList.item
```

---

## 6. 알려진 함정 / 운영 메모

1. **PERIOD 비정형** — `'1/4'`, `'2/4'`, `'9월'` 등 운영기관별 표기. `qtr VARCHAR2(10)` 그대로 적재. dedup 4키에 포함되므로 같은 실제 분기에 대해 `'9월'` ↔ `'3/4'` 혼용은 다른 행으로 판정됨 (소스 데이터 측 이슈).
2. **isNotNull 동적 비교 함정** — 응답값 NULL + 기존행 값 있음 → "매치" 판정 → SKIP 가능. 4/29 사용자 결정 — **그대로 보존** (레거시 동작).
3. **제원 변경감지 = `BRNCH_NM` + `ADDR` 2 컬럼만** — 다른 필드 변경(담당자, 주소상세 외 정보) 은 감지 안 됨 → 갱신 안 됨. 레거시 동작.
4. **공통 2 (LEGACY_CODE_NO + SPOT_STD_CODE) 양쪽 매핑** — 같은 응답 필드를 두 Endpoint 의 매핑에 모두 등록. API Collector 의 등록 화면에서 양쪽 작업 필요.
5. **PIC/PIC_NM 충돌 우회** — 표준화 자료의 1:1 충돌(`CHARGE→PIC_NM`, `OFFICE→PIC_NM`) 을 우리쪽 엔티티에서 `pic` (CHARGE) + `pic_nm` (OFFICE) 로 분리 유지.

---

## 7. 다음 단계

- §B.0 — 엔티티 UK 제거 (위 매핑은 UK 무관, 단지 적재 흐름)
- §A.4 — API Collector UI Endpoint 2개 등록 (이 문서의 §5 가 입력 데이터)
- §A.5 — Mock API Key 등록
- §A.6 — 수동 실행 → 소스 PG 양쪽 row 발생 검증
