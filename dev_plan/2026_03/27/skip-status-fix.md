# Internal Loader 전건 스킵 시 상태 보정

## 목적
- Internal Loader가 전건 스킵(write=0, skip=read)인데 SUCCESS로 리포팅되는 문제 수정
- 스킵 사유가 실행 이력에 남지 않는 문제 수정

## 현상
- TM_GD970001(제원)에 데이터 없음 → spot_id 매칭 실패 → 151,095건 전부 스킵
- 결과: status=SUCCESS, read=151095, write=0, skip=151095, errorMessage=null

## 수정 대상
1. `sync-agent-bojo-int/.../InternalBojoLoadStep.java` (283행)
   - write=0 & skip>0 → Status.SKIPPED + 경고 메시지
2. `sync-agent-common/.../PipelineRunner.java` (68행 부근)
   - Step이 SKIPPED 반환 시 finalStatus를 SKIPPED로 설정 (FAILED보다 낮은 우선순위)

## 상태 우선순위
- FAILED > SKIPPED > SUCCESS
- 하나라도 FAILED → FAILED
- FAILED 없고 하나라도 SKIPPED → SKIPPED
- 전부 SUCCESS → SUCCESS

## 영향 범위
- Orchestrator 콜백 수신 시 SKIPPED 상태 처리 확인 필요
- 프론트 실행이력에서 SKIPPED 표시 확인 필요
