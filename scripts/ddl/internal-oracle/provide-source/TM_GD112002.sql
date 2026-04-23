-- ──────────────────────────────────────────────────────────
-- TM_GD112002 — 드림서비스_공공관정 (이전: WT_DREAM_PERMWELL_PUBLIC)
-- 대상 API: MEGOKR selectNgw09/09_01 (Type A2/A3)
-- 실행 계정: K1M (NGW 스키마 역할)
-- 표준화: WT_DREAM_PERMWELL_PUBLIC → TM_GD32002 → TM_GD112002 (환경부표준 컬럼명)
--
-- merge-key: PRMSN_DCLR_NO (허가신고번호) — NULLABLE (표준화상), PK 제약 없음
-- 멱등성 원칙: 이미 테이블/컬럼/데이터가 있어도 안전하게 실행
-- ──────────────────────────────────────────────────────────

-- 1. 테이블 생성 (이미 있으면 스킵)
BEGIN
    EXECUTE IMMEDIATE '
        CREATE TABLE TM_GD112002 (
            LINK_TRSM_SGG_CD      VARCHAR2(7),
            PRMSN_DCLR_NO         VARCHAR2(30),
            PRMSN_DCLR_FRM_CD     CHAR(1),
            YR_SE                 CHAR(4),
            RGN_CD                VARCHAR2(10),
            CTPV_NM               VARCHAR2(40),
            SGG_NM                VARCHAR2(40),
            EMD_NM                VARCHAR2(30),
            LI_NM                 NVARCHAR2(40),
            MTN                   CHAR(1),
            BNJ                   VARCHAR2(20),
            HO                    VARCHAR2(10),
            UGWTR_USG             VARCHAR2(20),
            UGWTR_DTL_USG_CD      CHAR(2),
            DKPP_YN               CHAR(1),
            LAT_DG                VARCHAR2(20),
            LAT_MI                VARCHAR2(20),
            LAT_SS                VARCHAR2(20),
            LOT_DG                VARCHAR2(20),
            LOT_MI                VARCHAR2(20),
            LOT_SS                VARCHAR2(20),
            DPH_VL                NUMBER(10),
            DGG_CALBR             NUMBER(10),
            DELP_DIA              NUMBER(10),
            PUMP_HRSPW            NUMBER(22),
            WTRIT_PLAN_QTR        NUMBER(5),
            WPMP_ABLT             NUMBER(10),
            YR_USQTY              NUMBER(22),
            PUB_PRVTEST_SE        CHAR(1),
            WQ_INSP_YMD           VARCHAR2(8),
            WQ_INSP_RSLT          NVARCHAR2(100),
            PNU                   VARCHAR2(19),
            XCRD                  NUMBER(10),
            YCRD                  NUMBER(10)
        )
    ';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -955 THEN RAISE; END IF;  -- ORA-00955: 이미 존재 → 무시
END;
/

-- 2. 추적 컬럼 추가 (없는 것만)
DECLARE
    PROCEDURE add_col(p_name VARCHAR2, p_def VARCHAR2) IS
    BEGIN
        EXECUTE IMMEDIATE 'ALTER TABLE TM_GD112002 ADD ' || p_name || ' ' || p_def;
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
UPDATE TM_GD112002 SET LINK_STATUS = 'PENDING' WHERE LINK_STATUS IS NULL;

-- 4. 테이블/컬럼 주석 (여러 번 실행해도 안전)
COMMENT ON TABLE TM_GD112002 IS '드림서비스_공공관정';
COMMENT ON COLUMN TM_GD112002.LINK_TRSM_SGG_CD     IS '연계전송시군구코드';
COMMENT ON COLUMN TM_GD112002.PRMSN_DCLR_NO        IS '허가신고번호';
COMMENT ON COLUMN TM_GD112002.PRMSN_DCLR_FRM_CD    IS '허가신고형태코드';
COMMENT ON COLUMN TM_GD112002.YR_SE                IS '연도구분';
COMMENT ON COLUMN TM_GD112002.RGN_CD               IS '지역코드';
COMMENT ON COLUMN TM_GD112002.CTPV_NM              IS '시도명';
COMMENT ON COLUMN TM_GD112002.SGG_NM               IS '시군구명';
COMMENT ON COLUMN TM_GD112002.EMD_NM               IS '읍면동명';
COMMENT ON COLUMN TM_GD112002.LI_NM                IS '리명';
COMMENT ON COLUMN TM_GD112002.MTN                  IS '산';
COMMENT ON COLUMN TM_GD112002.BNJ                  IS '번지';
COMMENT ON COLUMN TM_GD112002.HO                   IS '호';
COMMENT ON COLUMN TM_GD112002.UGWTR_USG            IS '지하수용도';
COMMENT ON COLUMN TM_GD112002.UGWTR_DTL_USG_CD     IS '지하수상세용도코드';
COMMENT ON COLUMN TM_GD112002.DKPP_YN              IS '음용여부';
COMMENT ON COLUMN TM_GD112002.LAT_DG               IS '위도도';
COMMENT ON COLUMN TM_GD112002.LAT_MI               IS '위도분';
COMMENT ON COLUMN TM_GD112002.LAT_SS               IS '위도초';
COMMENT ON COLUMN TM_GD112002.LOT_DG               IS '경도도';
COMMENT ON COLUMN TM_GD112002.LOT_MI               IS '경도분';
COMMENT ON COLUMN TM_GD112002.LOT_SS               IS '경도초';
COMMENT ON COLUMN TM_GD112002.DPH_VL               IS '심도값';
COMMENT ON COLUMN TM_GD112002.DGG_CALBR            IS '굴착구경';
COMMENT ON COLUMN TM_GD112002.DELP_DIA             IS '토출관직경';
COMMENT ON COLUMN TM_GD112002.PUMP_HRSPW           IS '펌프마력';
COMMENT ON COLUMN TM_GD112002.WTRIT_PLAN_QTR       IS '취수계획분기';
COMMENT ON COLUMN TM_GD112002.WPMP_ABLT            IS '양수능력';
COMMENT ON COLUMN TM_GD112002.YR_USQTY             IS '년사용량';
COMMENT ON COLUMN TM_GD112002.PUB_PRVTEST_SE       IS '공공사설구분';
COMMENT ON COLUMN TM_GD112002.WQ_INSP_YMD          IS '수질검사일자';
COMMENT ON COLUMN TM_GD112002.WQ_INSP_RSLT         IS '수질검사결과';
COMMENT ON COLUMN TM_GD112002.PNU                  IS 'PNU';
COMMENT ON COLUMN TM_GD112002.XCRD                 IS 'X좌표';
COMMENT ON COLUMN TM_GD112002.YCRD                 IS 'Y좌표';
COMMENT ON COLUMN TM_GD112002.LINK_STATUS          IS '연계상태 (PENDING/SUCCESS/FAILED)';
COMMENT ON COLUMN TM_GD112002.EXECUTION_ID         IS '실행 ID';
COMMENT ON COLUMN TM_GD112002.SOURCE_REFS          IS '소스 참조';
COMMENT ON COLUMN TM_GD112002.EXTRACTED_AT         IS '추출 시각';
COMMENT ON COLUMN TM_GD112002.UPDATED_AT           IS '갱신 시각';

