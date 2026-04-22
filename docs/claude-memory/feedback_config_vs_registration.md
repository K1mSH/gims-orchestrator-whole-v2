---
name: 코드/yml 설정이 DB 등록에 종속되지 않게
description: Orchestrator DB 등록(datasource ID 등)이 단일 진실원(SSOT). yml에 ID 하드코딩해서 등록 변경 시 전파 문제 만들지 않는다
type: feedback
originSessionId: ea7e6d3a-0f30-4805-9b02-4966c58ab352
---
yml 등 코드 레벨 설정값이 **Orchestrator DB 등록 스키마 변경에 연쇄 수정을 요구**하게 설계하지 않는다.

**Why:** datasource ID 같은 값은 Orchestrator DB의 auto-generated PK. 운영자가 DB 등록을 재등록/삭제하면 ID가 바뀔 수 있고, 그럴 때 yml을 수동 수정해서 재배포해야 하는 구조는 취약함. 개발 설정이 운영 등록보다 후순위여야 함. 2026-04-22 확정.

**How to apply:**
- Orchestrator DB에서 관리하는 엔티티의 PK/ID를 yml/코드에 박지 말 것
- 연결이 필요하면 **런타임 자동 매칭**(URL 기반 조회, 기동 시 질의 등) 또는 **기존 등록 정보 재활용**(예: Agent.targetDatasourceId 재사용) 방식 고려
- 단일 진실원(SSOT)은 Orchestrator DB 등록 → 그 변경이 어디로도 전파될 필요 없어야 함
- 역방향으로 생각: "이 설정이 하드코딩되면 DB 재등록 시 뭘 수정해야 하지?" — 답이 "yml 수정 후 재배포"면 설계 바꿀 것
