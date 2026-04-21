# 레거시 제공 API — 재분류 + 실제 제공 컬럼 정의

> 작성일: 2026-04-21
> 소스: D:/dev/claude/copySource/v3/
> 분석 대상: sql_megokrapi.xml, sql_drought119api.xml, sql_opn.xml

---

## 재분류 결과 요약

인증/로그용 제외, **데이터 API만** 정리.

### Type A: 단순 복사 (7건)

단일 테이블 SELECT, Oracle 함수/JOIN 없음. NVL→COALESCE, ||→|| 등 PG 호환 가능한 수준.

| # | SQL ID | API명 | 소스 테이블 | 제공 컬럼 수 |
|---|--------|-------|-----------|:---:|
| A1 | selectNgw08 | 공공관정 가뭄지원 | TM_GD00203 | 11 |
| A2 | selectNgw09 | 공공관정 상세 | WT_DREAM_PERMWELL_PUBLIC | 34 |
| A3 | selectNgw09_01 | 공공관정 상세 (목록) | WT_DREAM_PERMWELL_PUBLIC | 34 |
| A4 | selectdroght119 | 가뭄119 인허가관정 | SDE_NGWS.WT_DREAM_PERMWELL_PUBLIC_21033 | 33 |
| A5 | info_general | 관측망 상세 | TM_GD10001 | 5 |
| A6 | info_yhjs_info | 영향조사 상세 | TM_GD50001 | 7 |
| A7 | selectNgw04_01 | 수질검사결과 (TMP) | TMP_MEGOKR_API | 127 |

> A2/A3은 같은 테이블, A7은 이미 전처리된 임시 테이블

### Type B: 전처리 필요 (18건)

JOIN, 사용자정의함수, PIVOT, DBLINK, 동적SQL 등.

| # | SQL ID | API명 | 소스 테이블 | 난이도 | 복잡 요소 |
|---|--------|-------|-----------|:---:|------|
| B1 | selectNgw03 | 수질검사 개요 | TM_GD30301, TM_GD10001 | 중 | 2중 서브쿼리 |
| B2 | selectNgw03_01 | 수질검사 개요 (TMP) | TMP_MEGOKR_API | 저 | DECODE→CASE 변환 |
| B3 | selectNgw04 | 수질검사 결과 | TM_GD30302, 30301, 10001 | 상 | PIVOT 125컬럼 |
| B4 | info_permwell | 인허가관정 상세 | RGETNPMMS01, TC_GD00100 | 중 | JOIN + FN_GD_GET_GUBUN 함수 |
| B5 | info_general_105 | 보조관측망 상세 | TM_GD10001 외 5개 | 상 | 5테이블 LEFT JOIN |
| B6 | info_general_211215 | 수질측정망 상세 | VIEW_GTEST | 중 | Oracle 뷰 의존 |
| B7 | info_observation_station1 | 수위관측소 | DBLINKUSR.* 4개 | 최상 | DBLINK + 실시간 시간매칭 |
| B8 | info_observation_station0 | 우량관측소 | DUBRFOBSIF 외 2개 | 최상 | DBLINK + 실시간 시간매칭 |
| B9 | linkage_analy_chart | 관측그래프 | PM_GD60201, 60101, 10001 | 상 | CTE+UNION+PIVOT+스칼라 |
| B10 | observationStationTime | 관측소 시간서비스 | PM_GD60201, 60101, 10001 | 상 | PIVOT+스칼라 |
| B11 | waterQualityInfo | 수질정보 (범용) | 10001, 30301, 30302, TC00002 | 최상 | 동적PIVOT+3JOIN+스칼라 |
| B12 | waterQualityInfoDJ | 수질정보 (대전) | 10001, 30301, 30302 | 최상 | 동적PIVOT+3JOIN |
| B13 | waterQualityMfdsInfo | 식약처 수질 | 70201, 70202, 20910 | 상 | 동적PIVOT+2JOIN+스칼라 |
| B14 | searchInspection | 검사항목 조회 | TM_GD30310, TC_GD00002 | 저 | LEFT JOIN (공통코드) |
| B15 | searchAllInspection | 전체 검사항목 | TM_GD30310, TC_GD00002 | 저 | LEFT JOIN (공통코드) |
| B16 | actualUseDetailDJ | 대전 이용실태 | TC00100, 20930, RGETNTGMS02 | 중 | CTE 2개 + 3JOIN |
| B17 | unRegitsFclySmrize | 대전 미등록시설 | TM_GD00301 | 중 | 스칼라서브쿼리 8개+UNION |
| B18 | gnlwtqltinfo_inputsittn | 대전 수질입력현황 | 10001, 30301, 30302 | 상 | UNION+3중서브+3JOIN |

