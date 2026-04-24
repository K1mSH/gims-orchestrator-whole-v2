# Provide Operation 등록 스펙

> 작성일: 2026-04-24
> 범위: 현재 이식 완료된 provide 타겟으로 서빙 가능한 레거시 API **12건** operation 등록 스펙
> 대상: `gims-api-provider` 의 `api_prv_operation` / `api_prv_operation_column` / `api_prv_operation_param` 테이블
> 등록 방식: 관리 UI (`ApiPrvManageController` 경유)
> 호출 테스트: Postman 으로 `GET http://localhost:8095/api/provide/{operationId}?...`

> **판정 기준**: "우리 PG 타겟 + WHERE 파라미터로 커버 가능한가"
> - ✅ **같은 PG 타겟 + WHERE 만 다름** → 재활용 (B2/A7 쌍, 같은 `api_prv_tmp_megokr_api`)
> - ❌ **타겟이 다르면** 별개 URL + 별개 operation (B1 → `api_prv_tm_gd110301`)
> - ❌ **엔진으로 구조 재현 불가** (JOIN / PIVOT / 서브쿼리) → 이번 범위 제외 (B3 는 EAV PIVOT 필요 → Type B 이식 후)

---

## 1. 개요

현재 이식 완료된 `api_prv_*` 타겟에 대해 레거시 API URL **12건** 을 operation 으로 등록 가능. 이번 작업은 **기존 자산 검증** + **제공 계층 패턴 확립** 목적. Type B 이식 후에는 동일 방식으로 추가 operation (B3/B4/B5/... ) 이 붙음.

### 1.1 대응 매트릭스

| 레거시 URL | 제공 타겟 | 비고 |
|---|---|---|
| `/megokrApi/ngw08` (A1) | `api_prv_tm_gd000203` | 1:1 |
| `/megokrApi/ngw09` (A2) | `api_prv_tm_gd112002` | WHERE 단건 |
| `/megokrApi/ngw09_01` (A3) | `api_prv_tm_gd112002` | 목록 |
| `/megokrApi/ngw04_01` (A7) | `api_prv_tmp_megokr_api` | 126 컬럼 (SN + 125 WT_*) |
| `/megokrApi/ngw03` (B1) | **`api_prv_tm_gd110301`** | 22 메타, 별개 타겟 |
| `/megokrApi/ngw03_01` (B2) | `api_prv_tmp_megokr_api` | 22 메타 페이징 (A7 과 같은 타겟) |
| `/megokrApi/ngw04` (B3) | ⏸ **이번 범위 제외** | `api_prv_tm_gd110302` 는 EAV 원본, PIVOT 필요 → Type B Step 이식 후 |
| `/drought119Api/selectDrought119` (A4) | `api_prv_wt_dream_permwell_public_21033` | 1:1 |
| OPN `getNationalGroundwater` (A5) | `api_prv_tm_gd120001` | WHERE josacode 고정 |
| OPN `getSeawaterPermeation` (A5) | 동상 | 다른 josacode |
| OPN `getRuralGroundwater` (A5) | 동상 | 다른 josacode |
| OPN `getBasicSurvey` (A5) | 동상 | 다른 josacode |
| OPN `getImpactInvestigation` (A6) | `api_prv_tm_gd130001` | WHERE 단건 |

## 2. 공통 설정

### 2.1 datasource_id
- 값: **api_provider PG 의 Orchestrator 등록 ID** (UI 드롭다운에서 선택)
- 13건 모두 동일 datasource 사용

### 2.2 응답 포맷 / 페이징
- `responseFormat`: `JSON` (기본값)
- `pageSize`: 기본값 유지 (100). 대용량 응답(ngw04_01 페이징) 만 조정
- `maxPageSize`: 기본 1000. 레거시 호환 필요 시 10000 로 상향

### 2.3 API Key / 게시
- **API Key 검증**: 이번엔 임시값으로 처리 (추후 실운영 시 별도 설계)
- `isPublished`: **`true`** 로 등록해야 `/api/provide/{id}` 에서 노출됨 (ApiGatewayController 가 미게시 건은 404)

## 3. UI 필드 용어집 (헷갈릴 때 참조)

`gims-api-provider` 의 DB 엔티티를 UI 가 그대로 노출하므로 **관리 UI 의 한글 라벨 = 아래 표의 "한글 라벨" 열**. 영문 필드명은 이 문서 본문(§5) 표에 나오는 이름. 둘이 같은 대상입니다.

### 3.1 Operation (상위 — API 1개당 1건)

| UI 한글 라벨 | 영문 필드명 | 설명 |
|---|---|---|
| 오퍼레이션ID | `operationId` | URL 경로. `/api/provide/{여기}` 로 호출됨 |
| 오퍼레이션명 | `operationName` | 한글 설명명 (UI 표시용) |
| 설명 | `description` | 상세 설명 |
| 대상 데이터소스ID | `datasourceId` | **api_provider PG 의 Orchestrator 등록 ID** (드롭다운에서 선택) |
| 대상 테이블명 | `tableName` | 어떤 `api_prv_*` 테이블에서 읽을지 (소문자) |
| 응답포맷 | `responseFormat` | `JSON` (기본) |
| 기본페이지크기 | `pageSize` | 보통 100 |
| 최대페이지크기 | `maxPageSize` | 보통 1000, 대용량(ngw04-01)만 10000 |
| 정렬컬럼 | `orderByColumn` | ORDER BY 할 DB 컬럼명 (보통 PK 또는 sn) |
| 정렬방향 | `orderByDirection` | ASC / DESC |
| 게시여부 | `isPublished` | **true 로 해야 `/api/provide/{id}` 에서 응답됨** |
| 활성여부 | `isActive` | true |

### 3.2 Columns (응답 필드 — Operation 당 N개 추가)

