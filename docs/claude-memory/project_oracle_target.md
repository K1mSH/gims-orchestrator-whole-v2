---
name: Internal Agent target DB Oracle 전환
description: bojo-int의 target DB를 PostgreSQL에서 Oracle로 전환해야 함 (도커)
type: project
---

Internal Agent(bojo-internal)의 target DB(GIMS 본체)가 실제 운영 환경에서는 Oracle임.

**Why:** 현재 개발은 PG(29001)로 하고 있지만, 실서버는 Oracle. SQL 호환성 확보 필수.

**How to apply:**
- Oracle XE 도커 컨테이너 신규 구성 (gvenzl/oracle-xe)
- 기존 내부 PG의 GIMS target 테이블을 Oracle에 DDL 이관
- bojo-internal 코드: Oracle 호환 SQL (MERGE INTO, 날짜 함수 등)
- common의 Multi-DB 분기에 Oracle 추가 필요할 수 있음
- Orchestrator datasource 설정 변경
- todo/03-bojo-internal.md에 항목 추가됨 (3/25)
