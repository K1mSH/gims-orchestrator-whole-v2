# API Collector — targetDatasourceId 수정 기능 추가

## 목적
커스텀 실행기 상세화면에서 `targetDatasourceId`를 수정할 수 없는 문제 해결.
현재 저장 시 `endpoint` 원본값을 그대로 전송하여 UI에서 변경 불가.

## 현재 문제
- `handleSaveInfo`에서 `targetDatasourceId: endpoint.targetDatasourceId` (원본값 고정)
- UI에 편집 필드 자체가 없음
- D5(안양 이용량)의 `targetDatasourceId`가 `dmz_api_collector`로 잘못 등록되어 실행 실패

## 수정 대상 파일 (1개)
- `sync-orchestrator/frontend/components/api-collect/InfoTab.tsx`

## 변경 내용

### 1. state에 targetDatasourceId 추가
- `form` state 또는 별도 state로 `targetDatasourceId` 관리
- 초기값: `endpoint.targetDatasourceId || ''`

### 2. UI — 기본정보 섹션에 드롭다운 추가
- Orchestrator `/api/datasources` API에서 목록 가져와 select 옵션으로 표시
- 커스텀/범용 모두 표시 (SND target이 어디든 될 수 있으므로)
- 위치: 실행기 선택 아래 or 설명 위

### 3. 저장 로직 수정
- `handleSaveInfo`에서 `targetDatasourceId`를 state 값으로 전송

## 영향 범위
- 프론트엔드 InfoTab.tsx 1개 파일만 수정
- 백엔드 변경 없음 (update API가 이미 targetDatasourceId 수용)
