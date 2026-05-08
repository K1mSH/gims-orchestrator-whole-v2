# 보조망(BOJO) Internal IF Entity 표준화 정합 회귀 fix

## 1. 발단

03 bojo Step 5 (Internal Loader) 추적 검증 2단계 단건 역추적 중 **데이터 손실 발견**.

| 항목 | 값 |
|---|---|
| IF_RSV_SEC_OBSVDATA id=132 | GWDEP=`6.27`, GWTEMP=`14`, EC=`221` |
| PM_GD970201 SOURCE_REFS=같은 IF 의 EAV 1:3 row | OBSRVN_DATA_VL=`6` / `14` / `221` |
| 손실 발생 | **GWDEP 6.27 → "6"** (정수 자릿수 이하 절단) |

GWTEMP/EC는 원래 정수라 우연히 일치, GWDEP만 손실 노출.

## 2. 표준 기준 (정답지)

### 2-1. 표준화 문서 — 보조망 4 Target 테이블

`dev_plan/2026_04/06/표준화_컬럼매핑.md` "기존 bojo-loader (표준화 필요)" 섹션 = 환경부 표준 (Internal Oracle 적용).

| 테이블 | 핵심 컬럼 | type/length |
|---|---|---|
| TM_GD970001 (관측소) | BRNCH_ID | NUMBER(22) PK |
| TM_GD970101 (결과) | RSLT_ID | NUMBER(10) PK |
| **PM_GD970201 (관측자료)** | **OBSRVN_DATA_VL** | **VARCHAR2(20)** ← 측정값 문자열 저장이 표준 |
| TM_GD980002 (Link) | OBSVTR_ID | VARCHAR2(30) PK |

### 2-2. 표준 데이터 흐름 (정합 baseline)

```
외부 PG (double precision)
    ↓
DMZ IF_RSV (PG, Double ✅)
    ↓
DMZ Target (PG, Double ✅)
    ↓
DMZ IF_SND (PG, Double ✅)
    ↓
Internal IF_RSV (Oracle NUMBER) ← Entity 매핑 정밀도가 핵심
    ↓
Internal Target (Oracle PM_GD970201.OBSRVN_DATA_VL VARCHAR2(20))  ← String 변환
```

### 2-3. IF 표준 정책 (사용자 룰)

- **IF 테이블은 표준화 문서에 정의 없음** (외부 → 내부 buffer 영역)
- **IF 의 type 은 Target 정밀도가 손실되지 않도록 Target 기준으로 맞춤**
- 최종 Target = Oracle (환경부 표준 적용)
- 중간 (DMZ) = PG (Double 정합)

## 3. 분석 결과

### 3-1. Internal Target Entity 정합 (검증 결과: 정상)

| Entity | 표준화 문서 정합 |
|---|:-:|
| `TmGd970001` (16 컬럼) | ✅ 모든 컬럼 type/length 일치 |
| `TmGd970101` (18 컬럼) | ✅ 모든 컬럼 type/length 일치 |
| `PmGd970201` (5 컬럼) | ✅ 특히 OBSRVN_DATA_VL = String (VARCHAR2 20) 정합 |
| `TmGd980002` (9 컬럼) | ✅ 모든 컬럼 type/length 일치 |

→ **Target은 표준 그대로 둠. 수정 불필요**.

### 3-2. Internal IF Entity 회귀 (7건)

`infolink-agent-bojo-internal/src/main/java/com/infolink/agent/bojo/entity/iftable/`

#### IfRsvSecObsvdata (관측데이터 IF)

| Field | 현재 | DMZ baseline | Oracle DDL | 결정 |
|---|:-:|:-:|:-:|:-:|
| `gwdep` (지하수위 m) | **Long** | Double | NUMBER | **Double 로 수정** |
| `gwtemp` (지하수온 °C) | **Long** | Double | NUMBER | **Double 로 수정** |
| `ec` (전기전도도 μS/cm) | **Long** | Double | NUMBER | **Double 로 수정** |

#### IfRsvSecJewon (제원 IF)

| Field | 현재 | DMZ baseline | Oracle DDL | 결정 |
|---|:-:|:-:|:-:|:-:|
| `pyogo` (표고/지반고) | **Long** | Double | NUMBER | **Double 로 수정** |
| `guldep` (굴착깊이 m) | **Long** | Double | NUMBER | **Double 로 수정** |
| `guldia` (굴착지름 mm) | **Long** | Double | NUMBER | **Double 로 수정** |
| `casingHeight` (케이싱높이 m) | **Long** | Double | NUMBER | **Double 로 수정** |

### 3-3. 회귀 외 type 차이 (별 사이클 — 본 fix 범위 밖)

