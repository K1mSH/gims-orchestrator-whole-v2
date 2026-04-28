---
name: feedback_provide_response_v3_compat
description: provide 응답 키 = v3 레거시 alias 유지 (외부 호환성). 내부 DB 만 표준화
type: feedback
---

provide(API 제공) 핸들러의 외부 응답 JSON 키는 **v3 레거시 alias 그대로** 사용한다 — 내부망 Oracle 의 컬럼은 표준화 (환경부 표준) 으로 적용해도, 외부 응답 키만큼은 v3 호환 유지.

**Why:** v3 외부 사용자 코드가 응답 키 기준으로 작성됨. 표준화 명으로 바꾸면 외부 사용자 일괄 깨짐. provide 모듈 본질 = 외부 제공 안정성 (`feedback_provide_layer_upsert` 와 일관). 내부 DB 표준화 ≠ 외부 API 명세.

**How to apply:**
- v3 SQL 의 OPNResultVO/TmGd30310 등 VO 필드명 = JSON 응답 키 (소문자/snake_case/camelCase 혼재)
- 핸들러 SQL 의 SELECT alias 는 v3 그대로 (대문자/camelCase) — ResultSet 추출용
- row.put 키 = VO 필드 형식 — JSON 응답
- metadata.column().columnName 과 aliasName 둘 다 VO 필드 형식 (v3 alias)
- 운영자 화면 default 도 v3 alias (Type B 등록 시 datasource introspection 으로 표준화 명 보여주면 안 됨)
- 신규 핸들러 작성 시 v3 SQL 의 resultClass VO + 필드명 먼저 확인
- 예외: B16-DJ/KB 의 `YN` 컬럼은 VO 가 대문자 — 그대로 유지
- 예외: B13 의 동적 PIVOT 컬럼 `c0001~` (소문자) — VO 의 `c0001` 필드 형식 따라감
