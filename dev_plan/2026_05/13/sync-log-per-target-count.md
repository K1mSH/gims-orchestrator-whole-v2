# SyncLog 저장 공통화 + per-target 건수 — 처리현황 오표시 fix & 중복 제거

작성일: 2026-05-13
발견 경위: 04-others 검증 — `internal-jeju-loader` 4회차(`...3d067a41`) 후 처리현황 화면 `PM_GD970202` 성공건수 = **36**, 상세조회 = **9건**. `PM_GD970201`도 36(실제 27), `jeju-jewon-load`의 5개 타겟도 전부 12(실제 12/12/12/12/36).

## 원인

- `sync_log` = 매핑(step) 단위 1행, `write_count` 하나만. `target_tables` 는 JSON.
- 프론트(`executions/[id]/page.tsx` L341~344): target 행 그릴 때 `stat.targetCounts?.[t]` 있으면 그 값, 없으면 **step 전체 writeCount 를 모든 target 에 그대로** 표시.
- `targetCounts` = `ExecutionDataController.parseTargetCounts()` 가 `target_tables` JSON 이 `[{"name":"X","count":N},...]` 형식일 때만 채움. 단순 배열 `["X","Y"]` 면 null → fallback.
- DMZ Loader/공통 step 은 대부분 single-target 이라 fallback(writeCount=그 한 테이블 건수)이 우연히 맞음 → 문제 안 보임.
- **Internal Loader jeju 3 step 은 멀티타겟**인데 `target_tables` 단순배열 저장 → 모든 타겟에 step writeCount 동일 표시.
- `JejuJewonLoadStep` 은 `writeCount++`(IF행당 1) → step writeCount 자체도 IF건수(12)일 뿐 실 적재행수(84 = 12×7: 5테이블, 970101은 ×3)와 불일치.
- 부수: `saveSyncLog`/`saveSyncLogMapping` 가 거의 동일하게 **8군데 복붙** — `SourceToTargetStep.saveSyncLogMapping`, `LoaderStepHelper.saveSyncLog`, `JejuJewonLoadStep`/`JejuObsvdataLoadStep`/`JejuFacilityLoadStep`/`SimpleLoadStep`/`UseLoadStep`(2개)/`InternalBojoLoadStep`(`saveSyncLogMapping` + `saveSyncLogMappingWithCounts` — 이미 per-target 버전 존재). `target_tables` JSON 조립 코드도 전부 복붙.

## 방침 — 공통 헬퍼로 단일화 + 멀티타겟 per-target count (백엔드/프론트 무변경, 이미 `[{name,count}]` 지원)

### 1. common — 신규 클래스 2개

**`com.infolink.agent.common.sync.TargetCountTracker`**
```java
public final class TargetCountTracker {
    private final LinkedHashMap<String, Long> counts = new LinkedHashMap<>();
    public TargetCountTracker(String... knownTables) { for (String t: knownTables) counts.put(t, 0L); } // 0건 테이블도 노출(순서 보존)
    public void inc(String table)          { add(table, 1); }
    public void add(String table, long n)  { counts.merge(table, n, Long::sum); }
    public long total()                    { return counts.values().stream().mapToLong(Long::longValue).sum(); }
    public LinkedHashMap<String,Long> asMap() { return counts; }
    public boolean isEmpty()               { return counts.isEmpty(); }
}
```

**`com.infolink.agent.common.sync.SyncLogWriter`** (정적 유틸 — `SyncLogRepository` 인자로 받음)
```java
public static void save(SyncLogRepository repo, String executionId, String stepId, String mappingName,
                        List<String> sourceTables, /* 단순배열 케이스 */ List<String> targetTables,
                        long read, long write, long failed, long skip,
                        List<String> failedKeys, String errorSummary, String sourcePkColumn) { ... }

/** per-target count 버전 — target_tables = [{"name":..,"count":..},...] */
public static void save(SyncLogRepository repo, String executionId, String stepId, String mappingName,
                        List<String> sourceTables, TargetCountTracker tracker,
                        long read, long failed, long skip,                       // write = tracker.total()
                        List<String> failedKeys, String errorSummary, String sourcePkColumn) { ... }
```
- JSON 조립(`["a","b"]` / `[{"name":"a","count":3}]`)은 Jackson `ObjectMapper` 로 한 곳에서. `try/catch` 로 감싸 저장 실패 시 `log.warn` (현행 동작 유지).
- `SyncLog` 엔티티에 정적 헬퍼 `static String simpleArrayJson(List<String>)`, `static String countArrayJson(Map<String,? extends Number>)` 만 둘 수도 있음 — 위치는 구현 시 결정(중복 0 가 목표).

