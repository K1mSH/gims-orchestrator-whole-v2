# 약수터 API ↔ 테이블 필드 매핑 비교

> **분석 일자**: 2026-04-14
> **출처**:
> - API 가이드 PDF: `OpenAPI활용가이드_국립환경과학원_수질DB(20221007).pdf`
> - 표준화 매핑: `docs/Standardizedtable/TM_GD20310.txt` (제원), `TD_GD20310.txt` (수질결과)

---

## 핵심 발견

### 대상 테이블 (API 1건 → 2개 테이블 분리 적재)

| 구분 | 현행 | 표준화(11자리) | 용도 |
|------|------|--------------|------|
| 제원정보 | `TM_GD20310` | `TM_GD010310` | 약수터 시설 기본정보 |
| 수질검사결과 | `TD_GD20310` | `TD_GD010310` | 수질 측정항목 결과 |

- 표준화 = **환경부 표준화 테이블** (11자리 테이블명 + 환경부표준 컬럼명)
- API 응답 1건에 제원+수질이 섞여서 옴 → 분리해서 적재

### API ↔ 현행 테이블 관계
- **API 필드명 = 현행 컬럼의 camelCase** (예: `ITEM_GENBACLOW` → `itemGenbaclow`)
- 제원 24개 + 수질 56개 필드 전부 1:1 매핑

### 현행 → 표준화 전환 시 주의
- **모든 컬럼명 변경** (현행 컬럼명 → 환경부표준 컬럼명)
- 일부 타입/길이 변경 있음 (아래 상세 표 참조)

---

## TM_GD20310 (제원정보) — 3자 비교

### 시설 기본정보 (24개 컬럼)

| API 필드 (PDF) | 현행 컬럼 (TM_GD20310) | 환경부표준 (TM_GD010310) | 설명 | 타입/길이 변경 |
|---------------|----------------------|------------------------|------|--------------|
| `rowno` | `ROWNO` | `SEQ` | 순번 | VARCHAR2(6)→**NUMBER(22)** ⚠️ |
| `legacyCodeNo` | `LEGACY_CODE_NO` | `BRNCH_NO` | 지점번호 | 10→10 |
| `spotNm` | `SPOT_NM` | `BRNCH_NM` | 지점명 | 50→100 |
| `spotStdCode` | `SPOT_STD_CODE` | `BRNCH_STD_CD` | 지점표준코드 | 15→20 |
| `infoCreatInsttNm` | `INFO_CREAT_INSTT_NM` | `INFO_CRT_INST_NM` | 정보생성기관명 | 50→50 |
| *(코드)* | `CL_MIDDLE_NM` | `CHRTC_MCLSF` | 특성중분류 | 4→4 |
| *(코드)* | `CL_SMALL_NM` | `CHRTC_SCLSF` | 특성소분류 | 4→4 |
| `doNm` | `DO_NM` | `CTPV_NM` | 시도명 | 30→40 |
| `ctyNm` | `CTY_NM` | `SGG_NM` | 시군구명 | 25→30 |
| `adres` | `ADRES` | `ADDR` | 주소 | 500→**250** ⚠️ |
| `admcode` | `ADMCODE` | `STDG_CD` | 법정동코드 | 10→10 |
| *(좌표)* | `CRDNT_X` | `XCRD` | X좌표 | 21→20 |
| *(좌표)* | `CRDNT_Y` | `YCRD` | Y좌표 | 21→20 |
| `ablAt` | `ABL_AT` | `ABL_YN` | 폐지여부 | VARCHAR2(4)→**CHAR(1)** ⚠️ |
| `ablDe` | `ABL_DE` | `ABL_YMD` | 폐지일자 | 13→**8** ⚠️ |
| `dayAvg` | `DAY_AVG` | `DAY01_AVG_USR_CNT` | 1일평균이용자수 | VARCHAR2(10)→**NUMBER(22)** ⚠️ |
| `charge` | `CHARGE` | `PIC_NM` | 담당자 | 50→60 |
| `insDate` | `INS_DATE` | `INSTL_YMD` | 설치일자 | 10→**8** ⚠️ |
| `delYn` | `DEL_YN` | `DEL_YN` | 삭제여부 | VARCHAR2(4)→**CHAR(1)** ⚠️ |
| `office` | `OFFICE` | `PIC_NM` | 담당자명 | 50→60 |
| `officeTel` | `OFFICE_TEL` | `PIC_CNPL` | 담당자연락처 | 50→50 |
| `buildingNo` | `BUILDING_NO` | `BNO` | 건물번호 | 10→30 |
| `locJibun` | `LOC_JIBUN` | `LCTN_LOTNO` | 소재지_지번 | 10→20 |
| `commt` | `COMMT` | `RMRK` | 비고 | 500→500 |

