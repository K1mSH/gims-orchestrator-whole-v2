# 추적 — LIKE 일체 폐기, 정확 동등성(=)만 (정의기반 완전 형태)

작성일: 2026-05-13
배경: `trace-definition-only.md` 의 후속·강화. 사용자 추가 원칙 "정의된 걸 LIKE 로 찾으면 안 된다 — 다른 거 잡힐 수 있음"(제주 복합 PK 발견의 뿌리 원인). 추적 결정 로직 + 매칭 둘 다 휴리스틱 0.

## 원칙 강화

> **추적은 정의된 값을 정의된 형태 그대로 만들어서 `=` 로 찾는다.**
>
> - 이름 매칭: `equalsIgnoreCase` (정확 동등).
> - source_refs 매칭: `WHERE source_refs = ?` **순수 동등성**. LIKE/contains/substring 전부 금지.
> - 매칭에 쓸 값은 **backend 가 정의(`SourceRefUtils.build` 와 동일 로직)로 재현**해서 만듦. LIKE 토큰매칭, prefix 차감, baseName 매칭, contains, "어쩌면 매칭될 것" 류 추측 일체 금지.
> - 정확매칭 실패 = 정의에 매핑 없음 = 명확한 에러. 휴리스틱 fallback 으로 메꾸지 않음.
>
> → 메모리 룰 `feedback_trace_definition_only` 강화(LIKE 추가 금지 명시).

## 작동 원리 (정방향 추적)

우리 모든 writer (`SourceToTargetStep` / `LoaderStepHelper` / jeju·use·simple·internal 커스텀 step / `SaeolLinkPlanSndStep`) 가 `SourceRefUtils.buildJson(context, sourceTable, pkValues)` 한 군데로 source_refs 작성 — 형식 동일:
```
["{zone}:{dsId}:{tableId}:{pk}"]   (pk = pkValues |-join, DB PK 제약조건 KEY_SEQ 순)
```
입력 = StepContext 의 `sourceZoneShortCode` / `sourceDatasourceDbId` / `sourceTableIds.get(tableName)` (= datasource_table.id) + 원본 row PK.

→ **backend 가 같은 입력으로 같은 함수 결과 재현 가능**. 재현한 exactRef 로 `WHERE source_refs = ?` 한 번에 정확매칭.

```
사용자 클릭 (source 행, sourceTable + pkColumns + pkValues)
       ↓
backend (orchestrator.ExecutionService.traceBySourcePk):
  1. agent.agentTables 에서 sourceTable 정확매칭(equalsIgnoreCase) SOURCE DatasourceTable srcDt 검색
     → 없으면 에러 "agent 의 SOURCE 테이블 등록 없음: X"
  2. Datasource srcDs = datasource(srcDt.datasourceId)
     - zone = zoneConfigRepository.findShortCodeByZone(srcDs.getZone())   // E/D/IC/IS/U
     - dsId = srcDs.getId()
     - tableId = srcDt.getId()
  3. pk = pkValues 콤마구분 → split → "|" join  (frontend 가 findPkColumns 순서 = DB 제약조건 순서로 보냄)
  4. exactSourceRefs = "[\"" + zone + ":" + dsId + ":" + tableId + ":" + pk + "\"]"
  5. proxy 로 forward (query param: executionId, sourceTable, exactSourceRefs)
       ↓
proxy (ExecutionDataController.traceBySourcePk):
  1. sync_log 에서 source_tables 에 sourceTable 정확매칭(containsSourceTable 헬퍼 = equalsIgnoreCase) 매핑들 검색
     → 그 매핑들의 target_tables 합 = candidates
     → candidates 비었으면 에러 "sync_log 에 sourceTable=X 매핑 없음"
  2. for candidate in candidates:
       WHERE source_refs = ? AND execution_id = ?   (exactSourceRefs)
       매칭되면 그 행들 반환 + break
  3. 다 0 = NOT_SYNCED (메시지: exactSourceRefs, 시도한 candidates 명시)
```

**LIKE/Pattern A0/A/B/`queryByCompositePkTokens`/Step 2 Loader passthrough/Step 3 직접 PK 폴백** — 전부 삭제.

