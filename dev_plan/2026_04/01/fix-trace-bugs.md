# 추적 기능 버그 수정 계획

## 점검 결과 (4/1 실서비스 테스트)

| 기능 | 결과 | 비고 |
|------|------|------|
| `/tables` (매핑별 통계) | 정상 | |
| `/data/source`, `/data/target-if` | 정상 | |
| `/tables/{tableName}` (처리현황 클릭) | **실패** | 404 → `{}` |
| trace-source 단일PK (sn=1001) | 정상 | FOUND |
| trace-source 복합PK (20260325\|SC001) | **실패** | SOURCE_NOT_FOUND |

---

## 버그 A: getTableLog — 테이블명/매핑명 불일치

**현상**: Orchestrator가 테이블명(`if_snd_use_status_data`)을 보내는데, `getTableLog()`는 매핑명(`use`)으로만 검색 → 404

**수정** (733~740줄, 745~760줄):
- `findByExecutionIdAndMappingName()` 실패 시 → `findByExecutionId()` 전체 조회 → `sourceTables`/`targetTables` JSON에 name 포함된 SyncLog 반환
- `getTableLog()`, `getTableFailedInfo()` 동일 적용

---

## 버그 B: 복합PK — 타입 바인딩 + 서브쿼리

모든 추적은 결국 **단건 반복 조회** 또는 **IN절 배치**. 복합PK도 동일한 패턴이고 건수 차이만 있음.

### B-1: trace-source 타입 바인딩 (1124줄)
- `typedValue("20260325")` → Long 변환 성공 → varchar 컬럼에 BIGINT 바인딩 → 에러
- 이미 `isNumericPkColumn()` 분기 코드 작성됨 (3/31) → **JAR 미빌드 상태**
- 빌드만 하면 해결

### B-2: buildSourceFilter 서브쿼리 (401~427줄)
- `SPLIT_PART(source_refs, ':', 4)` → 복합PK면 `20260325|SC001` 통째로 나옴 → BIGINT 캐스팅 실패
- **수정**: 복합PK 감지 시 서브쿼리 스킵 → 기존 배치 분할 경로(430줄~)로 빠지게 함
- 감지 방법: `sourceRefsSet`에서 샘플 1건 `parseSourceRefsPks()` → `"|"` 포함 여부

---

## 수정 파일

| 파일 | 변경 |
|------|------|
| `sync-agent-common/.../controller/ExecutionDataController.java` | A: getTableLog/getTableFailedInfo fallback, B-2: 복합PK 서브쿼리 스킵 |

## JAR 배포
common 빌드 → `sync-proxy-dmz/libs/`, `sync-agent-bojo/libs/`, `sync-agent-others/libs/` 복사

## 테스트

| # | 테스트 | 기대 |
|---|--------|------|
| 1 | `/tables/if_snd_use_status_data` | SyncLog 반환 |
| 2 | `/tables/use` (매핑명) | 기존 호환 유지 |
| 3 | trace-source 단일PK (sn=1001) | FOUND (회귀 없음) |
| 4 | trace-source 복합PK (20260325\|SC001) | FOUND |
| 5 | source 데이터 조회 (use_jeju_day) | 데이터 표시 |
| 6 | bojo 기존 추적 (sec_jewon_view 등) | 회귀 없음 |
