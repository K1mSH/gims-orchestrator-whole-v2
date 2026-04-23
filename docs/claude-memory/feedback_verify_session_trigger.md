---
name: 검증 전담 세션 트리거
description: "검증 전담 세션 준비해줘" 지시 시 verify/ 체계 읽고 verifier 모드 진입
type: feedback
---

사용자가 "검증 전담 세션 준비해줘" 또는 유사 표현("검증 세션 시작", "verify 세션 준비" 등)을 말하면 **verify/ 체계의 verifier 세션으로 즉시 진입**. 맥락 설명 없이 트리거 한 문구로 모드 전환.

**Why:** 검증 세션은 반복 호출. 매번 체계/역할/이전 상태를 설명하면 비효율.

**How to apply — 준비 순서:**

### 1. 핵심 문서 읽기 (순서 중요)
- `verify/README.md` — 세션 역할 / 워크플로 / 문서 템플릿
- `verify/_invariants/00-overview.md` — 11 카테고리 invariant 요약
- `verify/deployment/README.md` — 실배포 게이트 문서 지도
- `verify/map/feature-dependency.md` — 파트 의존 맵 + 겹침 매트릭스
- 가장 최근 `verify/runs/YYYY-MM-DD.md` — 이전 검증 상태 스냅샷

### 2. 현재 열린 이슈 / 작업 파악
- `verify/issues/OPEN/` — VER-NNN (미해결 회귀)
- `verify/tasks/OPEN/` — TASK-NNN (파생 작업)
- `verify/issues/DONE/`, `verify/tasks/DONE/` — 완료분(참고)

### 3. 역할 자각 (항상 유지)
- **코드 수정 금지 (read-only)** — 발견 시 즉시 수정 X, VER/TASK 문서로 핸드오프
- 주요 산출물 = **목록 / 문서** (다른 세션이 처리할 재료)
- **커밋 우선순위 낮음** — 축적 후 묶어서, 개발 세션 커밋 방해 금지
- 다른 세션 진행 파일(git status unstaged 등) 건드리지 않음

### 4. 사용자에게 제시할 선택지
- 공통 규약 grep 스캔 (새 invariant 위반 탐지)
- 이전 runs 의 "후속 확인 필요" 항목 처리
- 새 checklist / invariant 세부 파일 작성
- 기존 VER/TASK 상태 체크 (다른 세션이 해결했는지 재검증)

### 5. 작업 산출 흐름
- 스캔 수행 → `verify/runs/YYYY-MM-DD.md` 에 기록 (없으면 생성)
- 위반 발견 → `verify/issues/OPEN/VER-NNN-{kebab-title}.md`
- 파생 작업 → `verify/tasks/OPEN/TASK-NNN-{kebab-title}.md`
- 번호는 기존 최대번호+1 (OPEN + DONE 합산 기준)

**관련 메모리**
- `project_verify_system.md` — verify/ 체계 전체 구조
- `feedback_config_replacement_sync.md` — 개발 세션이 지킬 규약 (verifier 가 감시 대상)
