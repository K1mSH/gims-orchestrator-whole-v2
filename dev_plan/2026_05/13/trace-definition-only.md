# 추적 결정 로직 — "이름 정의 기반 전용" 원칙 적용 (휴리스틱 일소)

작성일: 2026-05-13
발견 경위: 04-others 이용량 클린 테스트 — `internal-use-rcv_7fab8314` 의 TARGET `IF_RSV_USE_JEJU_DAY` 행 역추적 시 400 "Source 테이블이 존재하지 않습니다: IF_RSV_USE_JEJU_DAY" (TARGET 이름이 SOURCE 자리에 그대로 박혀 에러). 진단: `traceToSource` 분기 1·2(if_rsv_/if_snd_) 의 baseName 차감 fallback 이 **Internal RCV**(source `if_snd_*` / target `IF_RSV_*` — 양쪽 다 prefix) 에서 깨짐. 4/22(`67a0b3f`) 작업은 분기 3(else) 만 정확매칭으로 강화했고 분기 1·2 는 "기존 기능이므로 유지" 라 보수적으로 둔 것 — 새 패턴(Internal RCV) 등장으로 가정 무너짐.

## 원칙

> **추적 코드는 "이름 정의 기반" 만 한다 — 추론·휴리스틱·규칙성 추측 일체 금지.**
>
> - 진실원:
>   ① `sync_log.target_tables` / `source_tables` (= `SyncLogWriter` 가 박은 실 매핑명)
>   ② `source_refs.tableId` → `datasource_table.id` (= 실 등록 ID)
>   ③ `source_refs` 의 zone/dsId/tableName 같이 직접 박힌 값
> - **금지**: prefix 검사·차감 (`if_rsv_`/`if_snd_`/`_view` 떼기), contains/substring 매칭, "양방향 contains"(source⊃target / target⊃source), baseName 매칭, IF 이름 규칙 가정, 도메인 네이밍 판별(`startsWith("if_")`).
> - 정확매칭 실패 시 = **명확한 에러 메시지** (실패한 입력값 + 시도한 진실원 노출). 원본 유지 후 다른 곳에서 깨지게 두지 않음.
> - 추적 코드에 "이 테이블이 IF 야 / Loader 야 / SND 야" 같은 **도메인 네이밍 판별** 자체가 없어야 함. `isSndRelay`/`isLoaderTarget` 같은 플래그도 추적 결정 로직에선 안 씀.

→ 메모리 룰 추가: `feedback_trace_definition_only` (계획 승인 시 같이).

## 전수 감사 결과 — 휴리스틱 박힌 위치 10곳

