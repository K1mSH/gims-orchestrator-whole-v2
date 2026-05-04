-- ──────────────────────────────────────────────────────────
-- B4 WellInfoHandler 의존 Oracle 함수 2종
--
-- 원본 자료: D:\dev\claude\copySource\oracle_fn\oracle_fn_for_b4.txt (NGW v3 PL/SQL)
-- 변환 근거: docs/Standardizedtable/_converted/standardized_detail.tsv
-- 계획 문서: dev_plan/2026_05/04/b4-oracle-functions-ddl.md
--
-- 변환 매트릭스:
--   FN_GD_GET_CMMTNDCODE — 테이블 동일(TC_GD00002), 컬럼 3개만 표준화 이름으로 치환
--     UGRWTR_CMMN_GRP_CODE → GROUP_CD_SN  (실측 VARCHAR2(20))
--     UGRWTR_CMMN_CODE     → UGWTR_COM_CD (CHAR(50))
--     CODE_CTNT            → CD_CN        (VARCHAR2(500))
--   FN_GD_GET_GUBUN — 시그니처만 표준화 컬럼 참조로 변경, 본문 v3 그대로
--     TM_GD10001.JOSACODE  → TM_GD120001.UGWTR_EXMN_CD (VARCHAR2(3))
--
-- 스키마: k1m (NGW 역할 겸함, NGW. prefix 제거)
-- ──────────────────────────────────────────────────────────

SET SERVEROUTPUT ON
SET LINESIZE 200
SET PAGESIZE 100

PROMPT ============================================================
PROMPT [1/4] FN_GD_GET_CMMTNDCODE 생성
PROMPT ============================================================

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

SHOW ERRORS FUNCTION FN_GD_GET_CMMTNDCODE;

PROMPT ============================================================
PROMPT [2/4] FN_GD_GET_GUBUN 생성
PROMPT ============================================================

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

SHOW ERRORS FUNCTION FN_GD_GET_GUBUN;

PROMPT ============================================================
PROMPT [3/4] 객체 상태 검증 (STATUS = VALID 여야 함)
PROMPT ============================================================

COL OBJECT_NAME FORMAT A30;
COL OBJECT_TYPE FORMAT A12;
COL STATUS      FORMAT A10;
SELECT object_name, object_type, status
  FROM user_objects
 WHERE object_name IN ('FN_GD_GET_CMMTNDCODE','FN_GD_GET_GUBUN')
 ORDER BY object_name;

PROMPT ============================================================
PROMPT [4/4] 동작 검증 시나리오 7건
PROMPT ============================================================

PROMPT --- #1 CMMTNDCODE NGW_0003/01 → '생활용' 기대 ---
SELECT FN_GD_GET_CMMTNDCODE('NGW_0003','01') AS r1 FROM DUAL;

PROMPT --- #2 CMMTNDCODE NGW_0013/11 → '가정용' 기대 ---
SELECT FN_GD_GET_CMMTNDCODE('NGW_0013','11') AS r2 FROM DUAL;

PROMPT --- #3 CMMTNDCODE NGW_9999/99 → NULL 기대 (NO_DATA_FOUND fallback) ---
SELECT FN_GD_GET_CMMTNDCODE('NGW_9999','99') AS r3 FROM DUAL;

PROMPT --- #4 GUBUN '104'/'1' → '국가 관측망' 기대 (한글명 분기) ---
SELECT FN_GD_GET_GUBUN('104','1') AS r4 FROM DUAL;

PROMPT --- #5 GUBUN '104'/'0' → '104' 기대 (코드 분기) ---
SELECT FN_GD_GET_GUBUN('104','0') AS r5 FROM DUAL;

PROMPT --- #6 GUBUN '99'/'1' → '지하수 지질 조사공' 기대 (TO_NUMBER>10 분기) ---
SELECT FN_GD_GET_GUBUN('99','1') AS r6 FROM DUAL;

PROMPT --- #7 GUBUN '1'/'1' → '허가공' 기대 (정확 매칭 분기) ---
SELECT FN_GD_GET_GUBUN('1','1') AS r7 FROM DUAL;

EXIT;
