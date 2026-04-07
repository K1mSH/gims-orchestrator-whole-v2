# I5: UseLoadStep 구현 계획

> 작성일: 2026-04-07
> 선행: IF_RSV_USE_LEGACY_DATA, IF_RSV_USE_STATUS_DATA 존재 (RCV 완료)
> 신규 Agent: internal-use-loader (auto-discover 등록 테스트 겸용)

## 목적

이용량 데이터(시간자료/일자료/수신현황/관측데이터)를 IF 테이블에서 GIMS 타겟 테이블로 적재한다.

## 소스 → 타겟

```
IF_RSV_USE_LEGACY_DATA
  │
  ├─→ PM_GD111021 (이용량시간자료)  — MERGE (BRNCH_ID + OBSRVN_DT)
  ├─→ PM_GD111022 (이용량일자료)    — MERGE (BRNCH_ID + OBSRVN_YMD), 일 집계
  └─→ TM_GD111024 (최근수신현황)    — MERGE (BRNCH_ID), 후처리 (MAX일시)

IF_RSV_USE_STATUS_DATA
  │
  └─→ TM_GD111025 (이용량관측데이터) — INSERT (SN)
```

## IF 소스 컬럼

### IF_RSV_USE_LEGACY_DATA

| 컬럼 | 용도 |
|------|------|
| ID | PK |
| SN | 일련번호 (배치 추적) |
| TELNO | 전화번호 → TM_GD111010.TELNO → BRNCH_ID 확보 |
| OBSR_DT | 관측일시 (TIMESTAMP) |
| LAST_MEASURE_VALUE | 최종측정값 |
| USGQTY | 사용량 (**음수 → 0 변환**) |
| LINK_STATUS | PENDING → SUCCESS/FAILED |

### IF_RSV_USE_STATUS_DATA

| 컬럼 | 용도 |
|------|------|
| ID | PK |
| SN | 일련번호 |
| TELNO | 전화번호 |
| OBSR_DT | 관측일시 |
| LAST_CHANGE_DT | 최종변경일시 |
| LINK_STATUS | PENDING → SUCCESS/FAILED |

## 타겟 테이블 (4개, 전부 신규 DDL)

### PM_GD111021 (이용량시간자료)

| 컬럼 | 타입 | NN | 값 |
|------|------|:--:|-----|
| BRNCH_ID | NUMBER(22) | Y | TELNO → TM_GD111010에서 조회 |
| OBSRVN_DT | DATE | Y | OBSR_DT |
| LAST_MSRMT_VL | NUMBER(10) | | LAST_MEASURE_VALUE |
| USE_QNT | NUMBER(22) | | USGQTY (음수→0) |
| EXECUTION_ID | VARCHAR2(255) | | 추적용 |
| SOURCE_REFS | VARCHAR2(4000) | | 추적용 |

PK: BRNCH_ID + OBSRVN_DT

### PM_GD111022 (이용량일자료)

| 컬럼 | 타입 | NN | 값 |
|------|------|:--:|-----|
| BRNCH_ID | NUMBER(22) | Y | TELNO → TM_GD111010 |
| OBSRVN_YMD | VARCHAR2(8) | Y | OBSR_DT에서 YYYYMMDD 추출 |
| LAST_MSRMT_VL | NUMBER(10) | | **일별 MAX**(LAST_MEASURE_VALUE) |
| USE_QNT | NUMBER(22) | | **일별 SUM**(USGQTY) |
| EXECUTION_ID | VARCHAR2(255) | | |
| SOURCE_REFS | VARCHAR2(4000) | | |

PK: BRNCH_ID + OBSRVN_YMD

### TM_GD111024 (이용량최근수신현황)

| 컬럼 | 타입 | NN | 값 |
|------|------|:--:|-----|
| BRNCH_ID | NUMBER(22) | Y | PK |
| OBSRVN_DT | DATE | Y | MAX(OBSRVN_DT) from PM_GD111021 |
| EXECUTION_ID | VARCHAR2(255) | | |
| SOURCE_REFS | VARCHAR2(4000) | | |

PK: BRNCH_ID

### TM_GD111025 (이용량관측데이터)

| 컬럼 | 타입 | NN | 값 |
|------|------|:--:|-----|
| SN | NUMBER(22) | Y | PK — IF의 SN 그대로 |
| TELNO | VARCHAR2(100) | | 전화번호 |
| OBSRVN_DT | DATE | | OBSR_DT |
| LAST_CHG_DT | DATE | | LAST_CHANGE_DT |
| EXECUTION_ID | VARCHAR2(255) | | |
| SOURCE_REFS | VARCHAR2(4000) | | |

PK: SN

## 핵심 로직

### 1. BRNCH_ID 조회 (TELNO 기반)

```sql
SELECT BRNCH_ID FROM TM_GD111010 WHERE TELNO = ?
```
- 캐시 필요 (TELNO → BRNCH_ID)
- 미발견 시 SKIP + 경고

### 2. 음수 → 0 변환

```java
if (usgqty != null && usgqty < 0) usgqty = 0;
```

