# 약수터 수질검사 API 수집 파이프라인 — 구현 계획

> **작성일**: 2026-04-15
> **상태**: 계획
> **선행 분석**: `dev_plan/2026_04/14/` (테이블 분석, 필드 매핑, 파이프라인 계획)
> **참조 패턴**: 뉴스/나라장터 API Collector 내부망 전송 (4/14 완성)

---

## 0. 레거시 대비 변경 사항 및 근거

> 레거시 `AdminBatchServiceImpl.waterQualityWriter()` 로직을 분석한 결과,
> 우리 아키텍처(API Collector → SND → RCV → Loader)에서는 일부 로직을 단순화할 수 있다고 판단.
> 레거시 코드: `D:\dev\project\GIMS\GIMS_SOURCE\newgims_v2\src\gims\service\impl\AdminBatchServiceImpl.java`
> 레거시 SQL: `src\egovframework\sqlmap\com\gims\sql_adminInfo.xml` (adm.selectWaterQuality*, adm.insertWaterQuality*, adm.updateWaterQualityInfo)

### 변경 1: 중복 검증 위치 — Loader → API Collector

| 항목 | 레거시 | 우리 |
|------|--------|------|
| 중복 검증 위치 | 적재 시점 (waterQualityWriter) | API Collector 수집 시점 (ON CONFLICT) |
| 제원 판단 | Target(TM_GD20310) 조회 → INSERT/UPDATE/SKIP | 소스 PG conflict-key → ON CONFLICT DO UPDATE |
| 수질 판단 | Target(TD_GD20310) 1차 4키 + 2차 전필드 비교 | 소스 PG conflict-key(4키) → ON CONFLICT DO UPDATE |

**근거**: 레거시의 모든 조회가 "본인 테이블"만 참조하고 다른 테이블을 참조하지 않음.
우리 구조에서는 Target의 모든 데이터가 반드시 API Collector를 경유하므로,
소스 테이블 상태 = Target 상태. 따라서 소스에서 중복 검증하면 Loader에서 재검증 불필요.
또한 이 배치가 **유일한 데이터 소스**임을 확인 (v2에서 관리자 수동 등록/다른 코드의 쓰기 없음).

### 변경 2: 수질 전 필드 비교 → 4키 conflict-key로 단순화

| 항목 | 레거시 | 우리 |
|------|--------|------|
| 수질 중복 판단 | 1차 4키 조회 → 2차 65개 전 필드 AND 비교 → 완전 동일만 SKIP | 4키 conflict-key → 동일 키면 UPDATE |
| 같은 4키 + 다른 수질값 | INSERT 허용 (중복 행 발생) | UPDATE (최신 값으로 갱신) |

**근거**: 레거시의 2단계 전 필드 비교는 PK/UK 없는 구조에서의 방어 코드로 추정.
같은 지점+연도+분기+채수일자에 다른 수질값이 들어오는 것은 정상적 시나리오가 아님.
레거시 DB의 4키 중복 데이터도 테스트 오염으로 확인.
**⚠️ 미결**: 팀장 확인 필요 — 전 필드 비교가 비즈니스 요구사항이면 커스텀 LoadStep으로 교체 가능.

### 변경 3: Target PK/UK

| 항목 | 레거시 | 우리 |
|------|--------|------|
| 제원 PK/UK | 없음 | 없음 (레거시 동일). SN(IDENTITY) JPA용 추가 |
| 수질 PK/UK | 없음 | 없음 (레거시 동일). SN(IDENTITY) JPA용 추가 |
| 소스 PG UK | — | 제원: `legacy_code_no + spot_std_code`, 수질: `legacy_code_no + yyyy + period + samp_date` (ON CONFLICT용, 필수) |

**근거**: 레거시에 PK/UK 없음 확인 (`user_constraints` 조회). Target Oracle은 레거시 존중하여 UK 안 걸음.
소스 PG에만 UK 설정 — PostgreSQL ON CONFLICT 동작에 필수.
SN(IDENTITY)은 JPA @Id + 추적(execution_id, source_refs)용. 레거시 로직에 영향 없음.

### 변경 4: Loader 방식 — 커스텀 Step → SimpleLoadStep

| 항목 | 레거시 | 우리 |
|------|--------|------|
| 적재 방식 | 건건이 SELECT → 조건 분기 → INSERT/UPDATE | SimpleLoadStep MERGE (1:1) |
| 커스텀 로직 | 제원 변경감지 + 수질 2단계 비교 | 불필요 (API Collector에서 처리) |

