# E2E 파이프라인 테스트 계획서

## 목적
전체 파이프라인(RCV → Loader → SND → Internal RCV → Internal Loader)의
**건수 정합성 + Source 추적 + SyncLog 정확성**을 처음부터 끝까지 검증

---

## 테스트 데이터 전략

### 외부 DB obsvdata 현황
| DB | jewon (full copy) | obsvdata 전체 | 날짜 분포 |
|----|-------------------|--------------|----------|
| daejeon (PG 29000) | 402 | 15,287 | 2/24: 4952, 2/25: 19, 3/4: 4957, 3/5: 4957, **3/10: 402** |
| keunsan (PG 29000) | 402 | ~15,281 | (유사 분포), **3/10: 402** |
| infoworld-local (MySQL 29010) | 402 | ~15,339 | (유사 분포), **3/10: 402** |

### 테스트 데이터 설계
- **3/10(오늘) 날짜로 obsvdata 402건 × 3개 DB 신규 INSERT 완료**
  - 시간: 00:00:00 (1시간대), remark = 'test-0310'
  - 402개 obsv_code × 1시간대 = 402건/DB
- **link_ngwis 날짜를 3/9로 통일** (TRUNCATE 아님, UPDATE)
  - `UPDATE link_ngwis SET obsv_date = '2026-03-09', obsv_time = '235959'`
  - RCV 조회 조건: `obsv_time > lastTime` (초과, 이상 아님 — `>=` → `>` 수정 완료)
  - 3/9 23:59:59 초과 → **3/10 00:00:00 데이터만 정확히 수집**
- **코드 수정 1**: `LinkTableObsvDataFetcher.java` — 극점 중복 방지
  - MySQL: `DATE_FORMAT >= ?` → `DATE_FORMAT > ?`
  - PostgreSQL: `TO_CHAR >= ?` → `TO_CHAR > ?`
- **코드 수정 2**: `OrchestratorClient.java` — 콜백 재시도 추가
  - `notifyStarted`, `notifyFinished` 모두 최대 3회 재시도 (간격 2s/4s/6s)
  - 이전에 발생하던 콜백 유실(Agent 완료 → Orchestrator 미수신) 방지
- **빌드 완료**: common JAR → 4개 모듈(bojo, bojo-int, proxy-dmz, proxy-internal) 복사 + 빌드
- **결과**: jewon 402건 = obsvdata 402건 (업체당), 깔끔한 1:1 대조 가능

### 기대 건수 (확정)
- **N = 402** (업체당 obsvdata)
- jewon = 402/업체, obsvdata = 402/업체
- 전체: jewon = 1206, obsvdata = 1206

---

## 0. 데이터 초기화 (Clean Slate)

### 현재 상태 (이전 테스트 잔여 데이터)
- DMZ DB: RCV 3개 + Loader 실행 완료 상태
  - if_rsv_sec_jewon: 1206, if_rsv_sec_obsvdata: 1206
  - sec_jewon: 1206, sec_obsvdata: 1206
  - sync_log: RCV 6건 + Loader 2건
- Internal DB: 이전 세션 데이터 잔존 가능
- Orchestrator: execution_history 4건 (RCV 3 + Loader 1)
- 외부 DB: **3/10 테스트 데이터 402건×3 INSERT 완료** (정리 불필요)

### TRUNCATE 대상

| DB | 포트/DB명 | TRUNCATE 대상 |
|----|-----------|---------------|
| DMZ | 29001/dev | `if_rsv_sec_jewon`, `if_rsv_sec_obsvdata`, `if_snd_sec_jewon`, `if_snd_sec_obsvdata`, `sec_jewon`, `sec_obsvdata`, `sync_log`, `execution` |
| Internal | 29002/dev | `if_rsv_sec_jewon`, `if_rsv_sec_obsvdata`, `pm_gd970201`, `tm_gd980002`, `sync_log`, `execution` |
| Orchestrator | 29001/orchestrator | `execution_step_history`, `execution_history` (DELETE, FK 순서 주의) |

### link_ngwis 초기화 (UPDATE, TRUNCATE 아님)
```sql
UPDATE link_ngwis SET obsv_date = '2026-03-09', obsv_time = '235959', update_time = CURRENT_TIMESTAMP;
```
- 402개 obsv_code 레코드 유지, 날짜만 3/9 23:59:59로 통일
- RCV가 `> 3/9 23:59:59` 조건으로 3/10 데이터만 수집

### 초기화에서 제외 (건드리지 않는 것)
| 대상 | 이유 |
|------|------|
| `tm_gd970001` (Internal) | GIMS 제원 마스터, Loader target 아님 |
| `tm_gd970101` (Internal) | target에서 제거됨, 데이터 있어도 무시 |
| 외부 DB 3개 | 읽기 전용 소스, 3/10 테스트 데이터 포함 |

### 초기화 후 검증
- TRUNCATE 대상 테이블 전부 `SELECT COUNT(*) = 0` 확인
- `link_ngwis`: 건수 유지, `MAX(obsv_date) = 2026-03-09`, `obsv_time = 235959` 확인