### 기존 분류와 달라진 것

| API | 기존 | 실제 | 이유 |
|-----|:---:|:---:|------|
| info_permwell (RGETNPMMS01) | A | **B** | JOIN + Oracle 사용자정의함수 |
| selectNgw03 (TM_GD30301) | A | **B** | 2중 서브쿼리 (TM_GD10001) |
| searchInspection (TM_GD30310) | A | **B** | LEFT JOIN (TC_GD00002) |
| info_general_211215 (VIEW_GTEST) | A | **B** | Oracle 뷰 의존 |

---

## Type A — 실제 제공 컬럼 정의

### A1. selectNgw08 — 공공관정 가뭄지원 (TM_GD00203)

```sql
SELECT BRTC_NM, SIGUN_NM, EMD_NM, LI_NM,
       POPLTN_VALUE, LPCD_CTNT, DMAND_QUA_VALUE,
       SUPLY_PSBLQY_VALUE, NSTT_VALUE,
       TOT_PUBWELL_CO, USE_PUBWELL_CO
FROM TM_GD00203
WHERE BRTC_NM = ?
```

| # | 소스 컬럼 | 타입 | 설명 |
|---|----------|------|------|
| 1 | BRTC_NM | VARCHAR(40) | 시도명 |
| 2 | SIGUN_NM | VARCHAR(40) | 시군구명 |
| 3 | EMD_NM | VARCHAR(30) | 읍면동명 |
| 4 | LI_NM | VARCHAR(40) | 리명 |
| 5 | POPLTN_VALUE | NUMBER | 인구수 |
| 6 | LPCD_CTNT | VARCHAR(100) | 지역특성내용 |
| 7 | DMAND_QUA_VALUE | NUMBER | 수요량값 |
| 8 | SUPLY_PSBLQY_VALUE | NUMBER | 공급가능량값 |
| 9 | NSTT_VALUE | NUMBER | 부족량값 |
| 10 | TOT_PUBWELL_CO | NUMBER | 총공공관정수 |
| 11 | USE_PUBWELL_CO | NUMBER | 가용공공관정수 |

PG 제공 테이블: `api_prv_tm_gd00203`
merge-key: 복합 (BRTC_NM + SIGUN_NM + EMD_NM + LI_NM) 또는 별도 PK 확인 필요

---

### A2/A3. selectNgw09 / selectNgw09_01 — 공공관정 상세 (WT_DREAM_PERMWELL_PUBLIC)

```sql
SELECT REL_TRANS_, PERM_NT_NO, PERM_NT_FO, YY_GBN, REGNCODE,
       BRTC_NM, SIGUN_NM, EMD_NM, LI_NM, SAN, BUNJI, HO,
       UWATER_SRV, UWATER_DTL, UWATER_POT,
       LTTD_DG, LTTD_MINT, LTTD_SC, LITD_DG, LITD_MINT, LITD_SC,
       DPH, DIG_DIAM, PIPE_DIAM, PUMP_HRP, FRW_PLN_QU, RWT_CAP,
       Y_USE_QUA, PUB_PRI_GB, QW_ISP_YMD, QW_ISP_RT,
       PNU, TMX_VALUE, TMY_VALUE
FROM WT_DREAM_PERMWELL_PUBLIC
```

