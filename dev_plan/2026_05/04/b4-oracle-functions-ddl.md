# B4 Oracle 함수 2종 — Internal Oracle 박기 (사전 준비)

**작성일**: 2026-05-04
**관련 작업**: B4 `WellInfoHandler` (`info_permwell`) 의존 함수 사전 배치
**범위**: Internal Oracle (`gims_orchestrator_inner_oracle`, XEPDB1) **DDL 추가만**. B4 핸들러 구현은 별 사이클.

---

## 1. 목적

B4 `WellInfoHandler` 가 호출하는 Oracle 사용자정의함수 2종을 내부 Oracle 에 미리 박아둠. 핸들러 자체는 후속 사이클에서 작업하더라도, 함수 의존성을 먼저 해결해 향후 작업 진입 시점 단축.

원본 자료: `D:\dev\claude\copySource\oracle_fn\oracle_fn_for_b4.txt` (사용자 제공)

원본 v3 PL/SQL 의 컬럼/테이블 이름 → 우리 표준화 DB 의 이름으로 1:1 치환해서 박는다 (표준화 매핑 문서 `docs/Standardizedtable/_converted/standardized_detail.tsv` 근거).

---

## 2. 표준화 매핑 (변환 매트릭스)

### 2.1 `FN_GD_GET_CMMTNDCODE` — 공통코드 룩업

원본 v3 시그니처:
```sql
FUNCTION FN_GD_GET_CMMTNDCODE(
  in_code_id IN TC_GD00002.UGRWTR_CMMN_GRP_CODE%TYPE,
  in_code    IN TC_GD00002.UGRWTR_CMMN_CODE%TYPE
) RETURN VARCHAR2
```

원본 v3 본문 (의역):
```sql
SELECT CODE_CTNT INTO RESULT
  FROM TC_GD00002
 WHERE UGRWTR_CMMN_GRP_CODE = in_code_id
   AND UGRWTR_CMMN_CODE     = in_code;
```

**컬럼 매핑** (TC_GD00002 → TC_GD00002, **테이블명 동일**):

| 원본 v3 | 표준화 (실제 internal-oracle 컬럼) | 실측 타입 |
|---|---|---|
| `UGRWTR_CMMN_GRP_CODE` | `GROUP_CD_SN` | VARCHAR2(20) |
| `UGRWTR_CMMN_CODE` | `UGWTR_COM_CD` | CHAR(50) |
| `CODE_CTNT` | `CD_CN` | VARCHAR2(500) |

> 표준화 문서상 `GROUP_CD_SN` 정의는 NUMBER(22) 이지만, 현재 internal-oracle 은 NGW_xxxx 문자열 그룹코드 정책으로 VARCHAR2(20) 박혀있음 — **실측 타입에 맞춰 `%TYPE` 참조** (표준화 문서 ↔ 실 DB 정합 검토 별 항목, 본 작업 범위 밖).

### 2.2 `FN_GD_GET_GUBUN` — 조사코드 → 한글명/코드 분기

원본 v3 시그니처:
```sql
FUNCTION FN_GD_GET_GUBUN(
  in_josacode  IN TM_GD10001.JOSACODE%TYPE,
  in_josagubun IN VARCHAR2
) RETURN VARCHAR2
```

**테이블 매핑** (`TM_GD10001` → `TM_GD120001`, 표준화 후 테이블명 변경됨):

| 원본 v3 | 표준화 (실제 internal-oracle) | 실측 타입 |
|---|---|---|
| `TM_GD10001` | `TM_GD120001` | 존재 ✅ |
| `TM_GD10001.JOSACODE` | `TM_GD120001.UGWTR_EXMN_CD` | VARCHAR2(3) |

**함수 본문**: 변경 0 — 본문은 코드값 분기만 하고 테이블 SELECT 안 함. 시그니처 `%TYPE` 만 표준화 컬럼으로 변경.

> 본문의 코드값 (`'1'`, `'2'`, `'104'`, `'211'`, `'105'`, `'102'`, `'101'`, `'213'`, `TO_NUMBER(in_josacode)>10`) 은 v3 비즈니스 로직 그대로 보존. 한글명 (`'허가공'`, `'국가 관측망'`, `'수질 측정망'`, ...) 도 동일.

### 2.3 스키마 정책

원본은 `NGW."FN_GD_GET_*"` (스키마 prefix 포함). 우리 internal-oracle 은 `k1m` 사용자가 NGW 역할 겸함 (`scripts/ddl/internal-oracle/provide-source/00_create_schemas.sql:7` 명시 정책).