추적 결정 로직 직접 박힘 = 8곳 (#8/#9 는 원칙 적용 범위 밖 — 결정 로직 아님).

| # | 파일:줄 | 휴리스틱 | 종류 | 정리 방향 |
|---|---|---|---|---|
| 1 | common `ExecutionDataController:1046` | `sourceTable.startsWith("if_snd_")` → `isSndRelay` 플래그 | prefix 검사 → fallback 분기 힌트 | **제거** (fallback 분기 자체 제거 — 정확매칭 실패 = 에러) |
| 2 | 〃 `:1053` | `if (lowerTable.startsWith("if_rsv_") \|\| startsWith("if_snd_"))` → 분기 1·2 진입 | 도메인 네이밍 분기 | **분기 통합** — 단일 경로 |
| 3 | 〃 `:1062~1063` | `replaceFirst("^if_rsv_", "")` + `replaceFirst("^if_snd_", "")` → baseName | prefix 차감 | **삭제** — sync_log target_tables 정확매칭으로 대체 |
| 4 | 〃 `:1078` | 주석에 "양방향 contains" (구현 없음) | 사상 잔재 | **주석 삭제** |
| 5 | 〃 `:858~876` | Pattern A: `LIKE %:{sourceTable}:{pk}"]` | 이름 기반 LIKE (옛 `I:dsId:tableName:pk` 형식 호환) | **별개 정리**(아래 §외) — 잔존 데이터 정리 후 제거 |
| 6 | 〃 `:878~889` | Pattern B: `LIKE %:{pk}"]` (테이블 무시) | pkValue 만으로 매칭 | **삭제** — 다른 테이블 동일 PK 와 충돌 위험. tableId 정밀(Pattern A0) 우선이라 안전망 명분도 약함 |
| 7 | common `SyncLog:104~105` | `containsSourceTable` = JSON 문자열 contains | JSON substring | **삭제** — `containsTable`(같은 클래스 738번 메서드 — `parseJsonArray` 후 `equalsIgnoreCase`) 로 통일. 호출처 `ExecutionDataController:295, 381` 교체 |
| 8 | common `ExecutionDataController:1660` | `dataType.contains("INT")` 등 — 숫자 PK 판별 | datatype 키워드 검사 | **원칙 적용 X** (추적 결정 로직 아님, 타입 캐스팅 결정) — 두자 |
| 9 | common `SourceRefUtils:66~72` | `buildFallback` — tbId null 일 때 `0` 채움 | source_refs 생성 시점 fallback | **원칙 적용 X** (생성 시점, 추적 결정 아님) — 두자. 다만 trace 시 tbId=0 ref 만나면 진실원 ② 깨지는 건 별개(메타 누락 케이스, ExecutionService 가 메타 제공하면 발생 안함) |
| 10 | orchestrator `ExecutionService:246~247` | `sourceTable.replace("_view", "")` + `tableName2.contains(sourceBase)` → ifTableName 자동 해석 | `_view` 차감 + contains | **삭제** — sync_log target_tables 또는 agent_tables 정확매칭으로 대체 |

## 정리 후 모양 — 단일 정확매칭 경로

### `traceToSource` (역방향: TARGET/IF 행 → 원본 SOURCE)

```java
@GetMapping("/{executionId}/trace-source")
public ResponseEntity<...> traceToSource(executionId, sourceRefs, sourceTable, manageDsId) {
    // 1. sourceRefs 파싱
    List<String> pkValues = parseSourceRefsPks(sourceRefs);
    if (pkValues.isEmpty()) return 400;

    // 2. clickedTable(= 사용자가 화면에서 클릭한 테이블) → 실제 SOURCE 테이블명
    //    진실원 우선순위(전부 정의 기반, 휴리스틱 없음):
    String resolvedSource = resolveTraceSourceTable(mgmtJdbc, executionId, sourceTable, sourceRefs);
    if (resolvedSource == null) {
        return 400 "역추적 매핑 해석 실패: 입력 {sourceTable}, executionId {executionId}";
    }
    sourceTable = resolvedSource;

    // 3. source datasource 에서 sourceTable 조회 (기존 흐름)
    ...
}

private String resolveTraceSourceTable(JdbcTemplate mgmtJdbc, String executionId, String clickedTable, String sourceRefs) {
    if (clickedTable == null || clickedTable.isBlank()) {
        // sync_log 첫 매핑 source 사용 (한 매핑짜리 fallback — 정의에 의존)
        return ExecutionDataReader.findSyncLogsByExecutionId(mgmtJdbc, executionId).stream()
                .map(SyncLog::getSourceTables).filter(Objects::nonNull)
                .flatMap(j -> parseJsonArray(j).stream())
                .findFirst().orElse(null);
    }
    String lower = clickedTable.toLowerCase();

    // ① sync_log target_tables 정확매칭(equalsIgnoreCase) → 그 매핑의 source_tables[0]
    List<SyncLog> allLogs = ExecutionDataReader.findSyncLogsByExecutionId(mgmtJdbc, executionId);
    for (SyncLog s : allLogs) {
        if (parseJsonArray(s.getTargetTables()).stream().anyMatch(t -> t.equalsIgnoreCase(lower))) {
            List<String> srcs = parseJsonArray(s.getSourceTables());
            if (!srcs.isEmpty()) return srcs.get(0);
        }
    }

    // ② source_refs.tableId 정확조회 (mgmtJdbc 가 orchestrator DB 일 때만 동작 — 대부분 null)
    String byId = resolveSourceTableByRefsTableId(mgmtJdbc, sourceRefs);
    if (byId != null) return byId;

    // ③ 옛 `I:dsId:tableName:pk` 형식 source_refs 에서 직접 추출
    String byRefsName = parseSourceRefsTableName(sourceRefs);
    if (byRefsName != null) return byRefsName;

    // 다 실패 — 호출부가 에러 처리
    return null;
}
```

`isSndRelay`/`isLoaderTarget` 플래그 + 그들이 분기시키는 fallback 들(`L1241/L1276` 의 SND Relay·Loader 재조회) — **정확매칭이 성공하면 sourceTable 이 올바른 진짜 source 가 되어 본 흐름이 통하므로 fallback 자체가 불필요**. 정확매칭이 실패한다 = 매핑이 정의 자체에 없다 = 진실원이 부재한 상황이므로 강제 fallback 으로 메우지 말고 명확한 에러로 노출.

> ⚠ 회귀 검토 포인트: `isSndRelay` 분기(L1276)의 SND Relay 재조회가 현재 어떤 케이스에서 실제 발동하고 있는지 확인. 만약 sync_log 가 그 매핑을 정상 기록하고 있다면 정확매칭으로 잡힐 것 — fallback 불필요. 만약 그 SND step 이 sync_log 를 기록 안 하는(또는 잘못 기록) 케이스라면 → sync_log 쪽 / 또는 SND step 의 SyncLogWriter 호출을 고쳐야 함(정의 쪽 보강). 추적 코드에 휴리스틱으로 메꾸지 않음.

### `traceBySourcePk` (정방향: SOURCE 행 → TARGET)

```java
@GetMapping("/{executionId}/trace")
public ResponseEntity<...> traceBySourcePk(...) {
    // Step 1: source_refs 에 새 ref 임베딩된 케이스 (RCV/SND/Internal RCV/Internal Loader)
    for (String candidateTable : targetTables) {
        // 복합 PK: |-토큰 독립매칭 (현행 그대로 — 휴리스틱 아님, 정확매칭)
        if (compositePk) { ... queryByCompositePkTokens(...) ... continue; }

        // 단일 PK: Pattern A0 = tableId 정밀
        if (srcTableId != null) {
            String pat = "%:" + srcTableId + ":" + pkValue + "\"]";
            // ... 매칭 ...
        }

        // Pattern A (옛 `I:dsId:tableName:pk` 호환) — sourceTable 이름은 source_refs 에 박힌 정의값.
        //   ※ 휴리스틱 아니라 옛 형식 호환. 새 형식 표준화 + 잔존 데이터 정리되면 제거 가능 — 별개 정리
        String patA = "%:" + sourceTable + ":" + pkValue + "\"]";
        // ... 매칭 ...

        // Pattern B 제거: `%:{pk}"]` 헐거운 매칭 — 삭제 (tableId/이름 진실원이 다 실패해야 도달 = 그땐 매핑 없음 = 에러가 맞음)
    }

    // Step 2/3 도 정확매칭만 — 현행 흐름 유지(이미 정확매칭 기반).
}
```

### `SyncLog.containsSourceTable` → 삭제, `containsTable(SyncLog, name)` 사용 (같은 파일 ExecutionDataController 738번)

```java
// SyncLog.java — 삭제
// public boolean containsSourceTable(String tableName) { ... 문자열 contains ... }
// public boolean containsTargetTable(String tableName) { ... 문자열 contains ... }

