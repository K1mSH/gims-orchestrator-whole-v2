# 약수터 파이프라인 — 재개 계획 (API 교체)

> **작성일**: 2026-04-29
> **베이스 계획서**: `dev_plan/2026_04/15/yaksoter-pipeline-impl-plan.md` (확정본, 그대로 유효)
> **선행 분석**: `dev_plan/2026_04/14/` 3건 (테이블/매핑/파이프라인)
> **상태**: 4/29 EOD — **수집 단계 완료** ✅ / SND/RCV/Loader (코드 작업) = 4/30 진행

## 진행 현황 (4/29 EOD)

| 단계 | 상태 | 비고 |
|--:|:----:|------|
| §A.1 새 ServiceKey 발급 | ✅ | id=3 mock 키로 등록 |
| §A.2 첫 호출 검증 | ✅ | dataRootPath 확정 + 응답 UPPER_SNAKE 확인 |
| §A.3 매핑표 v2 작성 | ✅ | `yaksoter-field-mapping-v2.md` |
| §A.4 Endpoint 2개 등록 | ✅ | id=26 (제원, 화면 등록) + id=27 (수질, DB INSERT 자동화) |
| §A.5 API Key 등록 | ✅ | mock 자체 데이터 (id=3) — 별도 등록 불필요 |
| §A.6 수동 실행 적재 | ✅ | 제원 854 unique / 수질 1000/1000 (3차 시도 — NULL/길이 풀기 후 OK) |
| §B.0 엔티티 UK 정정 | ✅ | B-1 채택 후 소스 PG UK 자연키 박음 |
| §B.0+ 엔티티 NOT NULL 풀기 | ✅ | 8 엔티티 + dev DB 4 테이블 |
| §B.0+ 엔티티 길이 100→500 | ✅ | 4 엔티티 + dev DB 2 테이블 (icpt_artcl/actn_mttr) |
| §B.1 SND YAML | ⏭ 4/30 | `dmz-others-snd-yaksoter.yml` |
| §B.2 Int RCV YAML | ⏭ 4/30 | `internal-yaksoter-rcv.yml` |
| §B.3 Loader YAML | ⏭ 4/30 | `internal-yaksoter-loader.yml` (SimpleLoadStep) |
| §B.4 Agent 등록 | ⏭ 4/30 | 화면 또는 DB INSERT |
| §B.5 E2E | ⏭ 4/30 | SND → RCV → Loader |
| §B.5b dedup 검증 | ⏭ 4/30 | 같은 yyyy 두 번 실행 결과 변화 0 |
| §B.6 추적 검증 | ⏭ 4/30 | `/trace-source` |
| §C.1~C.3 회귀 / dev_log / 커밋 | ⏭ 4/30 | |

---

## 1. 배경

- 4/15 시점 엔티티 8개 + 4모듈 빌드까지 완료한 후 **`data.go.kr` API `Forbidden`** 으로 14일간 동결
  - 4/15 EOD 기준 담당자 직접 연락 (조훈제 032-590-7465) 이 후속 항목이었음
- 4/29 사용자가 **"내용이 같은 다른 API"** 발견 — 활용신청 가능
- 그 사이 (4/16~4/28) api-provider / Type B / docker PoC 작업이 진행되었으나 **약수터 영역(api-collector / others / bojo-int) 은 그대로** — 4/15 의 자산이 모두 그대로 유효함

---

## 2. 새 API 정보 (사용자 제공)

| 항목 | 값 |
|------|-----|
| 운영기관 | 토양지하수 먹는물 공동시설 운영결과 DB |
| Base URL | `https://apis.data.go.kr/1480523/WaterQualityService` |
| Endpoint | `/getSgisDrinkWaterList` |
| 호출 한도 | 10,000 (단위: 일/월 — 활용신청서 재확인 필요) |
| 인증 | `ServiceKey` (query param, **대문자 S**) |
| 페이징 | `pageNo` / `numOfRows` |
| 응답 형식 | `resultType=XML/JSON` (JSON 사용) |
| 필터 파라미터 | `yyyy` (복수 콤마 구분 `2012,2013`) / `sido` / `sgg` / `period` (예: `2/4`) / `legacyCodeNo` (복수 콤마) / `spotNm` |

