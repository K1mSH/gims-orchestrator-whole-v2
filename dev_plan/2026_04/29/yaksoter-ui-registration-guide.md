# 약수터 — UI 등록 정보 가이드

> **작성일**: 2026-04-29
> **상위 계획서**: `dev_plan/2026_04/29/yaksoter-resume-replace-api.md`
> **매핑 원본**: `dev_plan/2026_04/29/yaksoter-field-mapping-v2.md`
> **목적**: 사용자가 UI 화면에서 입력할 모든 값 한 곳에 정리

---

## 0. 등록 순서

```
A. API Key 등록 (api-collector 자체 키 관리)
   ↓ (키 ID 발급)
B. Endpoint 1 등록 — 약수터 제원 (tm_gd010310)
C. Endpoint 2 등록 — 약수터 수질 (td_gd010310)
   ↓ (등록 완료)
D. 수동 실행 — 양쪽 Endpoint 각각 1회 → 소스 PG 적재 확인
   ↓ (코드 작업 끝나면)
E. Agent 등록 — DMZ Others SND / Internal RCV / Internal Loader (orchestrator)
F. 스케줄 등록 — 분기 1회
```

---

## A. API Key — 등록 불필요 ✅ (4/29 발견)

`MockApiController.getApiKeys():430-468` 에 `id=3 / "공공데이터포털 인증키"` 로 우리가 받은 ServiceKey 가 **이미 박혀 있음**:

```
qaj/1MknxOAoegbjhf6jCDH8pSH4Pt8Je88U572wPHObU85DnuxJ/vmoBeNlry6ELaUkjgmXHz+EPWst7gZcOw==
```

→ 별도 등록 작업 X. 아래 §B/§C 의 `serviceKey` Param 의 `staticValue` 에 그냥 **`3`** 만 박으면 됨.

> 운영 시 외부 키 관리에서 가져올 예정 (지금은 mock 자체 데이터).

---

## B. Endpoint 1 — 약수터 제원

> **등록 화면 흐름** (page.tsx 라인 817 기준): 매핑 영역은 **테스트 호출 → 데이터 루트 선택** 후 자동으로 펼쳐짐. 신규 등록 폼 처음엔 안 보이는 게 정상.
>
> ```
> 신규 등록 → ① 기본정보 → ② Param 입력 → ③ 테스트 호출 버튼 ★
>          → ④ 응답 트리에서 'getSgisDrinkWaterList.item' 선택 ★
>          → ⑤ Target Datasource(dmz) + Target Table(tm_gd010310) 선택
>          → ⑥ 매핑 영역 자동 펼쳐짐 (sourceField 89개 자동 채워짐)
>          → ⑦ 24개만 targetColumn 매칭, 나머지 65개는 비워둠 → 등록
> ```

### B-1. 기본 정보

| 항목 | 값 |
|------|-----|
| Endpoint 이름 | `약수터-제원` (자유) |
| 설명 | 토양지하수 먹는물 공동시설(약수터) 제원정보 — `getSgisDrinkWaterList` |
| URL | `https://apis.data.go.kr/1480523/WaterQualityService/getSgisDrinkWaterList` |
| HTTP 메서드 | `GET` |
| 응답 형식 | `JSON` |
| **dataRootPath** | `getSgisDrinkWaterList.item` |
| Target Datasource | **`dmz`** (PG 29001 / db=`dev` / id=1018) — `api_collector` 별도 DB 폐기, dev 통합 |
| Target Table | `tm_gd010310` |

### B-2. Param (5건 — 양쪽 Endpoint 동일)

| # | name | 입력 방식 | 값 | 필수 | 비고 |
|--:|------|---------|----|:----:|------|
| 1 | `serviceKey` | API Key 참조 | (§A 의 키 ID) | Y | **소문자 s 주의**, `isApiKeyRef=true`, `staticValue` = §A 키 ID |
| 2 | `pageNo` | 정적 | `1` | N | 페이징 자동 (api-collector 가 처리) |
| 3 | `numOfRows` | 정적 | `1000` | N | totalCount 3231 (yyyy=2020) → 4페이지 |
| 4 | `resultType` | 정적 | `JSON` | N | 대문자/소문자 모두 허용되나 `JSON` 권장 |
| 5 | `yyyy` | 동적 / 스케줄 | `2020` (테스트) → 운영 시 `현재년도-1` | N | 복수 콤마 `2020,2021` 가능 |

