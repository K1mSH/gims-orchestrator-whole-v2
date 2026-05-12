# yml 선언형 WHERE 필터 (계획)

작성일: 2026-05-12
관련: 같은 폴더 `fix-rcv-conditions-step-skip.md` (선행 버그 fix, 5/12 완료)

> **진행 상황 (2026-05-12): Phase 1~4 구현 완료.**
> - Phase 1 common: `model/WhereFilterDef.java` (신규, `ALL_COLUMNS="*"` 인식자 포함) — JAR 9개 복사
> - Phase 2 bojo-internal: `AgentDefinition.whereFilters` + `AgentConfigLoader` 파싱 + `PipelineController GET /{code}/where-filters` + `internal-bojo-loader.yml`(region: OBSV_CODE LIKE/IN, period: OBSV_DATE BETWEEN)
> - Phase 3 orchestrator-backend: `AgentService.getWhereFilters` + `AgentController GET /{id}/where-filters` — 중계 확인
> - Phase 4 frontend: `WhereFilterDef` 타입 + `agentApi.getWhereFilters` + `agents/[id]/page.tsx` 큐레이션 UI — tsc clean
> - **미진행**: Phase 5 (백엔드 화이트리스트 가드), Phase 6 (dmz-bojo-loader 등 나머지 모듈 인프라+yml). 인식자 토큰 = `"*"` 채택.

## 배경 / 문제

수동 실행 WHERE(conditions) 기능이 현재는 "Agent의 등록 소스 테이블 + 그 테이블의 전체 컬럼"을 범용 드롭다운으로 노출한다.

- **단순 카피 단계(RCV 등)**: 범용으로 OK — "선택적 카피"라 obsv_code/obsv_date 등 아무 컬럼이나 의미 있음.
- **로직 있는 단계(Loader)**: 위험. 로더는 내부 로직에 따라 *어느 테이블의 어느 컬럼에 조건을 거는 게 의미 있는지*가 정해져 있는데, 운영자는 그 내부 로직을 모름.
  - 예: `internal-bojo-loader` 는 `IF_RSV_SEC_OBSVDATA` 만 읽고(`tm_gd970001` 은 brnch_id 룩업/READ ONLY), `IF_RSV_SEC_JEWON` 은 소스 등록은 돼 있지만 코드상 안 읽음 → 거기 조건 달면 조용히 무시됨.
  - 레거시 `executeSending()` 의 "지역 단위 실행" = `getJewon` SQL 에 `obsrvt_id LIKE 'GN-SAC-G1%'` 끼우기 = 우리는 `IF_RSV_SEC_OBSVDATA.obsv_code LIKE 'GN-SAC-G1%'` 한 줄. 운영자가 "어느 테이블/컬럼"인지 알 필요 없이 "지역 = GN-SAC-G1%" 만 입력하게 해야 함.

## 기존 자산 (재확인 — 5/12)

이미 비슷한 게 있다. 그대로 패턴 차용:
- **`select-tables`** (yml): "WHERE 조건 드롭다운에 노출할 테이블 목록". 없으면 steps 의 source-table 자동 수집. `AgentDefinition.selectTables` → agent `GET /api/pipeline/{code}/select-tables` → Orchestrator `AgentService` 중계 → 프론트 `GET /agents/{id}/select-tables` → conditions UI 테이블 드롭다운.
- **`retention-candidates`** (yml): `AgentDefinition.retentionCandidates` → agent endpoint → Orchestrator → 프론트. `RetentionCandidatesProvider` 빈 (DataRetentionController 가 검증에도 사용).
- **`StepDefinition`** + `StepDefinitionController` (`/api/pipeline/step-definitions`): step 메타.

→ `where-filters` 는 **`select-tables` 의 확장**: "테이블 목록" → "테이블 + 컬럼 + 라벨 + 연산자" 큐레이션. `select-tables` / 자동수집은 폴백으로 유지 (= `where-filters` 미선언 시).

## 설계

### Agent yml 에 `where-filters` 선언

`config/agents/*.yml` 에 `steps` / `select-tables` / `retention-candidates` / `table-mappings` 와 나란히 추가. "선정의 요소는 yml" 원칙(메모리 `feedback_retention_yml_target_only` 와 동일 정신).

