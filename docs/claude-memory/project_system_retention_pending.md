---
name: project-system-retention-pending
description: Agent 관리 테이블(sync_log) retention 사각지대 — 옵션 A 합의 + 계획서 작성 후 보류. 사용자 결정 4건 대기 중
metadata: 
  node_type: memory
  type: project
  originSessionId: 2c187110-367c-41c1-aa69-6c9cff7aba0c
---

# Agent 시스템 관리 테이블 Retention 확장 — 보류 안건

## 사실 (2026-05-14 논의)

Agent 가 자기 메타 DB 에 쓰는 관리 테이블(대표적으로 `sync_log`)이 현행 retention 사각지대.
- 현행 retention-candidates 화이트리스트는 비즈니스 target/source 만 다룸
- sync_log 는 Agent 의 target 도 source 도 아니라 등재 불가 → 무한 누적
- 사용자가 "DB 관리 메뉴 분리" 가능성부터 탐색 → 난잡함 우려 → 본 의도가 "관리 테이블 사각지대"임이 드러남

## 합의된 방향 — 옵션 A

Agent yml 에 `system-retention-candidates` 신규 섹션 추가 (= 기존 4 layer 검증 / Scheduler / cleanup endpoint 그대로 재사용). DB 관리 화면 신설 안 함, "정의는 흩어두되 view 만 통합" 가능성도 후속 검토.

탈락한 대안:
- 완전 분리(datasource 별 yml / Orchestrator DB 화면 CRUD): 분리 비용 대비 실익 적음, 단일 진실원 깨질 위험
- 현 상태 유지: sync_log 사각지대 해결 안 됨

## 계획서 위치

`dev_plan/2026_05/14/system-retention-extension.md` — 구조 / 코드 변경 6단계 Phase / 리스크 8건 / scope 명시 모두 정리됨.

## 보류 사유 — 사용자 결정 대기 (Phase 0)

다음에 재논의 시 결정 필요:
- **Q1** yml 키 명칭 — 분리형 (`system-retention-candidates`, 추천) vs 통합형 (`type: system`)
- **Q2** shared 테이블 중복 청소 방지 — Scheduler dedup (A, 추천) / advisory lock (B) / 그대로 (C)
- **Q3** Orchestrator DB execution 등 본 작업 범위 포함? — 계획서는 **Agent sync_log 만** 으로 한정 제안
- **Q4** sync_log 보존일 default — **90일** 권장. 운영자 미입력 시 default 적용 vs 명시 입력 강제

**Why:** 사용자가 결정 보류. 이번 세션에서 코드 진입 안 함.

**How to apply:** 사용자가 "retention 관리테이블", "sync_log 정리/청소", "system retention", "관리 테이블 보존", "agent 메타 DB 청소" 같은 키워드로 다시 언급하면 본 메모리 + 계획서로 컨텍스트 복원하고 Q1~Q4 결정부터 묻기. 새 계획 다시 세우지 말 것.

## 관련

- 단일 진실원 정책: `dev_plan/2026_05/08/retention-candidates-safety.md`
- [[feedback_retention_yml_target_only]] — "이중 설정 방지" 원칙. 본 확장은 별도 채널이라 충돌 X
- [[feedback_agent_at_target]] — Agent target = 자기 JPA datasource = sync_log 위치
- [[project_verify_system]] — sync_log 가 검증 데이터로 활용됨 → 보존일 결정 시 trace 추적 가능 기간 영향 (R1)
