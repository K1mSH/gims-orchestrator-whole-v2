---
name: 운영자 UI에 내부구조 개념 노출 금지
description: Agent/datasource 등록 같은 운영자 화면에 common 구조, 관리 테이블 위치 등 내부 구현 세부를 노출하지 않는다
type: feedback
originSessionId: ea7e6d3a-0f30-4805-9b02-4966c58ab352
---
운영자가 쓰는 등록/관리 UI에 **common 파이프라인 내부 구조**(예: execution/sync_log 저장 위치, 관리 테이블 개념 등)를 필드로 노출하지 않는다.

**Why:** Agent를 등록하는 운영자는 우리 개발 내부구조(common/Proxy/관리 테이블 위치)를 모른다. "관리 DB 위치"처럼 내부 개념을 UI에 내보이면 운영자가 혼란스럽고, 무엇을 선택할지 판단 불가. 2026-04-22 provide Agent 관리 DB 라우팅 설계 시 사용자 지적으로 확정.

**How to apply:**
- 새 필드를 Agent/datasource 등록 화면에 추가하기 전에 "이거 운영 관점에서 의미 있나?" 먼저 검증
- 내부 구조상 필요한 정보는 **시스템 자동 파악** 또는 **기존 등록 정보 재활용**으로 해결
- 운영자 기준: source/target, URL, zone처럼 **업무적으로 이해 가능한 개념**만 노출
- 개발자 기준 설정(Agent 개발자가 yml에 쓰는 것)과 운영자 기준 등록(Orchestrator UI)은 분리된 영역