---

## 1. RCV 3개 실행

### 대상 Agent
| Agent | Orchestrator ID | 외부 DB | 특이사항 |
|-------|----------------|---------|----------|
| dmz-bojo-rcv-daejeon | 7 | PG 29000/daejeon | 소문자 |
| dmz-bojo-rcv-keunsan | 12 | PG 29000/keunsan | 대문자 테이블/컬럼 |
| dmz-bojo-rcv-infoworld-local | 9 | MySQL 29010/infoworld_local | MySQL |

### RCV 동작 원리
- **jewon**: `full-copy: true` → 전체 조회 (402건/업체)
- **obsvdata**: link_ngwis 기반 증분 — 3/9 23:59:59 초과 → **3/10 00:00:00 데이터 402건/업체**
- **link_ngwis**: RCV 후 LinkTableUpdateStep이 obsv_code별 최신 날짜 UPSERT (3/9 → 3/10 갱신)

### RCV 검증 항목

| # | 검증 항목 | 방법 | 기대값 |
|---|----------|------|--------|
| 1-1 | Orchestrator 실행 상태 | `execution_history.status` | 3건 모두 SUCCESS |
| 1-2 | SyncLog jewon 건수 | `sync_log WHERE mapping_name='jewon'` | read=402, write=402 (×3) |
| 1-3 | SyncLog obsvdata 건수 | `sync_log WHERE mapping_name='obsvdata'` | read=402, write=402 (×3) |
| 1-4 | IF_RSV jewon 총건수 | `SELECT COUNT(*) FROM if_rsv_sec_jewon` | 1206 |
| 1-5 | IF_RSV obsvdata 총건수 | `SELECT COUNT(*) FROM if_rsv_sec_obsvdata` | 1206 |
| 1-6 | link_ngwis 최신 날짜 | `SELECT MAX(obsv_date) FROM link_ngwis` | 2026-03-10 (3/9 → 3/10 갱신 확인) |
| 1-7 | Source 추적 - jewon | Proxy `/api/execution-data/source` | 각 agent별 402건 |
| 1-8 | Source 추적 - obsvdata | Proxy `/api/execution-data/source` | 각 agent별 402건 |

> **핵심**: jewon과 obsvdata **둘 다** 추적 테스트 필수

---

## 2. DMZ Loader 실행

### Agent
| Agent | Orchestrator ID | 동작 |
|-------|----------------|------|
| dmz-bojo-loader | 17 | IF_RSV → Target (sec_jewon, sec_obsvdata) |

### Loader 동작 원리
- **Incremental (기본)**: `link_status = PENDING` 레코드만 처리
- RCV 직후이므로 IF_RSV의 모든 레코드가 PENDING → 전체 처리

### Loader 검증 항목

| # | 검증 항목 | 방법 | 기대값 |
|---|----------|------|--------|
| 2-1 | 실행 상태 | `execution_history.status` | SUCCESS |
| 2-2 | SyncLog jewon | `sync_log` | read=1206, write=1206 |
| 2-3 | SyncLog obsvdata | `sync_log` | read=1206, write=1206 |
| 2-4 | Target sec_jewon 건수 | DB 직접 조회 | 1206 |
| 2-5 | Target sec_obsvdata 건수 | DB 직접 조회 | 1206 |
| 2-6 | IF_RSV link_status | `WHERE link_status = 'SUCCESS'` | 전체 = 2412 (1206×2) |
| 2-7 | Source 추적 - jewon | Proxy `/source` (source_refs 모드) | 1206건 |
| 2-8 | Source 추적 - obsvdata | Proxy `/source` (source_refs 모드) | 1206건 |

---

## 3. DMZ SND 실행

### Agent
| Agent | Orchestrator ID | 동작 |
|-------|----------------|------|
| dmz-bojo-snd | 18 | Target → IF_SND |

### SND 검증 항목

| # | 검증 항목 | 방법 | 기대값 |
|---|----------|------|--------|
| 3-1 | 실행 상태 | | SUCCESS |
| 3-2 | SyncLog jewon | | read=1206, write=1206 |
| 3-3 | SyncLog obsvdata | | read=1206, write=1206 |
| 3-4 | IF_SND jewon 건수 | DB | 1206 |
| 3-5 | IF_SND obsvdata 건수 | DB | 1206 |
| 3-6 | Source 추적 - jewon | Proxy (PK 파싱 모드) | 1206건 |
| 3-7 | Source 추적 - obsvdata | Proxy (PK 파싱 모드) | 1206건 |

---

## 4. Internal RCV 실행

### Agent
| Agent | Orchestrator ID | 동작 |
|-------|----------------|------|
| internal-bojo-rcv | 19 | DMZ IF_SND → Internal IF_RSV |

### Internal RCV 검증 항목