| 이 문서 표기 | UI 한글 라벨 | 영문 필드명 | 설명 |
|---|---|---|---|
| **프로바이드 컬럼** | DB컬럼명 | `columnName` | **이 operation 이 읽는 `api_prv_*` 타겟의 실제 컬럼명** (소문자, snake_case) |
| **응답필드명** | 응답필드명(별칭) | `aliasName` | **외부 응답 JSON 에 나올 필드명**. 없으면 프로바이드 컬럼명 그대로 |
| 순서 | 표시순서 | `displayOrder` | 1, 2, 3, ... |
| 가공 | 가공타입 | `transformType` | NONE / ROUND / DATE_FORMAT / COALESCE / SUBSTRING |
| 가공파라미터 | 가공파라미터 | `transformParam` | 가공타입에 따라 (예: ROUND 는 소숫점 자릿수) |

> **핵심**: 한 operation 에 **응답 컬럼 수만큼** Columns 추가 버튼을 눌러 등록. 예를 들어 12개 컬럼이면 12번 등록.
> 이 문서 §5 본문의 Columns 표는 **"프로바이드 컬럼 / 응답필드명"** 으로 표기 — UI 의 "DB컬럼명 / 응답필드명(별칭)" 에 해당.

#### 응답필드명 자동(대문자) 기능 활용 판단 기준

Columns 탭에서 체크박스 체크 시 응답필드명이 **프로바이드 컬럼 대문자** 로 자동 채워진다. 상단 "응답필드명 자동(대문자)" 버튼으로 일괄 재채움도 가능.

| 케이스 | 자동 대문자 적합? | 예시 |
|---|:--:|---|
| **프로바이드 컬럼 ≈ 레거시 이름** (표준화 후에도 이름 유지) | ✅ 그대로 저장 | `wt_tot_col_cnts` → `WT_TOT_COL_CNTS` (A7 수질검사결과) |
| **표준화로 이름 완전히 바뀐 컬럼** | ❌ 수동 입력 필요 | `prmsn_dclr_no` ↔ `PERM_NT_NO`, `xcrd` ↔ `TMX_VALUE` (A2/A3/A4) |

§5.x 각 operation 에 자동 대문자 적합 여부를 명시해뒀으니 참고하세요.

### 3.3 Params (WHERE 조건 — 필요한 경우만 M개 추가)

| UI 한글 라벨 | 영문 필드명 | 설명 |
|---|---|---|
| 요청파라미터명 | `paramName` | **URL 쿼리스트링 키** (예: `?gennum=12345` 의 `gennum`). camelCase 관례 |
| WHERE대상컬럼명 | `columnName` | **WHERE 절에 들어갈 타겟 테이블의 DB 컬럼명** (소문자) |
| 연산자 | `operator` | EQ / LIKE / LIKE_START / LIKE_END / GT / GTE / LT / LTE / IN / BETWEEN |
| 필수여부 | `isRequired` | true 면 값 미전달 시 400 에러 |
| 기본값 | `defaultValue` | 값 미전달 시 사용. 고정값으로 쓰려면 hidden=true 와 함께 |
| 데이터타입 | `dataType` | STRING / NUMBER / DATE |
| 숨김여부 | `isHidden` | **true 면 외부 미노출 (고정값 전용)**. JOSACODE 같은 운영자 고정 필터에 사용 |

> **혼동 주의**: `paramName` 은 URL 쿼리 키, `columnName` 은 DB 컬럼. 둘은 다를 수 있음.
> 예: `gennum=12345` 가 WHERE `gwel_no = 12345` 로 변환되려면 → `paramName=gennum`, `columnName=gwel_no`

### 3.4 샘플 워크스루 — `opn-impact-investigation` (A6) 등록 실습

가장 단순한 operation 을 **UI 화면에 나오는 순서** 그대로 입력하는 예시. 이걸 한 번 해보시면 나머지 11건도 같은 패턴입니다.

#### 1단계: Operation 기본 정보 등록

| UI 라벨 | 입력값 |
|---|---|
| 오퍼레이션ID | `opn-impact-investigation` |
| 오퍼레이션명 | 영향조사보고서 상세 |
| 설명 | OPN getImpactInvestigation 대체 |
| 대상 데이터소스ID | (api_provider PG 선택 — 드롭다운) |
| 대상 테이블명 | `api_prv_tm_gd130001` |
| 응답포맷 | `JSON` |
| 기본페이지크기 | `100` |
| 최대페이지크기 | `1000` |
| 정렬컬럼 | `sn` |
| 정렬방향 | `ASC` |
| 게시여부 | ✅ true |
| 활성여부 | ✅ true |

→ 저장.

#### 2단계: Columns 7번 추가

각 행마다 "컬럼 추가" 버튼 → 4개 필드 입력 → 저장. 가공타입/파라미터는 전부 `NONE` / 비워둠.

| 순서 | 프로바이드 컬럼 | 응답필드명 |
|:--:|---|---|
| 1 | `isvr_no` | `YH_SNO` |
| 2 | `isvr_nm` | `RPT_TITLE` |
| 3 | `prmtv_data_inst_nm` | `SOUR_GOV` |
| 4 | `data_crtr_yr` | `PRESSYEAR` |
| 5 | `pblcn_mm` | `PRESSMONTH` |
| 6 | `isvr_ccd` | `GUBUN` |
| 7 | `prlg_sn` | `EXTEN_NUM` |

#### 3단계: Params 1개 추가

| 요청파라미터명 | WHERE대상컬럼명 | 연산자 | 필수 | 기본값 | 데이터타입 | 숨김 |
|---|---|---|:--:|---|---|:--:|
| `yhSno` | `isvr_no` | `EQ` | ✅ | — | `STRING` | — |

#### 4단계: Postman 호출

```
GET http://localhost:8095/api/provide/opn-impact-investigation?yhSno=1234567890
```

