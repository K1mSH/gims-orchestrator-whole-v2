# sync_log target_tables 의 per-target count 메타 추가

## 1. 배경

03 bojo Step 5 PASS 게이트 — 사용자 화면 "테이블별 처리 현황" 표 의 multi-target 표시 정확성 부족.

**현재 동작**:
- sync_log obsvdata mapping target_tables = `["PM_GD970201","TM_GD970101","TM_GD980002"]`
- mapping 의 write count = 44691 (단일 값)
- frontend 가 mapping 을 flat 하게 펼쳐서 SOURCE/TARGET 행 표시 시, 각 target 행이 mapping write count 를 그대로 받음
- 결과: TARGET PM/TM_GD970101/TM_GD980002 모두 44691 표시

**실측**:
- PM_GD970201 = 44691 행 (EAV 1:3 INSERT)
- TM_GD970101 = ensureResultId 가 신규 생성한 11개 row (실 INSERT 11)
- TM_GD980002 = 1206 행 UPSERT

→ 사용자 직관 X. "건수도 개별 테이블도 합산으로 뜨는 거 같은데"

## 2. 결정 (사용자 5/8)

**B 먼저 진행 — 카운터 표시 정확화 우선**.

각 target 별 실 적재 카운트를 sync_log 메타에 담아 frontend 가 정확한 카운트로 표시.

## 3. 설계

### 3-1. sync_log.target_tables JSON 형식 확장

**기존**:
```json
["PM_GD970201","TM_GD970101","TM_GD980002"]
```

**변경 후**:
```json
[
  {"name":"PM_GD970201","count":44691},
  {"name":"TM_GD970101","count":11},
  {"name":"TM_GD980002","count":1206}
]
```

→ DB 스키마 변경 X (TEXT/JSON 컬럼 그대로). 형식 표기 변경.

> `ExecutionDataController.parseJsonArray` 가 이미 `{"name":..., "count":...}` 형식 부분 지원 (`if (node.isObject() && node.has("name")) result.add(node.get("name").asText())`) — name 추출은 OK, count 추출용 새 메서드 필요.

### 3-2. backward-compat

기존 형식 `["str"]` 도 그대로 처리 (다른 Loader 1:1 mapping 영향 0). parseJsonArray 는 그대로 유지 + 새 메서드 `parseTargetTableInfos` 추가.

### 3-3. Backend 변경 (3 파일)

**1) `infolink-agent-common/.../entity/TargetTableInfo.java` (신규)**
```java
public record TargetTableInfo(String name, Long count) {
    public static TargetTableInfo of(String name) { return new TargetTableInfo(name, null); }
    public static TargetTableInfo of(String name, long count) { return new TargetTableInfo(name, count); }
}
```

**2) `ExecutionDataController.java`**
- 새 메서드 `parseTargetTableInfos(String json) → List<TargetTableInfo>` 추가
- `/tables` endpoint 에서 mapping 별 target counts 채워 TableStatsDto 반환

**3) `TableStatsDto.java`**
- `Map<String, Long> targetCounts` 필드 추가 (optional, null 허용)

### 3-4. Loader 변경 (1 파일)

**`InternalBojoLoadStep.java`**
- ensureResultId 가 신규 INSERT 한 row 수 카운팅 (rsltInserted 로컬)
- `saveSyncLogMapping` 호출 시 target_tables 를 per-count JSON 으로 박기
- 또는 Loader 가 직접 SyncLog 빌드 (saveSyncLogMapping 의 targetTables 인자가 `List<TargetTableInfo>` 를 받도록 시그니처 추가/변경)

선택안: **새 helper `saveSyncLogMappingWithCounts(... List<TargetTableInfo>)` 추가** — 기존 `saveSyncLogMapping(... List<String>)` 그대로 두어 다른 Loader 영향 0.

### 3-5. Frontend 변경 (2 파일)

**1) `types/index.ts`**
- `TableStats` interface 에 `targetCounts?: Record<string, number>` 추가

**2) `executions/[id]/page.tsx`**
- `flatTableStats` 변환 시 각 target 별 카운트:
```ts
const targetRows = mapping.targetTables.map(t => ({
    tableName: t,
    tableType: 'TARGET',
    totalCount: mapping.targetCounts?.[t] ?? mapping.writeCount,  // fallback
    ...
}));
```

## 4. 영향 점검 (회귀 룰 정합)

- 다른 Agent (others-dmz, provide-dmz, api-collector, api-provider, dmz-bojo) — 1:1 mapping 위주, **변경 영향 0** (saveSyncLogMapping 시그니처 backward compat)
- backend `/tables`/`/summary`/`/source`/`/target` endpoints — parseJsonArray 그대로 유지 + 새 메서드 추가라 영향 0
- frontend 의 다른 페이지 — TableStats 사용처 확인 필요 (`targetCounts` optional 이라 미사용 시 영향 0)

## 5. 후속 절차

1. Backend 변경 (TargetTableInfo + parseTargetTableInfos + TableStatsDto.targetCounts)
2. Loader 변경 (InternalBojoLoadStep)
3. Frontend 변경 (types + page.tsx)
4. common 재빌드 → 9 검증자 모듈 libs/ 복사
5. backend + bojo-internal + proxy-internal 재기동
6. cleanup (PM/Link/IF/sync_log/execution)
7. Step 5 재실행
8. 화면 검증:
   - TARGET PM_GD970201 = 44691 (그대로)
   - TARGET TM_GD970101 = 11 (실 INSERT)
   - TARGET TM_GD980002 = 1206 (실 UPSERT)
   - 총 쓰기 = 44691 (mapping write 그대로, 합산 메타 X)

## 6. 별 사이클

- **A. TM_GD970101 행 클릭 → "데이터 없음"** (ensureResultId execution_id 미박음). 본 fix(B) 통과 후 별 사이클로 fix.
- frontend 의 SOURCE 카운트 표시 룰 (현재 mapping read 그대로) — 본 fix 와 무관, 별 의제.
