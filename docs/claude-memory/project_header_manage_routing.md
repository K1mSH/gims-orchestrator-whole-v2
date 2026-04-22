---
name: 헤더 기반 관리 DB 라우팅
description: Proxy의 execution/sync_log 조회 시 X-Manage-Datasource-Id 헤더로 라우팅. Agent.targetDatasourceId를 그대로 주입
type: project
originSessionId: ea7e6d3a-0f30-4805-9b02-4966c58ab352
---
Orchestrator → Proxy → Agent 관리 테이블 조회 경로에서 헤더 기반 DataSource 라우팅을 사용한다.

**Why:** provide Agent(PG 29006)처럼 망의 공유 기본 DB가 아닌 곳에 관리 테이블(execution/sync_log)을 두는 Agent가 생기면서, Proxy 기본 DataSource만으로는 조회 불가능한 케이스 발생. 2026-04-22 확정.

**How to apply:**
- **헤더명**: `X-Manage-Datasource-Id`
- **값**: Agent.targetDatasourceId (Agent = target 쪽 규약에 따라 target ID가 곧 관리 DB ID)
- **주입 지점**: `sync-orchestrator/backend/.../service/ExecutionService.java` — Proxy 호출 시 `buildHeaders(agent)` 공통 헬퍼로 주입
- **수신 지점**: `sync-agent-common/.../controller/ExecutionDataController.java` — 13개 엔드포인트 전부 `@RequestHeader(required=false)`
- **라우팅 로직**: `DataSourceProvider.getJdbcTemplate(headerValue)` — null이면 기본 DataSource, 값 있으면 Orchestrator에서 접속정보 조회 + 캐싱 후 신규 JdbcTemplate
- **같은 DB 풀 중복 수용**: Proxy는 경량 조회만이라 실질 부담 없음. 중복 탈락 로직 없음

**구현 상태 (2026-04-22)**: 확정 후 구현 대기. 계획서: `dev_plan/2026_04/22/manage-datasource-header-routing.md`
