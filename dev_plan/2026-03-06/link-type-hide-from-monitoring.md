# LINK 타입 모니터링/표출에서 제외

## 목적
LINK 타입(link_ngwis, tm_gd980002)은 데이터 처리 로직에서는 유지하되,
프론트엔드 표출 및 로그/모니터링에서는 제외한다.

## 변경 범위

### 1. 프론트엔드: 실행 상세 페이지 (executions/[id]/page.tsx)
- 테이블별 처리현황(tableStats)에서 `tableType === 'LINK'` 필터링
- 테이블 선택 시에도 LINK 제외

### 2. 백엔드: ExecutionDataController.java (sync-agent-common)
- `/tables` 엔드포인트 응답에서 LINK 타입 SyncLog 제외
- `/summary` 집계에서 LINK 타입 제외

### 3. 유지 (변경 없음)
- LinkTableUpdateStep의 SyncLog 기록 → 유지 (데이터 처리 로직)
- InternalLoadStep의 SyncLog 기록 → 유지 (데이터 처리 로직)
- LINK 타입 enum/CSS 정의 → 유지 (나중에 필요할 수 있음)

## 수정 파일
1. `sync-orchestrator/frontend/app/executions/[id]/page.tsx`
2. `sync-agent-common/.../controller/ExecutionDataController.java`
