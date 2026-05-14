# Agent Retention / Where-filters 매트릭스

> 생성: 2026-05-14
> 단일 진실원 = 각 Agent yml 의 `retention-candidates` + `where-filters` 필드.
> 본 문서는 운영자/개발자가 한 페이지에서 전체 분포를 보기 위한 **요약 인덱스**.
> yml 변경 시 본 문서 같이 갱신 필수 (`feedback_retention_where_matrix_sync` 메모리 룰).

---

## 1. 개요

| 분류                 | 내용 |
|----------------------|------|
| Retention 정책       | `dev_plan/2026_05/08/retention-candidates-safety.md` (VER-016) — yml 화이트리스트 4-layer 검증 |
| Where-filters 정책   | `dev_plan/2026_05/12/yml-declared-where-filters.md` + Phase 6 롤아웃 — yml 선언형 WHERE 필터 |
| 검증 모델            | yml → Frontend dropdown → Backend PUT validation → Agent self-check (defense-in-depth) |
| 분류 기준            | retention = **데이터 발생 시점** 권장(관측일/측정일). transit/메타 시각도 운영 의도 검토 후 후보 가능. 마스터/Link/등록 류는 비대상(`[]`) |

---

## 2. Retention 매트릭스 (36 agent)

> **표 읽는 법** — `(table, dateColumn)` 쌍이 운영자 dropdown 후보. 빈 배열은 retention 비대상.
> "필드 없음" 은 yml 에 `retention-candidates` 키 자체 미선언 — backward-compat 으로 skip 되지만 의도 명시 권장.

### 2-1. DMZ Agent (bojo + others + provide)

| agent-code                            | type   | retention-candidates                          | 비고 |
|---------------------------------------|--------|-----------------------------------------------|------|
| dmz-bojo-rcv-bytek                    | RCV    | (if_rsv_sec_obsvdata, obsv_date)              | 외부 → IF_RSV 시계열 |
| dmz-bojo-rcv-chungnam                 | RCV    | (if_rsv_sec_obsvdata, obsv_date)              | 〃 |
| dmz-bojo-rcv-daejeon                  | RCV    | (if_rsv_sec_obsvdata, obsv_date)              | 〃 |
| dmz-bojo-rcv-hydronet-ara             | RCV    | (if_rsv_sec_obsvdata, obsv_date)              | 〃 |
| dmz-bojo-rcv-hydronet-idc             | RCV    | (if_rsv_sec_obsvdata, obsv_date)              | 〃 |
| dmz-bojo-rcv-hydronet-kyungnam        | RCV    | (if_rsv_sec_obsvdata, obsv_date)              | 〃 |
| dmz-bojo-rcv-hydronet-wonju           | RCV    | (if_rsv_sec_obsvdata, obsv_date)              | 〃 |
| dmz-bojo-rcv-infoworld-local          | RCV    | (if_rsv_sec_obsvdata, obsv_date)              | 〃 |
| dmz-bojo-rcv-infoworld-seoul          | RCV    | (if_rsv_sec_obsvdata, obsv_date)              | 〃 |
| dmz-bojo-rcv-keunsan                  | RCV    | (if_rsv_sec_obsvdata, obsv_date)              | 〃 |
| dmz-bojo-snd                          | SND    | (if_snd_sec_obsvdata, obsv_date)              | DMZ → Internal 송신용 시계열 |
| dmz-bojo-loader                       | LOADER | (sec_obsvdata, obsv_date)                     | DMZ 통합 시계열 |
| dmz-others-snd-api-collect            | SND    | `[]`                                           | 외부 API 수집 — TBD ⚠️ |
| dmz-others-snd-jeju                   | SND    | `[]`                                           | jeju (수위/제원/시설 혼합) — TBD ⚠️ |
| dmz-others-snd-saeol                  | SND    | 16 IF_SND_RGET\* × (EXTRACTED_AT + UPDATED_AT) = **32 후보** | 새올 SND transit — 우리 메타 시각 기준만 (원본 시각 영구 보존) |
| dmz-others-snd-yaksoter               | SND    | `[]`                                           | 약수터 시설 등록 — 비대상 OK |
| dmz-others-snd-use                    | SND    | (legacy/status × obsr_dt+extracted_at) + (jeju_day × extracted_at) = **5 후보** | 이용량 SND transit 시계열 |
| provide-tm-gd000203                   | LOADER | 필드 없음                                       | provide-* 전수 누락 ⚠️ |
| provide-tm-gd110301                   | LOADER | 필드 없음                                       | 〃 |
| provide-tm-gd110302                   | LOADER | 필드 없음                                       | 〃 |
| provide-tm-gd112002                   | LOADER | 필드 없음                                       | 〃 |
| provide-tm-gd120001                   | LOADER | 필드 없음                                       | 〃 |
| provide-tm-gd130001                   | LOADER | 필드 없음                                       | 〃 |
| provide-tmp-megokr-api                | LOADER | 필드 없음                                       | 〃 |
| provide-wt-dream-permwell-public-21033| LOADER | 필드 없음                                       | 〃 |