응답:
```json
{
  "data": [
    { "YH_SNO": "1234567890", "RPT_TITLE": "...", "SOUR_GOV": "...", ... }
  ],
  "pagination": { "page": 1, "pageSize": 100, "totalCount": 1, "totalPages": 1 }
}
```

→ 응답 필드가 **응답필드명 (alias) 으로 나오는지** 가 성공 기준. `data` 배열에 `YH_SNO`, `RPT_TITLE` 등이 나와야 정상. `isvr_no`, `isvr_nm` (프로바이드 컬럼) 으로 나오면 alias 등록 실수.

---

## 4. 엔진 제약 (등록 전 숙지)

`DynamicQueryService` 는 **단일 테이블 단순 SELECT 엔진**:

| 지원 O | 지원 X |
|---|---|
| SELECT 컬럼 + alias | JOIN |
| WHERE: EQ/LIKE/GT/GTE/LT/LTE/IN/BETWEEN | GROUP BY / 집계 |
| 컬럼 가공: ROUND/DATE_FORMAT/COALESCE/SUBSTRING | 서브쿼리 |
| ORDER BY 단일 컬럼 | UNION |
| LIMIT / OFFSET 페이징 | PIVOT |
| `is_hidden=true + default_value` 로 WHERE 고정값 | 문자열 `\|\|` 연결 |
| 복합 파라미터 (IN "a,b,c" / BETWEEN "a,b") | 동적 함수 (`SYSDATE` 등) |

→ **ADDR 조합 (BRTC+SIGUN+EMD+LI+ADDR)** 은 클라이언트가 합치도록. 엔진 추가 개선 시 `CONCAT` 가공 타입 추가 가능.
→ **동적 연도** (B1 의 `INVSTG_YEAR <= SYSDATE-2`) 는 default_value 에 정적 연도 (예: '2024') 넣고 매년 수동 갱신 or 파라미터 필수로.

## 5. Operation 스펙 (12건)

각 스펙은 다음 구조:
- **Operation**: `operationId`, `operationName`, `tableName`, `orderByColumn`, `pageSize`
- **Columns**: `프로바이드 컬럼` / `응답필드명` / 순서 (UI: DB컬럼명 / 응답필드명(별칭) / 표시순서)
- **Params**: `paramName`, `columnName`, `operator`, `isRequired`, `defaultValue`, `dataType`, `isHidden`

---

### 5.1 `megokrapi-ngw08` — 공공관정 가뭄지원 (A1)

**Operation**
| 필드 | 값 |
|---|---|
| operationId | `megokrapi-ngw08` |
| operationName | 공공관정 가뭄지원 |
| description | 시도명 기준 공공관정 가뭄지원 정보 (MEGOKR selectNgw08 대체) |
| tableName | `api_prv_tm_gd000203` |
| orderByColumn | `sn` |
| pageSize | 100 |
| isPublished | true |

**Columns** (11개, 레거시 응답 필드명에 맞춰 alias)

| # | 프로바이드 컬럼 | 응답필드명 | 순서 | 가공 |
|:--:|---|---|:--:|---|
| 1 | ctpv_nm | BRTC_NM | 1 | — |
| 2 | sgg_nm | SIGUN_NM | 2 | — |
| 3 | emd_nm | EMD_NM | 3 | — |
| 4 | li_nm | LI_NM | 4 | — |
| 5 | ppltn_cnt | POPLTN_VALUE | 5 | — |
| 6 | lpcd_cn | LPCD_CTNT | 6 | — |
| 7 | dmd_qnt_vl | DMAND_QUA_VALUE | 7 | — |
| 8 | sply_psblqy_vl | SUPLY_PSBLQY_VALUE | 8 | — |
| 9 | ovshrts_qnt_vl | NSTT_VALUE | 9 | — |
| 10 | tot_pub_gwel_cnt | TOT_PUBWELL_CO | 10 | — |
| 11 | use_pub_gwel_cnt | USE_PUBWELL_CO | 11 | — |

**Params**

| paramName | columnName | operator | required | defaultValue | dataType | hidden |
|---|---|---|:--:|---|---|:--:|
| brtcNm | ctpv_nm | EQ | ✅ | — | STRING | — |

**호출 예시**: `GET /api/provide/megokrapi-ngw08?brtcNm=서울특별시`

---

### 5.2 `megokrapi-ngw09` — 공공관정 상세 (단건, A2)

**Operation**
| 필드 | 값 |
|---|---|
| operationId | `megokrapi-ngw09` |
| operationName | 공공관정 상세 (단건) |
| tableName | `api_prv_tm_gd112002` |
| orderByColumn | `sn` |
| pageSize | 100 |

**Columns** (34개 — 레거시 SQL 의 SELECT 순서·alias 그대로)

| # | 프로바이드 컬럼 | 응답필드명 | # | 프로바이드 컬럼 | 응답필드명 |
|:--:|---|---|:--:|---|---|
| 1 | link_trsm_sgg_cd | REL_TRANS | 18 | lat_ss | LTTD_SC |
| 2 | prmsn_dclr_no | PERM_NT_NO | 19 | lot_dg | LITD_DG |
| 3 | prmsn_dclr_frm_cd | PERM_NT_FO | 20 | lot_mi | LITD_MINT |
| 4 | yr_se | YY_GBN | 21 | lot_ss | LITD_SC |
| 5 | rgn_cd | REGNCODE | 22 | dph_vl | DPH |
| 6 | ctpv_nm | BRTC_NM | 23 | dgg_calbr | DIG_DIAM |
| 7 | sgg_nm | SIGUN_NM | 24 | delp_dia | PIPE_DIAM |
| 8 | emd_nm | EMD_NM | 25 | pump_hrspw | PUMP_HRP |
| 9 | li_nm | LI_NM | 26 | wtrit_plan_qtr | FRW_PLN_QU |
| 10 | mtn | SAN | 27 | wpmp_ablt | RWT_CAP |
| 11 | bnj | BUNJI | 28 | yr_usqty | Y_USE_QUA |
| 12 | ho | HO | 29 | pub_prvtest_se | PUB_PRI_GB |
| 13 | ugwtr_usg | UWATER_SRV | 30 | wq_insp_ymd | QW_ISP_YMD |
| 14 | ugwtr_dtl_usg_cd | UWATER_DTL | 31 | wq_insp_rslt | QW_ISP_RT |
| 15 | dkpp_yn | UWATER_POT | 32 | pnu | PNU |
| 16 | lat_dg | LTTD_DG | 33 | xcrd | TMX_VALUE |
| 17 | lat_mi | LTTD_MINT | 34 | ycrd | TMY_VALUE |