**근거**: 변경 1~2에 의해 Loader 도착 시점에서는 이미 검증된 데이터만 존재.
MERGE(NOT MATCHED→INSERT, MATCHED→UPDATE)로 충분.
향후 전 필드 비교 필요 시 기존 파이프라인 구조상 **LoadStep만 교체**하면 됨.

### 변경 5: 테이블명/컬럼명 — 표준화 적용

| 항목 | 레거시 | 우리 |
|------|--------|------|
| 테이블명 | TM_GD20310 / TD_GD20310 | TM_GD010310 / TD_GD010310 (11자리 표준화) |
| 컬럼명 | 현행 (LEGACY_CODE_NO 등) | 환경부표준 (BRNCH_NO 등) |
| 타입/길이 | 현행 | 표준화 자료 기준 (`docs/Standardizedtable/` 참조) |

**근거**: 표준화 요구사항. 출처: `docs/Standardizedtable/TM_GD20310.txt`, `TD_GD20310.txt`.
필드 변환은 API Collector 필드 매핑에서 수행 → 전 구간 표준화 컬럼명으로 통일.

---

## 1. 목표

data.go.kr 약수터 수질검사 API(`getSgisDrinkWaterList`)를 기존 파이프라인으로 수집하여
내부망 Oracle에 제원(`TM_GD010310`) + 수질결과(`TD_GD010310`) 적재.

레거시 `AdminBatchServiceImpl`의 적재 로직을 Int Loader 커스텀 Step으로 이관.

---

## 2. 데이터 흐름

```
data.go.kr API (getSgisDrinkWaterList)
  │
  │  ← Endpoint 2개 등록 (같은 API, 필드 매핑만 다름)
  ↓
[DMZ] API Collector (8084)
  ├─ tm_gd010310      (제원 24개 필드)
  └─ td_gd010310  (수질 56개+ 필드)
  │
  ↓  SND (sync-agent-others, 8085)
  ├─ if_snd_tm_gd010310
  └─ if_snd_td_gd010310
  │
  ↓  Int RCV (sync-agent-bojo-int, 8092) — Proxy 8093 경유
  ├─ if_rsv_tm_gd010310      (Oracle)
  └─ if_rsv_td_gd010310  (Oracle)
  │
  ↓  Int Loader (sync-agent-bojo-int, 8092) — 커스텀 Step
  ├─ TM_GD010310  (제원: BRNCH_NO+BRNCH_STD_CD 복합키 UPSERT, BRNCH_NM/ADDR 변경감지)
  └─ TD_GD010310  (수질: 2단계 중복검사 — 1차 4키 → 2차 전필드 비교, 동일하면 SKIP)
```

- SND → RCV: 제원/수질 **평행하게** 2개 테이블 전송
- Loader: 커스텀 Step 1개에서 제원+수질 순차 처리

---

## 3. 결정 사항

| 항목 | 결정 |
|------|------|
| 1 API → 2 테이블 분리 | **(A) Endpoint 2개 등록** — 같은 API URL, 필드 매핑만 다르게 |
| Loader 방식 | **SimpleLoadStep** (API Collector에서 중복 검증 완료, 커스텀 불필요) |
| Target 테이블명 | `TM_GD010310` (제원), `TD_GD010310` (수질) — 11자리 표준화 기준 |
| 필드명 | 환경부표준 컬럼명 사용 (매핑 문서 기반 변환) |
| 중복 검증 | **API Collector에서 처리** — 소스 PG에 UK + ON CONFLICT DO UPDATE |
| 소스 PG UK | 제원: `legacy_code_no + spot_std_code`, 수질: `legacy_code_no + yyyy + period + samp_date` |
| Target Oracle PK/UK | **없음 (레거시 동일)**. SN만 JPA @Id용 IDENTITY로 추가 |
| SEQ (제원) | API 응답 rowno 그대로 적재 (자동채번 아님) |
| 향후 대비 | 팀장 확인 후 수질 전 필드 비교 필요 시 커스텀 LoadStep으로 교체 가능 |

---

## 4. 구현 항목 상세

### 4-1. API Collector — Endpoint 등록 (UI 작업)

**Endpoint 1: 약수터 제원**

| 항목 | 값 |
|------|-----|
| URL | `http://apis.data.go.kr/1480523/WaterQualityService/getSgisDrinkWaterList` |
| 파라미터 | `serviceKey`(API키참조), `numOfRows=30000`, `pageNo=1`, `resultType=JSON`, `yyyy` |
| dataRootPath | `getSgisDrinkWaterList.item` (또는 `body.items.item` — 테스트 필요) |
| Target 테이블 | `tm_gd010310` |
| 필드 매핑 | 24개 (legacyCodeNo→legacy_code_no, spotNm→spot_nm 등) |

