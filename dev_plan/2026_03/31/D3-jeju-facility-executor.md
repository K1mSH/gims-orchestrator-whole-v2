# D3. JejuFacilityExecutor 구현 계획서

## 개요
제주도 이용시설(지하수 이용 허가/신고) 데이터를 API에서 수집하여 DMZ DB에 적재하는 커스텀 실행기.
레거시: `RgetstgmsProgram.java` (메인, 자동) + `yearProgram.java` (수동 보정) → 신규: `JejuFacilityExecutor.java`

> 서비스 매핑 문서: `docs/useIncludeJeju/service-name-mapping.md` D3 항목
> SQL 매핑 XML: `D:\dev\claude\copySource\test\internal\in_use\source.xml`

## 레거시 소스 분석

### 두 프로그램의 관계

| 프로그램 | 용도 | reg_year | INSERT 대상 |
|---------|------|----------|------------|
| **RgetstgmsProgram** (메인) | 자동 실행 (작년 데이터) | `올해 - 1` 고정 | **2개**: `insetRgetnpmms01` + `insetRgetstgms01` |
| yearProgram (보조) | 수동 실행 (연도 입력) | 사용자 입력 | **1개**: `insetRgetstgms01`만 |

→ **RgetstgmsProgram이 기준**, yearProgram은 executionParams(`reg_year`)로 통합

### ⚠️ 레거시 SQL은 INSERT가 아닌 MERGE

XML 매핑(source.xml) 확인 결과, **두 SQL 모두 Oracle MERGE문** (UPSERT):

```sql
-- insetRgetnpmms01
MERGE INTO RGETNPMMS01 USING dual
ON (PERM_NT_NO = #perm_nt_no#)
WHEN MATCHED THEN UPDATE SET ...
WHEN NOT MATCHED THEN INSERT ...

-- insetRgetstgms01
MERGE INTO RGETSTGMS01 USING dual
ON (PERM_NT_NO = #perm_nt_no#)
WHEN MATCHED THEN UPDATE SET ...
WHEN NOT MATCHED THEN INSERT ...
```

→ **PK 확정: `PERM_NT_NO` (허가신고번호)** — pmms, stgms 동일
→ 신규도 UPSERT (ON CONFLICT perm_nt_no) 구현 — 레거시와 동일 동작

### API 사양
- URL: `http://water.jeju.go.kr/obsvsystem/rest/selectJejuUse.json`
- Method: POST
- 파라미터: `reg_year` (연도), `page` (페이지 번호)
- **페이징**: 첫 호출로 `totalCount` 확인 → `Math.ceil(totalCount / 1000) + 1`회 반복
- 응답: `{ "totalCount": 5000, "data": [ {...}, ... ] }`

### 데이터 처리 로직

#### 1. 필드 매핑 (properties → XML에서 확정)

XML 바인딩 변수에서 API→DB 매핑을 추출. properties `field_1→field_2`에 해당하는 컬럼들:

**RGETNPMMS01 (허가신고정보) — 33개 컬럼:**
| DB 컬럼 | 바인딩 변수 | 원본/변환 |
|---------|-----------|----------|
| PERM_NT_NO (PK) | perm_nt_no | field 매핑 |
| REL_TRANS_CGG_CODE | rel_trans_cgg_code | 코드변환 (countynm) |
| SF_TEAM_CODE | rel_trans_cgg_code | = REL_TRANS_CGG_CODE 동일값 |
| PERM_NT_FORM_CODE | perm_nt_form_code | 코드변환 (wellType) |
| APLR_GBN_CODE | aplr_gbn_code | 코드변환 (wellPublic) |
| PERM_YN | perm_yn | field 매핑 |
| LNHO_RAISE_YN | lnho_raise_yn | 상태변환 (wellStatusCode) |
| END_NT_YN | end_nt_yn | 상태변환 (wellStatusCode) |
| PERM_CANCEL_YN | perm_cancel_yn | 상태변환 (wellStatusCode) |
| JGONG_DEAL_YN | 고정값 '1' | 하드코딩 |
| FIRST_REG_DTHR | first_reg_dthr | 날짜변환 (TO_DATE, YYYY/MM/DD HH24:MI:SS) |
| DVOP_LOC_REGN_CODE | dvop_loc_regn_code | field 매핑 |
| DVOP_LOC_SAN | dvop_loc_san | field 매핑 |
| DVOP_LOC_BUNJI | dvop_loc_bunji | field 매핑 |
| DVOP_LOC_HO | dvop_loc_ho | field 매핑 |
| ORG_SNO | 고정값 '16' | 하드코딩 |
| PERM_NT_YMD | perm_nt_ymd | 날짜변환 (하이픈 제거) |
| UWATER_SRV | uwater_srv | 코드변환 (wellSrvCode) |
| UWATER_SRV_CODE | uwater_srv_code | 코드변환 (wellSrvCode) |
| UWATER_POTA_YN | uwater_pota_yn | 코드변환 (wellDrinkYn) |
| DIG_DPH | dig_dph | field 매핑 |
| DIG_DIAM | dig_diam | field 매핑 |
| LITD_DG | litd_dg | 좌표변환 |
| LITD_MINT | litd_mint | 좌표변환 |
| LITD_SC | litd_sc | 좌표변환 |
| LTTD_DG | lttd_dg | 좌표변환 |
| LTTD_MINT | lttd_mint | 좌표변환 |
| LTTD_SC | lttd_sc | 좌표변환 |
| FRW_PLN_QUA | frw_pln_qua | field 매핑 |
| DYN_EQN_HRP | dyn_eqn_hrp | field 매핑 |
| PIPE_DIAM | pipe_diam | field 매핑 |
| RWT_CAP | rwt_cap | field 매핑 |
| UWATER_DTL_SRV_CODE | uwater_dtl_srv_code | 복합 코드변환 |
| LAST_MOD_DTHR | first_reg_dthr | = FIRST_REG_DTHR 동일값 |

**RGETSTGMS01 (이용실태정보) — 32개 컬럼:**
| DB 컬럼 | 바인딩 변수 | pmms와 차이 |
|---------|-----------|------------|
| PERM_NT_NO (PK) | perm_nt_no | 동일 |
| REL_TRANS_CGG_CODE | rel_trans_cgg_code | 동일 |
| **YY_GBN** | yy_gbn | **pmms에 없음** |
| SF_TEAM_CODE | rel_trans_cgg_code | 동일 |
| PERM_NT_FORM_CODE | perm_nt_form_code | 동일 |
| **REGN_CODE** | dvop_loc_regn_code | pmms는 DVOP_LOC_REGN_CODE |
| **SAN** | dvop_loc_san | pmms는 DVOP_LOC_SAN |
| **BUNJI** | dvop_loc_bunji | pmms는 DVOP_LOC_BUNJI |
| **HO** | dvop_loc_ho | pmms는 DVOP_LOC_HO |
| LITD_DG~LTTD_SC (6개) | | 동일 |
| **ELEV** | elev | **pmms에 없음** |
| UWATER_SRV_CODE | uwater_srv_code | 동일 |
| **PUB_PRI_GBN** | perm_nt_form_code | **pmms에 없음** (= PERM_NT_FORM_CODE 값) |
| **POTA_YN** | uwater_pota_yn | pmms는 UWATER_POTA_YN |
| **Y_USE_QUA** | y_use_qua | **pmms에 없음** |
| **UWATER_SOUC_CODE** | uwater_souc_code (source.xml) / 고정 '1' (target.xml) | **pmms에 없음** |
| **DPH** | dig_dph | pmms는 DIG_DPH |
| DIG_DIAM | dig_diam | 동일 |
| **PUMP_HRP** | dyn_eqn_hrp | pmms는 DYN_EQN_HRP |
| RWT_CAP | rwt_cap | 동일 |
| PIPE_DIAM | pipe_diam | 동일 |
| **NAT_WTLV** | nat_wtlv | **pmms에 없음** |
| **STB_WTLV** | stb_wtlv | **pmms에 없음** |
| FRW_PLN_QUA | frw_pln_qua | 동일 |
| FIRST_REG_DTHR | first_reg_dthr | 동일 |
| LAST_MOD_DTHR | first_reg_dthr | 동일 |
| UWATER_DTL_SRV_CODE | uwater_dtl_srv_code | 동일 |

> **핵심**: 바인딩 변수명 = properties `field_2` 값. 대응하는 `field_1` (API 필드명)은 properties 미확보이므로 Mock 데이터에서 자연스러운 camelCase 네이밍으로 재구성