> 1번 `REL_TRANS` (끝에 `_` 없음) — 레거시 SQL: `REL_TRANS_ AS REL_TRANS` (프로바이드 컬럼 이름은 `link_trsm_sgg_cd` → 엔티티 기준).

(displayOrder 는 위 번호 순서)

**Params**

| paramName | columnName | operator | required | defaultValue | dataType | hidden |
|---|---|---|:--:|---|---|:--:|
| permNtNo | prmsn_dclr_no | EQ | ✅ | — | STRING | — |

**호출**: `GET /api/provide/megokrapi-ngw09?permNtNo=xxxxx`

---

### 5.3 `megokrapi-ngw09-01` — 공공관정 상세 (목록, A3)

레거시 `selectNgw09_01` 은 `selectNgw09` 와 **SELECT 절이 완전 동일** (34 컬럼 / 순서 / alias 모두 같음). WHERE 만 다름 (A2: 단건, A3: 시도 목록). 따라서 **Columns 는 §5.2 와 같은 alias 매핑을 그대로 등록** — 프로바이드 컬럼은 우리 PG 의 표준화 이름, 응답필드명은 레거시 대문자 이름.

**Operation**
| 필드 | 값 |
|---|---|
| operationId | `megokrapi-ngw09-01` |
| operationName | 공공관정 상세 (목록) |
| tableName | `api_prv_tm_gd112002` |
| orderByColumn | `sn` |
| pageSize | 100 |

**Columns** (34개 — §5.2 와 동일한 alias 매핑)

| # | 프로바이드 컬럼 | 응답필드명 | # | 프로바이드 컬럼 | 응답필드명 |
|:--:|---|---|:--:|---|---|
| 1 | link_trsm_sgg_cd | REL_TRANS | 18 | lat_ss | LTTD_SC |
| 2 | prmsn_dclr_no | PERM_NT_NO | 19 | lot_dg | LITD_DG |
| 3 | prmsn_dclr_frm_cd | PERM_NT_FO | 20 | lot_mi | LITD_MINT |
| 4 | yr_se | YY_GBN | 21 | lot_ss | LITD_SC |
| 5 | rgn_cd | REGNCODE | 22 | dph_vl | DPH |
| 6 | ctpv_nm | BRTC_NM | 23 | dgg_calbr | DIG_DIAM |
| 7 | sgg_nm | SIGUN_NM | 24 | delp_dia | PIPE_DIAM |
| 8 | emd_nm | EMD_NM | 25 | pump_hrspw | PUMP_HRP |
| 9 | li_nm | LI_NM | 26 | wtrit_plan_qtr | FRW_PLN_QU |
| 10 | mtn | SAN | 27 | wpmp_ablt | RWT_CAP |
| 11 | bnj | BUNJI | 28 | yr_usqty | Y_USE_QUA |
| 12 | ho | HO | 29 | pub_prvtest_se | PUB_PRI_GB |
| 13 | ugwtr_usg | UWATER_SRV | 30 | wq_insp_ymd | QW_ISP_YMD |
| 14 | ugwtr_dtl_usg_cd | UWATER_DTL | 31 | wq_insp_rslt | QW_ISP_RT |
| 15 | dkpp_yn | UWATER_POT | 32 | pnu | PNU |
| 16 | lat_dg | LTTD_DG | 33 | xcrd | TMX_VALUE |
| 17 | lat_mi | LTTD_MINT | 34 | ycrd | TMY_VALUE |

**Params**

| paramName | columnName | operator | required | defaultValue | dataType | hidden |
|---|---|---|:--:|---|---|:--:|
| brtcNm | ctpv_nm | EQ | ✅ | — | STRING | — |
| permNtNo | prmsn_dclr_no | EQ | — | — | STRING | — |

> A3 는 시도(`brtcNm`) 필터가 필수, 허가번호는 선택. 레거시 원본 `WHERE BRTC_NM=? [AND PERM_NT_NO=?]` 재현.

**호출**: `GET /api/provide/megokrapi-ngw09-01?brtcNm=대전광역시&page=1&pageSize=100`

---

### 5.4 `drought119-select` — 가뭄119 인허가관정 (A4)

레거시 `selectdroght119` 는 SDE 스냅샷 테이블(`WT_DREAM_PERMWELL_PUBLIC_21033`) 의 **33 컬럼** 반환. 엔티티 `ApiPrvWtDreamPermwellPublic21033` 에는 `link_trsm_sgg_cd`, `pnu` 도 있지만 **레거시 SQL 이 SELECT 하지 않아 응답 제외**.

> ⚠️ **자동 대문자 기능 부적합**: 이 operation 은 프로바이드 컬럼명(표준화 소문자) 과 레거시 응답 이름이 **완전 다른 체계** (예: `prmsn_dclr_no` ↔ `PERM_NT_NO`, `lat_dg` ↔ `LTTD_DG`, `xcrd` ↔ `TMX_VALUE`). 자동 대문자 버튼 누르면 `PRMSN_DCLR_NO` / `LAT_DG` / `XCRD` 로 채워져 레거시 규격과 안 맞음. **수동 입력 필수**.