**Endpoint 2: 약수터 수질결과**

| 항목 | 값 |
|------|-----|
| URL | 동일 |
| 파라미터 | 동일 |
| dataRootPath | 동일 |
| Target 테이블 | `td_gd010310` |
| 필드 매핑 | 65개+ (legacyCodeNo, yyyy, period + 수질 측정항목 전체) |

> API 키 발급이 현재 블로커 — 담당자 확인 필요. 키 확보 전까지 MockApiController로 테스트 가능.

### 4-2. 소스 테이블 DDL (api_collector DB, PG)

```sql
-- 제원 (컬럼명: 환경부표준, API Collector 필드 매핑에서 변환)
CREATE TABLE tm_gd010310 (
    sn BIGSERIAL PRIMARY KEY,
    seq VARCHAR(10),                    -- 순번 (API rowno 그대로)
    brnch_no VARCHAR(10),              -- 지점번호
    brnch_nm VARCHAR(100),             -- 지점명
    brnch_std_cd VARCHAR(20),          -- 지점표준코드
    info_crt_inst_nm VARCHAR(50),      -- 정보생성기관명
    chrtc_mclsf VARCHAR(4),            -- 특성중분류
    chrtc_sclsf VARCHAR(4),            -- 특성소분류
    ctpv_nm VARCHAR(40),               -- 시도명
    sgg_nm VARCHAR(30),                -- 시군구명
    addr VARCHAR(500),                 -- 주소
    stdg_cd VARCHAR(10),               -- 법정동코드
    xcrd VARCHAR(20),                  -- X좌표
    ycrd VARCHAR(20),                  -- Y좌표
    abl_yn VARCHAR(4),                 -- 폐지여부
    abl_ymd VARCHAR(13),               -- 폐지일자
    day01_avg_usr_cnt VARCHAR(10),     -- 1일평균이용자수
    pic VARCHAR(60),                   -- 담당자 (CHARGE)
    instl_ymd VARCHAR(10),             -- 설치일자
    del_yn VARCHAR(4),                 -- 삭제여부
    pic_nm VARCHAR(60),                -- 담당자명 (OFFICE)
    pic_cnpl VARCHAR(50),              -- 담당자연락처
    bno VARCHAR(30),                   -- 건물번호
    lctn_lotno VARCHAR(20),            -- 소재지_지번
    rmrk VARCHAR(500),                 -- 비고
    -- IF 메타
    link_status VARCHAR(20) DEFAULT 'PENDING',
    source_refs TEXT,
    execution_id VARCHAR(50),
    extracted_at TIMESTAMP,
    UNIQUE(brnch_no, brnch_std_cd)     -- ON CONFLICT용
);

-- 수질결과
CREATE TABLE td_gd010310 (
    sn BIGSERIAL PRIMARY KEY,
    legacy_code_no VARCHAR(10),         -- 지점번호
    spot_std_code VARCHAR(15),          -- 지점표준코드
    yyyy VARCHAR(4),                    -- 연도
    period VARCHAR(4),                  -- 분기
    insp_check VARCHAR(10),             -- 검사여부
    un_insp_desc VARCHAR(500),          -- 미검사사유
    accept_yn VARCHAR(10),              -- 적합여부
    suit VARCHAR(4),                    -- 적합
    unsuit VARCHAR(4),                  -- 부적합
    samp_date VARCHAR(10),              -- 채수일자
    insp_rst VARCHAR(500),              -- 부적합항목
    fail_desc VARCHAR(500),             -- 부적합시 조치사항
    -- 세균류 11개
    item_genbaclow VARCHAR(50),
    item_genbacmid VARCHAR(50),
    item_totbac VARCHAR(50),
    item_bac VARCHAR(50),
    item_festr VARCHAR(50),
    item_branfungus VARCHAR(50),
    item_grgungus VARCHAR(50),
    item_salmol VARCHAR(50),
    item_segel VARCHAR(50),
    item_sulfungus VARCHAR(50),
    item_yersinia VARCHAR(50),
    -- 중금속 11개
    item_pb VARCHAR(50),
    item_f VARCHAR(50),
    item_gas VARCHAR(50),
    item_se VARCHAR(50),
    item_hg VARCHAR(50),
    item_cn VARCHAR(50),
    item_cr6 VARCHAR(50),
    item_no3am VARCHAR(50),
    item_no3n VARCHAR(50),
    item_cd VARCHAR(50),
    item_boron VARCHAR(50),
    -- 유기화합물 17개
    item_bro3 VARCHAR(50),
    item_phenol VARCHAR(50),
    item_diazn VARCHAR(50),
    item_parat VARCHAR(50),
    item_penitro VARCHAR(50),
    item_carbaryl VARCHAR(50),
    item_tcet VARCHAR(50),
    item_tece VARCHAR(50),
    item_tce VARCHAR(50),
    item_dcm VARCHAR(50),
    item_benzene VARCHAR(50),
    item_toluene VARCHAR(50),
    item_etilben VARCHAR(50),
    item_xylene VARCHAR(50),
    item_dce VARCHAR(50),
    item_ccl4 VARCHAR(50),
    item_dbcp VARCHAR(50),
    -- 일반항목 16개
    item_c4h8o2 VARCHAR(50),
    item_gradient VARCHAR(50),
    item_kmn VARCHAR(50),
    item_smell VARCHAR(50),
    item_color VARCHAR(50),
    item_cu VARCHAR(50),
    item_abs VARCHAR(50),
    item_ph VARCHAR(50),
    item_zn VARCHAR(50),
    item_cl VARCHAR(50),
    item_fe VARCHAR(50),
    item_mn VARCHAR(50),
    item_muddy VARCHAR(50),
    item_so42 VARCHAR(50),
    item_al VARCHAR(50),
    item_uran VARCHAR(50),
    -- IF 메타
    link_status VARCHAR(20) DEFAULT 'PENDING',
    source_refs TEXT,
    execution_id VARCHAR(50),
    extracted_at TIMESTAMP,
    UNIQUE(legacy_code_no, yyyy, period, samp_date)  -- ON CONFLICT용
);
```

