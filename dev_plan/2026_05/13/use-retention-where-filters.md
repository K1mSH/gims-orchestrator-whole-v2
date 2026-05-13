# 이용량 — Retention 후보 + Where-filters yml 박기

작성일: 2026-05-13
배경: 이용량 SND/RCV/Loader 3 yml 의 `retention-candidates: []` 비대상 명시만 있고, `where-filters` 도 미선언(→ 프론트가 모든 source 컬럼 노출 — 메타 포함 ~20컬럼 노이즈). 다른 도메인(bojo) 패턴 따라 명시.

## 변경 대상

| yml                      | retention 추가 | where-filters 추가 |
|--------------------------|----------------|---------------------|
| `dmz-others-snd-use`     | 2 후보         | -                   |
| `internal-use-rcv`       | 2 후보         | -                   |
| `internal-use-loader`    | 2 후보         | 5 필터              |

(Where-filters 는 관례상 Loader 만 — RCV/SND 는 link_status 자동 동기화라 의미 약함.)

## Retention 후보 (6개)

| yml      | table                     | dateColumn   | description                                  |
|----------|---------------------------|--------------|----------------------------------------------|
| SND      | `if_snd_use_legacy_data`  | `obsr_dt`    | 이용량 시간자료 SND transit (DMZ→Internal)  |
| SND      | `if_snd_use_status_data`  | `obsr_dt`    | 이용량 관측데이터 SND transit                |
| RCV      | `IF_RSV_USE_LEGACY_DATA`  | `OBSR_DT`    | 이용량 시간자료 RCV (Internal 수신)          |
| RCV      | `IF_RSV_USE_STATUS_DATA`  | `OBSR_DT`    | 이용량 관측데이터 RCV                        |
| Loader   | `PM_GD111021`             | `OBSRVN_DT`  | 이용량 시간자료 (GIMS 통합)                  |
| Loader   | `TM_GD111025`             | `OBSRVN_DT`  | 이용량 관측데이터 (GIMS 통합)                |

**보류** (string YYYYMMDD — `RetentionCandidate` 가 date/timestamp 만 지원, 별개 enhancement todo):
- `if_snd_use_jeju_day` / `IF_RSV_USE_JEJU_DAY` / `PM_GD111022` (dateColumn varchar)

**비대상**:
- `TM_GD111024` (최근수신 Link 스냅샷 — `docs/non-traceable-tables.md`)

## Where-filters (5개, Loader 만)

source = `IF_RSV_USE_LEGACY_DATA` + `IF_RSV_USE_STATUS_DATA`. 운영자 의미있는 비즈니스 컬럼만 큐레이션 (메타 컬럼 `ID`/`SOURCE_REFS`/`LINK_STATUS`/`EXTRACTED_AT`/`UPDATED_AT`/`EXECUTION_ID` 숨김).

| key           | label                | table                     | column   | operators  | valueType  |
|---------------|----------------------|---------------------------|----------|------------|------------|
| telno-legacy  | 전화번호(시간자료)   | IF_RSV_USE_LEGACY_DATA    | TELNO    | LIKE, IN   | STRING     |
| period-legacy | 측정시간(시간자료)   | IF_RSV_USE_LEGACY_DATA    | OBSR_DT  | BETWEEN    | DATETIME   |
| telno-status  | 전화번호(관측데이터) | IF_RSV_USE_STATUS_DATA    | TELNO    | LIKE, IN   | STRING     |
| period-status | 측정시간(관측데이터) | IF_RSV_USE_STATUS_DATA    | OBSR_DT  | BETWEEN    | DATETIME   |
| stat          | 상태(관측데이터)     | IF_RSV_USE_STATUS_DATA    | STAT     | IN, =      | STRING     |

## 변경 파일

- `infolink-agent-others-dmz/.../config/agents/dmz-others-snd-use.yml`
- `infolink-agent-bojo-internal/.../config/agents/internal-use-rcv.yml`
- `infolink-agent-bojo-internal/.../config/agents/internal-use-loader.yml`

코드 변경 없음 (yml 만).

## 영향 / 회귀

- Retention: yml 후보 등록만. 실제 삭제는 orchestrator DB 의 보존일수 설정 + 실행 시 적용. yml 변경으로 즉시 데이터 영향 없음.
- Where-filters: 프론트 조건실행 UI 의 컬럼 select 가 큐레이션됨 (기존 ~20컬럼 → 5필터). 운영자 UX 개선. backend/agent 동작 무영향 (yml 메타 노출만).
- 보류분(jeju_day/PM_GD111022) — string 날짜 retention enhancement 별개 트랙.

## 작업 순서

1. 계획 승인.
2. 3 yml 수정.
3. yml 재로드 위해 재기동: `others-dmz` (8085) + `bojo-internal` (8092).
4. 검증:
   - Orchestrator UI → 이용량 agent (`internal-use-loader`) 의 수동실행 화면 → 조건실행 select 에 5 필터만 노출되는지 확인.
   - Retention 설정 화면 (있으면) → 후보 6개 떠있는지 확인.
5. dev_log + 커밋.
