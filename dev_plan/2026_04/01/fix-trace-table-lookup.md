# 추적 API 테이블명/매핑명 불일치 수정

## 문제
Orchestrator가 Proxy의 `/api/execution-data/{executionId}/tables/{tableName}` 호출 시 **테이블명**(if_snd_use_status_data)을 보내지만, common ExecutionDataController는 **mappingName**(use-status)으로 SyncLog를 조회하여 404 발생.

## 원인 분석
- Orchestrator `ExecutionService.getTableRecords()`: URL에 tableName 사용
- Common `ExecutionDataController.getTableLog()`: `findByExecutionIdAndMappingName()` 호출
- SyncLog에 sourceTables/targetTables가 JSON 배열로 저장되어 있으므로 테이블명으로도 검색 가능

## 수정 방안
common의 `getTableLog()` 에서 mappingName으로 못 찾으면 sourceTables/targetTables에서 테이블명으로 fallback 검색.

### 수정 파일
1. `sync-agent-common/.../controller/ExecutionDataController.java` — getTableLog, getTableFailedInfo

### 변경 로직
```
1. findByExecutionIdAndMappingName(executionId, name) — 기존 mappingName 매칭
2. 실패 시 → findByExecutionId(executionId) 전체 조회 → sourceTables/targetTables JSON에 name 포함된 SyncLog 반환
```

### 영향 범위
- common 컨트롤러 1개 파일
- 기존 mappingName 호출도 호환 유지 (1단계에서 찾으면 그대로 반환)
- common JAR 빌드 후 proxy-dmz, agent-bojo, agent-others libs/ 복사 필요
