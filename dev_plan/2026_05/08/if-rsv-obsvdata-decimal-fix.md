# IF_RSV_SEC_OBSVDATA Entity 소수점 회귀 fix (bojo-internal)

## 배경

03 bojo Step 5 (Internal Loader) 추적 검증 2단계 단건 역추적 중 **데이터 손실 발견**.

**증상**: IF_RSV_SEC_OBSVDATA id=132 의 `GWDEP=6.27` (NUMBER) 가 PM_GD970201 (Target) 의 `OBSRVN_DATA_VL='6'` (VARCHAR2) 으로 적재. **소수점 첫째 자리 이하 손실**. 동일 IF row 의 GWTEMP=14.0 / EC=221.0 은 원래 정수라 우연 일치.

**근본 원인**: `IfRsvSecObsvdata` Entity (bojo-internal) 에서 `gwdep` / `gwtemp` / `ec` field 를 `Long` 으로 매핑. JPA SELECT 시 Oracle NUMBER → Long.intValue() 식 변환으로 소수점 짤림. 그 후 `InternalBojoLoadStep.toDouble(row.get("gwdep"))` 가 이미 정수화된 Long 6 을 받아 Double 6.0 으로 cast → expandedRows → batchInsert → "6".

**원인 commit**: `94e2127 feat: bojo-int Entity 전환 Phase 5 완료 — Step 4개 IF 읽기 JPA 전환` (3월). bojo-internal 의 IF 읽기를 JdbcTemplate → JPA 로 전환하면서 entity field type 을 NUMBER 추정 시 Long 으로 잘못 매핑.

## 표준화 자료 비교

`feedback_no_regression_organic` + 사용자 룰 (엔티티 = 표준 기준, 임의 수정 X) 정합으로 표준화 자료 일치 여부 확인 후 결론 도출:

| 위치 | 컬럼 | type | 표준성 |
|---|---|---|---|
| 외부 소스 (`dev.sec_obsvdata_view`, PG) | gwdep/gwtemp/ec | `double precision` | 표준 |
| Internal Oracle DDL (`IF_RSV_SEC_OBSVDATA` in `scripts/ddl/internal-oracle/jpa-generated-ddl.sql`) | GWDEP/GWTEMP/EC | `NUMBER` (precision/scale 미지정 = 임의 정밀도) | 표준 |
| bojo-dmz `SecObsvdataView` Entity | gwdep/gwtemp/ec | `Double` | 표준 |
| bojo-dmz `IfRsvSecObsvdata` Entity | gwdep/gwtemp/ec | `Double` | 표준 |
| bojo-dmz `IfSndSecObsvdata` Entity | gwdep/gwtemp/ec | `Double` | 표준 |
| bojo-dmz `SecObsvdata` (Target) Entity | gwdep/gwtemp/ec | `Double` | 표준 |
| **bojo-internal `IfRsvSecObsvdata` Entity** | **gwdep/gwtemp/ec** | **`Long`** | **회귀** |

→ DDL 표준 + 외부 소스 + bojo-dmz 모든 entity 전부 소수 허용. **bojo-internal `IfRsvSecObsvdata` 만 Long**. 표준은 그대로 두고 회귀 entity 만 fix 정당.

## 수정 대상

**파일 1개**: `infolink-agent-bojo-internal/src/main/java/com/infolink/agent/bojo/entity/iftable/IfRsvSecObsvdata.java`

| 라인 | 변경 |
|---|---|
| 31 | `private Long gwdep;` → `private Double gwdep;` |
| 34 | `private Long gwtemp;` → `private Double gwtemp;` |
| 37 | `private Long ec;` → `private Double ec;` |

## 영향 범위 점검 (회귀 룰 정합)

`feedback_no_regression_organic` — 단일 케이스 땜질 X, 전체 검토.

### 같은 패턴 entity (Long → Double 후보) — 점검 결과

- 위 표 외 다른 agent (others-dmz / provide-dmz / api-collector / api-provider) 의 entity 검색 — **0건**. (grep `private (Long|Integer)` + sensor field name 패턴)

### 사용처 영향 (bojo-internal 안)

`InternalBojoLoadStep.java:199~201`:
```java
Double gwdep = toDouble(row.get("gwdep"));
Double gwtemp = toDouble(row.get("gwtemp"));
Double ec = toDouble(row.get("ec"));
```
- `row` = Map<String, Object>로 entity field 를 reflection 으로 변환한 결과
- `toDouble(Object val)` 는 `Number.doubleValue()` 호출 → **Long ↔ Double 모두 정상 변환**
- → entity 변경 후 **사용처 코드 수정 불필요**

### 별 사이클 (본 fix 범위 밖)

- `TmGd970130.ph` (Long) — 수질 테이블, 03 bojo 흐름과 무관. 04+ 다른 사이클에서 표준화 자료 비교 후 별도 진행.

## 수정 절차

1. Entity field 3건 fix (Long → Double)
2. bojo-internal 빌드 (`./gradlew clean build -x test`)
3. agent-bojo-internal (8092) 재기동
4. **데이터 cleanup**:
   - `DELETE FROM PM_GD970201 WHERE EXECUTION_ID LIKE 'internal-bojo-loader_%'` (잘못 적재된 44691행)
   - `UPDATE IF_RSV_SEC_OBSVDATA SET LINK_STATUS='PENDING', EXECUTION_ID=NULL WHERE LINK_STATUS='SUCCESS'` (14897건 재처리)
   - `DELETE FROM TM_GD980002` (1206행 — 재실행 시 동일 UPSERT 로 재생성)
5. 03 Step 5 재실행 (사용자 화면)
6. 추적 검증 2단계 재수행 — IF id=132 `GWDEP=6.27` → PM `OBSRVN_DATA_VL='6.27'` 정합 확인
7. 추적 검증 3단계 (사용자 UI 확인)

## 사이드 이슈 (이번 fix 범위 밖, 추후 결정)

- **`TmGd970130.ph` Long** — 수질 테이블. 표준화 자료 비교 + 04+ 사이클에서 처리.
- **OBSRVN_DATA_VL 이 VARCHAR2** — 표준이 그렇지만 `Double 6.27 → setObject → VARCHAR2` 변환 시 driver 동작이 "6.27" 문자열로 들어가는지 재검증 필요. (이번 fix 후 검증으로 자연스럽게 확인됨)
- **dev_logs 5/7 의 기대값 `write≈14897×3`** — 코드 의도(IF 단위)와 어긋남. 03 bojo 마무리 시 dev_logs / test_plan 갱신.

## 빌드/테스트 게이트

- BUILD SUCCESSFUL (bojo-internal)
- agent-bojo-internal `Started` 로그 확인
- Step 5 결과: read=14897, write=14897, skip=0, status=SUCCESS
- IF id=132 → PM 3 EAV row 중 GWDEP rsltId row 의 OBSRVN_DATA_VL = "6.27" (소수점 보존)