#### 2. 좌표변환 (EPSG:5186 → EPSG:4326 + 도분초 분리)
- `wellX, wellY` → 변환 후 **도(dg)/분(mint)/초(sc)** 로 분리
- 레거시 방식: 소수점 기준 **단순 문자열 자르기** (레거시 그대로 재현)
  ```
  coorX = "126.53214..."
  litd_dg  = "126"   (소수점 앞 전부)
  litd_mint = "53"   (소수점 뒤 1~2자리)
  litd_sc  = "21"    (소수점 뒤 3~4자리)
  ```

#### 3. 코드 변환 (8종)

| # | API 필드 | 변환 로직 | DB 컬럼 |
|---|----------|----------|---------|
| 1 | `wellDrinkYn` | "": "" / substring(1)=="0": "1" / else: "0" | uwater_pota_yn |
| 2 | `wellSrvCode` (substring(1)) | "1": 생활용 / "2": 공업용 / "3": 농어업용 / else: 기타("4") | uwater_srv + uwater_srv_code |
| 3 | `wellDtlsrv_code` | wellSrvCode별 복합 분기 (아래 상세) | uwater_dtl_srv_code |
| 4 | `countynm` | "제주시": "6510000" / else: "6520000" | rel_trans_cgg_code |
| 5 | `wellType` | permit: "1" / report: "2" / else: "0" | perm_nt_form_code |
| 6 | `wellPublic` | pub: "05" / else: "01" | aplr_gbn_code |
| 7 | `wellStatusCode` | "03": 양도 / "06": 폐공 / else: 정상 | lnho_raise_yn + end_nt_yn + perm_cancel_yn |
| 8 | `wellFstPermitDt` | "-"→"/" (first_reg_dthr), "-"제거 (perm_nt_ymd) | first_reg_dthr + perm_nt_ymd |

#### 3-1. wellDtlsrv_code 복합 분기 상세

```
wellSrvCode == "1" (생활용):
  dtlsrv "14" → "13"
  dtlsrv "17" → "18"
  dtlsrv "18" → "11"
  dtlsrv "2"  → "41"
  else        → "12"

wellSrvCode == "2" (공업용):
  무조건      → "24"

wellSrvCode == "3" (농어업용):
  dtlsrv "25" → "31"
  dtlsrv "26" → "35"
  dtlsrv "27" → "33"
  dtlsrv "28" → "34"
  else        → "30"

else (기타):
  dtlsrv "29" → "42"
  else        → "40"
```

#### 4. 상태 코드 변환 상세

| wellStatusCode | lnho_raise_yn | end_nt_yn | perm_cancel_yn | 날짜처리 |
|----------------|--------------|-----------|----------------|----------|
| "03" (양도) | "1" | " " | (미설정) | wellDealDt→lnho_raise_ymd (하이픈 제거) |
| "06" (폐공) | " " | "1" | (미설정) | wellDealDt→dvus_enddt (하이픈 제거) |
| else (정상) | "0" | "0" | "0" | lnho_raise_ymd = " " |

> RgetstgmsProgram에서 else 분기에 `perm_cancel_yn = "0"` 추가 (yearProgram에는 없음)

#### 5. 하드코딩 값 (XML에서 확인)

| 컬럼 | 값 | 대상 테이블 |
|------|-----|-----------|
| JGONG_DEAL_YN | '1' | pmms |
| ORG_SNO | '16' | pmms |
| UWATER_SOUC_CODE | source.xml: `#uwater_souc_code#` / target.xml: '1' | stgms |
| PUB_PRI_GBN | = perm_nt_form_code 값 재사용 | stgms |

---

## 구현 항목

### 1. Mock API (`/mock/jeju/facility`)
- MockApiController에 추가
- `reg_year`, `page` 파라미터 지원
- `totalCount` + `data` 배열 응답
- 페이징 시뮬레이션: 1페이지에 전체 반환 (10~15건)
- 다양한 코드값 포함:
  - wellSrvCode: "A1"/"A2"/"A3"/"A4" (substring(1)로 1/2/3/4)
  - wellDtlsrv_code: "14","17","18","2","25","26","27","28","29" (복합 분기 커버)
  - wellType: "permit"/"report"/"other"
  - wellPublic: "pub"/"pri"
  - wellStatusCode: "03"/"06"/"01"
  - wellDrinkYn: ""/"A0"/"A1" (substring(1)로 0/1)
