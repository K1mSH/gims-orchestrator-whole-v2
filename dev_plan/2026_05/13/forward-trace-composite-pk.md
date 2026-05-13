# 정방향 추적 — 복합 PK 소스 테이블 지원 (토큰 독립매칭, 순서 무관)

작성일: 2026-05-13
발견 경위: 04-others 이용량 클린 테스트 — `dmz-others-snd-use` 실행 후, `use_jeju_day`(제주 일일이용량, PK `obsrvt_id,obsr_de` 복합) 소스 행에서 **정방향 추적이 안 됨**. IF→소스 역추적은 정상.

## 원인

- `use-jejuday-snd`(`source-to-if` 팩토리) 가 `if_snd_use_jeju_day` 에 쓴 source_refs = `["D:1018:107:20260325|SC001"]` — 복합 PK 를 `|` 로 조인한 한 덩어리. (조인 순서 = DB PK 제약조건 KEY_SEQ 순 = `obsr_de|obsrvt_id` — yml `primary-key: obsrvt_id,obsr_de` 와 반대지만 `SourceToTargetStep.detectSourcePrimaryKey` 가 `metaData.getPrimaryKeys()` 를 KEY_SEQ 순으로 읽어서 그럼. yml 순서는 fallback 용.)
- **역추적(IF→소스)**: 타겟행 source_refs 에 `20260325|SC001` 통째로 있음 → `|` split → `WHERE obsr_de=? AND obsrvt_id=?` (쓸 때와 같은 순서) → 정상.
- **정방향(소스→IF)** (`ExecutionDataController.traceBySourcePk` L781~): 프론트(`executions/[id]/page.tsx` L222)가 **`tableData.columns[0]` 한 컬럼만** PK 로 추정해 `pkColumn=obsrvt_id, pkValue=SC001` 전송 → 백엔드가 `source_refs LIKE '%:{tableId}:{pkValue}"]'` 패턴 매칭 → 실제 `...:107:20260325|SC001"]` 라 `:107:SC001"]` 안 맞음. Pattern B(`%:{pkValue}"]`)도 `|SC001"]` 라 (앞이 `|` 라) 안 맞음.
- 단일 PK 소스(use_legacy_data `sn` 등)는 `pkValue` 가 곧 pk 세그먼트 전체라 정상.

## 방침 — 케이스 2: PK 값을 `|`-토큰으로 독립 매칭 (순서 무관)

ref 문자열을 "순서 맞춰 재조립" 하는 방식은 yml/DDL 순서 불일치에 취약 → 채택 안 함. 대신 **"이 tableId 의 pk 세그먼트 안에 이 값들이 각각 토큰으로 들어있다"** 로 매칭. 어느 자리든, 어느 순서든 OK. 단 PK 값을 **전부** 알아야 함(한 개만 알면 다른 행 오매칭 가능).

### 1. 백엔드 — `traceBySourcePk` (`infolink-agent-common ExecutionDataController`)
- 파라미터 `pkColumn` / `pkValue` 를 **콤마 구분 다중값** 허용 (기존 단일값 호출 그대로 동작 — split 후 1개면 현행과 동치).
  ```java
  List<String> pkVals = Arrays.stream(pkValue.split(",")).map(String::trim).filter(s->!s.isEmpty()).toList();
  ```
- Step 1 (source_refs 에 PK 임베딩 — RCV/SND/Internal RCV/Internal Loader 가 새 ref 생성) 매칭을, 각 candidateTable 에 대해:
  - **단일값** (`pkVals.size()==1`): 기존 그대로 (Pattern A0 `%:{tableId}:{v}"]`, A `%:{sourceTable}:{v}"]`, B `%:{v}"]`).
  - **다중값** (복합): `WHERE execution_id=? AND source_refs LIKE '%:{tableId}:%'`  (맞는 테이블 prefilter)  `AND` 각 값 `vi` 마다 `( source_refs LIKE '%:{tableId}:{vi}"]' OR source_refs LIKE '%:{tableId}:{vi}|%' OR source_refs LIKE '%|{vi}"]' OR source_refs LIKE '%|{vi}|%' )`.
    - `tableId` 없을 때(이름 기반 호환)는 `{tableId}` 자리에 `{sourceTable}` 대입한 변형도 같이 OR. (`I:dsId:tableName:pk` 옛 형식 잔존 데이터 대비)
    - LIKE 와일드카드 escape: `vi` 에 `%`/`_` 들어가면 escape (드물지만 방어). PK 값에 `,` 가 들어가면 split 깨짐 — 현실적으로 PK 에 콤마 거의 없음, 우려되면 프론트가 `|` 등 다른 구분자로 보내고 백엔드도 그걸로 split (구현 시 결정).
  - 매칭된 행 = 그 candidateTable 의 결과. (기존 break 흐름 유지 — 첫 매칭 테이블에서 멈춤.)
