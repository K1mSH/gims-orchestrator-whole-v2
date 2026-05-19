---
name: feedback-service-boot-one-by-one
description: 서비스 bootRun 은 한 번에 한 모듈씩 — 동시 부팅 경합 = PC 렉 원인
metadata: 
  node_type: memory
  type: feedback
  originSessionId: 80ff9d63-9e5b-42fc-933c-7c4483496968
---

서비스 bootRun 은 **한 번에 한 모듈씩** 순차로 띄운다. 이전 모듈이 LISTENING (혹은 actuator/health 200) 박힌 다음에 다음 모듈 bootRun 호출.

**Why**: 동시 bootRun 시 Gradle daemon / JVM 컴파일 / HikariPool 초기 connection 등이 경합해 사용자 PC 가 렉 걸리는 증상이 반복됨. 이전에 9 모듈 병렬 부팅 시 Internal Oracle HikariPool 30s timeout 발생한 것과 동일 원인 추정 ([[feedback_run_without_jar]] 의 bootRun 룰과 결합).

**How to apply**:
- 다중 모듈 기동 시 그룹 동시 부팅 금지. 1 모듈 bootRun → LISTENING 확인 → 다음 1 모듈.
- 사용자가 "그룹 순차" 라고 명시한 경우는 그룹 2~3 동시 OK, 디폴트는 1 by 1.
- 이미 시작한 동시 부팅은 사용자 의사 확인 (그대로 두기 vs 죽이고 재시작).