**Operation**
| 필드 | 값 |
|---|---|
| operationId | `drought119-select` |
| operationName | 가뭄119 인허가관정 |
| tableName | `api_prv_wt_dream_permwell_public_21033` |
| orderByColumn | `objectid` |
| pageSize | 100 |

**Columns** (33개, 레거시 SQL SELECT 순서 그대로)

| # | 프로바이드 컬럼 | 응답필드명 | # | 프로바이드 컬럼 | 응답필드명 |
|:--:|---|---|:--:|---|---|
| 1 | objectid | OBJECTID | 18 | lat_ss | LTTD_SC |
| 2 | prmsn_dclr_no | PERM_NT_NO | 19 | lot_dg | LITD_DG |
| 3 | prmsn_dclr_frm_cd | PERM_NT_FO | 20 | lot_mi | LITD_MINT |
| 4 | yr_se | YY_GBN | 21 | lot_ss | LITD_SC |
| 5 | rgn_cd | REGNCODE | 22 | dph_vl | DPH |
| 6 | ctpv_nm | BRTC_NM | 23 | dgg_calbr | DIG_DIAM |
| 7 | sgg_nm | SIGUN_NM | 24 | delp_dia | PIPE_DIAM |
| 8 | emd_nm | EMD_NM | 25 | pump_hrspw | PUMP_HRP |
| 9 | li_nm | LI_NM | 26 | wtrit_plan_qtr | FRW_PLN_QU |
| 10 | mtn | SAN | 27 | wpmp_ablt | RWT_CAP |
| 11 | bnj | BUNJI | 28 | yr_usqty | Y_USE_QUA |
| 12 | ho | HO | 29 | pub_prvtest_se | PUB_PRI_GB |
| 13 | ugwtr_usg | UWATER_SRV | 30 | wq_insp_ymd | QW_ISP_YMD |
| 14 | ugwtr_dtl_usg_cd | UWATER_DTL | 31 | wq_insp_rslt | QW_ISP_RT |
| 15 | dkpp_yn | UWATER_POT | 32 | xcrd | TMX_VALUE |
| 16 | lat_dg | LTTD_DG | 33 | ycrd | TMY_VALUE |
| 17 | lat_mi | LTTD_MINT | | | |

> **체크박스 해제할 컬럼**: `link_trsm_sgg_cd`, `pnu` — 엔티티엔 있지만 A4 응답엔 포함 안 함.

**Params**: 없음 (전체 목록)

**호출**: `GET /api/provide/drought119-select?page=1`

---

### 5.5 `opn-national-groundwater` — 국가지하수 관측망 상세 (A5 #1/4)

**Operation**
| 필드 | 값 |
|---|---|
| operationId | `opn-national-groundwater` |
| operationName | 국가지하수 관측망 상세 |
| tableName | `api_prv_tm_gd120001` |
| orderByColumn | `sn` |
| pageSize | 100 |

**Columns** (5개, 레거시 응답 기준)

| # | 프로바이드 컬럼 | 응답필드명 | 가공 |
|:--:|---|---|---|
| 1 | ugwtr_exmn_cd | JOSACODE | — |
| 2 | brnch_nm | JIGUNAME | — |
| 3 | addr | ADDR | — (레거시 `BRTC\|SIGUN\|...` 조합은 클라이언트가 처리 or 여기선 단일 addr 만) |
| 4 | prmtv_data_nm | SOURDATA | COALESCE '2011;' |
| 5 | prmtv_data_inst_nm | SOUR_GOV | COALESCE '2011;' |

> ADDR 은 엔티티의 단일 `addr` 컬럼 제공. 레거시와 완전 일치 위해서는 `ctpv_nm`, `sgg_nm`, `emd_nm`, `li_nm` 을 추가 컬럼으로 포함해 클라이언트 조합.

**Params**

| paramName | columnName | operator | required | defaultValue | dataType | hidden |
|---|---|---|:--:|---|---|:--:|
| gennum | gwel_no | EQ | ✅ | — | NUMBER | — |
| josacode | ugwtr_exmn_cd | EQ | — | `104` | STRING | ✅ (고정) |

> `josacode` 를 **hidden=true + default=104** 로 고정 → 이 operation 은 국가지하수 전용 (JOSACODE 104).

**호출**: `GET /api/provide/opn-national-groundwater?gennum=12345`

---

### 5.6 `opn-seawater-permeation` — 해수침투 관측망 (A5 #2/4)

§5.5 와 동일한 테이블·컬럼 구조 — **`operationId`, `operationName`, `josacode` default** 만 다름.

**Operation**
| 필드 | 값 |
|---|---|
| operationId | `opn-seawater-permeation` |
| operationName | 해수침투 관측망 상세 |
| tableName | `api_prv_tm_gd120001` |
| orderByColumn | `sn` |
| pageSize | 100 |

**Columns** (§5.5 와 동일한 5개 — 프로바이드 컬럼 + 응답필드명 alias 매핑 그대로 재등록)

| # | 프로바이드 컬럼 | 응답필드명 | 가공 |
|:--:|---|---|---|
| 1 | ugwtr_exmn_cd | JOSACODE | — |
| 2 | brnch_nm | JIGUNAME | — |
| 3 | addr | ADDR | — |
| 4 | prmtv_data_nm | SOURDATA | COALESCE '2011;' |
| 5 | prmtv_data_inst_nm | SOUR_GOV | COALESCE '2011;' |

**Params**

| paramName | columnName | operator | required | defaultValue | dataType | hidden |
|---|---|---|:--:|---|---|:--:|
| gennum | gwel_no | EQ | ✅ | — | NUMBER | — |
| josacode | ugwtr_exmn_cd | EQ | — | `112` | STRING | ✅ |