- Step 2(Loader 복사 패턴 — source 행의 source_refs 그대로 target 에 복사) 은 source 행을 직접 읽으므로 복합 무관 — `findPkColumns` 가 이미 다중 PK 반환하니 `WHERE pk1=? AND pk2=? ...` 로 source 행 조회하게 보강(현재 `srcPkCols.get(0)` 만 씀 → 다중이면 전체 사용). 그 행의 source_refs 로 타겟 매칭하는 뒷부분은 그대로.
- 역추적(`getSourceData`/`buildSourceFilter`) 은 손 안 댐 — 이미 복합 PK("|" 포함 시 배치 모드) 처리됨, 정상 동작 확인됨.

### 2. 백엔드 — 테이블 데이터 응답에 `pkColumns` 노출 (DB metadata 가 단일 진실원)
프론트가 "이 소스 테이블의 PK 가 뭐냐"를 알아야 그 값들을 전부 보낼 수 있음. 그 진실원 = **DB PK 제약조건** (`SourceToTargetStep.detectSourcePrimaryKey` / `buildSourceFilter`의 `findPkColumns` 가 이미 쓰는 그것). sync_log 의 `source_pk_column` 에 의존하지 않음 — 그건 `SourceToTargetStep` 만 채우고 커스텀 Loader step(jeju/use/simple/internal)은 null 이라 불완전. metadata 는 모든 테이블에 일관.
- `getSourceData` (`/{executionId}/source`) 응답에 `pkColumns: ["obsr_de","obsrvt_id"]` 추가 — 이미 있는 `findPkColumns(jdbc, actualTable, dbType)` 호출해서 채움 (KEY_SEQ 순). 빈 결과면 `[]`(또는 첫 컬럼 — 프론트 fallback 용).
- (선택) `getTargetData` 응답에도 동일 추가 — 타겟 행에서 정방향 추적할 일 있으면 대비. 지금 당장은 source 만 필수.
- → 추적 PK 결정 로직이 backward(`buildSourceFilter`)·forward 둘 다 **`findPkColumns` 한 군데**로 통일. 프론트는 결과만 받아 씀.

### 3. 프론트 — `executions/[id]/page.tsx` (`handleRowClick` 의 source 분기, L218~225 부근)
```ts
// 현재: const pkCol = tableData.columns[0]; const pkValue = String(row[pkCol] ?? '');
// 후: backend 가 준 pkColumns 사용 (없거나 빈 배열이면 첫 컬럼 fallback)
const pkCols = (tableData.pkColumns && tableData.pkColumns.length > 0)
    ? tableData.pkColumns : [tableData.columns[0]];
// 표시 row 의 키 대소문자 섞일 수 있음 — 대소문자 무시 매칭
const resolved = pkCols.map(c => Object.keys(row).find(k => k.toLowerCase() === c.toLowerCase()) ?? c);
const pkColParam   = resolved.join(',');
const pkValueParam = resolved.map(c => String(row[c] ?? '')).join(',');
result = await executionApi.traceBySourcePk(executionId, pkValueParam, pkColParam, sourceTableName);
```
- 프론트 타입(`types/index.ts` 의 테이블 데이터 응답 타입)에 `pkColumns?: string[]` 추가. `lib/api.ts` 패스스루.
- 추적 결과 표시(L668~669)는 콤마 그대로 (`obsrvt_id,obsr_de: SC001,20260325`) 무방 — 보기 좋게 zip 해서 `obsrvt_id=SC001, obsr_de=20260325` 으로 해도 됨(선택). backend echo 필드(`result.pkColumn`/`pkValue`)도 콤마 그대로.

### 4. 빌드
- `infolink-agent-common` 변경(`ExecutionDataController`, `TableStatsDto`) → clean build → JAR 9모듈 복사. Proxy 경유로만 호출되니 **proxy-dmz/proxy-internal 재기동 필요** (이들이 ExecutionDataController 를 들고 있나? — ExecutionDataController 는 각 agent 모듈에 있음. trace 는 Proxy → agent 로 포워딩. 그러니 **agent 모듈들(bojo-dmz/bojo-internal/others-dmz/provide-dmz) 재기동**. 정확히 어느 모듈이 trace 응답하는지 = 추적 대상 agent 의 물리 앱. 이용량/제주 = bojo-internal·others-dmz. 일단 전부 재기동이 안전.) ※ Proxy 는 단순 포워딩이라 JAR 갱신만, 재기동 불요일 수 있음 — 확인.
- 프론트 변경 → `npx tsc --noEmit` 로 타입체크 → dev 서버는 HMR.

