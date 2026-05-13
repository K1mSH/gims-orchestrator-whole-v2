# source_ref 생성 공통화 — Internal Loader 커스텀 step 6개를 SourceRefUtils 양식으로 통일

작성일: 2026-05-13 (제목/범위 개정 — 처음엔 "제주 3개 IF source_refs 전파"로 시작했으나, 전 에이전트 survey 결과 양식 통일 문제로 확장)
발견 경위: 04-others — `internal-jeju-loader` 실행(`...635a22ca`) 후 TM_GD111010 등에 `["I:internal:IF_RSV_RGETSTGMS01:21"]` 형태 source_refs. 사용자 지적 — 양식이 다른 에이전트와 다름, 차이 자체가 말이 안 됨.

## 양식 (이미 존재: `SourceRefUtils.build`)

```
"{zone}:{dsDbId}:{tbId}:{pk}"   — 전부 숫자 식별자
  zone   = zone 약어 (E=EXTERNAL, D=DMZ, IC=INTERNAL_COMMON, IS=INTERNAL_SERVICE)  ← context.getSourceZoneShortCode()
  dsDbId = datasource DB PK (숫자)                                                  ← context.getSourceDatasourceDbId()
  tbId   = datasource_table DB PK (숫자)                                            ← context.getSourceTableIds().get(tableName)
  pk     = 원본 레코드 PK 값
fallback (context 메타 누락 시): "{zone}:{dsId|0}:0:{pk}"
JSON 배열로 감쌈: SourceRefUtils.toJsonSingle(ref)  →  ["zone:dsId:tbId:pk"]
```

- RCV 의 pk = 외부 원본레코드 PK. Loader 의 pk = IF행 서러게이트 `id` (Loader 의 source = IF 테이블, 그 PK = `id`). SND 의 pk = SND source row PK.
- 확인됨: orchestrator `ExecutionService` (line 433~504) 가 실행 params 에 `sourceDatasourceDbId`/`sourceZoneShortCode`/`sourceTableIds` 를 넣어줌 — agent 유형 무관. 따라서 internal loader 의 `context` 에도 숫자 메타 들어옴 → `build()` 가 internal 에서도 정상 동작 (IF 테이블이 datasource_table + agent source 로 등록돼 있어야 tbId 잡힘 — yml `source-table:` 에 선언돼 있으면 ExecutionService 가 자동 등록).

## 현황 — 누가 양식을 쓰고 누가 안 쓰나

| 단계 | 코드 | 양식? |
|---|---|---|
| RCV | `common SourceToTargetStep` → `SourceRefUtils.build(context, sourceTable, pkValue)` | ✓ |
| DMZ Loader | `bojo-dmz LoaderStepHelper` → `SourceRefUtils.build(context, ifTableName, ifJewon.getId())` + `toJsonSingle` (2줄로 분리) | ✓ (헬퍼 거침) |
| SND | `others-dmz SaeolLinkPlanSndStep` 등 → `SourceRefUtils.build/buildComposite` | ✓ |
| **Internal Loader 6개** | `String.format("[\"I:%s:%s:%s\"]", sourceDsId, ifTable, ifId)` 손코딩 | ✗ |

✗ 6개 (모두 `infolink-agent-bojo-internal/.../loader/step/`):
| step | 줄 | 인자 |
|---|---|---|
| `InternalBojoLoadStep` | 215 | `(sourceDsId, ifObsvdataTable, row.get("id"))` |
| `SimpleLoadStep` | 155 | `(sourceDsId, ifTable, getVal(row,"ID"))` |
| `UseLoadStep` | 235, 334 | `(sourceDsId, IF_LEGACY/IF_STATUS, ifId)` |
| `JejuJewonLoadStep` | 151 | `(sourceDsId, ifTable, row.get("ID"))` |
| `JejuObsvdataLoadStep` | 144 | `(sourceDsId, ifTable, row.get("ID"))` |
| `JejuFacilityLoadStep` | 139 | `(sourceDsId, ifTable, row.get("ID"))` |

차이의 정체: `sourceDsId`=`context.getSourceDatasourceId()`(문자열 "internal") 를 dsId 자리에, 테이블 **이름**을 tbId 자리에, zone 을 `"I"` 로 하드코딩. **시맨틱(pk=IF행 서러게이트 id)은 DMZ Loader 와 동일** — 형식만 비표준.

## 수정

