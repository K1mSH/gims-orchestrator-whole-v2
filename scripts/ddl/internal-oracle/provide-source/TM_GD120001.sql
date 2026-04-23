-- ──────────────────────────────────────────────────────────
-- TM_GD120001 — 관정 (이전: TM_GD10001)
-- 대상 API: OPN info_general (Type A5)
-- 실행 계정: K1M (NGW 스키마 역할)
-- 표준화: TM_GD10001 → TM_GD120001 (환경부표준 컬럼명)
--
-- merge-key: GWEL_NO (관정번호) — NOT NULL, PK로 지정
-- 멱등성 원칙: 이미 테이블/컬럼/데이터가 있어도 안전하게 실행
-- ──────────────────────────────────────────────────────────

-- 1. 테이블 생성 (이미 있으면 스킵)
BEGIN
    EXECUTE IMMEDIATE '
        CREATE TABLE TM_GD120001 (
            GWEL_NO               NUMBER(22)   NOT NULL,
            UGWTR_EXMN_CD         VARCHAR2(3),
            BRNCH_NM              VARCHAR2(100),
            CTPV_NM               VARCHAR2(40),
            SGG_NM                VARCHAR2(40),
            EMD_NM                VARCHAR2(30),
            LI_NM                 VARCHAR2(40),
            ADDR                  VARCHAR2(250),
            CDSSTM_CN             VARCHAR2(4000),
            TRGNPT_CD             CHAR(1),
            LOT                   VARCHAR2(20),
            LAT                   VARCHAR2(20),
            XCRD                  NUMBER(10),
            YCRD                  NUMBER(10),
            ALTD_VL               NUMBER(10),
            DTL_PSTN_CN           VARCHAR2(1000),
            GRNDS_GWEL_NO         VARCHAR2(50),
            USER_NM               VARCHAR2(100),
            OWNR_NM               VARCHAR2(40),
            USE_YN                CHAR(1),
            WTLV_DATA_YN          CHAR(1),
            ELCRST_DATA_YN        CHAR(1),
            DRLL_DATA_YN          CHAR(1),
            BRNG_DATA_YN          CHAR(1),
            WPMP_DTA_YN           CHAR(1),
            WQ_DATA_YN            CHAR(1),
            PRMTV_DATA_NM         VARCHAR2(100),
            PRMTV_DATA_INST_NM    VARCHAR2(100),
            DATA_CRTR_YR          CHAR(4),
            RMRK                  VARCHAR2(500),
            BSN_NM                VARCHAR2(50),
            STDG_CD               VARCHAR2(10),
            GWEL_FRM_CD           CHAR(1),
            CONSTRAINT PK_TM_GD120001 PRIMARY KEY (GWEL_NO)
        )
    ';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -955 THEN RAISE; END IF;
END;
/

-- 2. 추적 컬럼 추가
DECLARE
    PROCEDURE add_col(p_name VARCHAR2, p_def VARCHAR2) IS
    BEGIN
        EXECUTE IMMEDIATE 'ALTER TABLE TM_GD120001 ADD ' || p_name || ' ' || p_def;
    EXCEPTION
        WHEN OTHERS THEN
            IF SQLCODE != -1430 THEN RAISE; END IF;
    END;
BEGIN
    add_col('LINK_STATUS',  'VARCHAR2(20) DEFAULT ''PENDING''');
    add_col('EXECUTION_ID', 'VARCHAR2(100)');
    add_col('SOURCE_REFS',  'VARCHAR2(4000)');
    add_col('EXTRACTED_AT', 'TIMESTAMP');
    add_col('UPDATED_AT',   'TIMESTAMP');
END;
/

-- 3. NULL인 LINK_STATUS를 PENDING으로 초기화
UPDATE TM_GD120001 SET LINK_STATUS = 'PENDING' WHERE LINK_STATUS IS NULL;

