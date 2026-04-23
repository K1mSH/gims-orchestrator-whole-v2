---
name: provide 계층은 항상 UPSERT + UK
description: provide(외부 제공) Loader는 RCV와 다른 계층 전략 — 항상 UPSERT + 자연키 UK 유지
type: feedback
---

provide Agent의 Loader 계층은 RCV의 "기본 INSERT + 조건실행 UPSERT" 전략과 달리, 항상 ON CONFLICT DO UPDATE + 자연키 UK를 유지한다.

**Why:** provide target은 외부 API 제공 계층이라 데이터 무결성이 내부 처리 효율·설계 순수성보다 우선한다. link_status 로직이 완벽해도 외부 노출 중복은 신뢰도 타격이 크고, UK 제약이 DB 레벨 마지막 방어선이 된다. 성능 오버헤드(ON CONFLICT 인덱스 룩업 1회)는 증분 필터로 소건수만 흐르는 특성상 무시 가능. 조건 실행(RESYNC) 지원도 공짜로 얻어진다. 2026-04-23 Type A 이식 중 ProvideLoadStep 설계 검토에서 결론.

**How to apply:**
- provide 엔티티 신설 시 자연키가 `@Id`면 그대로(PK=UK), `@Id`가 IDENTITY면 자연키에 `@UniqueConstraint` 반드시 선언
- ProvideLoadStep 구현을 "기본 INSERT로 리팩토링" 같은 제안은 하지 말 것 (계층 특성상 현 설계가 맞음)
- RCV(SourceToIfStep)의 `useUpsert = fullCopy || isResyncExecution` 분기를 provide에도 적용하려 하지 말 것 — 계층 목적이 다름
- bojo Loader(TargetRepositoryService의 batchUpsert*)도 동일 원칙으로 항상 UPSERT임. 같은 이유 적용