### 4-3. SND Agent (sync-agent-others)

**YAML**: `dmz-others-snd-yaksoter.yml`

```yaml
agent:
  code: dmz-others-snd-yaksoter
  name: 약수터 SND
  type: SND
  description: 약수터 API 수집 데이터 → IF_SND 전환

datasource:
  source:
    id: api-collector
    url: jdbc:postgresql://localhost:29001/api_collector
    username: k1m
    password: 1111
  target:
    id: dmz-pg
    url: jdbc:postgresql://localhost:29001/dev
    username: k1m
    password: 1111

steps:
  - name: tm-gd010310-snd
    factory-key: source-to-if
    source-table: tm_gd010310
    target-table: if_snd_tm_gd010310
    primary-key: sn
    conflict-key: source_refs
    full-copy: true

  - name: td-gd010310-snd
    factory-key: source-to-if
    source-table: td_gd010310
    target-table: if_snd_td_gd010310
    primary-key: sn
    conflict-key: source_refs
    full-copy: true
```

**엔티티 2개** (sync-agent-others):
- `IfSndTmGd010310.java` — if_snd_tm_gd010310
- `IfSndTdGd010310.java` — if_snd_td_gd010310

### 4-4. Int RCV (sync-agent-bojo-int)

**YAML**: `internal-yaksoter-rcv.yml`

```yaml
agent:
  code: internal-yaksoter-rcv
  name: 약수터 RCV
  type: RCV
  description: 약수터 IF_SND → IF_RSV 수신

datasource:
  source:
    id: dmz-pg-proxy
    url: jdbc:postgresql://localhost:8093/dev    # Proxy 경유
    username: k1m
    password: 1111
  target:
    id: internal
    url: jdbc:oracle:thin:@localhost:29004/XEPDB1
    username: k1m
    password: 1111

steps:
  - name: tm-gd010310-rcv
    factory-key: source-to-if
    source-table: if_snd_tm_gd010310
    target-table: if_rsv_tm_gd010310
    primary-key: sn
    conflict-key: source_refs
    skip-source-status-update: true

  - name: td-gd010310-rcv
    factory-key: source-to-if
    source-table: if_snd_td_gd010310
    target-table: if_rsv_td_gd010310
    primary-key: sn
    conflict-key: source_refs
    skip-source-status-update: true
```

**엔티티 2개** (sync-agent-bojo-int):
- `IfRsvTmGd010310.java` — IF_RSV_TM_GD010310 (Oracle)
- `IfRsvTdGd010310.java` — IF_RSV_TD_GD010310 (Oracle)

### 4-5. Int Loader — SimpleLoadStep

> 중복 검증은 API Collector(소스 PG)에서 ON CONFLICT로 처리 완료.
> Loader는 IF_RSV → Target 단순 MERGE만 수행.
> 향후 팀장 확인 후 수질 전 필드 비교가 필요하면 커스텀 LoadStep으로 교체 가능.