-- 4. 테이블/컬럼 주석
COMMENT ON TABLE TM_GD120001 IS '관정';
COMMENT ON COLUMN TM_GD120001.GWEL_NO             IS '관정번호';
COMMENT ON COLUMN TM_GD120001.UGWTR_EXMN_CD       IS '지하수조사코드';
COMMENT ON COLUMN TM_GD120001.BRNCH_NM            IS '지점명';
COMMENT ON COLUMN TM_GD120001.CTPV_NM             IS '시도명';
COMMENT ON COLUMN TM_GD120001.SGG_NM              IS '시군구명';
COMMENT ON COLUMN TM_GD120001.EMD_NM              IS '읍면동명';
COMMENT ON COLUMN TM_GD120001.LI_NM               IS '리명';
COMMENT ON COLUMN TM_GD120001.ADDR                IS '주소';
COMMENT ON COLUMN TM_GD120001.CDSSTM_CN           IS '좌표계내용';
COMMENT ON COLUMN TM_GD120001.TRGNPT_CD           IS '원점코드';
COMMENT ON COLUMN TM_GD120001.LOT                 IS '경도';
COMMENT ON COLUMN TM_GD120001.LAT                 IS '위도';
COMMENT ON COLUMN TM_GD120001.XCRD                IS 'X좌표';
COMMENT ON COLUMN TM_GD120001.YCRD                IS 'Y좌표';
COMMENT ON COLUMN TM_GD120001.ALTD_VL             IS '표고값';
COMMENT ON COLUMN TM_GD120001.DTL_PSTN_CN         IS '상세위치내용';
COMMENT ON COLUMN TM_GD120001.GRNDS_GWEL_NO       IS '현장관정번호';
COMMENT ON COLUMN TM_GD120001.USER_NM             IS '사용자명';
COMMENT ON COLUMN TM_GD120001.OWNR_NM             IS '소유자명';
COMMENT ON COLUMN TM_GD120001.USE_YN              IS '사용여부';
COMMENT ON COLUMN TM_GD120001.WTLV_DATA_YN        IS '수위자료여부';
COMMENT ON COLUMN TM_GD120001.ELCRST_DATA_YN      IS '전기탐사자료여부';
COMMENT ON COLUMN TM_GD120001.DRLL_DATA_YN        IS '시추자료여부';
COMMENT ON COLUMN TM_GD120001.BRNG_DATA_YN        IS '착정자료여부';
COMMENT ON COLUMN TM_GD120001.WPMP_DTA_YN         IS '양수자료여부';
COMMENT ON COLUMN TM_GD120001.WQ_DATA_YN          IS '수질자료여부';
COMMENT ON COLUMN TM_GD120001.PRMTV_DATA_NM       IS '원시자료명';
COMMENT ON COLUMN TM_GD120001.PRMTV_DATA_INST_NM  IS '원시자료기관명';
COMMENT ON COLUMN TM_GD120001.DATA_CRTR_YR        IS '자료기준연도';
COMMENT ON COLUMN TM_GD120001.RMRK                IS '비고';
COMMENT ON COLUMN TM_GD120001.BSN_NM              IS '유역명';
COMMENT ON COLUMN TM_GD120001.STDG_CD             IS '법정동코드';
COMMENT ON COLUMN TM_GD120001.GWEL_FRM_CD         IS '관정형태코드';
COMMENT ON COLUMN TM_GD120001.LINK_STATUS         IS '연계상태 (PENDING/SUCCESS/FAILED)';
COMMENT ON COLUMN TM_GD120001.EXECUTION_ID        IS '실행 ID';
COMMENT ON COLUMN TM_GD120001.SOURCE_REFS         IS '소스 참조';
COMMENT ON COLUMN TM_GD120001.EXTRACTED_AT        IS '추출 시각';
COMMENT ON COLUMN TM_GD120001.UPDATED_AT          IS '갱신 시각';

