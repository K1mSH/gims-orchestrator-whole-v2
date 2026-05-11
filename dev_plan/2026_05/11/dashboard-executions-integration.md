# 대시보드 ↔ /executions 목록 통합

작성일: 2026-05-11

## 1. 배경 / 사용자 결정

- `/executions` 목록 페이지는 사이드바 메뉴 없음. 대시보드 stat 카드 3개("실행 중"/"오늘 실행"/"오늘 실패") 클릭 시 deeplink 로만 진입.
- 대시보드는 이미 stat 카드 클릭 시 내부에서 테이블 전환 (Agent 테이블 / 실행이력 테이블) 하는 구조 존재 (`filteredHistory`, `showHistoryTable`).
- 단 "실행 중"/"오늘 실행"/"오늘 실패" 카드만 `<Link href="/executions?...">` 로 외부 페이지 이동 — 비일관.
- **사용자 결정**: 별도 `/executions` 목록 화면 쓰지 말고, 대시보드에서 카드 클릭 시 대시보드 내 테이블 전환으로 통합.

## 2. 변경 내용

### 2.1 대시보드 (`app/(main)/page.tsx`)
- "실행 중" 카드: `<Link href="/executions?status=RUNNING&startDate=...">` → `<div onClick={() => handleCardClick('running')}>` (전체/온라인/오프라인 카드와 동일 패턴)
- "오늘 실행" 카드: 동일하게 onClick
- "오늘 실패" 카드: 동일하게 onClick
- 카드 클릭 → `showHistoryTable` 분기로 대시보드 내 실행이력 테이블 표시 (기존 `filteredHistory` 로직 그대로 활용)
- 실행이력 테이블 행 클릭 → `/executions/{id}` (실행 상세) 로 이동 — 그대로 유지

### 2.2 `/executions` 목록 페이지 (`app/(main)/executions/page.tsx`)
- 제거하지 않고 **대시보드(`/`)로 redirect** — 외부 북마크/구 링크 대비
- 또는 완전 제거 후 next.js 가 404 처리 — 사용자 결정 필요

### 2.3 유지 (변경 없음)
- `/executions/[id]` (실행 상세 — 파이프라인 시각화 + 처리현황 + 추적) — 추적 검증 핵심 화면. KRDS 작업 대상으로 계속.
- agents/[id] HistoryTab — agent별 실행이력 (별개)

## 3. 한계 / 검토 사항
- 대시보드의 실행이력 테이블은 `recentHistory` 기반 (최근 N개만). `/executions` 페이지의 페이지네이션 + 상세 필터(상태/망/Agent/타입/날짜/검색)는 없어짐.
  - 사용자 의향: 대시보드 간략 버전으로 충분. (운영에서 별도 전체 이력 페이지 안 씀)
  - 만약 필터/페이지네이션이 필요하면 → 대시보드 historyTable 에 추가하거나 별도 작업
- backend `executionHistoryApi.getRecent()` 가 충분한 건수 반환하는지 확인 필요 (기본 최근 20~50개?)

## 4. 작업 순서
1. 본 계획 사용자 확인
2. `/executions` 목록 페이지 처리 방식 결정 (redirect vs 제거)
3. 대시보드 page.tsx — 실행 카드 3개 onClick 으로 변경
4. `/executions/page.tsx` redirect 또는 제거
5. 타입체크 + 사용자 화면 검증
6. KRDS 작업 복귀 (executions/[id] → users → 정리)

## 5. 영향 범위
- 기능 변경: `/executions` 진입점 제거, 대시보드에 실행이력 표시 통합
- 회귀 위험: 낮음 (대시보드의 historyTable 로직은 이미 존재, 카드 onClick 만 변경)
- KRDS 작업의 executions 목록 KRDS화는 이미 완료 — redirect 시 사용 안 됨 (코드는 남김)
