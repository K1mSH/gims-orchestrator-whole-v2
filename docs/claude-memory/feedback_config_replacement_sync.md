---
name: 설정 치환 대상 누적 관리
description: 새 서비스/DB/외부 의존 추가 시 verify/deployment/config-replacement.md 에 즉시 행 추가
type: feedback
---

새 서비스 / DB / 외부 API / 파일 경로 등 **환경에 따라 바뀔 값**을 도입할 때마다 `verify/deployment/config-replacement.md` 에 해당 행을 즉시 추가한다.

**Why:** 실배포 시점에 한꺼번에 식별하려 하면 반드시 누락이 발생. 개발 중 점진 누적만이 누락을 막는다. 실배포는 Claude 접근 불가 환경이라 누락 시 복구 비용 큼.

**How to apply:**
- yml/properties 에 `localhost` / dev 포트 / dev 계정 / 절대경로를 새로 추가하면 → 같은 커밋에 config-replacement.md 행 추가
- Orchestrator DataSource 등록에 새 DB 추가 시 동일
- 외부 API 연동 추가 시 동일
- 이 규약은 verify/_invariants/ 11B (임시값/고정값 구분) 의 운영 보강
