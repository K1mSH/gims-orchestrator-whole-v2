# Oracle TIME 타입 변환 이슈 수정

## 현상
- PG `obsv_time` (TIME 타입, 값 `03:00:00`) → Oracle `OBSV_TIME` (VARCHAR2(8)) 적재 시
- `java.sql.Time` 객체가 Oracle VARCHAR2에 바인딩되면서 `70/01/01`로 변환됨
- Internal Loader의 `buildObsrvnDt`에서 파싱 실패 → 전건 스킵

## 원인
- SourceToIfStep 277행: `params.add(record.get(column))` — source에서 가져온 값을 타입 변환 없이 그대로 사용
- PG JDBC는 TIME 컬럼을 `java.sql.Time`으로 반환
- Oracle JDBC는 `java.sql.Time`을 VARCHAR2에 넣을 때 epoch 날짜 기반 변환

## 수정 방안

### 수정 대상
- `sync-agent-common/.../step/SourceToIfStep.java` (277~278행 부근)

### 수정 내용
- target DB가 Oracle일 때 파라미터 바인딩 전 타입 변환 처리
- `java.sql.Time` → `HH:mm:ss` 또는 `HHmmss` 문자열 변환
- `java.sql.Date` → Oracle DATE에 바인딩은 그대로 OK (JDBC 호환)

### 추가 수정
- `sync-agent-bojo-int/.../InternalBojoLoadStep.java` `buildObsrvnDt()`
- `java.sql.Timestamp` 타입 처리 추가 (Oracle DATE→JDBC가 Timestamp으로 반환할 수 있음)

### 영향 범위
- Internal RCV (PG→Oracle 복사) — SourceToIfStep
- Internal Loader (Oracle 읽기→Oracle 쓰기) — buildObsrvnDt
- DMZ 구간은 PG→PG라 영향 없음

### obsv_time 형식 결정
- PG 원본: `03:00:00` (TIME, HH:mm:ss)
- Oracle DDL: VARCHAR2(8)
- 기존 코드(buildObsrvnDt): `HHmmss` 6자리 파싱 지원 + `HH:mm:ss` 8자리 지원
- → `HH:mm:ss` 형식으로 저장 (8자 = VARCHAR2(8)에 딱 맞음)
