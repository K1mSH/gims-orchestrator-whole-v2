---
name: 수정은 기존 로직/기능에 영향 없어야 (유기적 개발 대전제)
description: 모든 코드 수정은 기존 기능/로직에 영향 없어야 함. 이 프로젝트는 유기적 개발이라 작은 수정도 넓은 영향 가능
type: feedback
originSessionId: ea7e6d3a-0f30-4805-9b02-4966c58ab352
---
모든 코드 수정은 **기존 로직과 기능에 영향이 없어야 한다**. 이 프로젝트는 여러 Agent/Proxy/Orchestrator가 common을 공유하는 유기적 구조라 **한 케이스만 보고 수정하면 다른 케이스를 깰 수 있다**.

**Why:** 2026-04-22 provide Agent 테스트 중 trace-source 로직을 provide 케이스만 보고 반복 수정 → 사용자가 제동. "개발 대상이 유기적 개발이라서 더욱 신경 써야 한다"는 원칙 재천명. common의 컨트롤러 하나 수정이 bojo/bojo-int/others/collector/provide 전부에 영향.

**How to apply:**
- 변경 전: **해당 로직을 쓰는 모든 Agent 케이스를 먼저 열거** (RCV/Loader/SND × DMZ/Internal × 각 Agent)
- 각 케이스에서 "기존 동작이 어떻게 되는지" + "새 케이스에서 어떻게 돼야 하는지" 양쪽 시나리오 확인
- "무해한 방어 복구" 같은 판단도 땜질 가능성 있음 — 전체 케이스 검토 거쳐야 진짜 무해 확정
- 계획 문서에 "기존 Agent에 미치는 영향" 섹션 필수 (DMZ/Internal 각 Agent별 시나리오)
- 로직 변경 시 최소 원칙: **기존 케이스에서 행동 변화 없음** → 신규 케이스만 추가 분기
- 반복 수정 금지: 한 엔드포인트/로직을 같은 세션에서 여러 번 손보고 있으면 설계 재점검 신호
- 관련 원칙: `feedback_no_scope_creep.md`, `feedback_memory_pattern_first.md`, `feedback_strategy_check_before_plan.md`
