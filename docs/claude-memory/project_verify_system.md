---
name: verify/ 디렉토리 — 검증 세션 체계
description: 검증 전담 세션과 개발 세션 분리 운영. 구조/역할/핸드오프 규칙
type: project
---

verify/ 는 개발 세션(forward/parallel)과 별도의 검증 전담 세션이 운영하는 문서 체계.
기존 기능 확정성(회귀 방지) + 실배포 게이트 누적 관리가 목적.

## 구조
- `_invariants/` — 시스템이 상시 지켜야 할 불변조건 (11 카테고리)
- `deployment/` — 실배포 게이트 체크리스트 (8 파일)
- `checklists/` — Agent별 기능 회귀 체크 + `_invariants/` 횡단 규약 분리
- `issues/OPEN|DONE/` — VER-NNN 이슈 (검증에서 발견한 회귀/버그)
- `tasks/OPEN|DONE/` — TASK-NNN 파생 작업 지시
- `runs/` — 일별 검증 실행 기록
- `map/feature-dependency.md` — 파트 의존 맵 (병렬 가능 판단)

## 세션 역할
- 검증 세션: 코드 수정 금지(read-only), 회귀/규약 감시, 이슈/작업 문서 생성
- forward: 메인 개발
- parallel: 검증 세션이 만든 tasks 중 파트 안 겹치는 것 병렬 처리

## Why
병렬 개발 시 기존 기능 확정성이 흔들림. 실배포는 Claude 접근 불가라 진입 전 모든 결정이 누적되어 있어야 함.

## How to apply
- 체계 전체는 verify/README.md
- 배포 가이드는 verify/deployment/README.md
- 규약 개요는 verify/_invariants/00-overview.md