→ **`NGW.` prefix 제거하고 `k1m` 스키마에 직접 박음** (다른 객체와 동일 패턴).

---

## 3. 수정 대상 파일

### 3.1 신규

| 파일 | 내용 |
|------|------|
| `scripts/ddl/internal-oracle/provide-source/B4_functions.sql` | 함수 2종 CREATE OR REPLACE + 헤더 코멘트 (작업 의도 / 표준화 매핑 출처 명시) + 검증 SELECT |

### 3.2 수정 0건

코드 / entity / handler 변경 없음. **순수 DB DDL 만**.

---

## 4. DDL 본문 (작성할 SQL 미리보기)

```sql
-- ──────────────────────────────────────────────────────────
-- B4 WellInfoHandler 의존 Oracle 함수 2종
-- 원본: D:\dev\claude\copySource\oracle_fn\oracle_fn_for_b4.txt (NGW v3)
-- 변환: docs/Standardizedtable/_converted/standardized_detail.tsv 기반
--   FN_GD_GET_CMMTNDCODE: TC_GD00002 컬럼 3개만 표준화 이름으로 치환
--   FN_GD_GET_GUBUN:      TM_GD10001 → TM_GD120001 / JOSACODE → UGWTR_EXMN_CD
-- 스키마: k1m (NGW 역할 겸함, NGW. prefix 제거)
-- ──────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION FN_GD_GET_CMMTNDCODE(
  in_code_id IN TC_GD00002.GROUP_CD_SN%TYPE,
  in_code    IN TC_GD00002.UGWTR_COM_CD%TYPE
) RETURN VARCHAR2 IS
  RESULT VARCHAR2(500);
BEGIN
  SELECT CD_CN INTO RESULT
    FROM TC_GD00002
   WHERE GROUP_CD_SN  = in_code_id
     AND UGWTR_COM_CD = in_code;
  RETURN RESULT;
EXCEPTION
  WHEN NO_DATA_FOUND THEN RETURN NULL;
  WHEN OTHERS         THEN RETURN NULL;
END FN_GD_GET_CMMTNDCODE;
/

CREATE OR REPLACE FUNCTION FN_GD_GET_GUBUN(
  in_josacode  IN TM_GD120001.UGWTR_EXMN_CD%TYPE,
  in_josagubun IN VARCHAR2
) RETURN VARCHAR2 IS
  RESULT VARCHAR2(100) := '';
BEGIN
  IF in_josagubun = '1' THEN
    IF    in_josacode = '1'   THEN RESULT := '허가공';
    ELSIF in_josacode = '2'   THEN RESULT := '신고공';
    ELSIF in_josacode = '104' THEN RESULT := '국가 관측망';
    ELSIF in_josacode = '211' THEN RESULT := '수질 측정망';
    ELSIF in_josacode = '105' THEN RESULT := '보조 관측망';
    ELSIF in_josacode = '102' THEN RESULT := '정밀 기초 조사공';
    ELSIF in_josacode = '101' THEN RESULT := '광역 기초 조사공';
    ELSIF in_josacode = '213' THEN RESULT := '영향 조사공';
    ELSIF TO_NUMBER(in_josacode) > 10 THEN RESULT := '지하수 지질 조사공';
    ELSE  RESULT := '기타공';
    END IF;
  ELSE
    IF    in_josacode = '1'   THEN RESULT := '100';
    ELSIF in_josacode = '2'   THEN RESULT := '100';
    ELSIF in_josacode = '104' THEN RESULT := '104';
    ELSIF in_josacode = '211' THEN RESULT := '211';
    ELSIF in_josacode = '105' THEN RESULT := '105';
    ELSIF in_josacode = '102' THEN RESULT := '102';
    ELSIF in_josacode = '101' THEN RESULT := '101';
    ELSIF in_josacode = '213' THEN RESULT := '213';
    ELSIF TO_NUMBER(in_josacode) > 10 THEN RESULT := '101';
    ELSE  RESULT := '100';
    END IF;
  END IF;
  RETURN RESULT;
EXCEPTION
  WHEN NO_DATA_FOUND THEN NULL;
  WHEN OTHERS         THEN RAISE;
END FN_GD_GET_GUBUN;
/

-- 검증
SELECT object_name, status FROM user_objects
 WHERE object_name IN ('FN_GD_GET_CMMTNDCODE','FN_GD_GET_GUBUN');

SELECT FN_GD_GET_CMMTNDCODE('NGW_0003','01') AS r1 FROM DUAL; -- 기대: '생활용'
SELECT FN_GD_GET_CMMTNDCODE('NGW_0013','11') AS r2 FROM DUAL; -- 기대: '가정용'
SELECT FN_GD_GET_GUBUN('104','1')              AS r3 FROM DUAL; -- 기대: '국가 관측망'
SELECT FN_GD_GET_GUBUN('104','0')              AS r4 FROM DUAL; -- 기대: '104'
SELECT FN_GD_GET_GUBUN('99','1')               AS r5 FROM DUAL; -- 기대: '지하수 지질 조사공' (TO_NUMBER>10 분기)
SELECT FN_GD_GET_GUBUN('XX','1')               AS r6 FROM DUAL; -- 기대: '기타공' (TO_NUMBER 실패 분기)
```