| # | 소스 컬럼 | 설명 |
|---|----------|------|
| 1 | REL_TRANS_ | 관할행정구역코드 |
| 2 | PERM_NT_NO | 허가번호 |
| 3 | PERM_NT_FO | 허가형태 |
| 4 | YY_GBN | 연도구분 |
| 5 | REGNCODE | 지역코드 |
| 6 | BRTC_NM | 시도명 |
| 7 | SIGUN_NM | 시군구명 |
| 8 | EMD_NM | 읍면동명 |
| 9 | LI_NM | 리명 |
| 10 | SAN | 산여부 |
| 11 | BUNJI | 번지 |
| 12 | HO | 호 |
| 13 | UWATER_SRV | 지하수용도 |
| 14 | UWATER_DTL | 상세용도 |
| 15 | UWATER_POT | 음용여부 |
| 16 | LTTD_DG | 위도(도) |
| 17 | LTTD_MINT | 위도(분) |
| 18 | LTTD_SC | 위도(초) |
| 19 | LITD_DG | 경도(도) |
| 20 | LITD_MINT | 경도(분) |
| 21 | LITD_SC | 경도(초) |
| 22 | DPH | 심도 |
| 23 | DIG_DIAM | 굴착구경 |
| 24 | PIPE_DIAM | 관경 |
| 25 | PUMP_HRP | 펌프마력 |
| 26 | FRW_PLN_QU | 계획양수량 |
| 27 | RWT_CAP | 양수능력 |
| 28 | Y_USE_QUA | 연사용량 |
| 29 | PUB_PRI_GB | 공사구분 |
| 30 | QW_ISP_YMD | 수질검사일 |
| 31 | QW_ISP_RT | 수질검사결과 |
| 32 | PNU | 필지고유번호 |
| 33 | TMX_VALUE | TM X좌표 |
| 34 | TMY_VALUE | TM Y좌표 |

PG 제공 테이블: `api_prv_wt_dream_permwell_public`
merge-key: PERM_NT_NO (또는 복합 PK 확인 필요)

---

### A4. selectdroght119 — 가뭄119 (SDE_NGWS.WT_DREAM_PERMWELL_PUBLIC_21033)

```sql
SELECT OBJECTID, PERM_NT_NO, PERM_NT_FO, YY_GBN, REGNCODE,
       BRTC_NM, SIGUN_NM, EMD_NM, LI_NM, SAN, BUNJI, HO,
       UWATER_SRV, UWATER_DTL, UWATER_POT,
       LTTD_DG, LTTD_MINT, LTTD_SC, LITD_DG, LITD_MINT, LITD_SC,
       DPH, DIG_DIAM, PIPE_DIAM, PUMP_HRP, FRW_PLN_QU, RWT_CAP,
       Y_USE_QUA, PUB_PRI_GB, QW_ISP_YMD, QW_ISP_RT,
       TMX_VALUE, TMY_VALUE
FROM SDE_NGWS.WT_DREAM_PERMWELL_PUBLIC_21033
```

A2와 거의 동일 + OBJECTID 추가.

PG 제공 테이블: `api_prv_wt_dream_permwell_public_21033`
merge-key: OBJECTID

---

### A5. info_general — 관측망 상세 (TM_GD10001)

```sql
SELECT W.JOSACODE,
       W.SPOT_NM AS JIGUNAME,
       W.BRTC_NM || ' ' || W.SIGUN_NM || ' ' || W.EMD_NM || ' ' || W.LI_NM || ' ' || W.ADDR AS ADDR,
       NVL(W.SDTA_NM, '2011;') AS SOURDATA,
       NVL(W.SDTA_INSTT_NM, '2011;') AS SOUR_GOV
FROM TM_GD10001 W
WHERE W.GENNUM = ? AND W.JOSACODE = ?
```

| # | 제공 컬럼 (alias) | 소스 컬럼 | 설명 | 비고 |
|---|------------------|----------|------|------|
| 1 | JOSACODE | JOSACODE | 조사코드 | |
| 2 | JIGUNAME | SPOT_NM | 지구명 | alias |
| 3 | ADDR | BRTC_NM+SIGUN_NM+EMD_NM+LI_NM+ADDR | 주소 | 문자열 결합 |
| 4 | SOURDATA | SDTA_NM | 자료출처 | NVL→COALESCE |
| 5 | SOUR_GOV | SDTA_INSTT_NM | 자료제공기관 | NVL→COALESCE |