-- 5. 샘플 데이터 3건 (서로 다른 지역/허가번호, PENDING 상태)
MERGE INTO TM_GD112002 t USING (
    SELECT 'PWD-2020-0001' AS PRMSN_DCLR_NO FROM DUAL UNION ALL
    SELECT 'PWD-2020-0002' FROM DUAL UNION ALL
    SELECT 'PWD-2020-0003' FROM DUAL
) s ON (t.PRMSN_DCLR_NO = s.PRMSN_DCLR_NO)
WHEN NOT MATCHED THEN INSERT (
    PRMSN_DCLR_NO, LINK_TRSM_SGG_CD, PRMSN_DCLR_FRM_CD, YR_SE, RGN_CD,
    CTPV_NM, SGG_NM, EMD_NM, LI_NM, MTN, BNJ, HO,
    UGWTR_USG, UGWTR_DTL_USG_CD, DKPP_YN,
    LAT_DG, LAT_MI, LAT_SS, LOT_DG, LOT_MI, LOT_SS,
    DPH_VL, DGG_CALBR, DELP_DIA, PUMP_HRSPW, WTRIT_PLAN_QTR, WPMP_ABLT, YR_USQTY,
    PUB_PRVTEST_SE, WQ_INSP_YMD, WQ_INSP_RSLT, PNU, XCRD, YCRD,
    LINK_STATUS
) VALUES (
    s.PRMSN_DCLR_NO, '3020000', '1', '2020', '3020010100',
    '대전광역시', '유성구', '궁동', '궁리', 'N', '1-2', '1',
    '생활용수', '01', 'Y',
    '36', '21', '30', '127', '20', '45',
    100, 150, 50, 5, 20, 30, 2000,
    '1', '20200115', '적합', '3020010100100020001', 240000, 450000,
    'PENDING'
);
COMMIT;

-- 2, 3번째 레코드는 별도 값으로 개별 INSERT (지역 다양화)
MERGE INTO TM_GD112002 t USING (SELECT 'PWD-2020-0002' AS PRMSN_DCLR_NO FROM DUAL) s
ON (t.PRMSN_DCLR_NO = s.PRMSN_DCLR_NO AND t.CTPV_NM = '부산광역시')
WHEN NOT MATCHED THEN INSERT (
    PRMSN_DCLR_NO, CTPV_NM, SGG_NM, EMD_NM, LI_NM, UGWTR_USG, DKPP_YN,
    DPH_VL, DGG_CALBR, PUMP_HRSPW, WPMP_ABLT, PUB_PRVTEST_SE, LINK_STATUS
) VALUES (
    s.PRMSN_DCLR_NO, '부산광역시', '해운대구', '우동', '중리', '공업용수', 'N',
    120, 200, 7, 3000, '2', 'PENDING'
);
COMMIT;

MERGE INTO TM_GD112002 t USING (SELECT 'PWD-2020-0003' AS PRMSN_DCLR_NO FROM DUAL) s
ON (t.PRMSN_DCLR_NO = s.PRMSN_DCLR_NO AND t.CTPV_NM = '서울특별시')
WHEN NOT MATCHED THEN INSERT (
    PRMSN_DCLR_NO, CTPV_NM, SGG_NM, EMD_NM, LI_NM, UGWTR_USG, DKPP_YN,
    DPH_VL, DGG_CALBR, PUMP_HRSPW, WPMP_ABLT, PUB_PRVTEST_SE, LINK_STATUS
) VALUES (
    s.PRMSN_DCLR_NO, '서울특별시', '강남구', '역삼동', NULL, '농업용수', 'N',
    80, 120, 3, 1500, '1', 'PENDING'
);
COMMIT;

-- 최종 상태 확인
SELECT LINK_STATUS, COUNT(*) FROM TM_GD112002 GROUP BY LINK_STATUS;
SELECT PRMSN_DCLR_NO, CTPV_NM, SGG_NM, LINK_STATUS FROM TM_GD112002 ORDER BY PRMSN_DCLR_NO;
