# 약수터 수질검사 API 수집 파이프라인 계획

> **작성일**: 2026-04-14
> **목적**: data.go.kr 약수터 수질검사 API → DMZ 수집 → 내부망 적재 파이프라인 구축
> **관련 분석 문서**:
> - `gims-yaksoter-table-analysis.md` — v2/v3 비교 + API 현황
> - `gims-yaksoter-api-field-mapping.md` — API ↔ 테이블 필드 매핑

---

## 1. 배경

### v2 구조 (AS-IS)
```
data.go.kr API → AdminBatchServiceImpl (단일 앱)
  → TM_GD20310 (제원) INSERT/UPDATE
  → TD_GD20310 (수질) INSERT (중복 SKIP)
```
- 한 앱에서 수집 + 검증 + 적재를 한방에 처리
- 내부망에서 직접 외부 API 호출

### 우리 구조 (TO-BE)
```
data.go.kr API
  → [DMZ] API Collector (8084) — 수집 → IF 테이블
  → [DMZ] SND Agent — IF → IF_SND
  → [내부망] Int RCV — IF_SND → IF_RSV
  → [내부망] Int Loader — IF_RSV → Target (검증 + UPSERT)
```
- DMZ에서 외부 API 호출 (내부망은 외부 접근 불가)
- 검증 로직은 내부망 Loader에서 수행
- 기존 파이프라인(SND → Int RCV → Int Loader) 재활용

---

## 2. 데이터 흐름 상세

### Step 1: API Collector (DMZ, 8084) — 수집
- data.go.kr `getSgisDrinkWaterList` 호출
- 응답 JSON → 파싱 → **API Collector DB (PG 29001, api_collector)**에 적재
- 대상 테이블 2개 생성 필요:
  - `yaksoter_jewon` — 제원정보 (API 응답의 시설 필드)
  - `yaksoter_wq_result` — 수질검사 결과 (API 응답의 측정항목 필드)

### Step 2: SND Agent (DMZ, sync-agent-others 8085) — IF_SND 전환
- `yaksoter_jewon` → `if_snd_yaksoter_jewon`
- `yaksoter_wq_result` → `if_snd_yaksoter_wq_result`
- 기존 `source-to-if` Step Factory 사용

### Step 3: Int RCV (내부망, sync-agent-bojo-int 8092) — IF_RSV 수신
- `if_snd_yaksoter_jewon` → `if_rsv_yaksoter_jewon`
- `if_snd_yaksoter_wq_result` → `if_rsv_yaksoter_wq_result`
- 기존 RCV 파이프라인 그대로

### Step 4: Int Loader (내부망, sync-agent-bojo-int 8092) — 검증 + 적재
- `if_rsv_yaksoter_jewon` → `TM_GD010310` (표준화 제원)
- `if_rsv_yaksoter_wq_result` → `TD_GD010310` (표준화 수질결과)
- **검증 로직** (v2 로직 이관):
  - 제원: 지점번호(`BRNCH_NO`) 기준 존재 체크 → 시설명/주소 변경 시 UPDATE, 아니면 SKIP
  - 수질: 지점번호+연도+분기 기준 중복 체크 → 없으면 INSERT, 있으면 SKIP
- **필드 변환**: API 필드명(현행) → 환경부표준 컬럼명 (매핑 문서 참조)

---

## 3. 구현 항목

### 3-1. API Collector (DMZ)

| 항목 | 내용 |
|------|------|
| API Endpoint 등록 | UI에서 data.go.kr URL + 서비스키 + 파라미터 등록 |
| 응답 파싱 | `dataRootPath`: `body.items.item` (또는 `getSgisDrinkWaterList.item`) |
| 필드 매핑 등록 | API 필드 → 소스 테이블 컬럼 (65개 수질 + 24개 제원) |
| 스케줄 등록 | 분기 1회 (data.go.kr 갱신 주기에 맞춤) |
| 소스 테이블 DDL | `yaksoter_jewon`, `yaksoter_wq_result` (PG) |

**고려사항**:
- API 응답 1건에 제원+수질이 혼합 → **API Collector에서 2개 테이블로 분리 적재**하는 방법 필요
- 현재 API Collector는 1 Endpoint = 1 Target Table 구조
- 선택지:
  - (A) Endpoint 2개 등록 (같은 API, 다른 필드 매핑) — 2회 호출 but 단순
  - (B) 1회 호출 → 2개 테이블 적재 기능 추가 — 효율적 but 개발 필요
  - **(C) 1개 테이블에 전부 넣고, Loader에서 분리** — API Collector 수정 없음

### 3-2. SND Agent (DMZ, sync-agent-others)

| 항목 | 내용 |
|------|------|
| YAML 추가 | `dmz-others-snd-yaksoter.yml` (또는 기존 YAML에 step 추가) |
| Entity 추가 | `IfSndYaksoterJewon`, `IfSndYaksoterWqResult` |
| IF_SND 테이블 DDL | `if_snd_yaksoter_jewon`, `if_snd_yaksoter_wq_result` |