> 문자열 결합 + NVL은 PG에서도 동일 처리 가능 (||, COALESCE)
> 다만 **제공 테이블에는 결합된 ADDR 1개로 넣을지, 원본 5개 컬럼 그대로 넣을지** 결정 필요

PG 제공 테이블: `api_prv_tm_gd10001`
merge-key: GENNUM

> 주의: TM_GD10001은 Type B(info_general_105 등)에서도 소스로 사용됨.
> Type A용 제공 테이블은 이 API가 실제 노출하는 5개 컬럼만 포함.

---

### A6. info_yhjs_info — 영향조사 상세 (TM_GD50001)

```sql
SELECT YH_SNO AS ISVR_NO,
       RPT_TITLE AS ISVR_NM,
       SDTA_INSTT_NM AS SOUR_GOV,
       DTA_STDR_YEAR AS PRESSYEAR,
       ISU_MT AS PRESSMONTH,
       ISVR_CCD AS GUBUN,
       ET_SN AS EXTEN_NUM
FROM TM_GD50001
WHERE YH_SNO = ?
```

| # | 제공 컬럼 (alias) | 소스 컬럼 | 설명 |
|---|------------------|----------|------|
| 1 | ISVR_NO | YH_SNO | 영향조사번호 |
| 2 | ISVR_NM | RPT_TITLE | 보고서제목 |
| 3 | SOUR_GOV | SDTA_INSTT_NM | 자료제공기관 |
| 4 | PRESSYEAR | DTA_STDR_YEAR | 기준연도 |
| 5 | PRESSMONTH | ISU_MT | 발행월 |
| 6 | GUBUN | ISVR_CCD | 구분코드 |
| 7 | EXTEN_NUM | ET_SN | 확장일련번호 |

PG 제공 테이블: `api_prv_tm_gd50001`
merge-key: YH_SNO

---

### A7. selectNgw04_01 — 수질검사 결과 TMP (TMP_MEGOKR_API)

이미 PIVOT 완료된 임시 테이블. 127개 컬럼 (SN + JOSACODE + GENNUM + INVSTG_YEAR + ODR + 125개 수질항목 WT_*).

> 이 테이블 자체가 존재하는지, 누가 만드는지 불명확.
> TMP_MEGOKR_API가 실서버에 없으면 이 API는 사용 불가.
> **우선순위 낮음 — 확인 후 결정**

PG 제공 테이블: `api_prv_tmp_megokr_api` (확정 보류)
merge-key: SN

---

## Type B — 실제 제공 컬럼 정의

### B1. selectNgw03 — 수질검사 개요 (TM_GD30301 + TM_GD10001)

```sql
SELECT QLTWTR_INSPCT_SN, GENNUM, INVSTG_YEAR, ODR,
       DPH_CL_CODE, DPH_VALUE, WATSMP_DE, QLTWTR_INSPCT_DE,
       DTA_INPUT_DE, DCSN_DE, FRST_REGIST_DT, LAST_CHANGE_DT,
       UGRWTR_PRPOS_CODE, DRNK_AT,
       UGRWTR_WQN_INPUT_INSTT_CODE, QLTWTR_INSPCT_IMPRTY_RESN_CTNT,
       BRTC_NM, SIGUN_NM, EMD_NM, LI_NM, ADDR, PUBWELL_AT
FROM TM_GD30301
WHERE QLTWTR_INSPCT_SN IN (
  SELECT ... FROM TM_GD30301
  WHERE GENNUM IN (SELECT b.GENNUM FROM TM_GD10001 b WHERE JOSACODE IN ('104','112','113','215','216'))
)
```

| 제공 컬럼 수 | 22개 |
| 복잡 요소 | 2중 서브쿼리 (TM_GD10001 JOSACODE 필터) |
| 전처리 전략 | 서브쿼리를 풀어서 조건부 전체 적재 → Provider에서 필터 |

PG 정제 테이블: `api_prv_ngw03`
merge-key: QLTWTR_INSPCT_SN

