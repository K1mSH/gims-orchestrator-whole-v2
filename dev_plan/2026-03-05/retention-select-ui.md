# Retention UI: 테이블/컬럼 Select 방식 변경

## 목적
Retention 편집 시 free-text 입력 → 등록된 테이블 기반 `<select>` 드롭다운으로 변경.
사용자가 오타 없이 확정적으로 테이블/컬럼을 선택할 수 있도록 개선.

## 현재 상태
- 테이블명, 날짜컬럼: `<input type="text">` 직접 입력
- InfoTab에서 이미 `targetTables` (DatasourceTable[]) 를 fetch하고 있음
  - 각 테이블에 `columns[]` (DatasourceColumn[]) 포함

## 변경 내용

### 수정 파일: `sync-orchestrator/frontend/components/agent/InfoTab.tsx`

#### 테이블 선택
- `<input type="text">` → `<select>` 변경
- 옵션: `targetTables`의 `tableName` 목록
- 이미 InfoTab에서 fetch 중이므로 추가 API 호출 불필요

#### 날짜 컬럼 선택
- `<input type="text">` → `<select>` 변경
- 선택된 테이블의 `columns[]`에서 `columnName` 목록 제공
- 테이블 변경 시 dateColumn 초기화

#### 보존 기간
- 기존 `<input type="number">` 유지 (변경 없음)

## 영향 범위
- 프론트엔드 InfoTab.tsx 1개 파일만 수정
- 백엔드 변경 없음
