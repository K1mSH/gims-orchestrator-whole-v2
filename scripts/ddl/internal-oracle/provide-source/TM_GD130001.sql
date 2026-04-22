-- ──────────────────────────────────────────────────────────
-- TM_GD130001 — 영향조사보고서 (이전: TM_GD50001)
-- 대상 API: OPN info_yhjs_info (Type A6)
-- 실행 계정: K1M (NGW 스키마 역할)
-- 표준화: TM_GD50001 → TM_GD130001 (환경부표준 컬럼명)
--
-- 멱등성 원칙: 이미 테이블/컬럼/데이터가 있어도 안전하게 실행
--            ㄴ 다른 Agent가 이미 쓰고 있을 수 있으므로 덮어쓰지 않음
-- ──────────────────────────────────────────────────────────

-- 1. 테이블 생성 (이미 있으면 스킵)
BEGIN
    EXECUTE IMMEDIATE '
        CREATE TABLE TM_GD130001 (
            ISVR_NO              VARCHAR2(10)    NOT NULL,
            LCLGV_CD             VARCHAR2(7),
            ISVR_NM              VARCHAR2(200),
            PRMTV_DATA_INST_NM   VARCHAR2(100),
            DATA_CRTR_YR         VARCHAR2(4),
            PBLCN_MM             VARCHAR2(2),
            ISVR_CCD             VARCHAR2(1),
            PRLG_SN              NUMBER,
            ISVR_DATA_FRM_CD     VARCHAR2(1),
            CLCT_INST_NM         VARCHAR2(100),
            DATA_CLCT_YMD        VARCHAR2(10),
            DATA_INPT_YMD        VARCHAR2(8),
            DATA_RGTR_NM         VARCHAR2(50),
            CONSTRAINT PK_TM_GD130001 PRIMARY KEY (ISVR_NO)
        )
    ';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -955 THEN RAISE; END IF;  -- ORA-00955: 이미 존재 → 무시
END;
/

-- 2. 추적 컬럼 추가 (없는 것만, 실행 계정 상관 없이 ALTER 가능)
DECLARE
    PROCEDURE add_col(p_name VARCHAR2, p_def VARCHAR2) IS
    BEGIN
        EXECUTE IMMEDIATE 'ALTER TABLE TM_GD130001 ADD ' || p_name || ' ' || p_def;
    EXCEPTION
        WHEN OTHERS THEN
            IF SQLCODE != -1430 THEN RAISE; END IF;  -- ORA-01430: 이미 있음 → 무시
    END;
BEGIN
    add_col('LINK_STATUS',  'VARCHAR2(20) DEFAULT ''PENDING''');
    add_col('EXECUTION_ID', 'VARCHAR2(100)');
    add_col('SOURCE_REFS',  'VARCHAR2(4000)');
    add_col('EXTRACTED_AT', 'TIMESTAMP');
    add_col('UPDATED_AT',   'TIMESTAMP');
END;
/

-- 3. NULL인 LINK_STATUS를 PENDING으로 초기화 (기존 데이터 대응)
UPDATE TM_GD130001 SET LINK_STATUS = 'PENDING' WHERE LINK_STATUS IS NULL;

-- 4. 테이블/컬럼 주석 (여러 번 실행해도 안전)
COMMENT ON TABLE TM_GD130001 IS '영향조사보고서';
COMMENT ON COLUMN TM_GD130001.ISVR_NO             IS '영향조사보고서번호';
COMMENT ON COLUMN TM_GD130001.LCLGV_CD            IS '자치단체코드';
COMMENT ON COLUMN TM_GD130001.ISVR_NM             IS '영향조사보고서명';
COMMENT ON COLUMN TM_GD130001.PRMTV_DATA_INST_NM  IS '원시자료제공기관명';
COMMENT ON COLUMN TM_GD130001.DATA_CRTR_YR        IS '자료기준연도';
COMMENT ON COLUMN TM_GD130001.PBLCN_MM            IS '발행월';
COMMENT ON COLUMN TM_GD130001.ISVR_CCD            IS '영향조사보고서분류코드';
COMMENT ON COLUMN TM_GD130001.PRLG_SN             IS '부록일련번호';
COMMENT ON COLUMN TM_GD130001.ISVR_DATA_FRM_CD    IS '영향조사보고서자료형태코드';
COMMENT ON COLUMN TM_GD130001.CLCT_INST_NM        IS '수집기관명';
COMMENT ON COLUMN TM_GD130001.DATA_CLCT_YMD       IS '자료수집일자';
COMMENT ON COLUMN TM_GD130001.DATA_INPT_YMD       IS '자료입력일자';
COMMENT ON COLUMN TM_GD130001.DATA_RGTR_NM        IS '자료등록자명';
COMMENT ON COLUMN TM_GD130001.LINK_STATUS         IS '연계상태 (PENDING/SUCCESS/FAILED)';
COMMENT ON COLUMN TM_GD130001.EXECUTION_ID        IS '실행 ID';
COMMENT ON COLUMN TM_GD130001.SOURCE_REFS         IS '소스 참조';
COMMENT ON COLUMN TM_GD130001.EXTRACTED_AT        IS '추출 시각';
COMMENT ON COLUMN TM_GD130001.UPDATED_AT          IS '갱신 시각';

COMMIT;

-- 최종 상태 확인
SELECT LINK_STATUS, COUNT(*) FROM TM_GD130001 GROUP BY LINK_STATUS;