### B-2.5. UPSERT + 중복키 (4/29 2차 정정 — B-1 채택)

> 4/29 토론 2차에서 B-1 (자연키 UK + DO UPDATE) 채택 — 첫 토론의 B-2 (UK 없음) 뒤집힘.
> 매 실행 시 source 누적 막기 위해 **자연키 UK + UPSERT 활성화**.

| 항목 | 값 |
|------|-----|
| **UPSERT 체크박스** | **✅ 켜기** |
| isConflictKey 체크 컬럼 (제원) | `brnch_no` + `brnch_std_cd` (2개) |

매 실행 시: 같은 약수터 (brnch_no+brnch_std_cd) 들어오면 ON CONFLICT DO UPDATE → 최신값 갱신.

### B-3. 필드 매핑 (24건)

> `dev_plan/2026_04/29/yaksoter-field-mapping-v2.md` §2 표대로 24건 등록.
> sourceField 는 모두 **UPPER_SNAKE_CASE** / targetColumn 모두 소문자.

> **⚠️ 4/29 발견 — UI 한계**: transformType select 외에 **추가 config 입력 UI 없음** (page.tsx 라인 860-870). SUBSTRING `0,1` / REPLACE 정규식 같은 파라미터 입력 불가.
> → **변환이 config 필요한 행은 NONE 으로 두고, Loader 의 커스텀 Step (`YaksoterLoadStep`) 에서 변환 처리** — B-2 흐름과 일치.
> → 응답 실측 결과 우리 응답값 특성상 대부분 변환 불필요 (예: `ABL_AT="N"` 이미 1글자, `INS_DATE="19950816"` 이미 8자리).

| 변환 | sourceField → targetColumn | UI TransformType | 비고 |
|------|---------------------------|------------------|------|
| **NUMBER (UI 가능)** | `ROWNO` → `seq` | `NUMBER` | config 불필요, UI 만으로 동작 |
| **NUMBER (UI 가능)** | `DAY_AVG` → `day01_avg_usr_cnt` | `NUMBER` | 동일 |
| **Loader 처리** | `ABL_AT` → `abl_yn` | `NONE` | 운영 시 길이 초과 가능성 → Loader 에서 SUBSTRING(0,1) 방어 |
| **Loader 처리** | `DEL_YN` → `del_yn` | `NONE` | 동일 |
| **Loader 처리** | `INS_DATE` → `instl_ymd` | `NONE` | 운영 시 `1995.08.16` 형식 들어올 수 있음 → Loader 에서 점/하이픈 제거 |
| **Loader 처리** | `ABL_DE` → `abl_ymd` | `NONE` | 동일 |
| **그대로** | `LEGACY_CODE_NO` → `brnch_no` | `NONE` | — |
| **그대로** | `SPOT_NM` → `brnch_nm` | `NONE` | — |
| **그대로** | `SPOT_STD_CODE` → `brnch_std_cd` | `NONE` | — |
| **그대로** | `INFO_CREAT_INSTT_NM` → `info_crt_inst_nm` | `NONE` | — |
| **그대로** | `CL_MIDDLE_NM` → `chrtc_mclsf` | `NONE` | — |
| **그대로** | `CL_SMALL_NM` → `chrtc_sclsf` | `NONE` | — |
| **그대로** | `DO_NM` → `ctpv_nm` | `NONE` | — |
| **그대로** | `CTY_NM` → `sgg_nm` | `NONE` | — |
| **그대로** | `ADRES` → `addr` | `NONE` | (응답 길이 < 250 검증, 초과 시 `SUBSTRING 0,250`) |
| **그대로** | `ADMCODE` → `stdg_cd` | `NONE` | — |
| **그대로** | `CRDNT_X` → `xcrd` | `NONE` | — |
| **그대로** | `CRDNT_Y` → `ycrd` | `NONE` | — |
| **그대로** | `CHARGE` → `pic` | `NONE` | — |
| **그대로** | `OFFICE` → `pic_nm` | `NONE` | — |
| **그대로** | `OFFICE_TEL` → `pic_cnpl` | `NONE` | — |
| **그대로** | `BUILDING_NO` → `bno` | `NONE` | — |
| **그대로** | `LOC_JIBUN` → `lctn_lotno` | `NONE` | — |
| **그대로** | `COMMT` → `rmrk` | `NONE` | — |