### 제원 표준화 시 주의사항

| 현행 컬럼 | 변경 내용 | 위험 |
|----------|----------|------|
| `ROWNO` | VARCHAR2→NUMBER | 타입 변경 |
| `ABL_AT` (폐지여부) | VARCHAR2(4)→CHAR(1) | API에서 `N`/`Y`로 오니 OK, 하지만 다른 값 있으면 잘림 |
| `DEL_YN` (삭제여부) | VARCHAR2(4)→CHAR(1) | 같은 이슈 |
| `DAY_AVG` (이용자수) | VARCHAR2→NUMBER | 타입 변경, 숫자가 아닌 값 들어오면 에러 |
| `ADRES` (주소) | 500→250 | 긴 주소 잘릴 수 있음 |
| `INS_DATE` / `ABL_DE` | 10→8 | 날짜 포맷 변경 필요 |
| `CHARGE` ↔ `OFFICE` | 둘 다 `PIC_NM`으로 매핑 | **컬럼 충돌** — 담당자/담당자명이 같은 표준컬럼 |

---

## TD_GD20310 (수질검사 결과) — 3자 비교

### 기본 정보

| API 필드 (PDF) | 현행 컬럼 (TD_GD20310) | 환경부표준 (TD_GD010310) | 설명 | 길이 변경 |
|---------------|----------------------|------------------------|------|----------|
| `legacyCodeNo` | `LEGACY_CODE_NO` | `BRNCH_NO` | 지점번호 | 10→10 |
| `spotStdCode` | `SPOT_STD_CODE` | `BRNCH_STD_CD` | 지점표준코드 | 15→20 |
| `yyyy` | `YYYY` | `YR` | 연도 | 4→4 |
| `period` | `PERIOD` | `QTR` | 분기 | 4→10 |
| `inspCheck` | `INSP_CHECK` | `INSP_YN` | 검사여부 | 10→20 |
| `unInspDesc` | `UN_INSP_DESC` | `UN_INSP_RSN` | 미검사사유 | 500→500 |
| `acceptYn` | `ACCEPT_YN` | `STBLT_YN` | 적합여부 | 10→10 |
| `suit` | `SUIT` | `STBLT` | 적합 | 4→10 |
| `unsuit` | `UNSUIT` | `ICPT` | 부적합 | 4→10 |
| `samp_date` | `SAMP_DATE` | `WTSMP_YMD` | 채수일자 | 10→8 ⚠️ |
| `inspRst` | `INSP_RST` | `ICPT_ARTCL` | 부적합항목 | 500→100 ⚠️ |
| `failDesc` | `FAIL_DESC` | `ICPT_ACTN_MTTR` | 부적합시 조치사항 | 500→100 ⚠️ |

### 세균류 (11개)

| API 필드 | 현행 컬럼 | 환경부표준 | 설명 |
|---------|----------|----------|------|
| `itemGenbaclow` | `ITEM_GENBACLOW` | `GNRL_GERM_LOWTMP` | 일반세균저온(CFU/mL) |
| `itemGenbacmid` | `ITEM_GENBACMID` | `GNRL_GERM_MESPH` | 일반세균중온(CFU/mL) |
| `itemTotbac` | `ITEM_TOTBAC` | `CFBCTR` | 총대장균군(mL) |
| `itemBac` | `ITEM_BAC` | `CLBCL` | 대장균(mL) |
| `itemFestr` | `ITEM_FESTR` | `FCFS` | 분원성대장균군(mL) |
| `itemBranfungus` | `ITEM_BRANFUNGUS` | `FCSTRCCI` | 분원성연쇄상구균(mL) |
| `itemGrgungus` | `ITEM_GRGUNGUS` | `PAERGNS` | 녹농균(mL) |
| `itemSalmol` | `ITEM_SALMOL` | `SLMN` | 살모넬라(mL) |
| `itemSegel` | `ITEM_SEGEL` | `SHIGLA` | 쉬겔라(mL) |
| `itemSulfungus` | `ITEM_SULFUNGUS` | `SFSRA` | 아황산환원혐기성포자형성균(mL) |
| `itemYersinia` | `ITEM_YERSINIA` | `YERSNA` | 여시니아균(mL) |