**호출**: `GET /api/provide/opn-seawater-permeation?gennum=12345`

---

### 5.7 `opn-rural-groundwater` — 농촌지하수 관측망 (A5 #3/4)

§5.5 와 동일한 테이블·컬럼 구조 — `josacode` default 만 다름.

**Operation**
| 필드 | 값 |
|---|---|
| operationId | `opn-rural-groundwater` |
| operationName | 농촌지하수 관측망 상세 |
| tableName | `api_prv_tm_gd120001` |
| orderByColumn | `sn` |
| pageSize | 100 |

**Columns** (§5.5 와 동일한 5개)

| # | 프로바이드 컬럼 | 응답필드명 | 가공 |
|:--:|---|---|---|
| 1 | ugwtr_exmn_cd | JOSACODE | — |
| 2 | brnch_nm | JIGUNAME | — |
| 3 | addr | ADDR | — |
| 4 | prmtv_data_nm | SOURDATA | COALESCE '2011;' |
| 5 | prmtv_data_inst_nm | SOUR_GOV | COALESCE '2011;' |

**Params**

| paramName | columnName | operator | required | defaultValue | dataType | hidden |
|---|---|---|:--:|---|---|:--:|
| gennum | gwel_no | EQ | ✅ | — | NUMBER | — |
| josacode | ugwtr_exmn_cd | EQ | — | `113` | STRING | ✅ |

> ⚠️ 실제 JOSACODE 값 (104/112/113/215/216 중 농촌지하수) 은 운영 DB 의 `TC_GD00002` (UGRWTR_CMMN_GRP_CODE='NGW_xxxx') 공통코드로 검증 후 확정 권장.

**호출**: `GET /api/provide/opn-rural-groundwater?gennum=12345`

---

### 5.8 `opn-basic-survey` — 기초조사 (A5 #4/4)

§5.5 와 동일한 테이블·컬럼 구조 — `josacode` default 만 다름.

**Operation**
| 필드 | 값 |
|---|---|
| operationId | `opn-basic-survey` |
| operationName | 기초조사 상세 |
| tableName | `api_prv_tm_gd120001` |
| orderByColumn | `sn` |
| pageSize | 100 |

**Columns** (§5.5 와 동일한 5개)

| # | 프로바이드 컬럼 | 응답필드명 | 가공 |
|:--:|---|---|---|
| 1 | ugwtr_exmn_cd | JOSACODE | — |
| 2 | brnch_nm | JIGUNAME | — |
| 3 | addr | ADDR | — |
| 4 | prmtv_data_nm | SOURDATA | COALESCE '2011;' |
| 5 | prmtv_data_inst_nm | SOUR_GOV | COALESCE '2011;' |

**Params**

| paramName | columnName | operator | required | defaultValue | dataType | hidden |
|---|---|---|:--:|---|---|:--:|
| gennum | gwel_no | EQ | ✅ | — | NUMBER | — |
| josacode | ugwtr_exmn_cd | EQ | — | `215` | STRING | ✅ |

> ⚠️ 기초조사 JOSACODE 값 확정 필요 (215 또는 216). `TC_GD00002` 공통코드 확인 후 정확한 값으로.

**호출**: `GET /api/provide/opn-basic-survey?gennum=12345`

---

### 5.9 `opn-impact-investigation` — 영향조사 상세 (A6)

**Operation**
| 필드 | 값 |
|---|---|
| operationId | `opn-impact-investigation` |
| operationName | 영향조사보고서 상세 |
| tableName | `api_prv_tm_gd130001` |
| orderByColumn | `sn` |
| pageSize | 100 |

**Columns** (7개)

| # | 프로바이드 컬럼 | 응답필드명 |
|:--:|---|---|
| 1 | isvr_no | YH_SNO |
| 2 | isvr_nm | RPT_TITLE |
| 3 | prmtv_data_inst_nm | SOUR_GOV |
| 4 | data_crtr_yr | PRESSYEAR |
| 5 | pblcn_mm | PRESSMONTH |
| 6 | isvr_ccd | GUBUN |
| 7 | prlg_sn | EXTEN_NUM |

**Params**

| paramName | columnName | operator | required | defaultValue | dataType | hidden |
|---|---|---|:--:|---|---|:--:|
| yhSno | isvr_no | EQ | ✅ | — | STRING | — |

**호출**: `GET /api/provide/opn-impact-investigation?yhSno=1234567890`

---

### 5.10 `megokrapi-ngw04-01` — 수질검사결과 TMP (A7)

**Operation**
| 필드 | 값 |
|---|---|
| operationId | `megokrapi-ngw04-01` |
| operationName | 수질검사결과 (TMP 페이징) |
| tableName | `api_prv_tmp_megokr_api` |
| orderByColumn | `sn` |
| orderByDirection | ASC |
| pageSize | 10000 |
| maxPageSize | 10000 |

**Columns** (126개)
- SN → alias `QLTWTR_INSPCT_SN`
- WT_* 125개 (`wt_tot_col_cnts` .. `wt_wtl`) alias 대문자

> 컬럼이 많아 UI 등록 시 일괄 추가 기능 필요. 엔티티(`ApiPrvTmpMegokrApi`) 에서 wt_* 필드 이름 추출 후 각각 대문자 alias.

**Params**

| paramName | columnName | operator | required | defaultValue | dataType | hidden |
|---|---|---|:--:|---|---|:--:|
| sn | sn | GTE | — | `0` | NUMBER | — |
| gennum | gennum | EQ | — | — | NUMBER | — |

**호출**: `GET /api/provide/megokrapi-ngw04-01?sn=0&pageSize=10000`

---

### 5.11 `megokrapi-ngw03` — 수질검사개요 단건 (B1)