```yaml
agent-code: internal-bojo-loader
type: LOADER
steps:
  - id: internal-bojo-load
    factory-key: internal-bojo-load
    source-table: IF_RSV_SEC_OBSVDATA
    target-table: [PM_GD970201, TM_GD970101, TM_GD980002]

# 수동 실행 시 노출/허용할 WHERE 필터. 이 목록만 허용 (백엔드 가드).
where-filters:
  # ── 큐레이션 항목: 특정 컬럼, 라벨·연산자·힌트 고정 (로직 있는 단계용) ──
  - key: region
    label: 지역(관측코드)
    table: IF_RSV_SEC_OBSVDATA
    column: OBSV_CODE
    operators: [LIKE, IN]
    valueType: STRING
    hint: "예: GN-SAC-G1%  /  GN-%  /  코드목록(쉼표 구분)"
  - key: period
    label: 기간
    table: IF_RSV_SEC_OBSVDATA
    column: OBSV_DATE
    operators: [BETWEEN]
    valueType: DATE

  # ── 범용 항목: 인식자 column: "*" → 이 테이블 전체 컬럼 허용 (단순 카피 단계용) ──
  # - table: IF_RSV_FOO
  #   column: "*"

retention-candidates:
  - table: PM_GD970201
    dateColumn: OBSRVN_DT
```

### 인식자(와일드카드) 규칙

- `column: "*"` (YAML 에서 `*` 는 alias 기호라 따옴표 필수) → "이 테이블 전체 컬럼 허용 = 범용 모드". 프론트는 컬럼 메타에서 드롭다운 생성, 연산자 전체 허용, valueType 은 컬럼 타입에서 추론.
- `column: <컬럼명>` → 그 `(table, column)` 조합만. `label`/`operators`/`hint`/`valueType` 적용.
- 한 Agent 가 섞어 쓸 수 있음 (테이블별로 `*` 또는 큐레이션).
- `table: "*"` 도 허용(선택) = "아무 소스 테이블이나" 단축형.
- `where-filters` 키 자체가 **없으면** → 현재 동작 유지 (등록 소스 테이블 전체, 모든 컬럼 — 하위호환). 있으면 → 적힌 것만 허용.

### 동작 흐름

1. Agent 기동 시 `where-filters` 파싱 → Agent capability(=Orchestrator 가 조회하는 step-definitions 응답)에 포함
2. Orchestrator 가 프론트에 중계 (`GET /api/agents/{id}` 또는 step-definitions 응답에 `whereFilters` 필드 추가)
3. 프론트 conditions UI 분기:
   - `whereFilters` 있고 큐레이션 항목 → `[필터 선택(지역/기간/…)] [연산자(허용된 것만)] [값(+hint placeholder)]`. 내부적으로 선언된 table/column 박아 ExecutionCondition 생성. `valueType=DATE` 면 date picker.
   - `column: "*"` 항목 → 기존 범용 `[테이블][컬럼][연산자][값]` (그 테이블 한정)
   - `whereFilters` 없음 → 기존 범용 UI 그대로
4. 백엔드 가드(`ExecutionService.triggerExecution` 또는 Agent 측 PipelineRunner): Agent 가 `where-filters` 선언했는데 그 화이트리스트(`*` 항목 포함) 밖의 `(table, column)` 조건이 오면 400/거부. ("정의된 것만" 강제)
5. (선택) Agent 기동 시 검증: `where-filters[].table` 이 그 Agent 의 source/if 테이블 중 하나인지, `column` 이 실제 존재하는지 (틀리면 기동 경고/실패)

### 필터 → 조건 태깅

`where-filters` 는 "잘 태깅된 ExecutionCondition 을 만들어주는 메뉴"일 뿐. 멀티스텝 RCV 면 필터마다 `table` 명시 → 해당 테이블 읽는 step 이 기존 tableName-매칭 로직(`SourceToTargetStep.fetchSimpleCopy` / `ConditionBuilder.buildIfTableQuery` / `DmzBojoLoadStep.buildMergedConditions`)으로 집어감. 즉 실행 경로는 안 바뀌고, 입력단만 큐레이션.

## 영향 범위 (= `select-tables` 와 동일 패턴)