---

### B3. selectNgw04 — 수질검사 결과 PIVOT (TM_GD30302)

```sql
SELECT * FROM TM_GD30302
PIVOT(MAX(RESULT_VALUE) FOR WLTTS_ID_CODE IN ('0001' AS WT_TOT_COL_CNTS, ... '0125' AS WT_WTL))
```

| 제공 컬럼 수 | 1 (PK) + 125 (수질항목) = 126개 |
| 복잡 요소 | Oracle PIVOT 125컬럼 |
| 전처리 전략 | Oracle에서 PIVOT 실행 → flat 결과를 PG에 UPSERT |

PG 정제 테이블: `api_prv_ngw04`
merge-key: QLTWTR_INSPCT_SN

---

### B4. info_permwell — 인허가관정 (RGETNPMMS01 + TC_GD00100)

```sql
SELECT FN_GD_GET_GUBUN(A.PERM_NT_FORM_CODE, 1) PERM_NT_FORM_CODE,
       B.BRTC_NM || ' ' || B.SIGUN_NM || ... ADDR,
       FN_GD_GET_CMMTNDCODE('NGW_0003', '0'||A.UWATER_SRV_CODE) UWATER_SRV_CODE,
       FN_GD_GET_CMMTNDCODE('NGW_0013', A.UWATER_DTL_SRV_CODE) UWATER_DTL_SRV_CODE,
       A.UWATER_POTA_YN, A.DIG_DPH, A.DIG_DIAM, A.ESB_DPH,
       A.ND_QT, A.FRW_PLN_QUA, A.RWT_CAP, A.DYN_EQN_HRP, A.PIPE_DIAM
FROM RGETNPMMS01 A LEFT OUTER JOIN TC_GD00100 B ON ...
```

| # | 제공 컬럼 | 설명 | 비고 |
|---|----------|------|------|
| 1 | PERM_NT_FORM_CODE | 허가형태명 | FN_GD_GET_GUBUN 함수 → 코드→명칭 변환 |
| 2 | ADDR | 주소 | TC_GD00100 JOIN + 문자열 결합 |
| 3 | UWATER_SRV_CODE | 용도명 | FN_GD_GET_CMMTNDCODE 함수 |
| 4 | UWATER_DTL_SRV_CODE | 상세용도명 | FN_GD_GET_CMMTNDCODE 함수 |
| 5 | UWATER_POTA_YN | 음용여부 | |
| 6 | DIG_DPH | 굴착심도 | DECODE null처리 |
| 7 | DIG_DIAM | 굴착구경 | DECODE null처리 |
| 8 | ESB_DPH | 양수기심도 | DECODE null처리 |
| 9 | ND_QT | 수량 | DECODE null처리 |
| 10 | FRW_PLN_QUA | 계획양수량 | DECODE null처리 |
| 11 | RWT_CAP | 양수능력 | DECODE null처리 |
| 12 | DYN_EQN_HRP | 동수위마력 | NVL 0처리 |
| 13 | PIPE_DIAM | 관경 | DECODE null처리 |

| 복잡 요소 | JOIN + 사용자정의함수 2종 |
| 전처리 전략 | Oracle에서 함수 포함 SQL 실행 → 결과(명칭 변환 완료)를 PG에 적재 |

PG 정제 테이블: `api_prv_permwell`
merge-key: (REL_TRANS_CGG_CODE + PERM_NT_NO) 또는 별도 PK

---

### B5. info_general_105 — 보조관측망 상세 (5테이블 JOIN)

```sql
SELECT GENNUM, OBSV_NAME AS JIGUNAME, OBSV_CODE, INPERM_NO,
       SIDO||' '||SIGUNGU||' '||UPMYUNDO||NVL(RI,'') AS ADDR,
       PYOGO, MGR_ORG AS SOUR_GOV, INSDATE, OBSV_TYPE, WELL,
       CASING_HEIGHT, GULDEP, GULDIA, GIGWANMETHOD, GIGWANITEM,
       YONGDO_CD AS GROUNDUSE, UMYONG AS UWATER_POTA_YN
FROM (TM_GD10001 LEFT JOIN TM_GD60101 LEFT JOIN TM_GD60130
      LEFT JOIN TM_GD60001 LEFT JOIN TM_GD60002 LEFT JOIN TM_GD70002)
```