### 3. PM_GD111021 MERGE (시간자료)

```sql
MERGE INTO PM_GD111021 t
USING (SELECT ? AS BRNCH_ID, ? AS OBSRVN_DT FROM DUAL) s
ON (t.BRNCH_ID = s.BRNCH_ID AND t.OBSRVN_DT = s.OBSRVN_DT)
WHEN MATCHED THEN UPDATE SET LAST_MSRMT_VL = ?, USE_QNT = ?, ...
WHEN NOT MATCHED THEN INSERT ...
```

### 4. PM_GD111022 MERGE (일자료 — 일별 집계)

레거시는 개별 INSERT 후 서브쿼리로 집계했지만, 우리는 **PM_GD111021 INSERT 후 집계 쿼리**로 처리:

```sql
MERGE INTO PM_GD111022 t
USING (
  SELECT BRNCH_ID, TO_CHAR(OBSRVN_DT, 'YYYYMMDD') AS OBSRVN_YMD,
         MAX(LAST_MSRMT_VL) AS LAST_MSRMT_VL,
         SUM(USE_QNT) AS USE_QNT
  FROM PM_GD111021
  WHERE BRNCH_ID = ? AND OBSRVN_DT BETWEEN ? AND ?
  GROUP BY BRNCH_ID, TO_CHAR(OBSRVN_DT, 'YYYYMMDD')
) s ON (t.BRNCH_ID = s.BRNCH_ID AND t.OBSRVN_YMD = s.OBSRVN_YMD)
WHEN MATCHED THEN UPDATE ...
WHEN NOT MATCHED THEN INSERT ...
```

### 5. TM_GD111024 후처리 (최근수신현황)

전체 배치 완료 후 1회 실행:

```sql
MERGE INTO TM_GD111024 t
USING (SELECT BRNCH_ID, MAX(OBSRVN_DT) AS OBSRVN_DT FROM PM_GD111021 GROUP BY BRNCH_ID) s
ON (t.BRNCH_ID = s.BRNCH_ID)
WHEN MATCHED THEN UPDATE SET OBSRVN_DT = s.OBSRVN_DT, ...
WHEN NOT MATCHED THEN INSERT ...
```

### 6. TM_GD111025 INSERT (관측데이터)

IF_RSV_USE_STATUS_DATA에서 직접 INSERT (MERGE 불필요, SN이 PK):

```sql
INSERT INTO TM_GD111025 (SN, TELNO, OBSRVN_DT, LAST_CHG_DT, EXECUTION_ID, SOURCE_REFS)
VALUES (?, ?, ?, ?, ?, ?)
```

## 실행 순서 (Step 내부)

```
1. IF_RSV_USE_LEGACY_DATA 조회 (PENDING)
2. 건별: TELNO → BRNCH_ID 확보 → 음수변환 → PM_GD111021 MERGE
3. 영향받은 (BRNCH_ID, 날짜) 그룹별: PM_GD111022 집계 MERGE
4. TM_GD111024 후처리 (전체 MAX 일시)
5. IF_RSV_USE_LEGACY_DATA 상태 업데이트

6. IF_RSV_USE_STATUS_DATA 조회 (PENDING)
7. 건별: TM_GD111025 INSERT
8. IF_RSV_USE_STATUS_DATA 상태 업데이트
```

## 수정 대상 파일

| 파일 | 변경 | 비고 |
|------|------|------|
| `scripts/oracle-init-use-target.sql` | **신규** — 4개 타겟 DDL | PM_GD111021, PM_GD111022, TM_GD111024, TM_GD111025 |
| `UseLoadStep.java` | **신규** — I5 Step 구현 | 2개 IF 소스, 4개 타겟 |
| `UseLoadStepFactory.java` | **신규** — Factory | use-load factory-key |
| `internal-use-loader.yml` | **신규** — Agent YAML | 신규 Agent |

## YAML

```yaml
agent-code: internal-use-loader
type: LOADER

steps:
  - id: use-load
    name: 이용량 적재
    factory-key: use-load
    source-table: [IF_RSV_USE_LEGACY_DATA, IF_RSV_USE_STATUS_DATA]
    target-table: [PM_GD111021, PM_GD111022, TM_GD111024, TM_GD111025]
```

## 구현 순서

```
1. DDL: 4개 타겟 테이블 생성 (Oracle 29004)
2. UseLoadStep.java 구현
3. UseLoadStepFactory.java 구현
4. internal-use-loader.yml 작성
5. 빌드 + 기동
6. auto-discover로 Agent 등록 (신규 Agent 테스트)
7. E2E 테스트
```

## 주의사항

- BRNCH_ID 미발견 (TM_GD111010에 TELNO 없음) → SKIP + 경고
- PM_GD111022는 I3(JejuFacilityLoadStep)과 **테이블 공유** — 컬럼 호환 확인 필요
- 레거시의 SN 배치 윈도우(10000) 방식은 폐기 → IF 테이블의 LINK_STATUS 기반으로 전환
- 레거시의 TM_GD70004 (마지막 SN 추적) 테이블도 폐기 → LINK_STATUS가 대체