> `TransformType` 의 `REPLACE` 가 정규식 입력 가능한지 확인 필요 (4/24 LOOKUP 추가 시점 기준). 안 되면 사용자가 보고 → claude 가 `DATE_FORMAT` 같은 다른 옵션으로 대체.

---

## C. Endpoint 2 — 약수터 수질

### C-1. 기본 정보

| 항목 | 값 |
|------|-----|
| Endpoint 이름 | `약수터-수질` (자유) |
| 설명 | 토양지하수 먹는물 공동시설(약수터) 수질검사결과 — `getSgisDrinkWaterList` |
| URL / HTTP / 응답 | **§B-1 과 동일** |
| **dataRootPath** | `getSgisDrinkWaterList.item` (동일) |
| Target Datasource | **`dmz`** (PG 29001 / db=`dev` / id=1018) — `api_collector` 별도 DB 폐기, dev 통합 |
| Target Table | `td_gd010310` |

### C-2. Param

> **§B-2 와 동일** (같은 API → 같은 Param). 별도 등록.

### C-2.5. UPSERT + 중복키 (B-1)

| 항목 | 값 |
|------|-----|
| **UPSERT 체크박스** | **✅ 켜기** |
| isConflictKey 체크 컬럼 (수질) | `brnch_no` + `yr` + `qtr` + `wtsmp_ymd` (4개) |

### C-3. 필드 매핑 (67건)

> `dev_plan/2026_04/29/yaksoter-field-mapping-v2.md` §3 표대로 67건 등록.

**12 기본** (B-3 룰 동일 — config 필요한 변환은 NONE 으로 두고 Loader 에서 처리):

| 변환 | sourceField → targetColumn | UI TransformType | 비고 |
|------|---------------------------|------------------|------|
| 그대로 (공통) | `LEGACY_CODE_NO` → `brnch_no` | `NONE` | — |
| 그대로 (공통) | `SPOT_STD_CODE` → `brnch_std_cd` | `NONE` | — |
| 그대로 | `YYYY` → `yr` | `NONE` | — |
| 그대로 | `PERIOD` → `qtr` | `NONE` | — |
| 그대로 | `INSP_CHECK` → `insp_yn` | `NONE` | — |
| 그대로 | `UN_INSP_DESC` → `un_insp_rsn` | `NONE` | — |
| 그대로 | `ACCEPT_YN` → `stblt_yn` | `NONE` | — |
| 그대로 | `SUIT` → `stblt` | `NONE` | — |
| 그대로 | `UNSUIT` → `icpt` | `NONE` | — |
| Loader 처리 | `INSP_RST` → `icpt_artcl` | `NONE` | Target 100자 잘림 방어 → Loader 에서 SUBSTRING(0,100) |
| Loader 처리 | `FAIL_DESC` → `icpt_actn_mttr` | `NONE` | 동일 |
| Loader 처리 | `SAMP_DATE` → `wtsmp_ymd` | `NONE` | `2020-09-22` → `20200922` 변환 → Loader 에서 정규식 |

**55 측정항목** (모두 `NONE`):

