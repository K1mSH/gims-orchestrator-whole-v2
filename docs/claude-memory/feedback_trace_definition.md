---
name: 추적 검증 = 단건 추적 포함
description: 추적 검증 시 건수 확인만이 아니라 행 클릭 단건 역추적(/trace-source)까지 반드시 수행
type: feedback
---

추적 검증이라 하면 건수 확인(summary, target/source totalCount)뿐 아니라 **단건 역추적(trace-source)까지 포함**한다.

**Why:** 건수만 맞아도 실제 행 단위 매칭이 깨져있을 수 있음. 사용자가 프론트에서 실제로 쓰는 기능이 행 클릭 추적이므로 이것까지 검증해야 의미 있음.

**How to apply:** E2E 테스트 시 각 Step 실행 후 (1) summary 건수 (2) target/source 목록 건수 (3) target 행 1건 클릭 → trace-source 역추적 데이터 확인, 이 3단계를 모두 수행.
