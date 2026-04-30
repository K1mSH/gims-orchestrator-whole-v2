# Type A 응답 컬럼 v3 호환 보정 — RNUM / JOSACODE 누락분 추가

> 작성일: 2026-04-29
> 정책 근거: `feedback_provide_response_v3_compat` (외부 응답 = v3 레거시 alias)
> 원자료: `D:/dev/claude/copySource/v3/src/egovframework/sqlmap/com/gims/sql_megokrapi.xml`

---

## 1. 배경

4/29 세션에서 Type A 12종(A1~A7 + B1/B2) 응답 컬럼을 v3 레거시 SQL 과 1:1 비교한 결과, **2건 누락** 확인:

| # | Operation | 누락 컬럼 | v3 SQL 위치 |
|:-:|---|---|---|
| A7 | `megokrApi/ngw04_01` | **RNUM** | `sql_megokrapi.xml:303-305` 외부 SELECT `ROWNUM AS RNUM, TB1.*` |
| B2 | `megokrApi/ngw03_01` | **RNUM**, **JOSACODE** | `sql_megokrapi.xml:44-83` 외부 SELECT `ROWNUM AS RNUM, TB1.*` + 내부 `JOSACODE` |

> A4 는 내부 서브쿼리에 `ROWNUM AS rnum`이 있으나 외부 SELECT 가 33컬럼만 가져와 응답에 미노출 → 무관

외부 v3 사용자가 RNUM/JOSACODE 에 의존했다면 호환 깨짐. 사용자 정책: "외부 제공 컬럼은 레거시와 동일해야 함."

---

## 2. 설계 검토 — 4 안 비교 후 확정

| 안 | 방식 | 엔티티 | DDL | 코드 | column 더미 row | 적재 코드 | 데이터 변경 시 |
|---|---|:--:|:--:|---|:--:|:--:|---|
| ① 물리 컬럼 | `api_prv_tmp_megokr_api ADD COLUMN rownum BIGINT` + 적재 시 ROW_NUMBER 박기 | 1 | 1 | 0 | 0 | **변경 발생** | **수동 재계산 필요** (UPSERT 마다 전체 row UPDATE) |
| ② SQL 합성 (transform_type='ROWNUM') | column 등록에 `_rownum` 더미 행 + buildSelectClause 분기 | 0 | 0 | 3줄 | A7=1, B2=1 (운영자 화면 노출) | 0 | 자동 (응답 시 합성) |
| ③ SQL 합성 (column_name='ROWNUM') | column 등록에 `ROWNUM` 매직 키워드 + buildSelectClause 분기 | 0 | 0 | 3줄 | A7=1, B2=1 (운영자 화면 노출) | 0 | 자동 |
| **④ 자바 후처리 (화이트리스트)** | **`DynamicQueryService.execute()` 끝에 operationId Set 보고 RNUM 부착** | **0** | **0** | **~10줄** | **0** | **0** | **자동 (응답 시 합성)** |

### 확정: ④ 자바 후처리

**선택 근거 (사용자 결정):**
- 운영자 column 모델 깨끗 (운영자 화면에 더미 행 미노출)
- DB 변경 0 (RNUM 부분)
- 의미적으로 v3 ROWNUM 과 동등 — "응답 호출 시점의 결과셋 row 위치를 1부터 부여"
- 컬럼 값 의존 없이 List 인덱스만 사용
- 데이터 추가/삭제 시 자동 반영 (별도 갱신 정책 불요)

**물리 컬럼 안 ①의 함정:**
- 적재 시 ROW_NUMBER 박으면 데이터 추가/삭제마다 전체 재계산 필요
- A7/B2 가 같은 테이블 공유 — 향후 다른 정렬 op 추가 시 동일 RNUM 컬럼 충돌
- v3 ROWNUM 본질은 "데이터 아닌 표현(의사컬럼)" — 물리 컬럼은 의미 어긋남

**JOSACODE 는 별개:**
- 진짜 PG 컬럼(`api_prv_tmp_megokr_api.josacode`) → column 행 등록이 자연 (운영자 모델 본래 용도)
- `register_ops.py` B2 columns 끝에 추가 + PG 1행 INSERT

---

## 3. 수정 내역

### 3.1 코드 (`DynamicQueryService.java`)

**클래스 상단 상수 (필드 부근):**
```java
private static final Set<String> ROWNUM_PREPEND_OPS = Set.of(
    "megokrApi/ngw04_01",   // A7 — v3 selectNgw04_01 외부 SELECT의 ROWNUM AS RNUM
    "megokrApi/ngw03_01"    // B2 — v3 selectNgw03_01 외부 SELECT의 ROWNUM AS RNUM
);
```

**`execute()` — 데이터 쿼리 직후 (line 44-46 사이):**
```java
List<Map<String, Object>> data = jdbc.queryForList(
        sqlResult.dataSql, sqlResult.dataParams.toArray());

if (ROWNUM_PREPEND_OPS.contains(operation.getOperationId())) {
    data = prependRownum(data);
}
```