> **응답 스키마는 4/15 계획과 동일하다는 가정** — 사용자가 "내용이 같은 API" 라고 명시.
> 실제 호출 후 dataRootPath / 필드명 검증 필요 (작업 순서 §A 의 첫 단계).

---

## 3. 4/15 계획 대비 변경점

| 항목 | 4/15 계획 | 새 API (4/29 호출 검증) | 조치 |
|------|----------|---------------------|------|
| 프로토콜 | `http://` | **`https://`** | API Collector Endpoint URL 등록 시 https |
| 서비스키 파라미터명 | `serviceKey` (소문자 s) | **`serviceKey` (소문자 s)** ✅ 동일 | 4/29 정정 — 활용신청 화면 표기는 `ServiceKey` 였으나 실제 호출은 소문자 |
| `numOfRows` | 30000 (단발 전량 의도) | **default 10**, max 미상 / `totalCount=3231` (yyyy=2020) | 1000 으로 페이징 분할. 한도 10000 충분 |
| `period` 형식 | 단순 분기 추정 | **응답값 비정형** — `1/4` / `2/4` / `4/4` / `9월` 혼재 | qtr VARCHAR(10) 길이 OK. 운영기관별 표기 다름 — 정형화 시도 ❌ (레거시 동작 보존) |
| 추가 필터 | yyyy 만 | `sido` / `sgg` / `legacyCodeNo` / `spotNm` 가용 | 운영 시 시도 단위 분할 호출 옵션 (미사용해도 무관) |
| **dataRootPath** | `body.items.item` 또는 `getSgisDrinkWaterList.item` 추정 | **`getSgisDrinkWaterList.item` ✅ 확정** (4/29 호출) | 추정 적중 |
| **응답 필드명** | camelCase 추정 (`legacyCodeNo`) | **UPPER_SNAKE_CASE ✅ 확정** (`LEGACY_CODE_NO` / `SPOT_NM` / `ITEM_GENBACLOW` 등) | 매핑 등록 시 sourceField 대문자 박기. 엔티티/Target 컬럼은 환경부표준 그대로 — 매핑표만 정정 |
| 응답 필드 수 | 24 (제원) + 65 (수질) = 89 추정 | **89 ✅ 확정** (1건 = 제원+수질 합본) | 4/15 분석 적중 |
| 응답 메타 | 미확인 | `header / item / numOfRows / pageNo / totalCount` 포함 | API Collector 페이징 정상 진입 가능 |

**바뀌지 않는 것 (4/15 계획 그대로 유효)**:
- 데이터 흐름 4단계 (API Collector → SND → Int RCV → Int Loader)
- 1 API → 2 Endpoint 등록 분리 (제원/수질, 같은 URL, 매핑만 다름) — 결정 (A)
- Target Oracle PK·UK 없음 정책
- 환경부표준 컬럼명 + 24/65+ 필드 매핑
- 테이블명 `tm_gd010310` / `td_gd010310` / Target `TM_GD010310` / `TD_GD010310`

---

## 3.5. dedup 정책 — **B-2 채택 (4/29 결정, 4/15 §0 정정)**

> **사용자 입장**: "레거시가 한 만큼 우리도 한다 — 기능 완벽 보전 우선"
> **결정 결과**: 4/15 §0 의 변경 1/2/4 가 뒤집힘. 레거시 `AdminBatchServiceImpl.waterQualityWriter()` + `adm.selectWaterQualityCheckDup` SQL 충실 재현.

### 4/15 §0 정정표