// ExecutionDataController.java — 호출처 교체
// 295: syncLog.containsSourceTable(name) → containsSourceTable(syncLog, name)
// 381: 동일
// (또는 containsTable 메서드를 source/target 둘 다 받는 형태로 작은 시그니처 정리)
```

### orchestrator `ExecutionService.traceBySourcePk` — ifTableName 자동 해석

```java
// 현재 (L240~254): _view 차감 + tableName contains
// 후: sync_log 또는 agent_tables 정확매칭
if ((resolvedIfTableName == null || resolvedIfTableName.isBlank())) {
    // sync_log 의 매핑 중 source_tables 에 sourceTable 정확매칭 → 그 매핑의 target_tables[0]
    // (proxy 로 보내기 전에 미리 풀어주는 거 — proxy 도 같은 매칭 가능하니 사실상 생략해도 됨,
    //  하지만 호환 위해 백엔드에서도 시도)
    resolvedIfTableName = lookupTargetByExactSourceMatch(executionId, sourceTable);
}
```

또는 — 더 단순하게: **백엔드의 자동 해석 자체를 제거**. proxy 의 `ExecutionDataController.traceBySourcePk` 가 sourceTable 만 받고 sync_log 보고 알아서 풀면 됨. 백엔드는 그냥 sourceTable·pkValue·pkColumn 만 전달.

## 파일별 변경 목록

### 1. `infolink-agent-common/src/main/java/com/infolink/agent/common/controller/ExecutionDataController.java`

**`traceToSource` 메서드 (L1022~ )**:
- L1045~1111 의 if/else 분기(분기 1·2 IF prefix / 분기 3 else) 전체 → **단일 `resolveTraceSourceTable` 호출** 로 교체. baseName 차감, prefix 검사, contains 로직 다 제거. `isSndRelay`/`isLoaderTarget`/`originalIfTable` 변수와 그들이 분기시키는 fallback(L1241~ Loader TARGET 재조회, L1276~ SND Relay 재조회) 도 제거.
- L1124~ sourceTable 못 찾을 때 = 명확한 에러 메시지 (입력 + 시도한 매핑 노출).
- 새 헬퍼 `resolveTraceSourceTable(mgmtJdbc, executionId, clickedTable, sourceRefs)` 추가 (위 §정리 후 모양 참고).

**`traceBySourcePk` 메서드 (L781~ )**:
- Pattern B(L878~889) 삭제. Pattern A0(tableId) 그대로, Pattern A(이름 — 옛 형식 호환) 그대로(별개 정리).
- 복합 PK 토큰매칭은 현행 유지 (정확매칭 — 휴리스틱 아님).

**`buildSourceFilter` 안 `containsTable` 호출 (L295, L381 부근)**:
- `SyncLog.containsSourceTable` 직접 호출 있으면 `containsTable(syncLog, name)` 으로 교체. (이미 그쪽이 `parseJsonArray` 후 `equalsIgnoreCase` — 동등성 매칭.)

### 2. `infolink-agent-common/src/main/java/com/infolink/agent/common/entity/SyncLog.java`

- `containsSourceTable(String)` / `containsTargetTable(String)` 메서드 삭제(L103~111). JSON 문자열 contains 패턴 — 위험.
- 모든 호출처에서 `parseJsonArray` 후 `equalsIgnoreCase` 로 전환(또는 `ExecutionDataController.containsTable` 헬퍼 사용).

### 3. `infolink-orchestrator-backend/src/main/java/com/infolink/orchestrator/service/ExecutionService.java`

- `traceBySourcePk` L240~254 의 `_view` 차감 + contains → **삭제**. proxy 의 `ExecutionDataController` 가 sync_log 정확매칭으로 풀게 위임 (proxy 가 이미 그렇게 동작 — 백엔드 자동해석은 중복).
- 또는 백엔드가 풀고 싶으면 sync_log target_tables 매칭으로 교체.

### 4. (별개) Pattern A 옛 형식 호환 정리

- 현재 source_refs 표준화(`zone:dsId:tableId:pk`) 적용 — 새로 생성되는 데이터는 다 새 형식. 잔존 옛 형식(`I:dsId:tableName:pk`) 데이터 있으면 정리. 정리 후 Pattern A 제거 가능.
- 이번 PR 범위 밖. dev_log 메모로 남김.

## 검증

### 단위/회귀 매트릭스

각 케이스에서 양방향(정방향/역방향) 추적 + 매핑 검증:

| 케이스 | 매핑 패턴 (source→target) | 정확매칭 동작 |
|---|---|---|
| 외부 RCV (DMZ) | `npmms01` → `if_rsv_npmms01` | clicked `if_rsv_npmms01` → sync_log target_tables 매칭 → `npmms01` ✓ |
| DMZ Loader | `if_rsv_sec_jewon` → `sec_jewon` | clicked `sec_jewon` (TARGET) → sync_log target_tables 매칭 → `if_rsv_sec_jewon` ✓ |
| DMZ SND | `sec_jewon` → `if_snd_sec_jewon` | clicked `if_snd_sec_jewon` → 매칭 → `sec_jewon` ✓ |
| **Internal RCV** | **`if_snd_use_jeju_day` → `IF_RSV_USE_JEJU_DAY`** | clicked `IF_RSV_USE_JEJU_DAY` → 매칭 → `if_snd_use_jeju_day` ✓ (현재 버그 케이스) |
| Internal Loader (jeju) | `IF_RSV_TB_JEJU_JEWON` → `TM_GD970001` (외 fan-out) | clicked `TM_GD970001` → 매칭 → `IF_RSV_TB_JEJU_JEWON` ✓ |
| Provide | (target⊃source 케이스) | sync_log 가 정의대로 기록돼 있으면 정확매칭 → ✓ |
| 약수터 (SimpleLoad) | 〃 | 〃 |

→ 모든 케이스가 sync_log target_tables 정확매칭으로 해결됨 (정의 그대로). 휴리스틱 필요 없음.

### 실제 시나리오

1. **`internal-use-rcv_7fab8314` (현재 버그)**: TARGET `IF_RSV_USE_JEJU_DAY` 행 클릭 역추적 → `if_snd_use_jeju_day` 표시 (복합 PK 행 — `obsr_de=20260325, obsrvt_id=SC001` 등).
2. 단일 PK 회귀: `IF_RSV_USE_LEGACY_DATA` 행 역추적 → `if_snd_use_legacy_data` 표시.
3. 정방향: `if_snd_use_*` 소스 행 클릭 → `IF_RSV_USE_*` 매칭 (이미 작업했지만 이 정리로 더 깔끔).
4. 이용량 체인 다음 단계 (`UseLoadStep`) 후: `PM_GD111021` 행 역추적 → `IF_RSV_USE_LEGACY_DATA`. 정방향 그 반대.
5. 다른 도메인 회귀(샘플): bojo DMZ RCV 한 건, 새올 SND 한 건, 제주 Internal Loader 한 건 — 위 매트릭스대로 동작 확인.

## 빌드

- `infolink-agent-common clean build` → JAR 9모듈 `libs/` 복사.
- `infolink-orchestrator-backend clean build` (코드 변경).
- `infolink-proxy-dmz` / `infolink-proxy-internal` 재빌드·재기동 (ExecutionDataController 호스트 — Proxy 만 동작).
- 다른 agent 모듈은 코드 무변경, JAR 갱신만 — 재기동은 다음 자연스러운 시점에. (운영중이면 JAR 매칭 위해 재기동 권장.)

## 영향 / 회귀

- **변경**: common `ExecutionDataController` (분기 통합 + Pattern B 삭제 + 단일 헬퍼) / `SyncLog` (contains* 메서드 삭제) + 호출처. orchestrator `ExecutionService.traceBySourcePk` 자동해석 단순화.
- **동작 변화**:
  - Internal RCV TARGET 행 역추적 → 정상 동작 (현재 400 에러).
  - 다른 모든 케이스 → 동작 무변경 (sync_log 가 정의대로 기록돼 있으므로 정확매칭으로 똑같이 해결).
  - 매핑이 sync_log 에 없는 / 잘못 기록된 케이스 → 기존엔 휴리스틱 fallback 으로 우연히 맞기도 했지만, 이제 명확한 에러. 그런 케이스 있으면 그 매핑의 SyncLogWriter 호출이 빠졌거나 잘못된 거 — **정의 쪽 보강**(코드 fix 가 아니라 그 step 의 saveSyncLog 호출 추가/수정).
- **회귀 위험**:
  - SND Relay 분기(`isSndRelay` L1276~) 제거 — 이 분기가 실제 발동하는 케이스가 있는지 확인. 발동 중이라면 그 매핑이 sync_log 에 제대로 기록되고 있는지 확인. 안 되어 있으면 SND step 의 `SyncLogWriter` 호출 점검 (별개 작업).
  - `Loader TARGET 재조회` 분기(`isLoaderTarget` L1241~) 제거 — 마찬가지.
  - Pattern A(이름 기반 LIKE) 잔존 — 옛 형식 source_refs 데이터에 대한 호환. 잔존 데이터 있는지 + 정리 가능한지 별개 확인.
- **운영**: 추적은 조회 전용 — 데이터 안 건드림. 매핑 정의 자체는 변경 없음 (sync_log 기록 그대로).

## 작업 순서

1. **계획 승인** (이 문서) + 메모리 룰 `feedback_trace_definition_only` 추가.
2. **`isSndRelay`/`isLoaderTarget` fallback 발동 케이스 사전 확인** — git 로그·테스트 시나리오 검토. 발동 케이스 = sync_log 가 부족한 케이스인지 확인. 부족하면 그 부족분 별개 fix 계획.
3. **코드 수정**:
   a. common `ExecutionDataController.resolveTraceSourceTable` 헬퍼 신규. `traceToSource` 의 분기 1·2·3 통합. `isSndRelay`/`isLoaderTarget` 변수 + 그들의 fallback 분기 제거. 에러 메시지 명확화.
   b. common `ExecutionDataController.traceBySourcePk` Pattern B 제거.
   c. common `SyncLog` 의 `containsSourceTable`/`containsTargetTable` 삭제. 호출처 `containsTable` 헬퍼로.
   d. orchestrator `ExecutionService.traceBySourcePk` 의 ifTableName 자동해석 단순화 또는 제거.
4. **빌드**: common → JAR 9모듈 복사 → backend / proxy-dmz / proxy-internal 재빌드·재기동.
5. **검증**: 위 매트릭스 + 실제 시나리오 (현 `7fab8314` 케이스부터).
6. **dev_log 갱신** + **커밋** (별 커밋: trace 정밀화).
7. (별개) Pattern A 잔존 데이터 정리는 다음 사이클.

---

## 진행 (작성만, 코드 무변경)

- 전수 감사 완료 — 위 §휴리스틱 박힌 위치 10곳.
- 사용자 원칙 확정·정리 완료.
- 코드 수정 대기 (이 계획 승인 후).

---
## 진행 (2026-05-13 오후) — 일부 적용·강화로 대체
- 분기 1·2·3 통합 → 단일 `resolveTraceSourceTable` 헬퍼 (역방향 추적) 적용 완료. `isSndRelay`/`isLoaderTarget`/`originalIfTable` 변수 + 그들 fallback(L1241~/L1276~) + `traceSourceBySndBusinessKey`/`getUniqueKeyColumns` 메서드 제거. 검증: Internal RCV `IF_RSV_USE_JEJU_DAY` 역추적 정상 동작.
- `SyncLog.containsSourceTable`/`containsTargetTable` 메서드 제거 (JSON substring 휴리스틱). `ExecutionDataController.containsSourceTable`/`containsTargetTable`(parseJsonArray + equalsIgnoreCase) 헬퍼로 통일.
- 추가 발견: 단일 PK 정방향 추적이 깨짐 → 사용자 원칙 강화 ("LIKE 일체 금지"). 정방향 정리는 별도 계획서 `trace-equality-only.md` 로 분리·진행.