## 정합성 검증 (모든 케이스)

| 케이스 | writer 가 만드는 source_refs | backend 재현 입력 | 일치? |
|---|---|---|---|
| External RCV (`SourceToTargetStep`) | `["E:1:4:26"]` (zone=E, ds=external, tableId, pk=외부 PK) | agent.agentTables(SOURCE).datasource_table → tableId, datasource → zone/dsId, pkValues | ✓ |
| DMZ Loader (`LoaderStepHelper`) | `["D:dsId:ifTableId:ifRowId]` | 〃 (IF 테이블 = agent SOURCE) | ✓ |
| DMZ SND (`SourceToTargetStep`) | `["D:dsId:dmzTableId:pk]` | 〃 | ✓ |
| Others SND (`SaeolLinkPlanSndStep`) | 〃 | 〃 | ✓ |
| Internal RCV (`SourceToTargetStep`) | `["D:dmzDsId:ifSndTableId:ifSndRowId]` | 〃 | ✓ |
| Internal Loader 커스텀 (`Jeju*`/`Use*`/`Simple`/`InternalBojo`) | `["{zone}:{dsId}:{ifTableId}:{ifRowId}"]` (`SourceRefUtils.buildJson` 사용) | 〃 | ✓ |
| Provide | 〃 | 〃 | ✓ |
| API Collector | 〃 | 〃 | ✓ |

→ 모든 writer 가 `buildJson` 하나로 통일됐고, backend 가 동일 입력으로 동일 함수 재현 → exactRef 가 source_refs 와 **문자 단위로 동일** → `=` 매칭 성공.

## 단순화 효과

| 항목 | Before | After |
|---|---|---|
| `traceBySourcePk` Step 1 패턴 | A0(tableId LIKE) + A(이름 LIKE) + B(pk LIKE — 삭제됨) + queryByCompositePkTokens(토큰 LIKE) | `WHERE source_refs = ?` 단일 SQL |
| `traceBySourcePk` Step 2 | Loader passthrough (`source.source_refs` 별도 SELECT) | 삭제 (writer 가 표준 형식 buildJson 이라 Step 1 한 번이면 충분) |
| `traceBySourcePk` Step 3 | 직접 PK 폴백 (`WHERE pkCol = ?`) | 삭제 (정의 미스 = 에러) |
| `queryByCompositePkTokens` 헬퍼 | 단일/복합 PK 토큰 LIKE 4패턴 OR | 삭제 (backend exactRef 재현으로 대체) |
| target 후보 결정 | sync_log 모든 target_tables 합 | sync_log 의 sourceTable-매칭 매핑의 target_tables |
| 백엔드 `_view` + contains 자동해석 | (`67a0b3f` 이전 잔재) | 이미 삭제 |
| `SyncLog.containsSourceTable/Target` 메서드 | JSON 문자열 contains | 이미 삭제 (이름 매칭은 `ExecutionDataController.containsSourceTable(SyncLog,String)` 헬퍼 — parseJsonArray + equalsIgnoreCase) |

→ **추적 결정 로직에서 LIKE / 휴리스틱 0**. SQL 한 줄, 정의 진실원 한 번에.

## 변경 파일

### 1. backend `infolink-orchestrator-backend/src/main/java/com/infolink/orchestrator/service/ExecutionService.java`

`traceBySourcePk` 메서드:
- 입력 그대로(executionId, pkValue, pkColumn, sourceTable, ifTableName, targetTableName).
- agent → srcDt(SOURCE DatasourceTable, sourceTable equalsIgnoreCase 매칭) → srcDs → zone/dsId/tableId 해석.
- exactSourceRefs = `[\"{zone}:{dsId}:{tableId}:{pkValues |-join}\"]` (frontend 의 콤마구분 pkValue 를 `|`-join 으로 변환; frontend 가 DB 제약조건 순으로 보낸다는 전제).
- proxy 로 forward query param: `exactSourceRefs` 추가. 기존 pkValue/pkColumn/sourceTable 은 진단·로깅용으로 같이 보내거나 제거 (선택).
- 해석 실패(srcDt null, srcDs null, zone null 등) → backend 자체에서 400 에러 with 명확한 메시지.