| 제공 컬럼 수 | 17개 |
| 복잡 요소 | 5테이블 LEFT JOIN |
| 전처리 전략 | Oracle에서 JOIN SQL 실행 → flat 결과를 PG에 적재 |

PG 정제 테이블: `api_prv_general_105`
merge-key: GENNUM

---

### B6. info_general_211215 — 수질측정망 (VIEW_GTEST) ⚠️ 보류

VIEW_GTEST는 GIMS 내부 Oracle 뷰. 뷰 정의는 파악되어 있으나, 운영 DB 뷰 수정 가능 여부가 불확실.
LINK_STATUS 추가 또는 원본 테이블 직접 조회 등 방법을 담당자 협의 후 결정.

---

### B7. observation_station1 — 수위관측소 (DBLINK) ⚠️ 보류

DBLINKUSR 경유 외부 DB 테이블 4개:
1. DBLINKUSR.DUBWLOBSIF (수위관측소 기본)
2. DBLINKUSR.DUBMMWL (수위 측정값)
3. DBLINKUSR.V_WP_WKSDAMSBSN (소유역)
4. DBLINKUSR.V_WR_HACHEON_MST (하천 마스터)

실서버에 DB Link가 존재하는지, 표준화자료/실DB에서 확인 안 됨. 담당자 확인 후 결정.

### B8. observation_station0 — 우량관측소 (DBLINK) ⚠️ 보류

DBLINK 경유 외부 테이블 3개:
1. DUBRFOBSIF (우량관측소 기본)
2. DUBMMRF (강우량 측정값)
3. DBLINKUSR.V_WP_WKSDAMSBSN (소유역, B7과 공유)

B7과 동일 사유로 보류.

---

### B9/B10. 관측그래프 + 시간서비스 (PM_GD60201 PIVOT)

```sql
WITH TB AS (
  SELECT GENNUM, YMD, "5" AS ELEV, "163" AS WTEMP,
         ROUND((SELECT AL_VALUE FROM TM_GD10001 WHERE GENNUM=?) - "5", 2) AS LEV
  FROM (... PIVOT (MAX(OBSR_DTA_VALUE) FOR OBSR_IEM_ID IN (5, 163, 52, 333)) ...)
  UNION ...
)
```

| 제공 컬럼 수 | 6개 (GENNUM, YMD, ELEV, WTEMP, LEV, EC) |
| 복잡 요소 | CTE + UNION + PIVOT + 스칼라서브쿼리 + GENNUM 파라미터 의존 |
| 전처리 전략 | 관측소 목록 먼저 조회 → 관측소별 반복 실행 → PG 적재 |

PG 정제 테이블: `api_prv_linkage_chart`
merge-key: GENNUM + YMD (복합)

---

### B11/B12. 수질정보 (동적 PIVOT)

```sql
SELECT T1.GENNUM, T1.SPOT_NM, ...,
       MAX(CASE WHEN WLTTS_ID_CODE = '0001' THEN RESULT_VALUE END) AS C0001,
       ... (iBatis iterate 동적 생성)
FROM TM_GD10001 T1
  INNER JOIN TM_GD30301 T3 ON GENNUM
  LEFT JOIN TM_GD30302 T32 ON QLTWTR_INSPCT_SN
GROUP BY ...
```

| 복잡 요소 | 동적 PIVOT + 3JOIN + 스칼라서브쿼리 |
| 전처리 전략 | 항목 코드 목록 고정 → CASE WHEN 하드코딩 → Oracle 실행 → PG 적재 |

PG 정제 테이블: `api_prv_water_quality`
merge-key: GENNUM + QLTWTR_INSPCT_SN (복합)

---

### B13. 식약처 수질 (TM_GD70201 + 70202)

동적 PIVOT + 2JOIN + 스칼라서브쿼리. B11/B12와 유사 패턴.

PG 정제 테이블: `api_prv_water_quality_mfds`
merge-key: QLTWTR_INSPCT_SN

