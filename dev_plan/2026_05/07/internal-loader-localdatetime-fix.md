# Internal Loader LocalDateTime 회귀 fix

## 배경
1차 반입 통합 테스트 03 bojo Step 5 (Internal Loader) 실 데이터 적재 14897건 모두 실패.

```
java.lang.IllegalArgumentException: 지원하지 않는 날짜 타입: class java.time.LocalDateTime
  at InternalBojoLoadStep.buildObsrvnDt(InternalBojoLoadStep.java:352)
```

원인 = `94e2127 feat: bojo-int Entity 전환 Phase 5 완료 — Step 4개 IF 읽기 JPA 전환` 회귀.
- IF 읽기 JdbcTemplate → JPA Entity 전환
- IfRsvSecObsvdata.obsvDate : LocalDateTime
- buildObsrvnDt 헬퍼는 Timestamp/Date/LocalDate/String 만 분기 → LocalDateTime 분기에서 throw

`fdfa06b 내부망 Oracle 전환 E2E` (3월) 까지 JdbcTemplate 모드라 통과 → Phase 5 이후 첫 실 데이터 적재가 이번 1차반입 검증 → 발견.

## 수정 대상
**파일 1개**: `infolink-agent-bojo-internal/src/main/java/com/infolink/agent/bojo/loader/step/InternalBojoLoadStep.java`

**헬퍼 3개**:
1. `buildObsrvnDt(Object dateObj, Object timeObj)` (line 341)
   - `else if (dateObj instanceof LocalDateTime) { date = ((LocalDateTime) dateObj).toLocalDate(); }` 추가
   - timeObj 도 `else if (timeObj instanceof LocalDateTime) { time = ((LocalDateTime) timeObj).toLocalTime(); }` 추가 (방어)
2. `formatDateStr(Object dateObj)` (line 372)
   - `else if (dateObj instanceof LocalDateTime) { return ((LocalDateTime) dateObj).toLocalDate().format(yyyyMMdd); }` 추가
3. `formatTimeStr(Object timeObj)` (line 385)
   - `else if (timeObj instanceof LocalDateTime) { return ((LocalDateTime) timeObj).toLocalTime().format(HHmmss); }` 추가

## 영향 범위 점검 (회귀 룰 정합)
- InternalBojoLoadStep 만 위 3개 헬퍼 사용 (Grep 확인)
- Jeju*/Use*/Simple* LoadStep — 사용 안 함 → 영향 없음
- DMZ DmzBojoLoadStep — 헬퍼 패턴 없음 (LocalDateTime 은 TimeRange options 용도) → 영향 없음

## 사이드 이슈 (이번 fix 범위 밖)
- sync_log `error_summary` ORA-01461 — 14897건 실패 키 누적이 4000자 초과. 정상 흐름에선 발생 X. 별도 다룰지 추후 결정.

## 빌드/테스트
1. `cd infolink-agent-bojo-internal && ./gradlew clean build -x test`
2. agent-bojo-internal (8092) 재기동
3. Step 5 Internal Loader 재실행 → write > 0 + EAV 1:3 확장 확인
4. 사용자 직접 화면 확인 (0건 룰 정합)