> 마지막 케이스 (`'XX'`) — `TO_NUMBER('XX')` 가 ORA-01722 던지면 EXCEPTION 분기 (현재 함수: WHEN OTHERS THEN RAISE) 로 fail 가능. 원본 함수 동작 그대로 보존하므로 그게 v3 동작과 동일. 단순 검증 항목으로 동작 확인 후 처리 방향 결정 필요할 수 있음 — 본 PR 범위 밖, 메모만.

---

## 5. 실행 절차

1. `scripts/ddl/internal-oracle/provide-source/B4_functions.sql` 작성
2. `docker exec gims_orchestrator_inner_oracle bash -c "sqlplus -S k1m/1111@//localhost:1521/XEPDB1 @/tmp/B4_functions.sql"` 또는 stdin redirect
3. `USER_OBJECTS.STATUS = 'VALID'` 확인
4. 검증 SELECT 6건 실측 결과 캡처
5. dev_logs 갱신

---

## 6. 영향 범위

| 항목 | 영향 |
|------|------|
| 기존 코드 | **영향 0** — DB 객체만 추가 |
| 기존 데이터 | 영향 0 — DDL 만, 데이터 변경 없음 |
| 다른 핸들러 (B5/B6/B7/...) | 영향 0 — 함수 호출 안 함 |
| 추후 B4 `WellInfoHandler` 구현 | 의존성 사전 해결 — 핸들러 SQL 에서 바로 호출 가능 |
| 회귀 위험 | 매우 낮음 — 신규 함수 2개, 기존 객체 미변경 |

---

## 7. 미진행 항목 (본 PR 범위 밖)

- B4 `WellInfoHandler` Java 구현 — 별 사이클
- `api_prv_permwell` 엔티티 모듈 위치 정합 (현재 `sync-agent-provide` 에 있음, 다른 핸들러는 `gims-api-provider` 에 있음) — B4 본 구현 시점에 정리
- `oracle_fn_for_b4.txt` → `copySource/` 작업 디렉토리 외부 (`D:\dev\claude\copySource\`) 위치라 본 작업 산출물엔 포함 안 됨. 사용자 자료 보관 영역

---

## 8. 검증 시나리오

| # | 입력 | 함수 | 기대 결과 | 분기 의도 |
|:-:|---|---|---|---|
| 1 | `('NGW_0003','01')` | CMMTNDCODE | `'생활용'` | TC_GD00002 SELECT 정상 (NGW_0003 그룹) |
| 2 | `('NGW_0013','11')` | CMMTNDCODE | `'가정용'` | NGW_0013 그룹 정상 |
| 3 | `('NGW_9999','99')` | CMMTNDCODE | `NULL` | NO_DATA_FOUND → NULL |
| 4 | `('104','1')` | GUBUN | `'국가 관측망'` | 한글명 분기 (gubun=1) |
| 5 | `('104','0')` | GUBUN | `'104'` | 코드 분기 (gubun!=1) |
| 6 | `('99','1')` | GUBUN | `'지하수 지질 조사공'` | TO_NUMBER > 10 분기 |
| 7 | `('1','1')` | GUBUN | `'허가공'` | 정확 매칭 분기 |

---

## 9. 사용자 승인 후 진행

위 계획 OK 시:
1. SQL 파일 작성
2. Oracle 에 적용 (docker exec sqlplus)
3. 검증 시나리오 7건 실행
4. dev_logs/2026_05/2026-05-04.md 에 결과 추가