### 2. proxy `infolink-agent-common/src/main/java/com/infolink/agent/common/controller/ExecutionDataController.java`

`traceBySourcePk` 메서드(L781~):
- 파라미터 `exactSourceRefs` 추가(필수). 기존 `pkValue`/`pkColumn` 은 진단용으로만(로직에 안 씀).
- `srcTableId = findTableIdByName(...)` 호출 제거 (backend 가 이미 빌드해서 옴 — proxy 가 datasource_table 못 보는 문제도 함께 해소).
- `compositePk`/`pkVals`/`pkCols` 변수 + 복합 PK 분기 제거.
- candidate 좁힘: sync_log 에서 sourceTable 정확매칭 매핑들의 target_tables.
- Step 1 본체: 단일 SQL `WHERE source_refs = ? AND execution_id = ?` 로 단순화.
- Pattern A0 / Pattern A / Pattern B 코드 블록 삭제.
- Step 2 (Loader passthrough — `WHERE source_refs = sourceRefs` source DB) 삭제.
- Step 3 (직접 PK `WHERE pkColumn = ?`) 삭제.
- `queryByCompositePkTokens` 헬퍼 메서드 삭제.
- `splitCsv` 헬퍼는 다른 데서 안 쓰면 삭제.

### 3. (변경 없음) `infolink-agent-common SyncLog` / `containsSourceTable` 헬퍼들
- 이미 정리됨 (이전 작업). 그대로.

### 4. (변경 없음) frontend
- 현재 frontend 가 `pkColumn=a,b&pkValue=v1,v2` 콤마구분 보냄 — backend 가 split/join 처리. 변경 불요.
- (메모) PK 값에 `,` 들어가면 split 깨짐 — 우리 데이터엔 없음. 향후 안전성 강화 시 `pkValue` 반복 query param 또는 JSON 배열 전환 (별개 작업).

## 회귀 위험 점검

- **모든 writer 가 표준 형식(`zone:dsId:tableId:pk`) 만** ✓ (감사 완료, `30f393e` 커밋에서 `SourceRefUtils.buildJson` 단일 진입점화).
- **frontend 가 보내는 sourceTable** = TableStatsDto.sourceTables = sync_log 그대로 ✓ (자기 자신 매칭).
- **frontend 가 보내는 pkColumns 순서** = `findPkColumns` 의 KEY_SEQ 순(`getSourceData`/`executeBatchSourceQuery` 응답의 `pkColumns`) → backend 가 `|`-join 시 그 순서로 = SourceToTargetStep 작성 시 순서와 동일 ✓.
- **zone shortcode 해석**: backend 의 `zoneConfigRepository.findShortCodeByZone(...)` 사용 (`ExecutionService` 실행 시작 시점에 이미 동일 호출 — L420). 검증된 경로.
- **잔존 옛 `I:dsId:tableName:pk` 형식 source_refs**: backend 가 표준 형식만 빌드 → 옛 형식 매칭 안 됨. 잔존 데이터 있다면 추적 실패. **검증**: 9모듈 + `internal-jeju-rcv` 처리분에서 옛 형식 ref 남아있는지 grep 필요. (`30f393e` 이후 새 데이터는 표준. 그 이전 데이터 잔존 가능.)
- **PK 값에 `|` 포함**: split 사용 안 함(우리는 `|`-join 만 함), 그러나 PK 값 자체에 `|` 가 있으면 ref 가 자기 자신과 같지 않게 됨. 우리 데이터엔 없음.
- **PK 값 콤마 split 안전성**: frontend 콤마구분 → backend 콤마 split. PK 값에 `,` 들어가면 깨짐. 우리 데이터엔 없음, 향후 강화 별개.

## 검증 시나리오

