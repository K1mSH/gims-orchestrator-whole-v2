---
name: 조건실행 공통 헬퍼 필수
description: 커스텀 Step에서 IF 테이블 조건조회(PENDING/조건실행 분기)를 공통 헬퍼로 처리해야 함
type: feedback
---

커스텀 Step 구현 시 IF 테이블 조회 로직(PENDING 기본 vs 조건실행 분기)을 직접 구현하면 누락 발생.

**Why:** JejuJewonLoadStep에서 조건실행 처리를 빠뜨려서 link_status=SUCCESS 조건실행이 안 됐음. InternalBojoLoadStep에는 있었지만 복사하지 않아 누락.

**How to apply:** 
- ConditionBuilder에 IF 테이블 조건조회 공통 헬퍼 메서드 추가
- 모든 커스텀 Step(InternalBojoLoadStep, JejuJewonLoadStep, 향후 I2/I3/I5)에서 이 헬퍼 사용
- 새 Step 만들 때 반드시 조건실행 + Retention 지원 여부 확인
- Retention(데이터 보관 기간 정리)도 동일하게 공통화 대상