### 1. common `SourceRefUtils` — `build` varargs 통합 + `buildJson` 추가 (단일 진입점)
- `build(StepContext, String tableName, Object pk)` + `buildComposite(StepContext, String, Object...)` 분리 → **`build(StepContext, String tableName, Object... pkValues)` 하나로 통합**. 1개면 단일 PK, N개면 `|` 조인. (호출쪽이 "복합키냐"를 알 필요 없음 — 인자 개수만 다름. varargs 라 기존 `build(ctx, tbl, singlePk)` 호출 무수정 컴파일.)
  ```java
  public static String build(StepContext context, String tableName, Object... pkValues) {
      if (context == null || pkValues == null || pkValues.length == 0) return null;
      String pk = (pkValues.length == 1)
          ? String.valueOf(pkValues[0])
          : Arrays.stream(pkValues).map(v -> v == null ? "" : v.toString()).collect(Collectors.joining("|"));
      // ... 기존 zone:dsId:tbId:pk 조립 (fallback 동일)
  }
  ```
- `buildComposite` 제거. 호출처(`common SourceToTargetStep:341`, `others-dmz SaeolLinkPlanSndStep:373`) → `build(...)` 로 교체.
- `buildJson` 추가:
  ```java
  /** build + toJsonSingle 한 번에. 단건 source_refs JSON 의 단일 진입점. */
  public static String buildJson(StepContext context, String tableName, Object... pkValues) {
      return toJsonSingle(build(context, tableName, pkValues));
  }
  ```
- `buildList`(List<?> pks → List<String>) 는 별개 용도라 유지.
→ 앞으로 단건 source_refs JSON 은 무조건 `buildJson` 한 줄. 손코딩 재발 방지.

### 2. bojo-dmz `LoaderStepHelper` (line 67-68, 127-128) — 헬퍼로 정리 (동작 무변경)
```java
// 전
String sourceRef = SourceRefUtils.build(context, ifTableName, ifJewon.getId());
secJewon.setSourceRefs(SourceRefUtils.toJsonSingle(sourceRef));
// 후
secJewon.setSourceRefs(SourceRefUtils.buildJson(context, ifTableName, ifJewon.getId()));
```

### 3. bojo-internal — 6개 step 의 손코딩 → `SourceRefUtils.buildJson`
```java
// 전 (각 step)
Object ifId = row.get("ID");                              // (variable name varies)
String sourceRef = String.format("[\"I:%s:%s:%s\"]", sourceDsId, ifTable, ifId);
// 후
Object ifId = row.get("ID");
String sourceRef = SourceRefUtils.buildJson(context, ifTable, ifId);   // ifTable = 그 step 의 IF 소스 테이블명
```
- `import com.infolink.agent.common.util.SourceRefUtils;` 추가.
- `String sourceDsId = context.getSourceDatasourceId();` 가 source_ref 외 다른 데서 안 쓰이면 제거(미사용 경고 방지) — step 별 확인.
- `JejuJewonLoadStep` 은 1행→5(7)테이블 fan-out: 한 IF행에서 갈라진 7행이 같은 `sourceRef` 공유 — 현재도 그러므로 cardinality 변화 없음, 형식만 표준화.
- `UseLoadStep` 은 step 안에 IF 소스 2개(legacy/status) → 각각 해당 ifTable 로 buildJson 2회 (현재 구조 그대로, 함수 인자만 교체).

### 4. 빌드
- `infolink-agent-common` clean build → `infolink-agent-common-1.0.0-SNAPSHOT.jar` 을 9개 모듈 `libs/` 복사 (bojo-dmz / bojo-internal / others-dmz / provide-dmz / orchestrator-backend / api-collector / api-provider / proxy-dmz / proxy-internal).
- 재빌드 필요(코드 직접 변경): `infolink-agent-bojo-dmz`, `infolink-agent-bojo-internal`. 나머지는 common JAR 만 갱신(코드 무변경).
- 가동 중인 것만 재기동: bojo-internal(8092). bojo-dmz(8082)는 현재 미가동 — JAR 갱신만, 다음 기동 때 반영. (단 trace fix 미커밋 상태 — common 재빌드 시 그 변경도 같이 JAR 에 포함됨, 의도된 것)

### 5. 데이터 — 잘못된 형식 정리 & 재적재
- **제주 (지금 테스트 중)**: Oracle 29004 K1M 에서 `EXECUTION_ID='internal-jeju-loader_635a22ca-b05a-486e-ba27-2032c9dc35f4'` 행 삭제 — TM_GD970001 / TM_GD120001 / TM_GD970130 / TM_GD970002 / TM_GD970101 / TM_GD111010 / PM_GD970201 / PM_GD970202. ⚠ TM_GD970101·PM_GD970201 은 보조 internal loader 도 쓰는 공유 테이블 — execution_id 필터로 제주분만, 삭제 전 건수 확인. IF 3테이블(IF_RSV_TB_JEJU_JEWON / IF_RSV_TB_JEJU / IF_RSV_RGETSTGMS01) link_status → PENDING 복구. → `internal-jeju-loader` 재실행.
- **보조 internal / 약수터 / 이용량 (이전 세션 테스트분)**: 기존 타겟 데이터는 옛 형식(`["I:internal:...:id"]`) — 임의 클린 안 함(`feedback_test_no_unrequested_clean`). dev_log 에 "형식 변경됨, 다음 테스트 사이클에서 옛 형식 데이터 정리 + trace 재검증" 명시. (사용자가 지금 정리 원하면 별도 진행)