| Entity | Field | 현재 | DMZ | 처리 |
|---|---|:-:|:-:|---|
| IfRsvSecJewon | `id` | Long | Integer | 보류 (PK, 영향 적음) |
| IfRsvSecJewon | `well` | Long | Integer | 보류 (관정 번호, 정수 손실 X) |
| IfRsvSecJewon | `insdate`, `regdate` | LocalDateTime | Date | 보류 (시간 부분 손실 가능하나 운영상 영향 검토 필요) |

→ 표준화 문서에도 없고 직접 데이터 손실 일으키지 않음. verify/issues 등록 후 별 사이클.

### 3-4. 원인 commit

`94e2127 feat: bojo-int Entity 전환 Phase 5 완료 — Step 4개 IF 읽기 JPA 전환` (3월). bojo-internal IF 읽기를 JdbcTemplate → JPA 로 전환하면서 entity field type 추정 시 Oracle NUMBER (precision/scale 미지정) 를 Long 으로 잘못 매핑.

## 4. 결론 및 수정 결정

### 4-1. 결론

- **Internal Target = 표준 정합** (수정 불필요)
- **Internal IF = 표준화 문서 없으나, Target 정밀도 보존을 위해 Long → Double 일괄 fix 필요**
- 회귀 7건은 모두 같은 원인 (Phase 5 JPA 전환 시 type 추정 실수). 일괄 fix 가 회귀 룰(`feedback_no_regression_organic`) 정합

### 4-2. 수정하겠다 (S+A 일괄)

**파일 2개**, **field 7개** 변경:

| 파일 | line | 변경 |
|---|:-:|---|
| `infolink-agent-bojo-internal/.../entity/iftable/IfRsvSecObsvdata.java` | 31 | `private Long gwdep;` → `private Double gwdep;` |
| 〃 | 34 | `private Long gwtemp;` → `private Double gwtemp;` |
| 〃 | 37 | `private Long ec;` → `private Double ec;` |
| `infolink-agent-bojo-internal/.../entity/iftable/IfRsvSecJewon.java` | 52 | `private Long pyogo;` → `private Double pyogo;` |
| 〃 | 58 | `private Long guldep;` → `private Double guldep;` |
| 〃 | 61 | `private Long guldia;` → `private Double guldia;` |
| 〃 | 67 | `private Long casingHeight;` → `private Double casingHeight;` |

### 4-3. 사용처 영향 (수정 대상 코드 없음)

- `InternalBojoLoadStep.toDouble(row.get("gwdep"))` — `Number.doubleValue()` 호출 → Long/Double 양쪽 정상 변환
- IfRsvSecJewon 은 현재 Loader 가 직접 적재 안 함 (jewon은 GIMS 마스터 자체 관리, READ ONLY) — entity 사용처 코드 영향 0
- → **Java 코드 수정은 entity 7 line 만**

### 4-4. 후속 절차

1. Entity 2 file fix (7 fields)
2. `cd infolink-agent-bojo-internal && ./gradlew clean build -x test`
3. agent-bojo-internal (8092) 재기동
4. **데이터 cleanup** (잘못 적재된 분 제거):
   ```sql
   -- Internal Oracle (k1m/1111@29004)
   DELETE FROM PM_GD970201 WHERE EXECUTION_ID LIKE 'internal-bojo-loader_%';
   DELETE FROM TM_GD980002;
   UPDATE IF_RSV_SEC_OBSVDATA SET LINK_STATUS='PENDING', EXECUTION_ID=NULL WHERE LINK_STATUS='SUCCESS';
   COMMIT;
   ```
5. 03 Step 5 재실행 (사용자 화면)
6. 추적 검증 2단계 재수행 — IF id=132 `GWDEP=6.27` → PM `OBSRVN_DATA_VL='6.27'` 정합 확인
7. 추적 검증 3단계 (사용자 UI 화면 확인) — Step 5 PASS 선언

## 5. 별 사이클 (본 fix 범위 밖)

- B 우선순위 — IfRsvSecJewon `id`/`well`/`insdate`/`regdate` type 일관성 (DMZ Integer/Date vs Internal Long/LocalDateTime). verify/issues 등록.
- `TmGd970130.ph` (수질) Long — 03 보조망 외 별 테이블. 표준화 문서 비교 후 별 사이클.
- DMZ Target (sec_jewon, sec_obsvdata) entity vs 표준화 — DMZ 는 표준화 X 영역이라 비교 불필요.
- dev_logs 5/7 의 Step 5 기대값 `write≈14897×3` 표기 — 코드 의도 (IF 단위 = 14897) 와 어긋나는 부분. 03 bojo 마무리 시 dev_logs/test_plan 기대값 표기 갱신.
