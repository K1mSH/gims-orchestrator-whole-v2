# pm_gd970201 추적(정방향+역추적) 지원 계획

## 현재 상황

### 데이터 흐름
```
if_rsv_sec_obsvdata (SOURCE, id=100)
  ↓ EAV 확장 (1→3행)
pm_gd970201 (TARGET):
  {obsrvn_dta_id=301, result_id=118, value=5.96}   ← gwdep
  {obsrvn_dta_id=302, result_id=119, value=16.4}    ← gwtemp
  {obsrvn_dta_id=303, result_id=120, value=267}      ← ec
```

### pm_gd970201 현재 컬럼
```
obsrvn_dta_id (PK, SERIAL), result_id (FK), obsrvn_dta_value, obsrvn_dt, qlt_id
→ execution_id 없음, source_refs 없음 → 추적 불가
```

### 추적 메커니즘 (기존 다른 테이블)
- **정방향** (`/trace`): SOURCE 행 클릭 → pkValue로 IF 테이블의 source_refs에서 검색 → TARGET 조회
- **역추적** (`/trace-source`): IF/TARGET 행 클릭 → source_refs 파싱 → SOURCE 테이블 PK 조회
- **실행별 필터링** (`/target`): `WHERE execution_id = ?`

## 수정 내용

### 1. DB 스키마 — pm_gd970201에 컬럼 추가
```sql
ALTER TABLE pm_gd970201 ADD COLUMN execution_id VARCHAR(100);
ALTER TABLE pm_gd970201 ADD COLUMN source_refs TEXT;
CREATE INDEX idx_pm_gd970201_execution_id ON pm_gd970201(execution_id);
```

- `execution_id`: 실행별 필터링용 (`/target` 엔드포인트 자동 대응)
- `source_refs`: IF 레코드 PK 참조 (행 단위 추적용, 3행 모두 같은 IF id 참조)

### 2. GimsTargetRepository.batchInsertObsvdata() — SQL 수정
현재: `INSERT INTO pm_gd970201 (result_id, obsrvn_dta_value, obsrvn_dt, qlt_id) VALUES (?, ?, ?, ?)`
변경: `INSERT INTO pm_gd970201 (result_id, obsrvn_dta_value, obsrvn_dt, qlt_id, execution_id, source_refs) VALUES (?, ?, ?, ?, ?, ?)`

### 3. InternalLoadStep.execute() — expandedRows에 executionId, source_refs 추가
현재: `expandedRows.add(new Object[]{resultId, value, obsrvnDt, 1})`
변경: `expandedRows.add(new Object[]{resultId, value, obsrvnDt, 1, executionId, sourceRef})`

source_refs 형식: IF 레코드의 id를 기존 형식으로 저장
예: `["I:internal:if_rsv_sec_obsvdata:100"]` (I = Internal)

### 4. DDL 스크립트 반영
`scripts/gims-target-ddl.sql` — CREATE TABLE에 execution_id, source_refs 추가

## 수정 파일 목록
1. `scripts/gims-target-ddl.sql` — DDL 반영
2. `sync-agent-bojo-int/.../repository/GimsTargetRepository.java` — INSERT SQL 수정
3. `sync-agent-bojo-int/.../step/InternalLoadStep.java` — executionId, source_refs 전달

## 추적 동작 예상
- **실행별 필터링**: `/target?tableName=pm_gd970201` → `WHERE execution_id = ?` → 자동 동작 (기존 코드)
- **정방향 (SOURCE→TARGET)**: IF 행 클릭 → trace → pm_gd970201에서 `source_refs LIKE '%:100%'` 로 3행 찾기
- **역추적 (TARGET→SOURCE)**: pm_gd970201 행 클릭 → source_refs 파싱 → IF 행 조회

## 빌드/배포
- sync-agent-bojo-int 빌드만 필요 (common 수정 없음)
- DB: ALTER TABLE 실행 (기존 데이터는 execution_id/source_refs = NULL)