### 2. 호출처 전부 공통 헬퍼로 교체

| 파일 | 변경 |
|---|---|
| `common SourceToTargetStep.saveSyncLogMapping` | 본문 → `SyncLogWriter.save(...)` 위임 (single-target, 동작 무변경) |
| `bojo-dmz LoaderStepHelper.saveSyncLog` | 본문 → `SyncLogWriter.save(...)` 위임 (동작 무변경) |
| `bojo-internal InternalBojoLoadStep` | `saveSyncLogMapping`/`saveSyncLogMappingWithCounts` 2개 제거 → `SyncLogWriter.save` (멀티타겟이면 tracker 버전). 기존 `LinkedHashMap<String,Long> targetCounts` 흐름 그대로 tracker 로 |
| `bojo-internal SimpleLoadStep` | `saveSyncLog` 제거 → `SyncLogWriter.save`. **멀티타겟 여부 확인** — 멀티면 tracker, single 이면 단순 |
| `bojo-internal UseLoadStep` | `saveSyncLogMapping`(2 호출 use-legacy/use-status) → `SyncLogWriter.save`. 타겟 구조 확인 |
| `bojo-internal JejuJewonLoadStep` | `saveSyncLog` 제거. 루프에 `TargetCountTracker tracker = new TargetCountTracker("TM_GD970001","TM_GD120001","TM_GD970130","TM_GD970002","TM_GD970101")`. 각 `mergeGdXXXX` 후 `tracker.inc(...)`, `mergeGd970101`×3 → `tracker.add("TM_GD970101",3)`. `writeCount` 변수 폐기, 최종 `tracker.total()`. → `SyncLogWriter.save(repo,...,tracker,...)`. `StepResult.writeCount(tracker.total())` |
| `bojo-internal JejuObsvdataLoadStep` | `saveSyncLog` 제거. `TargetCountTracker tracker = new TargetCountTracker("PM_GD970201","PM_GD970202")`. 호출부에서 `isMultiDepth ? tracker.add("PM_GD970202",inserted) : tracker.add("PM_GD970201",inserted)`. `writeCount += inserted` 유지(=tracker.total() 과 일치). → tracker 버전 save |
| `bojo-internal JejuFacilityLoadStep` | single-target TM_GD111010. `TargetCountTracker tracker = new TargetCountTracker("TM_GD111010")`; 적재 시 `tracker.inc`. → tracker 버전 save (일관성) |

> ⚠ `feedback_no_regression_organic`: jeju 외 internal step(`SimpleLoadStep`/`UseLoadStep`)이 멀티타겟인지 코드 확인 필수. 멀티면 tracker 적용, single 이면 단순 배열 그대로(현행 OK)도 무방하나 가능하면 tracker 통일.

### 3. 빌드
- `infolink-agent-common clean build -x test` → `infolink-agent-common-1.0.0-SNAPSHOT.jar` 9모듈 `libs/` 복사 (bojo-dmz/bojo-internal/others-dmz/provide-dmz/orchestrator-backend/api-collector/api-provider/proxy-dmz/proxy-internal).
- 코드 직접 변경 모듈 재빌드: `infolink-agent-bojo-dmz`, `infolink-agent-bojo-internal`. (others-dmz 도 `SourceToTargetStep`/`SaeolLinkPlanSndStep` 통해 영향 — common JAR 갱신만이면 재빌드 불요, 단 안전하게 재빌드 검토)
- 가동 중 재기동: bojo-internal(8092), bojo-dmz(8082). (둘 다 현재 가동 중)

### 4. 검증
- 직전 4회차 적재분 정리: Oracle 29004 K1M `EXECUTION_ID='internal-jeju-loader_3d067a41-...'` 행 삭제 — TM_GD970001/TM_GD120001/TM_GD970130/TM_GD970002/TM_GD970101/TM_GD111010/PM_GD970201/PM_GD970202 (건수 확인 후). IF 3테이블(IF_RSV_TB_JEJU_JEWON/IF_RSV_TB_JEJU/IF_RSV_RGETSTGMS01) link_status → PENDING(12/12/12).
- `internal-jeju-loader` 5회차 재실행 → 처리현황 화면:
  - jeju-jewon-load: TM_GD970001 12 / TM_GD120001 12 / TM_GD970130 12 / TM_GD970002 12 / TM_GD970101 36, step write = 84.
  - jeju-obsvdata-load: PM_GD970201 27 / PM_GD970202 9, step write = 36.
  - jeju-facility-load: TM_GD111010 12.
