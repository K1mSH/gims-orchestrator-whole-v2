---
name: 엔티티 소유권 규칙
description: 내 DB 테이블만 JPA 엔티티, 남의 DB(새올 등)는 순수 JDBC — Agent 모듈 신규 생성 시 entity/ 구조 필수
type: feedback
---

내 기본 DB의 테이블만 JPA 엔티티로 정의한다. 다른 에이전트/외부 시스템의 DB는 순수 JDBC.

**Why:** 스키마 소유권이 다른 테이블을 엔티티로 정의하면 (1) 소유자가 스키마 변경 시 우리 엔티티도 수정 필요 (2) 별도 에이전트/DynamicEntityManager가 필요해져 복잡도 증가 (3) ddl-auto가 엉뚱한 DB에 테이블을 만들 수 있음.

**How to apply:**
- 새올 RGET* 원본 → JDBC (우리 소유 아님)
- RCV의 source(IF_SND) 읽기 → JDBC (다른 에이전트가 관리)
- Loader의 IF_RSV/Target 읽기 → JPA (내 DB)
- 새 Agent 모듈 생성 시 entity/{iftable,source,target} 구조 반드시 포함