### 2-2. Internal Agent (bojo + jeju/saeol/use/yaksoter/api-collect)

| agent-code                  | type   | retention-candidates                              | 비고 |
|-----------------------------|--------|---------------------------------------------------|------|
| internal-bojo-rcv           | RCV    | (IF_RSV_SEC_OBSVDATA, OBSV_DATE)                  | Internal 수신 시계열 |
| internal-bojo-loader        | LOADER | (PM_GD970201, OBSRVN_DT)                          | GIMS 관측 시계열 (EAV 1:3) |
| internal-jeju-rcv           | RCV    | `[]`                                               | jeju 혼합 — 수위 데이터 시계열 가능성 ⚠️ |
| internal-jeju-loader        | LOADER | `[]`                                               | 〃 |
| internal-saeol-rcv          | RCV    | 16 IF_RSV_RGET\* × (EXTRACTED_AT + UPDATED_AT) = **32 후보** | 새올 RCV transit |
| internal-saeol-loader       | LOADER | 16 RGET\* × (EXTRACTED_AT + UPDATED_AT) = **32 후보** | 새올 GIMS 마스터 — 우리 메타만 |
| internal-use-rcv            | RCV    | (LEGACY/STATUS × OBSR_DT+EXTRACTED_AT) + (JEJU_DAY × EXTRACTED_AT) = **5 후보** | 이용량 RCV transit 시계열 |
| internal-use-loader         | LOADER | (PM_GD111021 × OBSRVN_DT) + (TM_GD111025 × OBSRVN_DT+LAST_CHG_DT) = **3 후보** | 이용량 GIMS 통합 시계열 |
| internal-yaksoter-rcv       | RCV    | `[]`                                               | 약수터 시설 — 비대상 OK |
| internal-yaksoter-loader    | LOADER | `[]`                                               | 〃 |
| internal-api-collect-rcv    | RCV    | `[]`                                               | 외부 API 수집 — TBD ⚠️ |
| internal-api-collect-loader | LOADER | `[]`                                               | 〃 |

---

## 3. Where-filters 매트릭스 (36 agent)

> **표 읽는 법** — `key:table.column / operators / valueType` 1줄당 1필터. 운영자 수동실행 화면 조건 dropdown 에 노출.
> 관례 — RCV/SND 는 link_status 자동 동기화로 의미 약함 → **Loader 만 선언** 권장.

| agent-code                            | type   | where-filters |
|---------------------------------------|--------|---------------|
| dmz-bojo-rcv-* (10개)                 | RCV    | (없음) |
| dmz-bojo-snd                          | SND    | (없음) |
| **dmz-bojo-loader**                   | LOADER | `region_jewon: if_rsv_sec_jewon.obsv_code / LIKE,IN / STRING`<br>`region_obsvdata: if_rsv_sec_obsvdata.obsv_code / LIKE,IN / STRING`<br>`period: if_rsv_sec_obsvdata.obsv_date / BETWEEN / DATE` |
| internal-bojo-rcv                     | RCV    | (없음) |
| **internal-bojo-loader**              | LOADER | `region: IF_RSV_SEC_OBSVDATA.OBSV_CODE / LIKE,IN / STRING`<br>`period: IF_RSV_SEC_OBSVDATA.OBSV_DATE / BETWEEN / DATE` |
| internal-jeju-rcv                     | RCV    | (없음) |
| internal-jeju-loader                  | LOADER | (없음) — TBD ⚠️ |
| internal-saeol-rcv                    | RCV    | (없음) |
| internal-saeol-loader                 | LOADER | (없음) — TBD ⚠️ |
| internal-use-rcv                      | RCV    | (없음) |
| **internal-use-loader**               | LOADER | `telno-legacy: IF_RSV_USE_LEGACY_DATA.TELNO / LIKE,IN / STRING`<br>`period-legacy: IF_RSV_USE_LEGACY_DATA.OBSR_DT / BETWEEN / DATETIME`<br>`telno-status: IF_RSV_USE_STATUS_DATA.TELNO / LIKE,IN / STRING`<br>`period-status: IF_RSV_USE_STATUS_DATA.OBSR_DT / BETWEEN / DATETIME`<br>`stat: IF_RSV_USE_STATUS_DATA.STAT / IN,= / STRING` |
| internal-yaksoter-rcv                 | RCV    | (없음) |
| internal-yaksoter-loader              | LOADER | (없음) — TBD ⚠️ |
| internal-api-collect-rcv              | RCV    | (없음) |
| internal-api-collect-loader           | LOADER | (없음) — TBD ⚠️ |
| dmz-others-snd-* (5개)                | SND    | (없음) |
| provide-* (8개)                       | LOADER | (없음) — 전체 동기화, 필터 의미 약함 |