### 중금속 (11개)

| API 필드 | 현행 컬럼 | 환경부표준 | 설명 |
|---------|----------|----------|------|
| `itemPb` | `ITEM_PB` | `PMBM` | 납(mg/ℓ) |
| `itemF` | `ITEM_F` | `FLRN` | 불소(g/ℓ) |
| `itemGas` | `ITEM_GAS` | `ASNC` | 비소(mg/ℓ) |
| `itemSe` | `ITEM_SE` | `SE` | 셀레늄(mg/ℓ) |
| `itemHg` | `ITEM_HG` | `MRCR` | 수은(mg/ℓ) |
| `itemCn` | `ITEM_CN` | `CYN` | 시안(mg/ℓ) |
| `itemCr6` | `ITEM_CR6` | `CHRM` | 크롬(mg/ℓ) |
| `itemNo3am` | `ITEM_NO3AM` | `AMNG` | 암모니아성질소(mg/ℓ) |
| `itemNo3n` | `ITEM_NO3N` | `NTNG` | 질산성질소(mg/ℓ) |
| `itemCd` | `ITEM_CD` | `CDMM` | 카드뮴(mg/ℓ) |
| `itemBoron` | `ITEM_BORON` | `BOR` | 보론(mg/ℓ) |

### 유기화합물 (17개)

| API 필드 | 현행 컬럼 | 환경부표준 | 설명 |
|---------|----------|----------|------|
| `itemBro3` | `ITEM_BRO3` | `BRO3` | 브롬산염(mg/ℓ) |
| `itemPhenol` | `ITEM_PHENOL` | `PHNL` | 페놀(mg/ℓ) |
| `itemDiazn` | `ITEM_DIAZN` | `DZNN` | 다이아지논(mg/ℓ) |
| `itemParat` | `ITEM_PARAT` | `PARAT` | 파라티온(mg/ℓ) |
| `itemPenitro` | `ITEM_PENITRO` | `FENITRO` | 페니트로티온(mg/ℓ) |
| `itemCarbaryl` | `ITEM_CARBARYL` | `CARBARYL` | 카바릴(mg/ℓ) |
| `itemTcet` | `ITEM_TCET` | `TCRN` | 1.1.1-트리클로로에탄(mg/ℓ) |
| `itemTece` | `ITEM_TECE` | `TTRT` | 테트라클로로에틸렌(mg/ℓ) |
| `itemTce` | `ITEM_TCE` | `TCRT` | 트리클로로에틸렌(mg/ℓ) |
| `itemDcm` | `ITEM_DCM` | `DCMT` | 디클로로메탄(mg/ℓ) |
| `itemBenzene` | `ITEM_BENZENE` | `BNZN` | 벤젠(mg/ℓ) |
| `itemToluene` | `ITEM_TOLUENE` | `TLN` | 톨루엔(mg/ℓ) |
| `itemEtilben` | `ITEM_ETILBEN` | `ETBZ` | 에틸벤젠(mg/ℓ) |
| `itemXylene` | `ITEM_XYLENE` | `XLN` | 자일렌(mg/ℓ) |
| `itemDce` | `ITEM_DCE` | `DCTY` | 1.1디클로로에틸렌(mg/ℓ) |
| `itemCcl4` | `ITEM_CCL4` | `CCL4` | 사염화탄소(mg/ℓ) |
| `itemDbcp` | `ITEM_DBCP` | `DBCP` | 1,2-디브로모-3-클로로프로판(mg/ℓ) |

### 일반항목 (14개)