**YAML**: `internal-yaksoter-loader.yml`

```yaml
agent:
  code: internal-yaksoter-loader
  name: 약수터 Loader
  type: LOADER
  description: 약수터 IF_RSV → Target (SimpleLoadStep MERGE)

datasource:
  source:
    id: internal
    url: jdbc:oracle:thin:@localhost:29004/XEPDB1
    username: k1m
    password: 1111
  target:
    id: internal
    url: jdbc:oracle:thin:@localhost:29004/XEPDB1
    username: k1m
    password: 1111

steps:
  - name: yaksoter-jewon-load
    factory-key: simple-load
    source-table: IF_RSV_TM_GD010310
    target-table: TM_GD010310
    merge-key: BRNCH_NO,BRNCH_STD_CD

  - name: yaksoter-wq-load
    factory-key: simple-load
    source-table: IF_RSV_TD_GD010310
    target-table: TD_GD010310
    merge-key: BRNCH_NO,YR,QTR,WTSMP_YMD
```

**레거시 로직 vs 우리 구조 대응:**

| 레거시 동작 | 우리 구조에서 담당 |
|-----------|----------------|
| 제원 신규 INSERT | API Collector ON CONFLICT → SimpleLoadStep MERGE NOT MATCHED |
| 제원 변경 UPDATE | API Collector ON CONFLICT DO UPDATE → SimpleLoadStep MERGE MATCHED |
| 제원 동일 SKIP | API Collector에서 동일하면 link_status 유지 → 전달 안 됨 |
| 수질 신규 INSERT | API Collector INSERT → SimpleLoadStep MERGE NOT MATCHED |
| 수질 동일 SKIP | API Collector ON CONFLICT → 동일하면 전달 안 됨 |

> **미결**: 수질 4키 UK 확정 (팀장 확인 필요 — 레거시 전 필드 비교 대신 4키로 단순화)

**필드 변환 맵** (API snake_case → 환경부표준):

```java
// 제원 변환 (24개)
Map<String, String> JEWON_FIELD_MAP = Map.ofEntries(
    entry("legacy_code_no", "BRNCH_NO"),
    entry("spot_nm", "BRNCH_NM"),
    entry("spot_std_code", "BRNCH_STD_CD"),
    entry("info_creat_instt_nm", "INFO_CRT_INST_NM"),
    entry("do_nm", "CTPV_NM"),
    entry("cty_nm", "SGG_NM"),
    entry("adres", "ADDR"),
    entry("admcode", "STDG_CD"),
    entry("abl_at", "ABL_YN"),        // VARCHAR→CHAR(1) 변환 필요
    entry("abl_de", "ABL_YMD"),       // 날짜 10→8 변환 필요
    entry("day_avg", "DAY01_AVG_USR_CNT"),  // VARCHAR→NUMBER 변환 필요
    entry("ins_date", "INSTL_YMD"),   // 날짜 10→8 변환 필요
    entry("del_yn", "DEL_YN"),        // VARCHAR→CHAR(1) 변환 필요
    entry("charge", "PIC_NM"),
    entry("office", "PIC_NM"),        // ⚠️ 충돌 — 별도 처리 필요
    entry("office_tel", "PIC_CNPL"),
    entry("building_no", "BNO"),
    entry("loc_jibun", "LCTN_LOTNO"),
    entry("commt", "RMRK"),
    entry("crdnt_x", "XCRD"),
    entry("crdnt_y", "YCRD"),
    entry("cl_middle_nm", "CHRTC_MCLSF"),
    entry("cl_small_nm", "CHRTC_SCLSF")
    // rowno → SEQ (NUMBER 변환)
);
```

**타입 변환 처리** (표준화 자료 기준):

| 소스 필드 | Target 컬럼 | 변환 | 비고 |
|----------|-----------|------|------|
| `abl_at` (VARCHAR 4) | ABL_YN | → **VARCHAR2(1)** 첫 글자 추출 | Y/N |
| `del_yn` (VARCHAR 4) | DEL_YN | → **CHAR(1)** 첫 글자 추출 | Y/N |
| `day_avg` (VARCHAR 10) | DAY01_AVG_USR_CNT | → **NUMBER(22)** | 숫자 아니면 null |
| `rowno` (VARCHAR 6) | SEQ | → **NUMBER(22)** | 순번 |
| `ins_date` (VARCHAR 10) | INSTL_YMD | → **VARCHAR2(8)** | "1999.11.01" → "19991101" |
| `abl_de` (VARCHAR 13) | ABL_YMD | → **VARCHAR2(8)** | 날짜 포맷 정리 |
| `samp_date` (VARCHAR 10) | WTSMP_YMD | → **VARCHAR2(8)** | 채수일자 |
| `adres` (VARCHAR 500) | ADDR | → **VARCHAR2(250)** | 잘림 주의 |
| `insp_rst` (VARCHAR 500) | ICPT_ARTCL | → **VARCHAR2(100)** | 잘림 주의 |
| `fail_desc` (VARCHAR 500) | ICPT_ACTN_MTTR | → **VARCHAR2(100)** | 잘림 주의 |