- **field_1 필드** (properties 미확보, API 필드명 추정):
  - perm_nt_no 대응: `wellNo` 또는 `permissionNum`
  - dig_dph: `wellDepth`, dig_diam: `wellDiameter`
  - frw_pln_qua: `fwPlanQty`, dyn_eqn_hrp: `pumpHrp`
  - pipe_diam: `pipeDiameter`, rwt_cap: `rwtCapacity`
  - dvop_loc_regn_code: `wellDong`, dvop_loc_san: `wellSan`
  - dvop_loc_bunji: `wellBunji`, dvop_loc_ho: `wellHo`
  - elev: `wellElev`, nat_wtlv: `natWtlv`, stb_wtlv: `stbWtlv`
  - y_use_qua: `yearUseQty`, perm_yn: `permYn`
  - yy_gbn: `yyGbn`, uwater_souc_code: `uwaterSoucCode`

### 2. DB 테이블 (JPA 엔티티 2개)

대상 DB: 보조망 DB (PG, localhost:29001, dev)
PK: `perm_nt_no` (허가신고번호) — XML MERGE 조건에서 확정

#### `rgetnpmms01` (허가신고정보 — 레거시 테이블명 그대로)
- 33개 컬럼 (XML 바인딩 변수 기준)
- PK: perm_nt_no

#### `rgetstgms01` (이용실태정보 — 레거시 테이블명 그대로)
- 32개 컬럼 (XML 바인딩 변수 기준)
- PK: perm_nt_no
- pmms 대비 추가 컬럼: yy_gbn, elev, pub_pri_gbn, pota_yn, y_use_qua, uwater_souc_code, dph, pump_hrp, nat_wtlv, stb_wtlv
- pmms 대비 없는 컬럼: aplr_gbn_code, perm_yn, lnho_raise_yn, end_nt_yn, perm_cancel_yn, jgong_deal_yn, org_sno, uwater_srv, dvop_loc_regn_code, dvop_loc_san, dvop_loc_bunji, dvop_loc_ho, perm_nt_ymd

### 3. 동적 파라미터 YEAR 타입 추가 (선행 작업)

`reg_year`를 동적 파라미터로 등록하기 위해 DynamicType에 `YEAR` 추가.
기존 동적 파라미터(나라장터 4개 엔드포인트 × 2 = 8건, 전부 `TODAY` 타입)에 영향 없음.

#### 수정 대상

| 위치 | 파일 | 변경 |
|------|------|------|
| 백엔드 enum | `ApiParam.java` | `DynamicType`에 `YEAR` 추가 |
| 백엔드 resolver | `DynamicParamResolver.java` | `YEAR` 분기 추가: `LocalDate.now().plusYears(offset).format(format)` |
| 프론트 타입 | `types/api-collect.ts` | `DynamicType`에 `'YEAR'` 추가 |
| 프론트 UI | `InfoTab.tsx` | select에 `<option value="YEAR">연도</option>` 추가 |

#### 등록 예시 (reg_year)
```
paramName: reg_year
paramType: BODY
valueType: DYNAMIC
dynamicType: YEAR
dynamicFormat: yyyy
dynamicOffset: -1
description: 수집 연도 (기본: 작년)
```

→ 스케줄 실행 시 매년 자동으로 작년 연도 계산
→ 수동 실행 시 오버라이드 값 입력 가능 (기존 테스트 호출 오버라이드 기능 활용)

#### 기존 데이터 영향 분석

| 항목 | 영향 |
|------|------|
| DB enum (dynamic_type VARCHAR) | VARCHAR이므로 새 값 추가 자유 |
| 기존 8건 (TODAY) | DynamicParamResolver에서 TODAY/NOW 분기 그대로, YEAR는 새 분기 → **영향 없음** |
| 프론트 select | option 추가만, 기존 TODAY/NOW 선택지 변경 없음 → **영향 없음** |

### 4. JejuFacilityExecutor 구현