| 4/15 결정 | 4/29 결정 (1차) | 4/29 결정 (2차 — B-1 회귀) | 사유 (2차 변경) |
|----------|---------------|--------------------------|---------------|
| 변경 1: 중복 검증 위치 = API Collector (ON CONFLICT) | Loader 회귀 (B-2) | **API Collector + Loader 양쪽 (4/15 그대로 회귀)** | 사용자 통찰 — Target 결과 동일하면 source 단에서 미리 정돈이 합리적 |
| 변경 2: 수질 4키 UK 단순화 (1차 4키만) | 레거시 65필드 비교 유지 (B-2) | **4키 자연 dedup (4/15 그대로 회귀)** | 변경값 들어오면 최신값 갱신 (운영 시나리오 = "정정/재측정" 추정). "두 행 공존" 은 v2 구조 한계 (PK/UK 없음) 의 부작용으로 재해석 |
| 변경 3: Target PK/UK 없음 + 소스 PG에만 UK | 소스 PG UK 도 제거 (B-2) | **소스 PG UK 다시 박음 (4/15 그대로 회귀)** — 자연키 (제원: brnch_no+brnch_std_cd / 수질: brnch_no+yr+qtr+wtsmp_ymd). Target Oracle 은 그대로 PK/UK 없음 | source 단 자연 dedup |
| 변경 4: Loader = SimpleLoadStep | 커스텀 Step (B-2) | **SimpleLoadStep (4/15 그대로 회귀)** — source 단 dedup 후 IF/Target 단순 MERGE | 변경 1/2 회귀로 커스텀 의미 약함 |
| 변경 5: 테이블/컬럼 표준화 (`TM_GD010310` / 환경부표준) | **유지** | **유지** | v3 다른 파이프라인 일관성 / 표준화 자료 합의 존중 |

> **요약 (4/29 2차)**: B-2 (커스텀 Step + 누적) → **B-1 (자연키 UK + DO UPDATE + SimpleLoadStep) 으로 회귀**. 사실상 4/15 §3 원그림 그대로. 이유 = Target 결과 동일 + source 누적 부담 우려. "두 행 공존 변경 이력" 동작 포기.

### B-2 동작 시퀀스 (4/29 레거시 SQL 직접 검증 후)

```
[DMZ] API Collector — 응답 그대로 INSERT 누적 (UK 없음)
  ↓ SND/RCV — 그대로 패스
[내부] Int Loader — YaksoterLoadStep (Step 1개, item 단위 루프)
  for each item (제원+수질 합본):
    ① 1차 SELECT 수질 — 4키 (BRNCH_NO + YR + QTR + WTSMP_YMD) 만
    ② 1차 SELECT 제원 — BRNCH_NO + BRNCH_STD_CD (5컬럼만 SELECT)

    if 1차 수질 비어있음:
       제원 분기:
         - 1차 제원 비어있음   → INSERT TM_GD010310
         - BRNCH_NM/ADDR 변경  → UPDATE TM_GD010310 (변경감지 = 2 컬럼만!)
         - 동일                → 아무것도 안 함
       INSERT TD_GD010310 (수질 무조건)
    else:  // 1차 수질 있음
       ③ 2차 SELECT 수질 — 4키 + isNotNull 동적 (응답 not-null 필드만 AND)
       if 2차 비어있음:
          제원 분기 (위 동일)
          INSERT TD_GD010310
       // else (완전 동일행 존재): SKIP — 아무것도 안 함
```

### 레거시 함정 (B-2 정신 — 그대로 보존, 4/29 사용자 결정)

iBatis `<isNotNull>` 동적 비교의 특성:
- 응답값 NULL + 기존행 값 있음 → WHERE 미발사 → "매치" 판정 → SKIP (잘못된 SKIP 가능성)
- 응답값 있음 + 기존행 NULL → WHERE 발사 → 매치 안 됨 → INSERT

> 4/29 사용자: "여태 다른 시스템에서 이의제기 안 한 거면 우리도 그대로 가는 거지" — 우리 커스텀 Step 도 같은 방식 (응답값 not-null 필드만 비교) 으로 구현.

### 변경감지 컬럼 범위 (4/29 SQL 직접 검증)

- `selectWaterQualityInfo` SQL = 5컬럼만 SELECT (`LEGACY_CODE_NO, SPOT_NM, SPOT_STD_CODE, ADRES, ADMCODE`)
- Java 가 비교 = `SPOT_NM` + `ADRES` **두 컬럼만**
- 우리 표준화 컬럼명 = `BRNCH_NM` + `ADDR`

### 소스 PG UK 정책 변경