**신규 private 메서드:**
```java
/**
 * v3 레거시 호환 — 응답 row 에 RNUM 첫 키로 부착 (1부터 페이지 내 순번)
 * v3 SQL의 SELECT ROWNUM AS RNUM, TB1.* 흉내. 컬럼 값 의존 없이 List 인덱스만 사용.
 */
private List<Map<String, Object>> prependRownum(List<Map<String, Object>> rows) {
    List<Map<String, Object>> result = new ArrayList<>(rows.size());
    for (int i = 0; i < rows.size(); i++) {
        Map<String, Object> ordered = new LinkedHashMap<>();
        ordered.put("RNUM", i + 1);
        ordered.putAll(rows.get(i));
        result.add(ordered);
    }
    return result;
}
```

> RNUM 시작값 = 페이지 내 1부터. v3 는 페이징 없이 FETCH FIRST 10000 으로 한 번에 받아 RNUM=1~10000. 우리 pageSize=10000 동일. 데이터가 10000 초과해 page=2 가는 케이스도 v3 와 비교 무의미 (v3 는 그 시나리오 자체 없음).

### 3.2 DB INSERT (B2 JOSACODE 만)

`scripts/temp/restore_v3_columns.sql` 신규:
```sql
-- v3 응답 컬럼 호환 보정 — B2 JOSACODE 누락분 추가
-- 작성일: 2026-04-29
-- (RNUM 은 코드 측 자바 후처리로 처리 — DB 변경 없음)

INSERT INTO api_prv_operation_column
  (operation_id, column_name, alias_name, display_order, transform_type)
SELECT id, 'josacode', 'JOSACODE', 22, 'NONE'
FROM api_prv_operation
WHERE operation_id = 'megokrApi/ngw03_01';
```

> JOSACODE display_order=22 (끝 추가). 기존 0~21 행 무수정. 외부 사용자가 키 순서 의존했을 가능성 0 (v3 응답이 Java HashMap 비결정 순서였음).

### 3.3 register_ops.py 동기화

**B2 columns 끝에 JOSACODE 추가:**
```python
B2 = {
    ...
    "columns": [
        ("qltwtr_inspct_sn", "QLTWTR_INSPCT_SN"),
        ...
        ("pubwell_at", "PUBWELL_AT"),
        ("josacode", "JOSACODE"),       # 신규: v3 selectNgw03_01 내부 SELECT 의 JOSACODE
    ],
    ...
}
```

> A7 RNUM 은 자바 코드에서 처리하므로 register_ops.py columns 변경 없음.
> 신규 환경에서 register_ops.py 재실행 시 자동 정합 보장.

---

## 4. 영향 범위 / 회귀

| 영역 | 변경 | 영향 |
|---|---|---|
| 엔티티 | 0 | — |
| DDL | 0 | — |
| `DynamicQueryService` | 화이트리스트 + 후처리 메서드 | A7/B2 만 후처리 분기, 나머지 op 무영향 |
| `api_prv_operation_column` | B2 에 JOSACODE 1행 INSERT | B2 응답에 JOSACODE 키 추가 (호환 회복) |
| `register_ops.py` | B2 columns 정의 갱신 | 신규 환경 재등록 시 정합 |

**회귀 테스트:**
- `scripts/temp/test_ops.py` 12/12 200 OK (전수)
- A7 응답 첫 키 = `RNUM` (값 1, 2, 3, ...)
- B2 응답 첫 키 = `RNUM`, JOSACODE 컬럼 포함
- 나머지 10종 응답 키 변화 없음
- CUSTOM 16종 카탈로그 registered=True 유지

---

## 5. 작업 순서

1. `DynamicQueryService.java` 수정 — 상수 + `execute()` 분기 + `prependRownum()` 메서드
2. `scripts/temp/restore_v3_columns.sql` 작성 → PG 실행 (1행 INSERT)
3. `scripts/temp/register_ops.py` B2 columns 정의 갱신
4. 빌드 (`./gradlew clean build -x test`) — 본 모듈만
5. api-provider 재기동 (호스트 8095)
6. `test_ops.py` 회귀 — 12/12 200 OK + A7/B2 응답 검증
7. `dev_logs/2026_04/2026-04-29.md` 갱신 (기존 docker PoC 일지에 본 작업 추가)
8. 사용자에 커밋 여부 확인

---

## 6. 작업 후 확인 항목

- [ ] A7 (`megokrApi/ngw04_01`) 응답 첫 키 = `RNUM` (1, 2, 3, ...)
- [ ] B2 (`megokrApi/ngw03_01`) 응답 첫 키 = `RNUM`
- [ ] B2 응답에 `JOSACODE` 키 포함
- [ ] A1/A2/A3/A4/A5×4/A6/B1 응답 키 변화 없음
- [ ] `test_ops.py` 12/12 200 OK
- [ ] CUSTOM 카탈로그 16/16 registered=True
- [ ] `gims-api-provider/` git diff 코드 변경 1개 파일 (`DynamicQueryService.java`)
