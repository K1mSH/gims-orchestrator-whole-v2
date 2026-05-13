# 비추적(non-traceable) 테이블 목록

행 단위 `source_refs` 추적이 부적합해서 비추적으로 분류한 테이블. **`source_refs` 컬럼은 NULL 유지**. (※ SyncLog 매핑 포함 여부는 별개 차원 — 운영자에게 가시화하느냐의 문제. 두 차원 혼동 금지.)

| 테이블                       | 이유         | 어디서 적재                                                      |
|------------------------------|--------------|------------------------------------------------------------------|
| `link_ngwis`                 | Link/스냅샷  | `LinkTableUpdateStep` (DMZ Loader / `dmz-bojo-rcv-*.yml`)        |
| `tm_gd980002`                | Link         | `InternalBojoLoadStep` (`internal-bojo-loader.yml`)              |
| `tm_gd970101` (Link 한정 ※) | Link         | `InternalBojoLoadStep` (`internal-bojo-loader.yml`)              |
| `PM_GD111022` (일집계)       | 파생 집계    | `UseLoadStep.updateDailyAggregation` (`internal-use-loader.yml`) |
| `TM_GD111024` (최근수신)     | Link/스냅샷  | `UseLoadStep.updateLastReceive` (`internal-use-loader.yml`)      |

※ `tm_gd970101` — Link 컨텍스트(`internal-bojo-loader`)일 때만 비추적. **jeju-jewon-load 의 EAV 결과정의**로 쓰일 땐 일반 추적 대상 (`JejuJewonLoadStep`, `internal-jeju-loader.yml`).

이력: 2026-05-13 — 이용량 Loader 작업 중 `PM_GD111022` / `TM_GD111024` 추가, 과거 Link 룰 통합.