| API 필드 | 현행 컬럼 | 환경부표준 | 설명 |
|---------|----------|----------|------|
| `itemC4h8o2` | `ITEM_C4H8O2` | `DIOX14` | 1,4-다이옥산(mg/ℓ) |
| `itemGradient` | `ITEM_GRADIENT` | `TDS` | 경도(mg/ℓ) |
| `itemKmn` | `ITEM_KMN` | `PTPM_CNSM_QNT` | 과망간산칼륨소비량(mg/ℓ) |
| `itemSmell` | `ITEM_SMELL` | `SMLL` | 냄새 |
| `itemColor` | `ITEM_COLOR` | `CRMTY` | 색도(도) |
| `itemCu` | `ITEM_CU` | `CPPR` | 동(구리) |
| `itemAbs` | `ITEM_ABS` | `ABS` | 세제(음이온계면활성제)(mg/ℓ) |
| `itemPh` | `ITEM_PH` | `PH` | 수소이온농도 |
| `itemZn` | `ITEM_ZN` | `ZN` | 아연(mg/ℓ) |
| `itemCl` | `ITEM_CL` | `CRRD` | 염소이온(mg/ℓ) |
| `itemFe` | `ITEM_FE` | `IRON` | 철(mg/ℓ) |
| `itemMn` | `ITEM_MN` | `MNGN` | 망간(mg/ℓ) |
| `itemMuddy` | `ITEM_MUDDY` | `TRBT` | 탁도(NTU) |
| `itemSo42` | `ITEM_SO42` | `ECCBT_ION` | 황산이온(mg/ℓ) |
| `itemAl` | `ITEM_AL` | `ALMN` | 알루미늄(mg/ℓ) |
| `itemUran` | `ITEM_URAN` | `URAN` | 우라늄(mg/ℓ) |

---

## API에는 있으나 테이블에 없는 필드 (제원 정보)

아래 필드들은 API 응답에 포함되지만 `TD_GD20310`(수질결과)이 아닌 **제원 테이블(`TM_GD20310`)에 적재**:

| API 필드 | 설명 | 비고 |
|---------|------|------|
| `spotNm` | 지점명(약수터명) | 제원 |
| `infoCreatInsttNm` | 정보생성기관(시도) | 제원 |
| `doNm` | 시도 | 제원 |
| `ctyNm` | 시군구 | 제원 |
| `adres` | 주소 | 제원 |
| `admcode` | 법정동코드 | 제원 |
| `ablAt` | 폐지여부(Y/N) | 제원 |
| `ablDe` | 폐지일자 | 제원 |
| `dayAvg` | 1일평균이용자수 | 제원 |
| `charge` | 담당자 | 제원 |
| `insDate` | 설치일자 | 제원 |
| `office` | 담당부서명 | 제원 |
| `officeTel` | 담당자연락처 | 제원 |
| `buildingNo` | 건물번호 | 제원 |
| `locJibun` | 소재지_지번 | 제원 |
| `commt` | 비고 | 제원 |
| `delYn` | 삭제여부 | 제원 |
| `insp_date` | 검사일자 | 제원 또는 수질결과 |

> 제원 테이블 표준화 매핑 파일(`TM_GD20310.txt`)은 미확보 — 필요 시 확인 요청

---

## 주의: 표준화 시 길이 축소 항목

| 현행 컬럼 | 현행 길이 | 표준화 길이 | 위험 |
|----------|----------|-----------|------|
| `SAMP_DATE` | 10 | 8 | 날짜 포맷 변경 필요 (`2012-04-20` → `20120420`?) |
| `INSP_RST` | 500 | 100 | 부적합항목 텍스트 잘릴 수 있음 |
| `FAIL_DESC` | 500 | 100 | 조치사항 텍스트 잘릴 수 있음 |

---

## 결론

1. **API 필드 ↔ 현행 테이블**: 완전 1:1 일치 (camelCase ↔ UPPER_SNAKE)
2. **현행 → 표준화**: 컬럼명 전부 변경, 일부 길이 축소
3. **표준화 테이블에 적재하려면**: API 응답 → 현행 컬럼명 매핑 → 환경부표준 컬럼명 변환 (이 파일이 매핑 사전 역할)
4. **제원 테이블(`TM_GD20310` → `TM_GD01502`?) 매핑 파일도 필요**
