---
name: 실행 제어는 스케줄이 담당, 도메인 entity 의 isActive UI 노출 X
description: api-collect/api-provide 등 운영 도메인의 isActive 같은 "활성/실행 가능" 토글 UI는 노출하지 않음. 실행 여부는 스케줄(또는 호출 흐름)이 제어
type: feedback
originSessionId: c1918da9-0e6a-4f21-bd99-52c082ca924d
---
운영 도메인(api-collect endpoint, api-provide operation 등)의 `isActive` 같은 "활성/실행 가능" 토글을 InfoTab 등 화면에 노출하지 않는다.

**Why:**
- 실행 여부는 스케줄(스케줄 활성/비활성) 또는 외부 호출 흐름이 자체적으로 제어함
- entity 단위 isActive 토글을 별도 노출하면 사용자 입장에서 의미 중복 (스케줄 OFF 인데 isActive 는 ON 같은 비정합 상태 가능)
- 5/11 api-collect/[id] InfoTab 에서 활성 체크박스 제거 — schedule 로 일원화

**How to apply:**
- 새 InfoTab/등록 폼 작업 시 `isActive` 또는 비슷한 entity 활성 토글이 폼에 있으면 노출 제거 검토
- form 상태값은 그대로 유지 (현재 값 그대로 저장 → backend 동작 영향 없음)
- 단, 도메인이 명백히 "스케줄과 무관한 자체 활성 의미"를 가질 때만 예외 (예: 데이터 자체 노출 차단용 — 매우 드묾)
- 사용자가 명시적으로 토글 다시 노출 요청 시 예외