| # | 검증 항목 | 방법 | 기대값 |
|---|----------|------|--------|
| 4-1 | 실행 상태 | | SUCCESS (콜백 타임아웃 주의) |
| 4-2 | SyncLog jewon | Internal DB sync_log | read=1206, write=1206 |
| 4-3 | SyncLog obsvdata | | read=1206, write=1206 |
| 4-4 | Internal IF_RSV jewon 건수 | Internal DB | 1206 |
| 4-5 | Internal IF_RSV obsvdata 건수 | Internal DB | 1206 |
| 4-6 | Source 추적 - jewon | Internal Proxy `/source` | 1206건 |
| 4-7 | Source 추적 - obsvdata | Internal Proxy `/source` | 1206건 |

> 콜백 재시도 로직 추가됨 (3회) — 이전 콜백 유실 문제 해결 기대

---

## 5. Internal Loader 실행

### Agent
| Agent | Orchestrator ID | 동작 |
|-------|----------------|------|
| internal-bojo-loader | 20 | Internal IF_RSV → GIMS (pm_gd970201만) |

### Internal Loader 특이사항
- **obsvdata만 처리** (jewon은 매핑 없음 — tm_gd970001은 read-only 마스터)
- **tm_gd970101은 target에서 제거됨** → SyncLog에 나타나면 안 됨

### Internal Loader 검증 항목

| # | 검증 항목 | 방법 | 기대값 |
|---|----------|------|--------|
| 5-1 | 실행 상태 | | SUCCESS |
| 5-2 | SyncLog obsvdata | Internal DB sync_log | read=1206, write=1206 |
| 5-3 | SyncLog targetTables | `target_tables` 컬럼 | `["pm_gd970201"]` — tm_gd970101 없음 |
| 5-4 | pm_gd970201 건수 | Internal DB | 1206 |
| 5-5 | Source 추적 - obsvdata | Internal Proxy `/source` | 1206건 (source_refs 모드) |

---

## 6. 최종 정합성 요약표

파이프라인 완료 후 아래 표를 채워서 전체 건수 추적

```
[외부 DB]                    [DMZ DB 29001/dev]                              [Internal DB 29002/dev]
jewon_view (402×3)  →RCV→  if_rsv_jewon (1206) →Loader→ sec_jewon (1206) →SND→ if_snd_jewon (1206) →IntRCV→ if_rsv_jewon (1206)
obsvdata_view(402×3)→RCV→  if_rsv_obsv  (1206) →Loader→ sec_obsv  (1206) →SND→ if_snd_obsv  (1206) →IntRCV→ if_rsv_obsv  (1206) →IntLoader→ pm_gd970201 (1206)
                           link_ngwis (402개 obsv_code, max_date=3/10)
```

| 체크포인트 | jewon | obsvdata |
|-----------|-------|----------|
| 외부 Source (×3) | 402×3 = 1206 | 402×3 = 1206 |
| IF_RSV (DMZ) | 1206 | 1206 |
| Target (DMZ) | 1206 | 1206 |
| IF_SND (DMZ) | 1206 | 1206 |
| IF_RSV (Internal) | 1206 | 1206 |
| pm_gd970201 (GIMS) | — (매핑없음) | 1206 |
| link_ngwis | 초기 3/9 → RCV 후 3/10 | — |

---

## 7. Source 추적 테스트 (총 9건)

| 단계 | Agent | 테이블 | 추적 모드 | 기대건수 | Proxy |
|------|-------|--------|----------|---------|-------|
| RCV | daejeon | jewon | PK 파싱 | 402 | DMZ 8083 |
| RCV | daejeon | obsvdata | PK 파싱 | 402 | DMZ 8083 |
| RCV | keunsan | jewon | PK 파싱 | 402 | DMZ 8083 |
| RCV | keunsan | obsvdata | PK 파싱 | 402 | DMZ 8083 |
| Loader | dmz-loader | jewon | source_refs | 1206 | DMZ 8083 |
| Loader | dmz-loader | obsvdata | source_refs | 1206 | DMZ 8083 |
| SND | dmz-snd | jewon | PK 파싱 | 1206 | DMZ 8083 |
| SND | dmz-snd | obsvdata | PK 파싱 | 1206 | DMZ 8083 |
| Int Loader | int-loader | obsvdata | source_refs | 1206 | Internal 8093 |

> RCV는 3개 중 대표 2개(daejeon + keunsan)만 추적
> infoworld-local은 MySQL 특성상 별도 확인 필요 시 추가

---

## 8. 실행 순서

```
Step 0: 데이터 초기화 (link_ngwis 제외) → 검증 (대상 테이블 0건, link_ngwis 유지)
Step 1: RCV 3개 실행 (순차 또는 병렬) → 검증 1-1~1-8
Step 2: DMZ Loader 실행 → 검증 2-1~2-8
Step 3: DMZ SND 실행 → 검증 3-1~3-7
Step 4: Internal RCV 실행 → 검증 4-1~4-7
Step 5: Internal Loader 실행 → 검증 5-1~5-5
Step 6: 최종 정합성 표 작성
```

각 Step에서 검증 실패 시 → 원인 분석 후 해당 Step부터 재실행 (이전 Step은 유지)