```
ITEM_GENBACLOW   → gnrl_germ_lowtmp
ITEM_GENBACMID   → gnrl_germ_mesph
ITEM_TOTBAC      → cfbctr
ITEM_BAC         → clbcl
ITEM_FESTR       → fcfs
ITEM_BRANFUNGUS  → fcstrcci
ITEM_GRGUNGUS    → paergns
ITEM_SALMOL      → slmn
ITEM_SEGEL       → shigla
ITEM_SULFUNGUS   → sfsra
ITEM_YERSINIA    → yersna
ITEM_PB          → pmbm
ITEM_F           → flrn
ITEM_GAS         → asnc
ITEM_SE          → se
ITEM_HG          → mrcr
ITEM_CN          → cyn
ITEM_CR6         → chrm
ITEM_NO3AM       → amng
ITEM_NO3N        → ntng
ITEM_CD          → cdmm
ITEM_BORON       → bor
ITEM_BRO3        → bro3
ITEM_PHENOL      → phnl
ITEM_DIAZN       → dznn
ITEM_PARAT       → parat
ITEM_PENITRO     → fenitro
ITEM_CARBARYL    → carbaryl
ITEM_TCET        → tcrn
ITEM_TECE        → ttrt
ITEM_TCE         → tcrt
ITEM_DCM         → dcmt
ITEM_BENZENE     → bnzn
ITEM_TOLUENE     → tln
ITEM_ETILBEN     → etbz
ITEM_XYLENE      → xln
ITEM_DCE         → dcty
ITEM_CCL4        → ccl4
ITEM_DBCP        → dbcp
ITEM_C4H8O2      → diox14
ITEM_GRADIENT    → tds
ITEM_KMN         → ptpm_cnsm_qnt
ITEM_SMELL       → smll
ITEM_COLOR       → crmty
ITEM_CU          → cppr
ITEM_ABS         → abs
ITEM_PH          → ph
ITEM_ZN          → zn
ITEM_CL          → crrd
ITEM_FE          → iron
ITEM_MN          → mngn
ITEM_MUDDY       → trbt
ITEM_SO42        → eccbt_ion
ITEM_AL          → almn
ITEM_URAN        → uran
```

---

## D. 수동 실행 — 검증

### D-1. Endpoint 1 (제원) 수동 실행

- 화면: `/api-collect/{id}` → 테스트 실행 또는 즉시 실행 버튼
- 입력: 위 §B-2 의 Param (yyyy=2020 으로)
- 기대: `tm_gd010310` 에 약 3231 row INSERT (totalCount 와 일치)

### D-2. Endpoint 2 (수질) 수동 실행

- 동일 방식, `td_gd010310` 에 동일 row 수 INSERT

### D-3. 검증 SQL

```sql
SELECT count(*), max(extracted_at) FROM tm_gd010310;
SELECT count(*), max(extracted_at) FROM td_gd010310;
```

기대: 양쪽 약 3231 row, `extracted_at` 동일 시간대.

---

## E. (코드 작업 후) Agent 등록

> **claude 가 §B.0~B.3y 코드 작업 끝낸 후** 이 섹션 갱신해서 너에게 알려줄게.

대략 흐름:
1. SND Agent — `dmz-others-snd-yaksoter` (sync-agent-others, 8085)
2. Int RCV Agent — `internal-yaksoter-rcv` (sync-agent-bojo-int, 8092)
3. Int Loader Agent — `internal-yaksoter-loader` (sync-agent-bojo-int, 8092)
4. 각 Agent 의 스케줄 등록 (분기 1회)

---

## F. 함정 / 주의사항

1. **`serviceKey` 는 소문자 s** — 활용신청 화면엔 `ServiceKey` 로 표기되지만 실제 호출은 소문자.
2. **`isApiKeyRef=true` 누락 금지** — staticValue 에 키 ID (UUID 또는 PK) 를 박는 게 아니라 raw key 박으면 키 노출 위험.
3. **dataRootPath** 양쪽 Endpoint 동일 — 같은 API 라 응답 구조 같음.
4. **PERIOD 비정형** (`'1/4'` `'9월'` 혼재) — 정형화 시도 ❌, 그대로 적재.
5. **`numOfRows` 1000 으로 시작** — 한도 10000 이지만 안전 마진. 막히면 줄임.
6. **공통 2 매핑** (`LEGACY_CODE_NO` / `SPOT_STD_CODE`) — Endpoint 1 과 2 양쪽에 모두 등록 필수.
