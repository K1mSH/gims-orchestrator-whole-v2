---
name: Agent는 target 쪽에 둔다
description: 파이프라인 Agent 설계 규약 — target DB = 자기 JPA 기본 datasource = 관리 DB (execution/sync_log 저장소)
type: feedback
originSessionId: ea7e6d3a-0f30-4805-9b02-4966c58ab352
---
파이프라인 Agent를 설계할 때 **자기 JPA 기본 datasource(관리 DB)는 항상 target DB 쪽**으로 둔다.

**Why:** 이 규약 덕분에 Agent.targetDatasourceId 하나로 "관리 DB 위치"까지 자동 식별 가능. 만약 깨지면 별도 "관리 DB" 필드/개념이 필요해지고 운영자 UI에 내부 구조가 노출됨. 2026-04-22 provide Agent의 execution/sync_log 위치 이슈 해결 과정에서 확정된 규약.

**How to apply:**
- 새 파이프라인 Agent 만들 때 yml 기본 datasource = target 테이블이 있는 DB로 설정
- source는 외부/중간(Proxy 경유 동적 획득 OK), target은 반드시 자기 DB
- Orchestrator에 Agent 등록 시 targetDatasourceId를 자기 DB의 datasource ID로 지정
- collector 같은 특수 트랙(common 미사용)은 예외 — 이 규약 적용 대상 아님

**검증 현황 (2026-04-22)**
| Agent | 기본 datasource = target | 규약 준수 |
|-------|--------------------------|:---------:|
| bojo (DMZ) | dev PG 29001 (IF 테이블) | ✓ |
| others (DMZ) | dev PG 29001 (IF 테이블) | ✓ |
| bojo-internal (내부망) | Oracle 29004 (GIMS 실테이블) | ✓ |
| provide (내부망) | PG 29006 (api_prv_* 테이블) | ✓ |