> **타겟 정정**: 레거시 `selectNgw03` 은 원본 `TM_GD30301` 에서 조회 → 우리는 별개로 이식된 **`api_prv_tm_gd110301`** 타겟에서 서빙. `api_prv_tmp_megokr_api` 와는 별개 PG 테이블이라 WHERE 로 대체 불가.

**Operation**
| 필드 | 값 |
|---|---|
| operationId | `megokrapi-ngw03` |
| operationName | 수질검사개요 (단건) |
| tableName | `api_prv_tm_gd110301` |
| orderByColumn | `wq_insp_sn` |
| pageSize | 100 |

**Columns** (22개 메타 — 레거시 응답 alias 매핑)

컬럼 이름 체계가 표준화되면서 레거시 컬럼명 → 표준화 컬럼명 매핑이 필요:

| # | 프로바이드 컬럼 (표준) | 응답필드명 (레거시) | # | 프로바이드 컬럼 (표준) | 응답필드명 (레거시) |
|:--:|---|---|:--:|---|---|
| 1 | wq_insp_sn | QLTWTR_INSPCT_SN | 12 | last_chg_dt | LAST_CHANGE_DT |
| 2 | gwel_no | GENNUM | 13 | ugwtr_usg_cd | UGRWTR_PRPOS_CODE |
| 3 | exmn_yr | INVSTG_YEAR | 14 | dkpp_yn | DRNK_AT |
| 4 | cycl | ODR | 15 | ugwtr_wqmn_inpt_inst_cd | UGRWTR_WQN_INPUT_INSTT_CODE |
| 5 | dph_clsf_cd | DPH_CL_CODE | 16 | wq_insp_imps_rsn_cn | QLTWTR_INSPCT_IMPRTY_RESN_CTNT |
| 6 | dph_vl | DPH_VALUE | 17 | ctpv_nm | BRTC_NM |
| 7 | wtsmp_ymd | WATSMP_DE | 18 | sgg_nm | SIGUN_NM |
| 8 | wq_insp_ymd | QLTWTR_INSPCT_DE | 19 | emd_nm | EMD_NM |
| 9 | data_inpt_ymd | DTA_INPUT_DE | 20 | li_nm | LI_NM |
| 10 | cfmtn_ymd | DCSN_DE | 21 | addr | ADDR |
| 11 | frst_reg_dt | FRST_REGIST_DT | 22 | pub_gwel_yn | PUBWELL_AT |

**Params**

| paramName | columnName | operator | required | defaultValue | dataType | hidden |
|---|---|---|:--:|---|---|:--:|
| gennum | gwel_no | EQ | ✅ | — | NUMBER | — |
| invstgYear | exmn_yr | LTE | — | `2024` | STRING | ✅ (**매년 수동 갱신**) |

> **JOSACODE 필터 제외**: 레거시 `selectNgw03` 은 `GENNUM IN (SELECT GENNUM FROM TM_GD10001 WHERE JOSACODE IN '104,112,113,215,216')` 서브쿼리로 JOSACODE 필터를 적용하지만, `api_prv_tm_gd110301` 에는 JOSACODE 컬럼이 없음 (원래 TM_GD30301 에도 없음 — TM_GD10001 JOIN 필요). 엔진이 JOIN/서브쿼리 불가하므로 이 필터는 적용 불가. GENNUM 단건 조회만 제공.
> `invstgYear` default 는 레거시의 `SYSDATE-2` 년도 계산을 정적으로 대체. 매년 초에 갱신 필요 (또는 hidden=false 로 클라이언트 파라미터화).

**호출**: `GET /api/provide/megokrapi-ngw03?gennum=12345`

---

### 5.12 `megokrapi-ngw03-01` — 수질검사개요 목록 (B2)

> **A7 타겟 재활용**: 레거시 `selectNgw03_01` 은 `TMP_MEGOKR_API` 에서 조회 → 우리는 **`api_prv_tmp_megokr_api`** (A7 과 같은 타겟) 에서 WHERE 로 서빙.

B2 의 컬럼 스코프는 A7 의 메타 24 컬럼 중 아래 22개 해당 (아래는 `api_prv_tmp_megokr_api` 엔티티 컬럼명 기준):

**Operation**
| 필드 | 값 |
|---|---|
| operationId | `megokrapi-ngw03-01` |
| operationName | 수질검사개요 (목록) |
| tableName | `api_prv_tmp_megokr_api` |
| orderByColumn | `sn` |
| pageSize | 10000 |
| maxPageSize | 10000 |

**Columns** (22 메타, 레거시 응답 alias)

| # | 프로바이드 컬럼 | 응답필드명 | # | 프로바이드 컬럼 | 응답필드명 |
|:--:|---|---|:--:|---|---|
| 1 | qltwtr_inspct_sn | QLTWTR_INSPCT_SN | 12 | last_change_dt | LAST_CHANGE_DT |
| 2 | gennum | GENNUM | 13 | ugrwtr_prpos_code | UGRWTR_PRPOS_CODE |
| 3 | invstg_year | INVSTG_YEAR | 14 | drnk_at | DRNK_AT |
| 4 | odr | ODR | 15 | ugrwtr_wqn_input_instt_code | UGRWTR_WQN_INPUT_INSTT_CODE |
| 5 | dph_cl_code | DPH_CL_CODE | 16 | qltwtr_inspct_imprty_resn_ctnt | QLTWTR_INSPCT_IMPRTY_RESN_CTNT |
| 6 | dph_value | DPH_VALUE | 17 | brtc_nm | BRTC_NM |
| 7 | watsmp_de | WATSMP_DE | 18 | sigun_nm | SIGUN_NM |
| 8 | qltwtr_inspct_de | QLTWTR_INSPCT_DE | 19 | emd_nm | EMD_NM |
| 9 | dta_input_de | DTA_INPUT_DE | 20 | li_nm | LI_NM |
| 10 | dcsn_de | DCSN_DE | 21 | addr | ADDR |
| 11 | frst_regist_dt | FRST_REGIST_DT | 22 | pubwell_at | PUBWELL_AT |

