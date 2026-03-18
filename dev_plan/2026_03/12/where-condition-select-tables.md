# WHERE 조건 대상 테이블 — Agent YML에 select-tables 추가

## 목적
- 프론트엔드 "실행옵션 > WHERE 조건" 드롭다운에 표시할 테이블 목록을 Agent YML에 명시적 선언
- 기존 source-table / target-table 분류와 무관하게, 실제로 WHERE 조건이 의미 있는 테이블만 지정

## 배경
- source-table / target-table은 파이프라인 데이터 흐름(읽기→쓰기)을 정의
- 그러나 WHERE 조건 적용 대상과 항상 일치하지 않음
  - 예: Loader의 `sec_jewon`은 target-table이지만 재동기화 시 SELECT 조건 대상
- 별도의 `select-tables`를 두어 WHERE 조건 가능 테이블을 명확히 분리

## YML 변경

각 Agent YML에 `select-tables` 필드 추가:

```yaml
# 프론트엔드 "실행옵션 > WHERE 조건"에서 사용자가 선택할 수 있는 테이블 목록.
# 이 테이블들의 컬럼이 WHERE 조건 드롭다운에 표시된다.
# source-table/target-table 구분과 무관하게, 실제 조건 실행의 대상이 되는 테이블을 나열한다.
select-tables:
  - sec_jewon_view
  - sec_obsvdata_view
```

### Agent별 예시

**RCV (dmz-bojo-rcv-daejeon)**
```yaml
select-tables:
  - sec_jewon_view
  - sec_obsvdata_view
```

**Loader (dmz-bojo-loader)**
```yaml
select-tables:
  - if_rsv_sec_jewon
  - if_rsv_sec_obsvdata
  - sec_jewon
```

**SND (dmz-bojo-snd)**
```yaml
select-tables:
  - sec_jewon
  - sec_obsvdata
```

## 구현

### 1. Agent YML 파싱 (sync-agent-bojo)

**AgentDefinition.java** — `selectTables: List<String>` 필드 추가
**AgentConfigLoader.java** — `select-tables` 파싱 로직 추가

### 2. Agent API (sync-agent-bojo)

**기존 Controller에 엔드포인트 추가**

`GET /api/pipeline/select-tables`

```json
["sec_jewon_view", "sec_obsvdata_view"]
```

AgentDefinition에서 selectTables 반환. 테이블명만.

### 3. Orchestrator 프록시 (sync-orchestrator/backend)

**`GET /api/agents/{agentId}/select-tables`**

1. Agent에 `/api/pipeline/select-tables` 요청 → 테이블명 목록
2. 해당 Agent의 sourceDatasourceId/targetDatasourceId로 등록된 DatasourceTable 검색
3. tableName 매칭 → 컬럼(dataType 포함) 정보 합쳐서 반환

### 4. 프론트엔드 (sync-orchestrator/frontend)

**lib/api.ts** — `agentApi.getSelectTables(agentId)` 추가
**page.tsx** — WHERE 드롭다운 데이터 소스 변경:
- 현재: `datasourceApi.getRegisteredTables(sourceDatasourceId)` (전체)
- 변경: `agentApi.getSelectTables(agentId)` (YML 기반)

## 수정 파일

| 위치 | 파일 | 내용 |
|------|------|------|
| Agent | 각 agents/*.yml | select-tables 필드 + 주석 추가 |
| Agent | AgentDefinition.java | selectTables 필드 |
| Agent | AgentConfigLoader.java | select-tables 파싱 |
| Agent | 기존 Controller | /api/pipeline/select-tables 엔드포인트 |
| Orchestrator | AgentService 또는 Controller | 프록시 + DatasourceTable 매칭 |
| Frontend | lib/api.ts | API 함수 추가 |
| Frontend | page.tsx | 호출 대상 변경 |

## 영향 범위
- 신규 필드/엔드포인트 추가만, 기존 로직 변경 없음
- select-tables 미정의 시 빈 배열 반환 (기존 동작 유지)
