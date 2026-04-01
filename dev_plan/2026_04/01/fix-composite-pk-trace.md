# 복합PK 추적 버그 수정 계획

## 문제 요약
`use_jeju_day`(복합PK: `obsrvt_id + obsr_de`) 테이블에서 추적 2건 실패.

### 버그 1: source 조회 — buildSourceFilter 복합PK 미지원
- **위치**: `ExecutionDataController.java` 393번줄 부근
- **현상**: PK 컬럼이 복합일 때 `id`로 폴백 → `column "id" does not exist`
- **원인**: `findPkColumns()`가 복합PK를 반환하지만, buildSourceFilter는 단일 PK만 가정
  - `SPLIT_PART(source_refs, ':', 4)` → 단일값만 추출
  - `pkColumn IN (subquery)` → 단일 컬럼 IN절
- **영향**: 처리현황 SOURCE 행 클릭 시 데이터 조회 실패

### 버그 2: trace-source — 복합PK 바인딩 타입 불일치
- **위치**: `ExecutionDataController.java` 1124번줄
- **현상**: `typedValue("20260325")` → Long → `varchar = bigint` 타입 에러
- **원인**: 복합PK 각 파트를 `typedValue()`로 변환 → DB 컬럼 타입 무시
- **수정 완료 (미빌드)**: `isNumericPkColumn()` 결과로 분기하도록 이미 수정함

## 수정 대상
- `sync-agent-common/.../controller/ExecutionDataController.java`

## 수정 방안

### 버그 1 수정: buildSourceFilter 복합PK 분기
현재 단일PK 전제:
```
pkColumn IN (SELECT CAST(SPLIT_PART(source_refs, ':', 4) AS BIGINT) FROM target WHERE execution_id=?)
```

복합PK일 때:
- source_refs 형식: `["D:1018:107:20260325|SC001"]`
- SPLIT_PART(:, 4) → `20260325|SC001` (파이프 포함 문자열)
- 단일 IN절로는 매칭 불가 → **source_refs 기반 JOIN 또는 파싱 후 개별 매칭** 필요

**방안 A (권장)**: 복합PK면 서브쿼리 대신 source_refs IN 매칭
```sql
SELECT * FROM use_jeju_day 
WHERE EXISTS (
  SELECT 1 FROM if_snd_use_jeju_day t 
  WHERE t.execution_id = ? 
  AND use_jeju_day.source_refs = t.source_refs
)
```
→ 하지만 source 테이블에 source_refs가 없을 수 있음 (SND source는 link_status만 있음)

**방안 B**: 복합PK면 target에서 source_refs 목록 추출 → 파이프 분리 → 다중 컬럼 WHERE 생성
```
target의 source_refs에서 PK 파싱 → "20260325|SC001" → split("|") → [obsr_de, obsrvt_id]
→ WHERE (obsr_de = '20260325' AND obsrvt_id = 'SC001') OR (...)
```
→ PK 개수가 많으면 OR 절이 너무 길어짐 → 소량(<=1000)일 때만

**방안 C**: 복합PK면 기존 배치 모드(cross-DB 경로) 강제 사용
- sameDb여도 서브쿼리 대신 PK 파싱 → 개별 매칭
- `parseSourceRefsPks()`로 추출된 "20260325|SC001" → 복합PK 분리 → 다중 WHERE

### 버그 2 수정 (완료)
```java
// 변경 전
params.add(typedValue(pkParts[i]));

// 변경 후  
boolean isNumeric = isNumericPkColumn(sourceJdbc, actualTableName, pkColumns.get(i), sourceDbType);
params.add(isNumeric ? typedValue(pkParts[i]) : pkParts[i]);
```

## JAR 배포
common 빌드 후 3개 모듈 복사:
- sync-proxy-dmz/libs/
- sync-agent-bojo/libs/
- sync-agent-others/libs/

## 테스트
- use_status_data (단일PK sn) trace-source → FOUND 유지
- use_jeju_day (복합PK obsrvt_id+obsr_de) trace-source → FOUND
- use_jeju_day source 조회 (처리현황 SOURCE 클릭) → 데이터 표시
- bojo 기존 추적 (sec_jewon_view, sec_obsvdata_view) 회귀 없음