### 4-6. Target 테이블 컬럼 정의 (표준화 자료 기준)

> 출처: `docs/Standardizedtable/TM_GD20310.txt`, `TD_GD20310.txt`

**TM_GD010310 (제원)** — 24개 컬럼 + 추적 메타

| 환경부표준 컬럼 | 이전 컬럼 | 타입 | 길이 | NULL | 설명 |
|---------------|----------|------|------|------|------|
| SN | — | NUMBER | — | N | **JPA @Id (IDENTITY), 레거시에 없음** |
| SEQ | ROWNO | NUMBER | 22 | N | 순번 (API 응답 rowno 그대로 적재) |
| BRNCH_NO | LEGACY_CODE_NO | VARCHAR2 | 10 | N | 지점번호 |
| BRNCH_NM | SPOT_NM | VARCHAR2 | 100 | N | 지점명 |
| BRNCH_STD_CD | SPOT_STD_CODE | VARCHAR2 | 20 | N | 지점표준코드 |
| INFO_CRT_INST_NM | INFO_CREAT_INSTT_NM | VARCHAR2 | 50 | N | 정보생성기관명 |
| CHRTC_MCLSF | CL_MIDDLE_NM | VARCHAR2 | 4 | Y | 특성중분류 |
| CHRTC_SCLSF | CL_SMALL_NM | VARCHAR2 | 4 | Y | 특성소분류 |
| CTPV_NM | DO_NM | VARCHAR2 | 40 | Y | 시도명 |
| SGG_NM | CTY_NM | VARCHAR2 | 30 | Y | 시군구명 |
| ADDR | ADRES | VARCHAR2 | 250 | N | 주소 |
| STDG_CD | ADMCODE | VARCHAR2 | 10 | N | 법정동코드 |
| XCRD | CRDNT_X | VARCHAR2 | 20 | Y | X좌표 |
| YCRD | CRDNT_Y | VARCHAR2 | 20 | Y | Y좌표 |
| ABL_YN | ABL_AT | VARCHAR2 | 1 | N | 폐지여부 |
| ABL_YMD | ABL_DE | VARCHAR2 | 8 | Y | 폐지일자 |
| DAY01_AVG_USR_CNT | DAY_AVG | NUMBER | 22 | N | 1일평균이용자수 |
| PIC | CHARGE | VARCHAR2 | 60 | Y | 담당자 |
| INSTL_YMD | INS_DATE | VARCHAR2 | 8 | N | 설치일자 |
| DEL_YN | DEL_YN | CHAR | 1 | N | 삭제여부 |
| PIC_NM | OFFICE | VARCHAR2 | 60 | N | 담당자명 |
| PIC_CNPL | OFFICE_TEL | VARCHAR2 | 50 | N | 담당자연락처 |
| BNO | BUILDING_NO | VARCHAR2 | 30 | Y | 건물번호 |
| LCTN_LOTNO | LOC_JIBUN | VARCHAR2 | 20 | Y | 소재지_지번 |
| RMRK | COMMT | VARCHAR2 | 500 | Y | 비고 |
| — | — | VARCHAR2 | 500 | Y | SOURCE_REFS (추적) |
| — | — | VARCHAR2 | 50 | Y | EXECUTION_ID (추적) |

**TD_GD010310 (수질)** — 67개 컬럼 + 추적 메타