### 6. 검증
- 제주 재실행 후: 각 타겟의 `SOURCE_REFS` = `["{zone}:{dsId숫자}:{tbId숫자}:{ifId}"]` 형식 (zone = IF_RSV 테이블 datasource 의 zone 약어 — 내부 Oracle 이면 IC/IS). fan-out 그룹 채번 일관성 유지.
- trace-source: TM_GD111010 행 → `sourceTable=IF_RSV_RGETSTGMS01` FOUND (어제 tableId 기반 fix 와 맞물려 — 새 형식이 숫자 tbId 라 정확 매칭). 거기서 또 Proxy 경유 DMZ `if_snd_rgetstgms01` 까지.
- 회귀(코드 변경 영향 agent 전부): DMZ Loader(secJewon/secObsvdata 적재 — 동작 무변경 확인), 보조 internal loader(미가동이라 코드 검토만), 다른 RCV/SND(common 변경 무영향 — `build` 시그니처 불변, `buildJson` 신규).

### 7. 마무리
- dev_log `dev_logs/2026_05/2026-05-13.md`.
- 메모리 후보: "단건 source_refs = `SourceRefUtils.buildJson(context, table, pk)` 만 사용 — 손코딩 금지" (`feedback_*` 룰). 사용자 승인 후.
- 커밋 (trace fix 미커밋분도 같이 정리할지 결정 — 별 커밋 권장).

## 영향 / 회귀 요약
- 변경 파일: common `SourceRefUtils.java`(+메서드) / bojo-dmz `LoaderStepHelper.java`(정리) / bojo-internal 6개 step. 그 외 모듈은 common JAR 갱신만(코드 무변경, `build` API 불변).
- 동작 변화: Internal Loader 6종이 내보내는 source_refs 형식이 `["I:internal:테이블명:id"]` → `["{zone}:{dsId}:{tbId}:{id}"]` 로. 추적 로직(어제 tableId 기반)과 더 잘 맞음. 기존 옛 형식 데이터는 trace 시 fallback 경로(이름 기반)로 여전히 일부 처리 가능하나, 새 데이터는 정확 경로.
- 운영 영향: 제주는 첫 검증 사이클(운영 데이터 아님). 보조/약수터/이용량은 이미 테스트 사이클 중 — 운영 미배포.

## 작업 순서
계획 승인 → ① common `buildJson` 추가 → ② LoaderStepHelper 정리 → ③ bojo-internal 6 step 교체 + import → common clean build → JAR 9모듈 복사 → bojo-dmz/bojo-internal 재빌드 → bojo-internal kill+재기동(JAR) → 제주 데이터 정리(삭제+IF PENDING, 건수 확인) → 재실행 → source_refs 형식/fan-out/trace 검증 → 회귀 확인 → dev_log → 커밋

---

## 진행 상황 (2026-05-13, PC 재부팅 전 중단)

- ✅ `SourceRefUtils.build` varargs 통합 + `buildComposite` 제거 + `buildJson` 추가 (`buildFallback` 시그니처 정리)
- ✅ `SourceToTargetStep` / `SaeolLinkPlanSndStep` / `LoaderStepHelper` → `buildJson` 사용
- ✅ bojo-internal 6개 step (`InternalBojoLoadStep`/`SimpleLoadStep`/`UseLoadStep`/`JejuJewonLoadStep`/`JejuObsvdataLoadStep`/`JejuFacilityLoadStep`) → `buildJson(context, ifTable, ifId)` + import. `UseLoadStep` helper 메서드 인자 `sourceDsId`→`StepContext context`.
- ✅ common clean build → JAR(229440 bytes) 9모듈 복사 → bojo-internal/bojo-dmz clean build (둘 다 SUCCESS) → bojo-internal jar 재기동(8092 health 200)
- ✅ 제주 `...635a22ca` 회차 데이터 정리: 8테이블 execution_id 필터 삭제 (TM_GD970001 12/TM_GD120001 12/TM_GD970130 12/TM_GD970002 12/TM_GD970101 36/PM_GD970201 27/PM_GD970202 9; TM_GD111010 0) + IF 3테이블 PENDING(12/12/12). 공유테이블 잔존 = TM_GD970101 3618/PM_GD970201 74628/TM_GD970001 1206 (보존 확인)
- ⏳ **다음 세션**: `internal-jeju-loader` 재실행 → 새 형식/fan-out/trace 검증 → DMZ Loader 회귀(bojo-dmz 기동) → trace fix(5/12분)도 같이 검증 → 커밋
- 코드 전부 미커밋. 빌드 산출물(jar)은 이미 새 코드 — 재부팅 후 재빌드 불요.
