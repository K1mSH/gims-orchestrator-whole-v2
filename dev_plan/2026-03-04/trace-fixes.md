# 트레이스 2가지 버그 수정 계획

## 문제 1: 정방향 추적 실패 (SOURCE → TARGET)

### 원인
`/trace` 엔드포인트에서 sourceTable=`if_rsv_sec_obsvdata`의 TARGET 테이블을 찾을 때,
이름 기반 매칭(`sec_obsvdata`가 `pm_gd970201`에 포함되는지)을 사용 → 완전히 다른 이름이라 실패.

### 수정 방안
이름 매칭 실패 후 **source_refs 기반 폴백** 추가:
1. 해당 execution의 모든 TARGET SyncLog 엔트리 조회
2. 각 TARGET 테이블에서 `source_refs LIKE '%:{sourceTable}:{pkValue}%' AND execution_id = ?` 쿼리
3. 결과가 있으면 해당 테이블과 데이터를 직접 반환 (추가 조회 불필요)

### 수정 파일
- `sync-agent-common/.../controller/ExecutionDataController.java` — `/trace` 엔드포인트 (line 682 이후)

### 코드 위치
```java
// 기존: line 687에서 ifTableName == null이면 에러 반환
// 변경: 에러 반환 전에 source_refs 기반 폴백 시도
```

---

## 문제 2: 역추적 행 침범 (TARGET → SOURCE)

### 원인
pm_gd970201에서 3개 행(gwdep, gwtemp, ec)이 동일한 source_refs를 공유.
프론트엔드에서 `rowPk`를 `source_refs`의 pk로 계산 → 3개 행이 같은 rowPk를 가짐 →
한 행 클릭 시 `expandedRowPk === rowPk` 조건이 3개 행 모두에서 true → 3개 행 모두 확장됨.

### 수정 방안
TARGET 테이블의 `rowPk`를 source_refs pk 대신 **행 자체의 PK(첫 번째 컬럼)**로 변경:
- 렌더링 부분 (line 692): `rowPk` 계산을 첫 번째 컬럼 값 사용
- handleRowClick (line 164): `rowId` 계산도 동일하게 변경
- source_refs는 API 호출에만 사용 (row 객체에서 직접 읽음, rowPk와 무관)

### 수정 파일
- `sync-orchestrator/frontend/app/executions/[id]/page.tsx`

### 변경 내용
```typescript
// Before (line 692-696):
if (isIfTable || isTargetTable) {
    const sourceRefs = (row.sourceRefs || row.source_refs) as string;
    const parsed = parseSourceRefs(sourceRefs);
    rowPk = parsed?.refs[0]?.pk || String(idx);
}

// After:
if (isIfTable || isTargetTable) {
    // TARGET: 행 자체 PK 사용 (N:1 source_refs 중복 방지)
    // TARGET_IF: 행 자체 PK 사용 (일관성)
    const pkColumn = tableData.columns[0];
    rowPk = pkColumn ? String(row[pkColumn] ?? idx) : String(idx);
}
```

handleRowClick도 동일하게 변경.

---

## 수정 파일 목록
1. `sync-agent-common/.../controller/ExecutionDataController.java` — source_refs 기반 폴백
2. `sync-orchestrator/frontend/app/executions/[id]/page.tsx` — rowPk를 행 자체 PK로 변경