| 영역 | 변경 | 비고 |
|------|------|------|
| **common** | `WhereFilterDef` 모델 (`RetentionCandidate` 처럼 `com.infolink.agent.common.model`) | common JAR 수정 → 9개 모듈 복사 규칙 |
| **각 Agent 모듈** (bojo-dmz / bojo-internal / others-dmz / provide-dmz / api-collector) | `AgentDefinition` 에 `whereFilters` 필드 + `AgentConfigLoader.parseAgentDefinition` 에 `where-filters` 파싱 + `PipelineController` 에 `GET /api/pipeline/{code}/where-filters` 엔드포인트 (전부 `select-tables` 옆에 한 줄씩) | ~5 모듈 × 3 파일 |
| **orchestrator-backend** | `AgentService` 에 `where-filters` 조회 중계 (`getSelectTables` 옆) + `AgentController` 에 `GET /agents/{id}/where-filters` + (선택) `ExecutionService.triggerExecution` 에 화이트리스트 가드 | |
| **frontend** | `api.ts` 에 `getWhereFilters(id)` + `agents/[id]/page.tsx` conditions UI 분기 (whereFilters 있으면 큐레이션 UI, `column:"*"` 항목은 범용, 없으면 기존 `select-tables` 기반 UI) + `types/index.ts` `WhereFilterDef` | |
| ~~커스텀 스텝~~ | **수정 없음** — 이미 ExecutionCondition 처리 중 (5/12 fix 로 견고). where-filters 는 입력/UI 큐레이션일 뿐 |

## 단계별 진행 (제안)

1. **common**: `WhereFilterDef` 모델. 빌드 + JAR 9개 복사.
2. **bojo-internal 만** 먼저: `AgentDefinition.whereFilters` + 파싱 + `PipelineController` 엔드포인트 + `internal-bojo-loader.yml` 에 `where-filters`(region/period) 작성. 빌드·기동 → `curl /api/pipeline/internal-bojo-loader/where-filters` 로 노출 확인.
3. **orchestrator-backend**: 중계 엔드포인트. 빌드·기동 → `curl /api/agents/20/where-filters` 확인.
4. **frontend**: conditions UI 분기. agent 20 화면에서 "지역/기간" 큐레이션 UI 뜨는지 + 실행 → obsvdata 그 지역만 적재 + 추적현황 확인.
5. **백엔드 가드**: 화이트리스트 밖 조건 거부.
6. **나머지 Agent 모듈**: dmz-bojo-loader 등에 `AgentDefinition`/파싱/엔드포인트 + yml `where-filters` 점진. (미선언 모듈은 기존 동작 유지라 급하지 않음)

## 회귀 고려

- `where-filters` 미선언 Agent → 동작 0 변화
- 선언한 Agent → 화이트리스트 밖 조건 거부 (의도된 제약). 기존에 그런 조건으로 운용하던 흐름 없는지 확인
- 5/12 RCV 버그 fix(조건 N/A step skip) 와 독립 — 그 위에 얹힘

## 작업 순서(제안)

1. `WhereFilterDef` 모델 + yml 파싱 (common)
2. step-definitions/capability 응답에 `whereFilters` 노출 + Orchestrator 중계
3. 보조 `internal-bojo-loader.yml` / `dmz-bojo-loader.yml` / RCV 들에 `where-filters` 작성
4. 프론트 conditions UI 분기
5. 백엔드 가드
6. 테스트: 보조 Internal Loader 지역 실행(`region=GN-SAC-G1%`) → obsvdata 그 지역만 적재 + 추적현황 일치, 화이트리스트 밖 조건 거부 확인, 미선언 RCV 범용 유지 확인

## 미결 / 결정 필요

- `where-filters` 인식자 토큰: `"*"` 채택 (대안 `ANY`/`ALL`) — 확정 시 문서 갱신
- step-definitions 응답 DTO 위치 (common vs agent) — 구현 착수 시 확인
- "지역 단위 실행"을 제원등록까지 묶을지 — **묶지 않음으로 결론** (보조 제원 = GIMS 마스터, READ ONLY. Internal Loader 는 obsvdata 만. 레거시의 deleteAllJewon+insertJewon 은 그쪽이 제원도 관리했기 때문이고 우리 구조와 다름)
