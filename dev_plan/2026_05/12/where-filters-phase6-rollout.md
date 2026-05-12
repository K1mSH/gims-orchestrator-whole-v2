# Phase 6 — where-filters 인프라 전 모듈 롤아웃 + normalizeToList 하드닝 (계획)

작성일: 2026-05-12
선행: `yml-declared-where-filters.md` (Phase 1~4 완료 — common 모델 + bojo-internal + orchestrator + frontend)

## 목적

오늘 한 loader 관련 수정(where-filters 인프라 / dmz-bojo-loader.yml comma-string fix)을 **나머지 agent 모듈/로더에도 반영**해서 일관된 상태로 만든 뒤 테스트 진입. (반쯤 롤아웃된 기능으로 테스트 안 하려고)

## 작업 (코드만 — common 은 안 건드림, `WhereFilterDef` 는 이미 있음)

### 6a. where-filters 인프라 → 나머지 agent 모듈

`bojo-internal` 에 한 3-파일 패턴을 `bojo-dmz` / `others-dmz` / `provide-dmz` / `api-collector` 각각에:
1. `AgentDefinition.java` — `import WhereFilterDef;` + `private List<WhereFilterDef> whereFilters = new ArrayList<>();`
2. `AgentConfigLoader.parseAgentDefinition` — `retention-candidates` 파싱 옆에 `where-filters` 파싱 블록 (key/label/table/column/operators/valueType/hint)
3. `PipelineController` — `getRetentionCandidates` 옆에 `@GetMapping("/{agentCode}/where-filters")` → `def.getWhereFilters()`

> 모듈마다 클래스 이름/패키지는 동일 구조 (`com.infolink.agent.{xxx}.config.pipeline.AgentDefinition` 등). 파일 위치만 다름.

### 6b. normalizeToList 하드닝 (각 모듈 AgentConfigLoader)

`normalizeToList(Object value)` 에 — `value` 가 `String` 이고 `,` 포함하면 split + trim 해서 리스트로:
```java
if (value instanceof String s && s.contains(",")) {
    return Arrays.stream(s.split(",")).map(String::trim).filter(x -> !x.isEmpty()).collect(Collectors.toList());
}
```
→ `source-table: a, b` (문자열) 형태도 `[a, b]` 로 정규화됨. 기존 `[...]` 리스트·단일값은 영향 없음 (List 면 그대로, 단일 String 이면 그대로).

### 6c. dmz-bojo-loader.yml 에 where-filters 선언

```yaml
where-filters:
  - key: region
    label: 지역(관측코드)
    table: if_rsv_sec_jewon       # DMZ Loader 는 jewon+obsvdata 둘 다 읽으므로 obsv_code 조건은 양쪽에
    column: obsv_code
    operators: [LIKE, IN]
    valueType: STRING
    hint: "예: DJ-%  /  GN-SAC-G1%  /  코드목록(쉼표)"
  - key: region_obsv
    label: 지역(관측데이터, 관측코드)
    table: if_rsv_sec_obsvdata
    column: obsv_code
    operators: [LIKE, IN]
    valueType: STRING
    hint: "예: DJ-%  /  GN-SAC-G1%"
  - key: period
    label: 기간
    table: if_rsv_sec_obsvdata
    column: obsv_date
    operators: [BETWEEN]
    valueType: DATE
```
> jewon 에는 obsv_date 가 없으니 period 는 obsvdata 전용. "지역"을 양쪽에 적용하려면 region 행을 두 테이블에 각각 (프론트에서 행 2개 추가). — 또는 추후 "전체 테이블" 단축형으로 한 줄 처리. 일단 명시.

### 나머지 로더 where-filters 선언 (이번 범위 밖)

`internal-jeju-loader` / `internal-use-loader` / `internal-yaksoter-loader` / `internal-saeol-loader` / `internal-api-collect-loader` / SND 들 — IF 테이블·의미있는 컬럼이 도메인마다 달라서, 각 테스트 사이클(04-others 등) 들어갈 때 그 도메인 보면서 선언. 지금은 미선언 = 기존 범용 UI 유지.

## 영향 / 회귀

- common JAR 변경 없음 (`WhereFilterDef` 이미 존재). 각 모듈 재빌드만.
- 현재 가동 중: bojo-dmz(8082) 뿐 → bojo-dmz 만 재빌드·재기동. others-dmz / provide-dmz / api-collector 는 미기동이라 코드만 들어가고 자기 테스트 사이클에서 기동될 때 반영.
- where-filters 미선언 모듈/로더 → 기존 동작 0 변화 (빈 배열 응답 → 프론트 기존 select-tables UI).
- `normalizeToList` 하드닝 → `[...]` 리스트·단일값은 그대로, comma-string 만 새로 split. **회귀 없음.** (오히려 dmz-bojo-loader.yml 처럼 잘못 쓴 게 알아서 고쳐짐 — 단 이미 fix 했으니 거기엔 영향 없음)

## 검증

1. 각 모듈 `./gradlew clean build -x test` OK
2. bojo-dmz 재기동 → `curl -H "X-API-Key: ..." /api/pipeline/dmz-bojo-loader/where-filters` → region/region_obsv/period 노출. `/select-tables` 여전히 `["if_rsv_sec_jewon","if_rsv_sec_obsvdata"]`
3. Orchestrator 중계 `/api/agents/17/where-filters` → 동일
4. 프론트 agent 17 상세 → 조건실행 ▾ → 큐레이션 UI (지역/기간) 뜸. 미선언 agent (다른 로더) → 기존 범용 UI 그대로
5. (가능하면) others-dmz 등 빌드된 jar 로 한 번 기동해서 where-filters 엔드포인트 404 안 나는지(빈 배열로 200)만 스모크 — 또는 생략하고 자기 테스트 때

## 작업 순서

bojo-dmz (6a+6b+6c) → others-dmz (6a+6b) → provide-dmz (6a+6b) → api-collector (6a+6b) → 각 빌드 → bojo-dmz 재기동 → 검증 → dev_log/커밋