- 4/15: `legacy_code_no + spot_std_code` (제원), `legacy_code_no + yyyy + period + samp_date` (수질) — ON CONFLICT 용
- 4/29 정정: **소스 PG UK 도 제거** (레거시 정신 — UK 없는 누적). 엔티티 코드 수정 필요 (`@UniqueConstraint` 제거)
  - 같은 응답 재호출 시 source PG 부풀어짐 — 분기 1회 운영이라 누적량 미미. 운영 시 source 측 retention 정책으로 정리

### 레거시 SQL 이관 원본

| Step | 레거시 위치 |
|------|-----------|
| 4키 SELECT | `D:\dev\project\GIMS\GIMS_SOURCE\newgims_v2\src\egovframework\sqlmap\com\gims\sql_adminInfo.xml` — `adm.selectWaterQualityCheckDup` |
| 전 필드 비교 분기 + INSERT | `D:\dev\project\GIMS\GIMS_SOURCE\newgims_v2\src\gims\service\impl\AdminBatchServiceImpl.java` — `waterQualityWriter()` |
| 제원 변경감지 | 동일 파일 — `siteWriter()` (또는 `waterQualityWriter()` 의 제원 분기) |

> A.2 첫 호출 후 / B.3 Step 구현 전, 레거시 코드 정확한 라인 재확인 필요.

---

## 4. 살아있는 자산 점검 (4/15 EOD 시점)

| 모듈 | 자산 | 상태 |
|------|------|------|
| infolink-api-collector | `YaksoterJewon` (24컬럼+UK `brnch_no+brnch_std_cd`) | ⚠️ **UK 제거** 필요 (§3.5) |
| infolink-api-collector | `YaksoterWqResult` (65+ 컬럼+UK `brnch_no+yr+qtr+wtsmp_ymd`) | ⚠️ **UK 제거** 필요 (§3.5) |
| sync-agent-others | `IfSndTmGd010310` / `IfSndTdGd010310` | ✅ 그대로 사용 |
| sync-agent-bojo-int | `IfRsvTmGd010310` / `IfRsvTdGd010310` | ✅ 그대로 사용 |
| sync-agent-bojo-int | `TmGd010310` / `TdGd010310` (Target) | ✅ 그대로 사용 |
| 4모듈 빌드 | `./gradlew clean build -x test` | ✅ 4/15 OK (UK 제거 후 재빌드 필요) |

> 새 API 가 응답 스키마 동일 → 엔티티 컬럼 변경 0 건. UK 만 제거 (B-2 정책).
> 응답 스키마 다르면 §A.3 결과에 따라 컬럼/매핑 보강.

---

## 5. 잔여 작업 (4/15 §6 의 step 2~7)

### A. API 응답 검증 + Endpoint 등록 (블로커 해제 첫 단계)

| # | 작업 | 산출물 |
|---|------|--------|
| A.1 | 새 ServiceKey 발급 + 활용신청 | ✅ 4/29 사용자 완료 |
| A.2 | **curl 로 첫 호출** — `yyyy=2020&resultType=JSON` (default numOfRows=10) | ✅ 4/29 완료. HTTP 200 / dataRootPath / 필드명 / totalCount=3231 확인 (§3 변경점 표 갱신) |
| A.2b | numOfRows max 확인 — `numOfRows=1000` / `numOfRows=10000` 시도 | 🟡 추가 호출 |
| A.3 | 응답 vs 4/15 매핑표 diff — UPPER_SNAKE 89 필드 → 4/15 의 `JEWON_FIELD_MAP` / 수질 매핑 정정표 작성 | 🟡 다음 작업 — `dev_plan/2026_04/29/yaksoter-field-mapping-v2.md` 신규 산출 |
| A.4 | API Collector UI 에서 **Endpoint 2개** 등록 — sourceField 모두 UPPER_SNAKE 박기 | (1) 약수터 제원 — `tm_gd010310` 매핑 24개 (2) 약수터 수질 — `td_gd010310` 매핑 65개 |
| A.5 | API Key 등록 — **api-collector 자체 키 관리 (mock)** → Endpoint 의 `serviceKey` param `isApiKeyRef=true` + staticValue=키ID | 4/24 패턴 — 다른 서비스에서 키 가져오는 흐름은 운영, 지금은 우리쪽에 등록 |
| A.6 | 수동 실행 → 소스 PG 적재 확인 | `tm_gd010310` / `td_gd010310` 양쪽 row 발생 |

