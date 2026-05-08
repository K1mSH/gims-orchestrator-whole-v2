---
name: Retention 후보 = Agent yml의 자기 target 만 (이중 설정 방지)
description: Retention 정책 추가/검토 시 yml retention-candidates 에 자기 target 만 박는다 (이중 설정 + 의도 외 삭제 사고 방지)
type: feedback
originSessionId: cd3c8e47-e004-42d2-aed0-53637eca8bed
---
Agent 의 yml `retention-candidates` 에는 **자기 target 테이블만 박는다**. source (다른 Agent 의 target) 는 박지 않음.

**Why:** 5/8 saeol/yaksoter 에 부적합 dateColumn (first_reg_dthr/instl_ymd — 등록일/시설 설치일) 으로 449 row 의도 외 삭제 사고. 본질 = retention 룰의 부적절한 일반화 + Agent boundary 미준수. 사용자 명확한 룰 = "yml 에 박힌 후보만 운영자가 선택 가능 + 자기 target 만 책임 (이중 설정 방지)".

**How to apply:**
- 새 Agent yml 작성 시 retention-candidates = 시계열 데이터 적재 target 만 (마스터 / Link / 메타 / 시설등록 류 X)
- internal-bojo-loader (LOADER) 에 IF_RSV_SEC_OBSVDATA 박지 X (= internal-bojo-rcv 의 target). loader 는 PM_GD970201 만.
- retention 비대상 Agent (Others SND, 마스터 적재 류) = 빈 배열 명시 (`retention-candidates: []`). 운영 화면에서 "비대상" 메시지 + 설정 버튼 hide.
- date 컬럼 의미 = 데이터 발생 시점 (관측일 obsv_date / obsrvn_dt). 등록일/설치일/생성일 류는 retention 기준으로 부적합.
- 4 layer 검증: yml (단일 진실원) → Frontend dropdown → Backend PUT validation → Agent DataRetentionService 자체 검증 (defense-in-depth).
- `enabled` 필드 deprecate — `targets.length > 0` 만 검사 (등록 = 적용).