### 3-3. Int RCV (내부망, sync-agent-bojo-int)

| 항목 | 내용 |
|------|------|
| YAML 추가 | `int-rcv-yaksoter.yml` (또는 기존 YAML에 step 추가) |
| Entity 추가 | `IfRsvYaksoterJewon`, `IfRsvYaksoterWqResult` |
| IF_RSV 테이블 DDL | `if_rsv_yaksoter_jewon`, `if_rsv_yaksoter_wq_result` |

### 3-4. Int Loader (내부망, sync-agent-bojo-int) — 핵심

| 항목 | 내용 |
|------|------|
| 커스텀 Step | `YaksoterLoadStep` (또는 `DrinkWaterLoadStep`) |
| 필드 변환 | API 현행 컬럼 → 환경부표준 컬럼 매핑 (매핑 문서 기반) |
| 제원 검증 | `BRNCH_NO` 기준 UPSERT (시설명/주소 변경 시 UPDATE) |
| 수질 검증 | `BRNCH_NO` + `YR` + `QTR` 기준 중복 SKIP |
| Target 테이블 DDL | `TM_GD010310`, `TD_GD010310` (내부 Oracle) |
| 타입 변환 주의 | VARCHAR→NUMBER (순번, 이용자수), VARCHAR→CHAR(1) (Y/N 필드), 날짜 10→8 |

---

## 4. 테이블 설계 (신규)

### DMZ PG (api_collector DB)

```sql
-- 소스 테이블 (API Collector 적재 대상)
-- 컬럼명은 API 필드 camelCase → snake_case

CREATE TABLE yaksoter_jewon (
  legacy_code_no VARCHAR(10) PRIMARY KEY,  -- 지점번호
  spot_nm VARCHAR(50),
  spot_std_code VARCHAR(15),
  -- ... (24개 컬럼, 매핑 문서 참조)
);

CREATE TABLE yaksoter_wq_result (
  legacy_code_no VARCHAR(10),  -- 지점번호
  spot_std_code VARCHAR(15),
  yyyy VARCHAR(4),
  period VARCHAR(4),
  -- ... (56개 수질항목 + 기본필드)
  PRIMARY KEY (legacy_code_no, spot_std_code, yyyy, period)
);
```

### DMZ PG (dev DB) — IF_SND

```sql
CREATE TABLE if_snd_yaksoter_jewon ( ... + source_refs, link_status, execution_id, extracted_at );
CREATE TABLE if_snd_yaksoter_wq_result ( ... + source_refs, link_status, execution_id, extracted_at );
```

### 내부 PG (dev DB) — IF_RSV

```sql
CREATE TABLE if_rsv_yaksoter_jewon ( ... + source_refs, link_status, execution_id, extracted_at );
CREATE TABLE if_rsv_yaksoter_wq_result ( ... + source_refs, link_status, execution_id, extracted_at );
```

### 내부 Oracle — Target (표준화)

```sql
CREATE TABLE TM_GD010310 ( ... 환경부표준 컬럼명 24개 );
CREATE TABLE TD_GD010310 ( ... 환경부표준 컬럼명 56개 );
```

---

## 5. 작업 순서 (제안)

| 순서 | 작업 | 모듈 | 비고 |
|------|------|------|------|
| 1 | DDL 작성 (소스/IF_SND/IF_RSV/Target 전체) | scripts/ddl/ | 표준화 매핑 파일 기반 |
| 2 | API Collector에 Endpoint + 매핑 등록 | infolink-api-collector | UI에서 등록 or seed 스크립트 |
| 3 | API 수집 테스트 (수동 실행) | infolink-api-collector | 데이터 확인 |
| 4 | SND Agent YAML + Entity 추가 | sync-agent-others | source-to-if step |
| 5 | Int RCV YAML + Entity 추가 | sync-agent-bojo-int | 기존 RCV 패턴 |
| 6 | Int Loader 커스텀 Step 구현 | sync-agent-bojo-int | 검증 + 필드변환 + UPSERT |
| 7 | E2E 테스트 | 전체 | API 수집 → DMZ → 내부 → Oracle |
| 8 | 스케줄 등록 | infolink-api-collector | 분기 1회 |

---

## 6. 미결 사항

| # | 내용 | 담당 |
|---|------|------|
| 1 | **API 서비스키 발급** — data.go.kr에서 `WaterQualityService` 활용신청 필요 | 사용자 |
| 2 | **1 API → 2 테이블 분리**: (A) 2회 호출 vs (B) 멀티 타겟 vs (C) 단일 테이블+Loader 분리 | 결정 필요 |
| 3 | **표준화 Target 테이블명 확정**: `TM_GD010310` / `TD_GD010310`이 맞는지 | 확인 필요 |
| 4 | **Oracle DDL 배포**: 내부 Oracle에 표준화 테이블 생성 권한 | DBA |
| 5 | **API 호출 제한**: data.go.kr 트래픽 제한 확인 (개발계정 10,000건/월) | 확인 필요 |