### 5. 검증
- 이용량 클린 재실행분에서: `use_jeju_day` 의 어떤 행(예: `obsrvt_id=SC001, obsr_de=20260325`) 클릭 → 정방향 추적 → `if_snd_use_jeju_day` 의 `["D:1018:107:20260325|SC001"]` 행 1건 매칭 → 거기서 또 다음 단계(`internal-use-rcv` → `IF_RSV_USE_JEJU_DAY`) 까지.
- 단일 PK 회귀: `use_legacy_data` 의 `sn` 행 클릭 → 정방향 추적 여전히 정상 (split 후 1개 = 현행 동치).
- 역추적 회귀: `if_snd_use_jeju_day` 행 → 소스 역추적 여전히 정상 (안 건드림).
- 다른 agent(bojo RCV/Loader, 제주 등) trace 회귀: source_refs 형식 같으니 영향 없음 — 샘플 1~2건 확인.

### 6. 마무리
- dev_log `dev_logs/2026_05/2026-05-13.md`.
- 메모리 후보(승인 후): "복합 PK 소스 정방향 추적 = PK 값 전부를 토큰 독립매칭 (순서 무관). source_refs 의 복합 pk 조인 순서 = DB 제약조건 KEY_SEQ 순(yml 순서 아님). yml `primary-key` 는 conflict-key/IF상태 용도, 추적/ref 진실원은 DB 제약조건." → 메모리 `feedback_no_guess...` 류와 별개로 trace 규칙 1줄.
- 커밋 — 미커밋 누적분(SyncLog 공통화 / source_ref 표준화 / 5/12 trace tableId fix / 이번 composite trace fix) 정리해서 분리 커밋. (제주 5회차 검증 완료분 포함.)

## 영향 / 회귀 요약
- 변경: common `ExecutionDataController`(traceBySourcePk 다중값+토큰매칭, Step2 다중PK, getSourceData 응답에 pkColumns) / 프론트 `executions/[id]/page.tsx`(소스행 PK 다중전송) + `types/index.ts` + `lib/api.ts`. 백엔드 orchestrator·다른 step·`TableStatsDto`·sync_log 스키마 무변경.
- 동작 변화: 정방향 추적이 다중값 받으면 토큰매칭으로. 단일값은 현행과 동일. 역추적 무변경.
- 운영: 추적은 조회 전용 — 데이터 안 건드림. 단일 PK 소스(대부분)는 무변경.
- 미해결로 남기는 것: yml `primary-key` 순서 vs DB 제약조건 순서 불일치 자체 — 케이스 2 는 순서 안 따지므로 이 fix 엔 무관. 정리할지는 별개 결정(메모만).

## 작업 순서
계획 승인 → 백엔드: traceBySourcePk 다중값/토큰매칭 + Step2 다중PK + getSourceData 응답 pkColumns(`findPkColumns`) → common build → JAR 9모듈 복사 → agent 모듈 재빌드·재기동 → 프론트: api.ts/types pkColumns + page.tsx handleRowClick + `npx tsc --noEmit` → 이용량 use_jeju_day 정방향 추적 검증 + 단일PK/역추적 회귀 → dev_log → 커밋

---
## 진행 (2026-05-13 오후) — ✅ 완료·검증
- 백엔드: `traceBySourcePk` 콤마 다중값 + `queryByCompositePkTokens`(값 토큰매칭 `%:v"]`/`%:v|%`/`%|v"]`/`%|v|%`, tableId prefix 안 씀 — proxy mgmtJdbc 가 datasource_table 없는 dev DB 라 findTableIdByName null 문제 회피). Step2/3 복합 지원. `getSourceData`/`executeBatchSourceQuery` 응답에 `pkColumns`(`findPkColumns`). 프론트: `tableData.pkColumns` 로 PK 값 전부 콤마 전송 + fallback. types/api.ts pkColumns?.
- ⚠ 핵심 교훈: `ExecutionDataController` 는 agent 모듈에서 excludeFilter — **Proxy(proxy-dmz/proxy-internal)에서만 동작**. trace 변경은 proxy 재빌드·재기동해야 함. (agent 재기동 = 무의미.)
- common build → JAR 9모듈 → proxy-dmz/proxy-internal 재빌드·재기동.
- 검증: `use_jeju_day`(복합 PK) 소스 행 정방향 추적 → `if_snd_use_jeju_day` 매칭 OK. 사용자 확인 완료. 미커밋.
- TODO: `findPkColumns` 로그 INFO→debug 원복(디버깅용). 단일 PK 정방향/역추적 회귀 샘플 확인.