| 환경부표준 컬럼 | 이전 컬럼 | 타입 | 길이 | NULL | 설명 |
|---------------|----------|------|------|------|------|
| SN | — | NUMBER | — | N | **JPA @Id (IDENTITY), 레거시에 없음** |
| BRNCH_NO | LEGACY_CODE_NO | VARCHAR2 | 10 | N | 지점번호 |
| BRNCH_STD_CD | SPOT_STD_CODE | VARCHAR2 | 20 | N | 지점표준코드 |
| YR | YYYY | VARCHAR2 | 4 | Y | 연도 |
| QTR | PERIOD | VARCHAR2 | 10 | N | 분기 |
| INSP_YN | INSP_CHECK | VARCHAR2 | 20 | N | 검사여부 |
| UN_INSP_RSN | UN_INSP_DESC | VARCHAR2 | 500 | N | 미검사사유 |
| STBLT_YN | ACCEPT_YN | VARCHAR2 | 10 | Y | 적합여부 |
| STBLT | SUIT | VARCHAR2 | 10 | N | 적합 |
| ICPT | UNSUIT | VARCHAR2 | 10 | N | 부적합 |
| GNRL_GERM_LOWTMP | ITEM_GENBACLOW | VARCHAR2 | 20 | N | 일반세균저온 |
| GNRL_GERM_MESPH | ITEM_GENBACMID | VARCHAR2 | 20 | Y | 일반세균중온 |
| CFBCTR | ITEM_TOTBAC | VARCHAR2 | 20 | Y | 총대장균군 |
| CLBCL | ITEM_BAC | VARCHAR2 | 20 | Y | 대장균 |
| FCFS | ITEM_FESTR | VARCHAR2 | 20 | Y | 분원성대장균군 |
| FCSTRCCI | ITEM_BRANFUNGUS | VARCHAR2 | 20 | Y | 분원성연쇄상구균 |
| PAERGNS | ITEM_GRGUNGUS | VARCHAR2 | 20 | Y | 녹농균 |
| SLMN | ITEM_SALMOL | VARCHAR2 | 20 | Y | 살모넬라 |
| SHIGLA | ITEM_SEGEL | VARCHAR2 | 20 | Y | 쉬겔라 |
| SFSRA | ITEM_SULFUNGUS | VARCHAR2 | 20 | Y | 아황산환원혐기성포자형성균 |
| YERSNA | ITEM_YERSINIA | VARCHAR2 | 20 | Y | 여시니아균 |
| PMBM | ITEM_PB | VARCHAR2 | 20 | Y | 납 |
| FLRN | ITEM_F | VARCHAR2 | 20 | Y | 불소 |
| ASNC | ITEM_GAS | VARCHAR2 | 20 | Y | 비소 |
| SE | ITEM_SE | VARCHAR2 | 20 | Y | 셀레늄 |
| MRCR | ITEM_HG | VARCHAR2 | 20 | Y | 수은 |
| CYN | ITEM_CN | VARCHAR2 | 20 | Y | 시안 |
| CHRM | ITEM_CR6 | VARCHAR2 | 20 | Y | 크롬 |
| AMNG | ITEM_NO3AM | VARCHAR2 | 20 | Y | 암모니아성질소 |
| NTNG | ITEM_NO3N | VARCHAR2 | 20 | Y | 질산성질소 |
| CDMM | ITEM_CD | VARCHAR2 | 20 | Y | 카드뮴 |
| BOR | ITEM_BORON | VARCHAR2 | 20 | Y | 보론 |
| BRO3 | ITEM_BRO3 | VARCHAR2 | 20 | Y | 브로메이트 |
| PHNL | ITEM_PHENOL | VARCHAR2 | 20 | Y | 페놀 |
| DZNN | ITEM_DIAZN | VARCHAR2 | 20 | Y | 다이아지논 |
| PARAT | ITEM_PARAT | VARCHAR2 | 20 | Y | 파라티온 |
| FENITRO | ITEM_PENITRO | VARCHAR2 | 20 | Y | 페니트로티온 |
| CARBARYL | ITEM_CARBARYL | VARCHAR2 | 20 | Y | 카바릴 |
| TCRN | ITEM_TCET | VARCHAR2 | 20 | Y | 트리클로로에탄 |
| TTRT | ITEM_TECE | VARCHAR2 | 20 | Y | 테트라클로로에틸렌 |
| TCRT | ITEM_TCE | VARCHAR2 | 20 | Y | 트리클로로에틸렌 |
| DCMT | ITEM_DCM | VARCHAR2 | 20 | Y | 디클로로메탄 |
| BNZN | ITEM_BENZENE | VARCHAR2 | 20 | N | 벤젠 |
| TLN | ITEM_TOLUENE | VARCHAR2 | 20 | Y | 톨루엔 |
| ETBZ | ITEM_ETILBEN | VARCHAR2 | 20 | Y | 에틸벤젠 |
| XLN | ITEM_XYLENE | VARCHAR2 | 20 | Y | 자일렌 |
| DCTY | ITEM_DCE | VARCHAR2 | 20 | Y | 디클로로에틸렌 |
| CCL4 | ITEM_CCL4 | VARCHAR2 | 20 | Y | 사염화탄소 |
| DBCP | ITEM_DBCP | VARCHAR2 | 20 | Y | 1,2-디브로모-3-클로로프로판 |
| DIOX14 | ITEM_C4H8O2 | VARCHAR2 | 20 | Y | 다이옥산 |
| TDS | ITEM_GRADIENT | VARCHAR2 | 20 | Y | 경도 |
| PTPM_CNSM_QNT | ITEM_KMN | VARCHAR2 | 20 | Y | 과망간산칼륨소비량 |
| SMLL | ITEM_SMELL | VARCHAR2 | 20 | Y | 냄새 |
| CRMTY | ITEM_COLOR | VARCHAR2 | **25** | Y | 색도 |
| CPPR | ITEM_CU | VARCHAR2 | 20 | Y | 구리 |
| ABS | ITEM_ABS | VARCHAR2 | 20 | Y | 알킬벤젠소디움설포네이트 |
| PH | ITEM_PH | VARCHAR2 | **10** | Y | 수소이온농도 |
| ZN | ITEM_ZN | VARCHAR2 | 20 | Y | 아연 |
| CRRD | ITEM_CL | VARCHAR2 | 20 | Y | 염소이온 |
| IRON | ITEM_FE | VARCHAR2 | 20 | Y | 철 |
| MNGN | ITEM_MN | VARCHAR2 | 20 | Y | 망간 |
| TRBT | ITEM_MUDDY | VARCHAR2 | 20 | Y | 탁도 |
| ECCBT_ION | ITEM_SO42 | VARCHAR2 | 20 | Y | 황산이온 |
| ALMN | ITEM_AL | VARCHAR2 | 20 | Y | 알루미늄 |
| URAN | ITEM_URAN | VARCHAR2 | 20 | Y | 우라늄 |
| ICPT_ARTCL | INSP_RST | VARCHAR2 | **100** | Y | 부적합항목 |
| ICPT_ACTN_MTTR | FAIL_DESC | VARCHAR2 | **100** | Y | 부적합조치사항 |
| WTSMP_YMD | SAMP_DATE | VARCHAR2 | **8** | N | 채수일자 |
| — | — | VARCHAR2 | 500 | Y | SOURCE_REFS (추적) |
| — | — | VARCHAR2 | 50 | Y | EXECUTION_ID (추적) |