**(총 36 agent)**

---

## 4. 발견 사항 (별 트랙)

### ✅ 1. 이용량 retention/where (2026-05-14 완료)
- `dmz-others-snd-use` / `internal-use-rcv` / `internal-use-loader` (3 yml — 5 step) 정정
- retention 13 후보 + where 5 필터 등록 + 주석 정정
- 동반 fix: `DataRetentionScheduler` 가 cleanup body 에 `agent.targetDatasourceId` 자동 inject (운영자 입력 누락 방어)
- UseLoadStep MERGE 통일 (TM_GD111025) + 행단위 GREATEST 갱신 (TM_GD111024) + 카운트 fan-in 반영
- frontend UI 정합: retention dropdown unique table + dateColumn select, conditions UI label 형식 + datasource 안내
- 자세히: `dev_plan/2026_05/14/use-retention-where-merge-fix.md`

### ⚠️ 2. jeju 4 agent retention 검토 필요
- `dmz-others-snd-jeju` / `internal-jeju-rcv` / `internal-jeju-loader`
- 안에 시계열 (`if_snd_tb_jeju` 수위 관측) + 마스터 (`if_snd_tb_jeju_jewon` 제원 / `if_snd_rgetstgms01` 시설) 혼합.
- 수위 테이블은 retention 후보 가능, 제원/시설은 비대상. 분류 후 후보 등록.
- 별 todo — 본 사이클 외.

### ⚠️ 3. api-collect 4 agent retention 검토 필요
- `dmz-others-snd-api-collect` / `internal-api-collect-rcv` / `internal-api-collect-loader`
- 외부 API 수집 데이터 (뉴스/가뭄 등). 도메인별 시계열/스냅샷 분류 필요.
- 별 todo — 본 사이클 외.

### ⚠️ 4. provide-* 8 agent retention 필드 미선언
- provide-* 전 LOADER. 5/8 정책상 빈 배열 명시 권장 (`backward-compat` 으로 skip 중).
- 가벼운 정합 작업 — yml 에 `retention-candidates: []` + 주석 한 줄 추가.
- 본 사이클에 묶어 갈지, 별 todo 분리할지 결정 필요.

### ⚠️ 5. Loader Where-filters 미선언 6 agent
- `internal-jeju-loader` / `internal-saeol-loader` / `internal-use-loader` / `internal-yaksoter-loader` / `internal-api-collect-loader` + provide 8.
- 운영자 수동실행 시 모든 source 컬럼 노출되어 노이즈 (메타 컬럼 포함 ~10~20).
- 본 사이클 = `internal-use-loader` 5 필터 추가. 나머지 = 별 todo.

---

## 5. 동기화 룰

본 매트릭스의 단일 진실원은 yml. 아래 변경 시 **반드시 본 문서 같이 갱신**:
- yml `retention-candidates` 항목 추가/삭제/dateColumn 변경
- yml `where-filters` 항목 추가/삭제/operators/valueType 변경
- yml `agent-code` / `type` 변경 (드물지만 발생 시)

자동화는 별 트랙 — `scripts/dump-agent-config.py` 같은 yml → 마크다운 생성 스크립트 후보.

---

## 6. 변경 이력

| 일자       | 변경 |
|------------|------|
| 2026-05-14 | 신규 작성. 36 agent 전수 스캔 (Explore 서브에이전트). 이용량 정정 대상 + jeju/api-collect/provide 갭 명시. |