- 각 타겟 행 클릭 → 상세조회 행수 = 처리현황 성공건수 **일치**.
- source_ref 공통화(`jeju-loader-source-refs-propagate.md`) 검증도 같이: 새 source_refs 형식(`["{zone}:{dsId}:{tbId}:{ifId}"]`), fan-out 채번 일관, trace-source(TM_GD111010→IF_RSV_RGETSTGMS01→DMZ if_snd_rgetstgms01).
- 회귀: DMZ Loader(secJewon/secObsvdata 적재 — bojo-dmz 가동, single-target 무영향) 처리현황 정상. 다른 RCV/SND 무영향(`SyncLogWriter` 위임만, 동작 동일). InternalBojoLoad(보조 internal loader) — 코드검토 or 실행.
- trace fix(5/12분, tableId 기반)도 같이 검증.

### 5. 마무리
- dev_log `dev_logs/2026_05/2026-05-13.md`.
- 메모리 후보(승인 후): "SyncLog 저장은 `SyncLogWriter.save(...)` 만 사용 — `SyncLog.builder()` 직접 + `target_tables` JSON 손코딩 금지. 멀티타겟 step 은 `TargetCountTracker` 로 per-table 카운팅(프론트 처리현황 분해 표시용)."
- 커밋: source_ref 공통화 + trace fix(5/12) + SyncLog 공통화 — 별 커밋 3개 권장 (또는 source_ref+SyncLog 묶고 trace 별도).

## 영향 / 회귀 요약
- 변경 파일: common `TargetCountTracker`(신규)/`SyncLogWriter`(신규)/`SourceToTargetStep`(위임)/`SyncLog`(헬퍼 메서드 옵션) / bojo-dmz `LoaderStepHelper`(위임) / bojo-internal 6 step(위임 + jeju 3개 per-target 카운팅). 백엔드·프론트 무변경.
- 동작 변화: (a) 멀티타겟 step `sync_log.target_tables` 가 `["a","b"]` → `[{"name":"a","count":N},...]`, 처리현황 화면이 타겟별 실 건수 표시. (b) `JejuJewonLoadStep` `write_count` 12→84(실 적재행수). (c) 그 외 step·agent 출력 불변(JSON 형식·값 동일 — `SyncLogWriter` 가 같은 결과 생성).
- 운영: 제주 = 첫 검증 사이클(운영 데이터 아님). DMZ/보조/약수터/이용량 = 테스트 사이클 중, 운영 미배포.

## 작업 순서
계획 승인 → `SimpleLoadStep`/`UseLoadStep` 타겟 구조 확인 → common `TargetCountTracker`+`SyncLogWriter` 작성 → 8개 호출처 위임 교체 + jeju 3개 per-target 카운팅 + jewon writeCount 정정 → common clean build → JAR 9모듈 복사 → bojo-dmz/bojo-internal(+others-dmz) 재빌드 → bojo-internal/bojo-dmz 재기동 → 4회차분 데이터 정리 → 5회차 재실행 → 처리현황/상세조회 일치 + source_ref 형식 + trace 검증 → 회귀 확인 → dev_log → 커밋

---
## 진행 (2026-05-13 오후) — ✅ 완료·검증
- common `TableCountTracker`/`SyncLogWriter` 신규. 8개 호출처(`SourceToTargetStep`/`LoaderStepHelper`/jeju 3 step/`SimpleLoadStep`/`UseLoadStep`/`InternalBojoLoadStep`) 전부 위임. 멀티타겟은 tracker 로 per-table 카운팅. `JejuJewonLoadStep` writeCount → 실 적재행수. `UseLoadStep.updateLastReceive` 건수 리턴.
- common build → JAR 9모듈 → bojo-dmz/bojo-internal/others-dmz/proxy-dmz/proxy-internal 재빌드·재기동.
- 검증: `internal-jeju-loader` 재실행 → 처리현황 per-target 건수 = 상세조회 행수 일치(`PM_GD970202` 9 등). 사용자 확인 완료. 미커밋.
