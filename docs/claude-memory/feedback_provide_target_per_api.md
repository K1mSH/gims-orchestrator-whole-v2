---
name: provide 타겟 테이블은 레거시 API endpoint 단위로 분리
description: provide Agent 추가 시 레거시 API 1개당 타겟 1개. 컬럼/소스 공유해도 타겟 공유 금지
type: feedback
---

provide Agent의 PG 제공 테이블은 **레거시 API endpoint 단위로 1:1 분리**한다.
컬럼 구조가 유사하거나 소스가 같아도 타겟 테이블은 공유하지 않는다.

**Why:** provide 계층을 따로 만든 목적 자체가 **"외부 API endpoint별로 데이터 제공을 분리"**하는 것. 레거시에서 API 엔드포인트가 다르면 소비자 관점에서 다른 데이터이므로, 우리 타겟도 반드시 분리해야 한다. 공통화하면 provide 계층의 존재 이유가 사라지고 source_refs 네임스페이스 혼재 / 컬럼 NULL 혼재 / API 응답 스펙 혼란 등 후폭풍. 2026-04-23 A4(SDE_NGWS.WT_DREAM_PERMWELL_PUBLIC_21033) 이식 논의 중 확립.

**How to apply:**
- 새 provide Agent 추가 시: 레거시 API 1개 → 엔티티 1개 + PG 타겟 테이블 1개 + YAML 1개
- "컬럼 구조 비슷하니 기존 엔티티 공유 가능"이라는 제안은 하지 말 것. 해도 사용자에게 승인 받을 필요 자체가 없는 영역 (자동 분리가 기본)
- 소스 테이블 동일한 두 API도 타겟은 분리 (select 조건/응답 스펙 다를 수 있음)
- 예외적으로 공유가 필요해 보이면 "레거시 API 동일성" 증명부터. 증명 불가면 분리