### B. SND / RCV YAML + Loader (커스텀 Step 1종 신규)

| # | 작업 | 모듈 | 산출물 |
|---|------|------|--------|
| B.0 | **소스 엔티티 UK 제거** | infolink-api-collector | `YaksoterJewon` / `YaksoterWqResult` 의 `@UniqueConstraint` 제거 + 빌드 |
| B.1 | SND YAML | sync-agent-others | `dmz-others-snd-yaksoter.yml` (Step 2개: tm/td → if_snd_*) |
| B.2 | Int RCV YAML | sync-agent-bojo-int | `internal-yaksoter-rcv.yml` (if_snd_* → if_rsv_*) |
| **B.3** | **`YaksoterLoadStep` 신규 구현** (Step 1개) | sync-agent-bojo-int | Java Step + Factory 1건 등록 (`LoaderPipelineConfig`). 안에서 `processItem()` 루프 — 1차 4키 SELECT + 2차 isNotNull 동적 SELECT + 제원 변경감지 (BRNCH_NM/ADDR 2컬럼) → INSERT/UPDATE/SKIP. 레거시 `waterQualityWriter` + `selectWaterQualityCheck` + `selectWaterQualityInfo` 충실 이관 |
| B.3y | Int Loader YAML | sync-agent-bojo-int | `internal-yaksoter-loader.yml` (Step factory-key = `yaksoter-load`) |
| B.4 | Orchestrator 의 Agent/스케줄 등록 | UI | 분기 1회 (data.go.kr 갱신 주기 추정) |
| B.5 | E2E 한 사이클 — `yyyy=2020 numOfRows=1000` 으로 좁혀서 | 전체 | API → tm/td → IF_SND → IF_RSV → Target Oracle row 도달 |
| B.5b | **dedup 동작 검증** — 같은 yyyy 두 번 실행 | 전체 | (1) 동일 응답 → Target row 증가 0 (2) 수질값 변조 후 재실행 → 두 행 공존 (3) 시설명/주소 변조 후 재실행 → 제원 1행 갱신 |
| B.6 | 추적 검증 (`/trace-source`) | bojo-int | execution_id → source_refs → 외부 PG 원본 1건 역추적 OK |

### C. 회귀 / 점검

| # | 작업 | 비고 |
|---|------|------|
| C.1 | Type A 12종 회귀 (test_ops.py) | 본 작업의 영향 없음 — 200/200 유지 검증 |
| C.2 | api-provider CUSTOM 16종 카탈로그 | registered=True 유지 |
| C.3 | dev_log + 커밋 | `dev_logs/2026_04/2026-04-29.md` 보강 + `dev_logs/2026_04/2026-04-30.md` (작업일 기준) |

---

## 6. 미결 / TBD

| # | 내용 | 상태 |
|---|------|---------|
| 1 | dataRootPath | ✅ **`getSgisDrinkWaterList.item`** (4/29 §A.2 확정) |
| 2 | 응답 필드명 | ✅ **UPPER_SNAKE_CASE** (4/29 §A.2 확정 — `LEGACY_CODE_NO` 등) |
| 3 | numOfRows 최대값 | 🟡 default 10 확인. max 1000 / 10000 시도 후 확정 (§A.2 추가 호출) |
| 4 | 호출 한도 10000 의 단위 (일/월/계정 누적) | 🟡 활용신청서 / 마이페이지에서 사용자 확인 |
| 5 | 레거시 `waterQualityWriter` / `selectWaterQualityCheck` / `selectWaterQualityInfo` 정확한 라인 | ✅ 4/29 SQL/Service 직접 확인. `AdminBatchServiceImpl.java:133-190` + `sql_adminInfo.xml:8027-8256` |
| 6 | 제원 변경감지 비교 컬럼 범위 | ✅ 4/29 SQL 검증 — **`SPOT_NM` + `ADRES` 2컬럼만** (표준화 후 `BRNCH_NM` + `ADDR`) |
| 8 | 2차 비교의 isNotNull 함정 (응답 NULL → 잘못된 SKIP 가능) | ✅ 4/29 사용자 결정 — **그대로 보존** ("여태 다른 시스템 이의제기 없으면 그대로") |
| 7 | API Key 등록 패턴 — api-collector 자체 키 관리 (mock) | 🟡 **사용자 입장 = 4/24 isApiKeyRef 패턴, 다른 서비스 → 우리쪽 자체 등록 (mock)** §A.5 에 박음 |