-- 5. 샘플 데이터 4건 (서로 다른 관측소, PENDING)
MERGE INTO TM_GD120001 t USING (SELECT 10001 AS GWEL_NO FROM DUAL) s ON (t.GWEL_NO = s.GWEL_NO)
WHEN NOT MATCHED THEN INSERT (
    GWEL_NO, UGWTR_EXMN_CD, BRNCH_NM, CTPV_NM, SGG_NM, EMD_NM, LI_NM, ADDR,
    LOT, LAT, XCRD, YCRD, ALTD_VL,
    USER_NM, OWNR_NM, USE_YN,
    WTLV_DATA_YN, WQ_DATA_YN, BRNG_DATA_YN,
    PRMTV_DATA_NM, DATA_CRTR_YR, BSN_NM, STDG_CD, GWEL_FRM_CD, LINK_STATUS
) VALUES (
    s.GWEL_NO, '001', '노원 관측소', '서울특별시', '노원구', '상계동', NULL, '서울특별시 노원구 상계동 123',
    '127.0543', '37.6552', 210000, 455000, 35,
    '서울시', '국토부', 'Y',
    'Y', 'Y', 'Y',
    '국가지하수정보센터', '2024', '한강유역', '1117010100', '1', 'PENDING'
);
COMMIT;

MERGE INTO TM_GD120001 t USING (SELECT 10002 AS GWEL_NO FROM DUAL) s ON (t.GWEL_NO = s.GWEL_NO)
WHEN NOT MATCHED THEN INSERT (
    GWEL_NO, UGWTR_EXMN_CD, BRNCH_NM, CTPV_NM, SGG_NM, EMD_NM, ADDR,
    LOT, LAT, ALTD_VL, USE_YN, WQ_DATA_YN,
    PRMTV_DATA_NM, DATA_CRTR_YR, BSN_NM, LINK_STATUS
) VALUES (
    s.GWEL_NO, '002', '해운대 관측소', '부산광역시', '해운대구', '우동', '부산광역시 해운대구 우동 456',
    '129.1618', '35.1626', 12, 'Y', 'N',
    '낙동강청', '2024', '낙동강유역', 'PENDING'
);
COMMIT;

MERGE INTO TM_GD120001 t USING (SELECT 10003 AS GWEL_NO FROM DUAL) s ON (t.GWEL_NO = s.GWEL_NO)
WHEN NOT MATCHED THEN INSERT (
    GWEL_NO, UGWTR_EXMN_CD, BRNCH_NM, CTPV_NM, SGG_NM, EMD_NM, ADDR,
    LOT, LAT, ALTD_VL, USE_YN, WTLV_DATA_YN,
    DATA_CRTR_YR, GWEL_FRM_CD, LINK_STATUS
) VALUES (
    s.GWEL_NO, '003', '유성 관측소', '대전광역시', '유성구', '궁동', '대전광역시 유성구 궁동 789',
    '127.3504', '36.3637', 55, 'Y', 'Y',
    '2024', '2', 'PENDING'
);
COMMIT;

MERGE INTO TM_GD120001 t USING (SELECT 10004 AS GWEL_NO FROM DUAL) s ON (t.GWEL_NO = s.GWEL_NO)
WHEN NOT MATCHED THEN INSERT (
    GWEL_NO, UGWTR_EXMN_CD, BRNCH_NM, CTPV_NM, SGG_NM, ADDR, USE_YN,
    DATA_CRTR_YR, LINK_STATUS
) VALUES (
    s.GWEL_NO, '004', '안동 관측소', '경상북도', '안동시', '경상북도 안동시 송현동', 'N',
    '2023', 'PENDING'
);
COMMIT;

-- 최종 상태 확인
SELECT LINK_STATUS, COUNT(*) FROM TM_GD120001 GROUP BY LINK_STATUS;
SELECT GWEL_NO, BRNCH_NM, CTPV_NM, SGG_NM, LINK_STATUS FROM TM_GD120001 ORDER BY GWEL_NO;