> **굵은 글씨**: 대부분 20인 항목 중 예외 길이 (CRMTY=25, PH=10, ICPT_ARTCL/ICPT_ACTN_MTTR=100, WTSMP_YMD=8)

---

## 5. 미결 사항

| # | 내용 | 상태 |
|---|------|------|
| 1 | API 서비스키 발급 (data.go.kr 포털 비활성 → 담당자 직접 연락) | **블로커** |
| 2 | 수질 4키 UK 확정 (팀장 확인 — 레거시 전 필드 비교 대신 4키 단순화) | 팀장 확인 필요 |
| 3 | API 응답 dataRootPath 실제 확인 (JSON 구조) | 키 확보 후 테스트 |

---

## 6. 작업 순서

| 순서 | 작업 | 모듈 | 산출물 |
|------|------|------|--------|
| 1 | 소스 테이블 DDL + Entity (api_collector PG) | infolink-api-collector | tm_gd010310, td_gd010310 |
| 2 | IF_SND Entity + YAML | sync-agent-others | IfSndTmGd010310/WqResult + YAML |
| 3 | IF_RSV Entity + YAML | sync-agent-bojo-int | IfRsvTmGd010310/WqResult + YAML |
| 4 | Target Entity | sync-agent-bojo-int | TmGd010310, TdGd010310 |
| 5 | **YaksoterLoadStep 구현** | sync-agent-bojo-int | 커스텀 Step + Factory |
| 6 | Loader YAML | sync-agent-bojo-int | internal-yaksoter-loader.yml |
| 7 | 빌드 + 테스트 | 전체 | E2E (Mock API → ... → Oracle) |

> API 키 미확보 시: MockApiController에 약수터 샘플 데이터 추가하여 E2E 테스트 가능.

---

## 7. 영향 범위

| 모듈 | 변경 | 기존 코드 수정 |
|------|------|--------------|
| infolink-api-collector | Entity 2개 신규 | 없음 (UI 등록) |
| sync-agent-others | Entity 2개 + YAML 1개 신규 | 없음 |
| sync-agent-bojo-int | Entity 4개 + Step 1개 + Factory + YAML 1개 신규 | LoaderPipelineConfig에 Factory 등록 |
| sync-agent-common | 없음 | 없음 |