---

## 7. 영향 범위

| 모듈 | 변경 | 기존 코드 수정 |
|------|------|--------------|
| infolink-api-collector | Endpoint 2개 + Param/매핑 등록 (UI / DB row) + **엔티티 2개 UK 제거** | 엔티티 2개 (`@UniqueConstraint` 제거) |
| sync-agent-others | YAML 1건 신규 | 없음 |
| sync-agent-bojo-int | YAML 2건 신규 (RCV / Loader) + **커스텀 Step 1개 신규** | `YaksoterLoadStep` + `LoaderPipelineConfig` Factory 1건 등록 (안에서 제원/수질 분기) |
| sync-agent-common | — | 없음 |
| sync-orchestrator | Agent 3건 등록 (UI / DB row) | 없음 |

> 4/15 의 "코드 신규 0 건" 약속은 **B-2 채택으로 무효** (§3.5).
> 코드 신규: 엔티티 2개 수정 + 커스텀 Step 2개 + Factory 등록 2건.
> 기존 SimpleLoadStep / source-to-if Factory 는 그대로 살아있음 — 다른 파이프라인 영향 0.

---

## 8. 수락 기준

- A.6 — 소스 PG `tm_gd010310` / `td_gd010310` 양쪽에 행 발생 (UK 없음 → INSERT only)
- B.5 — 한 사이클 실행으로 IF_SND / IF_RSV / Target Oracle 4개 테이블 모두 같은 `execution_id` 로 row 적재
- **B.5b** — dedup 동작 검증 (B-2 핵심)
  - 같은 yyyy 두 번 실행 → Target 변화 0
  - 일부 수질값 변조 후 재실행 → Target 에 두 행 공존 (수질) — 레거시 동작 일치
  - 일부 시설명 변조 후 재실행 → Target 에 1행만, 시설명 갱신 (제원) — 레거시 동작 일치
- B.6 — `/trace-source` 로 Target 1건 → 외부 API 응답 원본 식별 가능 (source_refs 매칭)
- C.1 / C.2 — 회귀 200/200, 카탈로그 16/16

---

## 9. 작업 진입 게이트

| Q | 상태 |
|---|------|
| Q1: 새 API ServiceKey 발급 | ✅ 4/29 사용자 확인 완료 |
| Q2: 수질 dedup 정책 (B-2 vs B-1/B-3) | ✅ 4/29 토론 결정 — **B-2 (레거시 충실 재현)** §3.5 |
| Q2-추가: 제원도 같은 방향 (커스텀 Step) | 🟡 사용자 명시 동의 필요 — "기능 완벽 보전" 입장으로 (제원-B) 채택 가정 반영 |
| Q3: 본 계획서 확정 → §A.2 부터 진행 | 🟡 사용자 OK 대기 |

> Q2-추가 / Q3 OK 시 진행 순서:
> 1. §A.2 — claude 가 curl/Postman 으로 첫 호출 → 응답 트리 캡처 → 사용자 공유
> 2. §A.3 — 매핑 diff 결과 보고
> 3. §B.0 — 엔티티 UK 제거 + 빌드
> 4. §B.3a / §B.3b — 커스텀 Step 2종 구현 (각각 별도 PR-급 작업, 레거시 코드 재확인 포함)
> 5. §B.4 ~ §B.6 — Agent 등록 + E2E + dedup 검증

> 키 발급 ID 알려주면 §A.2 부터 진행.