**Params**

| paramName | columnName | operator | required | defaultValue | dataType | hidden |
|---|---|---|:--:|---|---|:--:|
| sn | sn | GTE | — | `0` | NUMBER | — |
| gennum | gennum | EQ | — | — | NUMBER | — |

> DECODE 보정 (INVSTG_YEAR/ODR) 은 엔진 불가. A7 적재 시점의 데이터 그대로. 필요시 추후 DDL 수준에서 보정.

**호출**: `GET /api/provide/megokrapi-ngw03-01?sn=0&pageSize=10000`

---

### 5.13 `megokrapi-ngw04` — 수질검사결과 단건 (B3) ⏸ **이번 범위 제외**

레거시 `selectNgw04` 는 `TM_GD30302` 의 EAV 구조를 PIVOT 해서 125 WT_* 컬럼으로 반환.

**이번 범위 제외 이유**:
- 현재 이식된 타겟 `api_prv_tm_gd110302` 는 **EAV 원본 그대로** (QLTWTR_INSPCT_SN + WLTTS_ID_CODE + RESULT_VALUE 3컬럼 장축형) → 레거시 응답(125컬럼 횡축형) 과 구조 자체가 다름
- 엔진은 PIVOT 불가
- `api_prv_tmp_megokr_api` 는 **별개 타겟** — WHERE 로 대체 불가 (판정 기준 위반)

→ **Type B `ApiPrvNgw04LoadStep` 이식 완료 후** `api_prv_ngw04` 타겟 operation 으로 추가 등록.

---

## 6. 등록 순서 제안 (난이도 / 검증 가치)

**총 12건** (B3 는 Type B 이식 후로 미룸):

1. **`opn-impact-investigation`** (A6) — 컬럼 7개, 파라미터 1개, 가장 단순 → UI 등록 흐름 & 호출 성공 테스트
2. **`megokrapi-ngw08`** (A1) — 파라미터 EQ 기본 패턴
3. **`megokrapi-ngw09-01`** (A3) — Params 없는 목록 조회 + 페이징
4. **`drought119-select`** (A4) — 파라미터 없는 전체 목록 + SDE 타겟
5. **`megokrapi-ngw09`** (A2) — 단건 조회 WHERE
6. **`opn-national-groundwater`** ~ **`opn-basic-survey`** (A5 4건) — `hidden=true + default` 로 **JOSACODE 고정** 패턴 확립. 1건 등록 후 나머지 3건 복제해 josacode default 값만 변경
7. **`megokrapi-ngw04-01`** (A7) — 126 컬럼 거대, pageSize 10000 — 대용량 응답 테스트
8. **`megokrapi-ngw03`** (B1) — **별개 타겟 `api_prv_tm_gd110301`** 검증. 표준화 컬럼명 → 레거시 alias 매핑 많음
9. **`megokrapi-ngw03-01`** (B2) — A7 타겟(`api_prv_tmp_megokr_api`) 재활용 검증. 컬럼 스코프만 축소 (126 → 22)

## 7. 한계 / 추후 개선 대상

| 한계 | 영향 | 개선 방향 |
|---|---|---|
| ADDR 문자열 조합 (`\|\|`) | `opn-national-groundwater` 등 ADDR 단일 컬럼만 제공 | 엔진에 `CONCAT` 가공 타입 추가 or 적재 시점에 조합 컬럼 생성 |
| 동적 연도 계산 (`SYSDATE-2`) | `megokrapi-ngw03` 의 invstgYear default 정적 | 매년 수동 갱신 / 파라미터 전달 / 엔진에 `CURRENT_YEAR` 상수 추가 |
| DECODE 보정 (B2 INVSTG_YEAR/ODR) | 원본 값 그대로 제공 | 적재 시점 보정 |
| 126 컬럼 UI 일괄 등록 | 수작업 부담 | UI 에 "엔티티 기반 자동 컬럼 생성" 기능 추가 (추후) |
| JOIN 불가 | 복합 소스 API (Type B 대부분) 는 Step 이식 필수 | Step 작성 (별도 계획) |

## 8. Postman 테스트 팁

- Base URL: `http://localhost:8095`
- 모든 응답은 JSON (기본): `{ data: [...], pagination: { page, pageSize, totalCount, totalPages } }`
- 페이지 파라미터: `?page=1&pageSize=100`
- API Key 는 임시 단계 — 현재 `ApiKeyValidationService` 가 검증 실패 시 401 반환하므로 **필요시 `MockApiKeyController` 로 발급** 또는 검증 우회 설정 확인
- SQL 문제 디버그: `logging.level.com.gims.provider: DEBUG` (이미 설정됨) — 실행된 SQL 이 로그에 찍힘

---

## 개정 이력

- 2026-04-24: 최초 작성. 이식 완료된 7개 타겟에 대한 13 operation 스펙 정리.
- 2026-04-24: 판정 기준 명료화 ("우리 PG 타겟 + WHERE 커버 가능" 여부).
  - **B1 `megokrapi-ngw03` 타겟 정정**: `api_prv_tmp_megokr_api` → **`api_prv_tm_gd110301`** (별개 PG 타겟). 표준화 컬럼명(wq_insp_sn, gwel_no, exmn_yr 등) 기반 컬럼/파라미터 재작성. JOSACODE 필터는 엔진 한계로 제외.
  - **B3 `megokrapi-ngw04` 제외**: `api_prv_tm_gd110302` 가 EAV 원본이라 PIVOT 필요 — Type B `ApiPrvNgw04LoadStep` 이식 후 등록.
  - **최종 13건 → 12건** + B1 타겟 정정.
