---
name: 계획 문서 작성 전 전략 확인 필수
description: 새 계획 문서 작성 시 ARCHITECTURE.md의 데이터 접근 전략/엔티티 소유권 규칙/DDL 관리 방식을 반드시 먼저 확인
type: feedback
---

계획 문서(dev_plan/) 작성 시 **반드시 기존 전략 문서를 먼저 확인**하고 그에 맞게 설계해야 한다.

**Why:** bojo-internal Entity 전환 건에서 ARCHITECTURE.md에 "읽기=JPA, 쓰기=JDBC" 전략이 명시되어 있었는데, bojo-internal 구현 시 이를 무시하고 전부 raw JDBC로 만들었음. 이후 52개 엔티티를 소급 생성하는 대규모 리팩터링이 필요해짐.

**How to apply:**
1. 계획 문서 작성 전 ARCHITECTURE.md의 다음 섹션 필수 확인:
   - 1.2.6 데이터 접근 전략 (JPA vs JDBCTemplate)
   - 엔티티 소유권 규칙 (내 DB = 엔티티, 남의 DB = JDBC)
   - DDL 스크립트 관리 (scripts/ddl/ 구조)
2. 새 모듈/Step 설계 시 entity/ 디렉토리 구조 포함 여부 확인
3. 계획 문서에 "전략 확인 완료" 항목 명시