---

### B14/B15. 검사항목 (TM_GD30310 + TC_GD00002)

```sql
SELECT A.JOSACODE, A.DTA_STDR_YEAR, A.QLTWTR_INSPCT_IEM_CODE,
       B.CODE_CTNT AS remarkCtnt
FROM TM_GD30310 A
LEFT JOIN TC_GD00002 B ON B.CODE_ID = A.QLTWTR_INSPCT_IEM_CODE AND B.CODE_GRP_ID = 'NGW_0026'
```

| 제공 컬럼 수 | 4개 |
| 복잡 요소 | LEFT JOIN (공통코드 테이블) — 난이도 낮음 |
| 전처리 전략 | Oracle에서 JOIN 실행 → PG 적재 |

PG 정제 테이블: `api_prv_inspection`
merge-key: JOSACODE + DTA_STDR_YEAR + QLTWTR_INSPCT_IEM_CODE (복합)

---

### B16. 대전 이용실태 (TC_GD00100 + TM_GD20930 + RGETNTGMS02)

CTE 2개 + 3JOIN + DECODE + 윈도우함수.

PG 정제 테이블: `api_prv_actual_use_dj`

---

### B17. 대전 미등록시설 (TM_GD00301)

스칼라서브쿼리 8개 + UNION ALL. 단일 테이블이지만 집계 로직 복잡.

PG 정제 테이블: `api_prv_unregits_fcly`

---

### B18. 대전 수질입력현황 (TM_GD10001 + 30301 + 30302)

UNION ALL + 3중 서브쿼리 + 3JOIN.

PG 정제 테이블: `api_prv_wq_input_status_dj`

---

## PG 제공 테이블 전체 목록 (확정)

### Type A (7개 — 소스 컬럼 그대로)

| PG 테이블 | 소스 테이블 | merge-key |
|-----------|-----------|-----------|
| api_prv_tm_gd00203 | TM_GD00203 | 복합 확인 필요 |
| api_prv_wt_dream_permwell_public | WT_DREAM_PERMWELL_PUBLIC | PERM_NT_NO |
| api_prv_wt_dream_permwell_public_21033 | SDE_NGWS.WT_DREAM_PERMWELL_PUBLIC_21033 | OBJECTID |
| api_prv_tm_gd10001 | TM_GD10001 | GENNUM |
| api_prv_tm_gd50001 | TM_GD50001 | YH_SNO |
| api_prv_tmp_megokr_api | TMP_MEGOKR_API | SN (확정 보류) |

> A2/A3은 같은 테이블 → 제공 테이블 1개

### Type B (최대 14개 — 전처리 결과 flat 컬럼)

| PG 테이블 | 전처리 소스 | merge-key |
|-----------|-----------|-----------|
| api_prv_ngw03 | TM_GD30301 + 10001 | QLTWTR_INSPCT_SN |
| api_prv_ngw04 | TM_GD30302 PIVOT | QLTWTR_INSPCT_SN |
| api_prv_permwell | RGETNPMMS01 + TC_GD00100 | 복합 확인 필요 |
| api_prv_general_105 | TM_GD10001 외 5개 | GENNUM |
| api_prv_view_gtest | VIEW_GTEST | GENNUM |
| api_prv_observation | DBLINKUSR.* | WLOBSCD |
| api_prv_rainfall | DUBRFOBSIF 외 | RFOBSCD |
| api_prv_linkage_chart | PM_GD60201 외 | GENNUM+YMD |
| api_prv_water_quality | TM_GD10001+30301+30302 | GENNUM+QLTWTR_INSPCT_SN |
| api_prv_water_quality_mfds | TM_GD70201+70202 | QLTWTR_INSPCT_SN |
| api_prv_inspection | TM_GD30310+TC_GD00002 | 복합 |
| api_prv_actual_use_dj | TC00100+20930+RGETNTGMS02 | 확인 필요 |
| api_prv_unregits_fcly | TM_GD00301 | 확인 필요 |
| api_prv_wq_input_status_dj | 10001+30301+30302 | 확인 필요 |
