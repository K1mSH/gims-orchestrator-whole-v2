---
name: MEMORY 패턴 우선 적용
description: 계획서 작성 시 MEMORY에 기록된 프로젝트 패턴을 먼저 확인하고 적용할 것
type: feedback
---

계획서/코드 작성 시 기존 코드 패턴보다 **MEMORY에 기록된 프로젝트 규칙을 먼저 확인**하고 적용할 것.

**Why:** 기존 AnyangUsageExecutor가 JdbcTemplate only로 되어있어서 그대로 따라갔지만, MEMORY에 "읽기=JPA, 쓰기=JdbcTemplate"이 명시되어 있었음. 사용자가 지적하기 전에 먼저 제안했어야 했음.

**How to apply:** 새 코드 계획 시 → MEMORY의 아키텍처/패턴 섹션 먼저 리뷰 → 기존 코드와 충돌 시 MEMORY 기준 우선.