1. **현 깨진 케이스 — Internal RCV 단일 PK 정방향**:
   - `internal-use-rcv_7fab8314`, source `if_snd_use_legacy_data`, pkValue=141707370, pkColumn=sn
   - backend: srcDt(if_snd_use_legacy_data of dmz) → tableId=N, zone="D", dsId=1018
   - exactRef = `[\"D:1018:N:141707370\"]`
   - proxy: candidate=IF_RSV_USE_LEGACY_DATA, `WHERE source_refs = exactRef` → 매칭 ✓

2. **복합 PK 정방향 — use_jeju_day**:
   - source `use_jeju_day`, pkValues=`obsr_de=20260325, obsrvt_id=SC001`
   - backend pk = `20260325|SC001` (frontend 가 DB 제약조건 순으로 보냄)
   - exactRef = `[\"D:1018:107:20260325|SC001\"]`
   - proxy: candidate=if_snd_use_jeju_day, `WHERE source_refs = exactRef` → 매칭 ✓ (`30f393e` 이미 검증된 데이터)

3. **역방향**: 영향 없음 (이미 정의기반 정확매칭으로 정리됨 `trace-definition-only.md`).

4. **회귀 (단일 PK 다른 케이스)**: 외부 RCV / DMZ Loader / DMZ SND / Internal Loader / Provide / 약수터 — 각 한 건 정방향. exactRef 가 작성된 ref 와 문자 단위 동일하면 매칭 ✓.

## 빌드 / 재기동

- `infolink-orchestrator-backend` 재빌드 → 재기동(8080).
- `infolink-agent-common` 재빌드 → JAR 9모듈 복사.
- `infolink-proxy-dmz`/`infolink-proxy-internal` 재빌드 → 재기동(8083/8093). Proxy 만 `ExecutionDataController` 호스트.
- agent 모듈은 코드 무변경(common JAR 만 갱신).

## 작업 순서

1. 계획 승인.
2. backend `ExecutionService.traceBySourcePk` 수정 — agent SOURCE 매칭 + exactRef 빌드 + proxy 호출 시 `exactSourceRefs` 전달.
3. proxy `ExecutionDataController.traceBySourcePk` 단순화 — Pattern/Step/queryByCompositePkTokens 삭제, `WHERE source_refs = ?` 한 줄.
4. 빌드 → JAR 9모듈 복사 → backend/proxies 재빌드·재기동.
5. 검증 (위 §검증 시나리오).
6. dev_log + 메모리 룰 `feedback_trace_definition_only` 본문 강화 (LIKE 일체 금지 추가) + `docs/claude-memory/` 동기화.
7. 커밋 (별 커밋: `feat(trace): 정의기반 동등성 매칭, LIKE 일체 폐기`).

---
## 진행 (2026-05-13 오후) — ✅ 완료·검증
- backend `ExecutionService.traceBySourcePk`: agent.agentTables 에서 sourceTable SOURCE 정확매칭 → datasource → zone shortCode + dsId + tableId → exactSourceRefs(`["zone:dsId:tableId:pk"]`) 빌드 → proxy 로 전달. `_view` 차감/contains 자동해석 잔재 제거.
- proxy `ExecutionDataController.traceBySourcePk` 단순화: Pattern A0/A/B / `queryByCompositePkTokens`(토큰 LIKE) / Step 2 Loader passthrough / Step 3 직접 PK / `splitCsv`/`findTableIdByName` 의존 — 전부 삭제. candidate 좁힘 = sync_log source_tables 정확매칭 → 매핑의 target_tables. 단일 SQL `WHERE source_refs = ? AND execution_id = ?` 한 줄.
- common build → JAR 9모듈 복사 → backend / proxy-dmz / proxy-internal 재빌드·재기동.
- 검증: `internal-use-rcv` 단일 PK 정방향 (`if_snd_use_legacy_data sn=141707370` → `IF_RSV_USE_LEGACY_DATA` 매칭) ✓. 복합 PK 정방향 회귀(`use_jeju_day` 복합 PK) — 같은 빌드 로직이라 동작 보장. 역방향 무영향.
- 메모리 룰 `feedback_trace_definition_only` 본문 강화 — LIKE 일체 금지 추가, `=` 정확 동등성만 명시. docs/claude-memory 동기화.