#### 핵심 로직
```
1. reg_year: 동적 파라미터로 resolve (YEAR 타입, offset=-1 → 작년)
   - DynamicParamResolver가 자동 치환
   - 수동 실행 시 오버라이드 가능
2. API 첫 호출 (reg_year, page 없이) → totalCount 확인
3. 페이징 루프: ceil(totalCount / 1000) + 1 회
   a. API 호출 (reg_year, page=N)
   b. data 배열 파싱
   c. 건별 처리:
      - field_1 → field_2 필드 매핑
      - 좌표변환 EPSG:5186→4326 + 도분초 문자열 분리
      - 코드변환 8종 (음용/용도/세부용도/지역/허가신고/공공민간/상태/날짜)
   d. UPSERT 2회 (ON CONFLICT perm_nt_no):
      - rgetnpmms01 (허가신고정보)
      - rgetstgms01 (이용실태정보)
4. 전체 결과 합산 반환
```

### 5. 엔드포인트 등록 + E2E 테스트
- API Collector UI에서 등록
- 실행 방식: 커스텀 (JejuFacilityExecutor)
- reg_year: 동적 파라미터 (YEAR, -1) — UI에서 등록
- 수동 실행 → 로그 확인 → DB 검증 (2개 테이블 모두)

---

## 레거시 대비 비교

| 항목 | 레거시 | 신규 |
|------|--------|------|
| DB 저장 | **MERGE** (Oracle MERGE INTO ... USING dual) | **UPSERT** (PG ON CONFLICT) — 동일 동작 |
| PK | PERM_NT_NO | PERM_NT_NO — 동일 |
| 에러 처리 | printStackTrace 후 다음 건 | 건별 skip + warn 로그 |
| 프로그램 분리 | RgetstgmsProgram + yearProgram 별도 | 1개 Executor + 동적 파라미터(YEAR)로 통합 |
| 연도 관리 | 코드에 하드코딩(올해-1) / 수동입력 | 동적 파라미터 YEAR 타입 (자동 계산 + 오버라이드) |
| 필드 매핑 | properties 파일 (외부) | 코드 내 상수 (명시적, 추적 가능) |
| 좌표변환 | 매건 CRSFactory 생성 | 1회 생성 재사용 |
| 페이징 | while 루프 | 동일 (API 사양) |

## ⚠️ 확인/결정 필요 사항

1. ~~PK 결정~~ → **확정: `PERM_NT_NO`** (XML MERGE 조건에서 확인)
2. **field_1 매핑**: properties 파일 미확보
   - XML 바인딩 변수(field_2)는 전부 확인 완료 (pmms 33개, stgms 32개)
   - field_1(API 필드명)은 Mock 데이터에서 camelCase로 재구성
   - 향후 실 API 연결 시 필드명 조정 가능
3. **좌표 도분초**: 레거시 방식(문자열 자르기) 그대로 재현
4. **UWATER_SOUC_CODE 차이**: source.xml은 `#uwater_souc_code#`, target.xml은 고정 '1'
   - source.xml(DMZ 적재) 기준으로 구현, field 매핑 포함

## 데이터 접근 방식: JdbcTemplate only
API Collector 커스텀 실행기는 동적 DataSource 특성상 JdbcTemplate만 사용.

## 수정 대상 파일

| 모듈 | 파일 | 변경 내용 |
|------|------|----------|
| **api-collector** | ApiParam.java | DynamicType에 `YEAR` 추가 |
| **api-collector** | DynamicParamResolver.java | YEAR 분기 추가 |
| **api-collector** | MockApiController.java | `/mock/jeju/facility` 엔드포인트 추가 |
| **api-collector** | Rgetnpmms01.java | **신규** — JPA 엔티티 (rgetnpmms01, 33컬럼) |
| **api-collector** | Rgetstgms01.java | **신규** — JPA 엔티티 (rgetstgms01, 32컬럼) |
| **api-collector** | JejuFacilityExecutor.java | **신규** — 커스텀 실행기 |
| **frontend** | types/api-collect.ts | DynamicType에 `'YEAR'` 추가 |
| **frontend** | components/api-collect/InfoTab.tsx | 동적 타입 select에 연도 옵션 추가 |

## 영향 범위
- DynamicType YEAR 추가: api-collector + frontend (기존 TODAY/NOW 영향 없음)
- Executor 신규 추가: api-collector만 (기존 기능 영향 없음)
