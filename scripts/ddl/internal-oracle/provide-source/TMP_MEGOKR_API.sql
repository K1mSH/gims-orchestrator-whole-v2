-- ──────────────────────────────────────────────────────────
-- TMP_MEGOKR_API — MEGOKR 수질검사결과 TMP (이미 PIVOT 완료된 임시 테이블)
-- 대상 API: MEGOKR selectNgw04_01 (Type A7)
-- 실행 계정: K1M (NGW 스키마 역할)
--
-- 컬럼: 149개 (SN + 메타 23개 + WT_* 수질항목 125개)
-- PK: 없음 (원본 존중 — NGW 문서상 전체 NULLABLE, 공식 PK 제약 없음)
--      단 실 데이터상 SN 이 유일 + NOT NULL 조건 만족 → YAML primary-key=SN 으로 로직 동작
-- 추적 컬럼: LINK_STATUS, EXECUTION_ID, SOURCE_REFS, EXTRACTED_AT, UPDATED_AT
--
-- 멱등성 원칙: 이미 테이블/컬럼이 있어도 안전하게 실행
-- ──────────────────────────────────────────────────────────

-- 1. 테이블 생성 (이미 있으면 스킵)
BEGIN
    EXECUTE IMMEDIATE q'[
        CREATE TABLE TMP_MEGOKR_API (
            SN                                  NUMBER(22),
            JOSACODE                            VARCHAR2(3),
            QLTWTR_INSPCT_SN                    NUMBER(22),
            GENNUM                              NUMBER(22),
            INVSTG_YEAR                         VARCHAR2(4),
            ODR                                 NUMBER(22),
            DPH_CL_CODE                         VARCHAR2(1),
            DPH_VALUE                           NUMBER(22),
            WATSMP_DE                           VARCHAR2(8),
            QLTWTR_INSPCT_DE                    VARCHAR2(8),
            DTA_INPUT_DE                        VARCHAR2(8),
            DCSN_DE                             VARCHAR2(8),
            FRST_REGIST_DT                      DATE,
            LAST_CHANGE_DT                      DATE,
            UGRWTR_PRPOS_CODE                   VARCHAR2(2),
            DRNK_AT                             VARCHAR2(1),
            UGRWTR_WQN_INPUT_INSTT_CODE         VARCHAR2(5),
            QLTWTR_INSPCT_IMPRTY_RESN_CTNT      VARCHAR2(4000),
            BRTC_NM                             VARCHAR2(40),
            SIGUN_NM                            VARCHAR2(508),
            EMD_NM                              VARCHAR2(40),
            LI_NM                               VARCHAR2(40),
            ADDR                                VARCHAR2(1000),
            PUBWELL_AT                          VARCHAR2(1),
            WT_TOT_COL_CNTS                     VARCHAR2(100),
            WT_TOT_CLF                          VARCHAR2(100),
            WT_FCL_CFS                          VARCHAR2(100),
            WT_ESC_COL                          VARCHAR2(100),
            WT_PLB                              VARCHAR2(100),
            WT_FLR                              VARCHAR2(100),
            WT_ASN                              VARCHAR2(100),
            WT_SLN                              VARCHAR2(100),
            WT_HDG                              VARCHAR2(100),
            WT_CYA                              VARCHAR2(100),
            WT_AMN_NTG                          VARCHAR2(100),
            WT_NTR_NTG                          VARCHAR2(100),
            WT_CDM                              VARCHAR2(100),
            WT_BOR                              VARCHAR2(100),
            WT_CHR                              VARCHAR2(100),
            WT_PEN                              VARCHAR2(100),
            WT_DZN                              VARCHAR2(100),
            WT_PRT                              VARCHAR2(100),
            WT_FNT                              VARCHAR2(100),
            WT_CBR                              VARCHAR2(100),
            WT_111_TCE                          VARCHAR2(100),
            WT_PCE                              VARCHAR2(100),
            WT_TCE                              VARCHAR2(100),
            WT_DCM                              VARCHAR2(100),
            WT_BEZ                              VARCHAR2(100),
            WT_TLE                              VARCHAR2(100),
            WT_EBZ                              VARCHAR2(100),
            WT_CSL                              VARCHAR2(100),
            WT_011_DRE                          VARCHAR2(100),
            WT_CTC                              VARCHAR2(100),
            WT_012_DBR_003_CRP                  VARCHAR2(100),
            WT_014_DOX                          VARCHAR2(100),
            WT_HDN                              VARCHAR2(100),
            WT_PPC                              VARCHAR2(100),
            WT_SML                              VARCHAR2(100),
            WT_FEV                              VARCHAR2(100),
            WT_COP                              VARCHAR2(100),
            WT_CMC                              VARCHAR2(100),
            WT_DTG                              VARCHAR2(100),
            WT_HID                              VARCHAR2(100),
            WT_ZIC                              VARCHAR2(100),
            WT_CRI                              VARCHAR2(100),
            WT_EVR                              VARCHAR2(100),
            WT_STE                              VARCHAR2(100),
            WT_MGN                              VARCHAR2(100),
            WT_TBD                              VARCHAR2(100),
            WT_SAI                              VARCHAR2(100),
            WT_ALM                              VARCHAR2(100),
            WT_ECD                              VARCHAR2(100),
            WT_OGP                              VARCHAR2(100),
            WT_006_CHR                          VARCHAR2(100),
            WT_HID_LBT                          VARCHAR2(100),
            WT_TDS                              VARCHAR2(100),
            WT_DSO                              VARCHAR2(100),
            WT_ORP                              VARCHAR2(100),
            WT_EHC                              VARCHAR2(100),
            WT_TRT                              VARCHAR2(100),
            WT_NTR                              VARCHAR2(100),
            WT_KAL                              VARCHAR2(100),
            WT_CAL                              VARCHAR2(100),
            WT_MGS                              VARCHAR2(100),
            WT_CLR                              VARCHAR2(100),
            WT_BBN                              VARCHAR2(100),
            WT_CAI                              VARCHAR2(100),
            WT_NTI                              VARCHAR2(100),
            WT_SNT                              VARCHAR2(100),
            WT_BRM                              VARCHAR2(100),
            WT_BRU                              VARCHAR2(100),
            WT_ATM                              VARCHAR2(100),
            WT_SLC                              VARCHAR2(100),
            WT_LTU                              VARCHAR2(100),
            WT_MBD                              VARCHAR2(100),
            WT_VND                              VARCHAR2(100),
            WT_GMN                              VARCHAR2(100),
            WT_CPE                              VARCHAR2(100),
            WT_NKE                              VARCHAR2(100),
            WT_EPN                              VARCHAR2(100),
            WT_PTA                              VARCHAR2(100),
            WT_MST                              VARCHAR2(100),
            WT_CRF                              VARCHAR2(100),
            WT_012_DRE                          VARCHAR2(100),
            WT_TOC                              VARCHAR2(100),
            WT_BTR                              VARCHAR2(100),
            WT_MBE                              VARCHAR2(100),
            WT_SSC                              VARCHAR2(100),
            WT_SDM                              VARCHAR2(100),
            WT_SMN                              VARCHAR2(100),
            WT_SGL                              VARCHAR2(100),
            WT_AHP                              VARCHAR2(100),
            WT_YNE                              VARCHAR2(100),
            WT_NTN                              VARCHAR2(100),
            WT_COI                              VARCHAR2(100),
            WT_CPM                              VARCHAR2(100),
            WT_CTM                              VARCHAR2(100),
            WT_012_DCM                          VARCHAR2(100),
            WT_MTB                              VARCHAR2(100),
            WT_ZCM                              VARCHAR2(100),
            WT_MGM                              VARCHAR2(100),
            WT_MBM                              VARCHAR2(100),
            WT_STM                              VARCHAR2(100),
            WT_BAM                              VARCHAR2(100),
            WT_BSM                              VARCHAR2(100),
            WT_ANM                              VARCHAR2(100),
            WT_NNM                              VARCHAR2(100),
            WT_FRM                              VARCHAR2(100),
            WT_TCL                              VARCHAR2(100),
            WT_TCM                              VARCHAR2(100),
            WT_MTM                              VARCHAR2(100),
            WT_THM                              VARCHAR2(100),
            WT_OPS                              VARCHAR2(100),
            WT_DRO                              VARCHAR2(100),
            WT_GRC                              VARCHAR2(100),
            WT_CBT                              VARCHAR2(100),
            WT_CBD                              VARCHAR2(100),
            WT_PSP                              VARCHAR2(100),
            WT_AMN                              VARCHAR2(100),
            WT_HGS                              VARCHAR2(100),
            WT_SCD                              VARCHAR2(100),
            WT_AMI                              VARCHAR2(100),
            WT_TBC                              VARCHAR2(100),
            WT_URN                              VARCHAR2(100),
            WT_RDN                              VARCHAR2(100),
            WT_FAP                              VARCHAR2(100),
            WT_NAI                              VARCHAR2(100),
            WT_WTL                              VARCHAR2(100)
        )
    ]';
EXCEPTION
    WHEN OTHERS THEN
        IF SQLCODE != -955 THEN RAISE; END IF;  -- ORA-00955: 이미 존재 → 무시
END;
/

-- 2. 추적 컬럼 추가
DECLARE
    PROCEDURE add_col(p_name VARCHAR2, p_def VARCHAR2) IS
    BEGIN
        EXECUTE IMMEDIATE 'ALTER TABLE TMP_MEGOKR_API ADD ' || p_name || ' ' || p_def;
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

-- 3. NULL인 LINK_STATUS 를 PENDING 으로 초기화
UPDATE TMP_MEGOKR_API SET LINK_STATUS = 'PENDING' WHERE LINK_STATUS IS NULL;

-- 4. 테이블/컬럼 주석
COMMENT ON TABLE TMP_MEGOKR_API IS 'MEGOKR 수질검사결과 임시 테이블 (PIVOT 완료)';
COMMENT ON COLUMN TMP_MEGOKR_API.SN                               IS '일련번호';
COMMENT ON COLUMN TMP_MEGOKR_API.JOSACODE                         IS '조사코드';
COMMENT ON COLUMN TMP_MEGOKR_API.QLTWTR_INSPCT_SN                 IS '수질검사일련번호';
COMMENT ON COLUMN TMP_MEGOKR_API.GENNUM                           IS '관정일련번호';
COMMENT ON COLUMN TMP_MEGOKR_API.INVSTG_YEAR                      IS '조사연도';
COMMENT ON COLUMN TMP_MEGOKR_API.ODR                              IS '순번';
COMMENT ON COLUMN TMP_MEGOKR_API.DPH_CL_CODE                      IS '심도분류코드';
COMMENT ON COLUMN TMP_MEGOKR_API.DPH_VALUE                        IS '심도값';
COMMENT ON COLUMN TMP_MEGOKR_API.WATSMP_DE                        IS '채수일자';
COMMENT ON COLUMN TMP_MEGOKR_API.QLTWTR_INSPCT_DE                 IS '수질검사일자';
COMMENT ON COLUMN TMP_MEGOKR_API.DTA_INPUT_DE                     IS '자료입력일자';
COMMENT ON COLUMN TMP_MEGOKR_API.DCSN_DE                          IS '확정일자';
COMMENT ON COLUMN TMP_MEGOKR_API.FRST_REGIST_DT                   IS '최초등록일시';
COMMENT ON COLUMN TMP_MEGOKR_API.LAST_CHANGE_DT                   IS '최종변경일시';
COMMENT ON COLUMN TMP_MEGOKR_API.UGRWTR_PRPOS_CODE                IS '지하수용도코드';
COMMENT ON COLUMN TMP_MEGOKR_API.DRNK_AT                          IS '음용여부';
COMMENT ON COLUMN TMP_MEGOKR_API.UGRWTR_WQN_INPUT_INSTT_CODE      IS '지하수수질입력기관코드';
COMMENT ON COLUMN TMP_MEGOKR_API.QLTWTR_INSPCT_IMPRTY_RESN_CTNT   IS '수질검사불가사유내용';
COMMENT ON COLUMN TMP_MEGOKR_API.BRTC_NM                          IS '시도명';
COMMENT ON COLUMN TMP_MEGOKR_API.SIGUN_NM                         IS '시군구명';
COMMENT ON COLUMN TMP_MEGOKR_API.EMD_NM                           IS '읍면동명';
COMMENT ON COLUMN TMP_MEGOKR_API.LI_NM                            IS '리명';
COMMENT ON COLUMN TMP_MEGOKR_API.ADDR                             IS '주소';
COMMENT ON COLUMN TMP_MEGOKR_API.PUBWELL_AT                       IS '공공관정여부';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_TOT_COL_CNTS                  IS '수질항목 WT_TOT_COL_CNTS (일반세균수)';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_TOT_CLF                       IS '수질항목 WT_TOT_CLF (총대장균군)';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_FCL_CFS                       IS '수질항목 WT_FCL_CFS (분원성대장균군)';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_ESC_COL                       IS '수질항목 WT_ESC_COL (대장균)';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_PLB                           IS '수질항목 WT_PLB (납)';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_FLR                           IS '수질항목 WT_FLR (불소)';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_ASN                           IS '수질항목 WT_ASN (비소)';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_SLN                           IS '수질항목 WT_SLN (셀레늄)';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_HDG                           IS '수질항목 WT_HDG (수소이온농도)';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_CYA                           IS '수질항목 WT_CYA (시안)';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_AMN_NTG                       IS '수질항목 WT_AMN_NTG (암모니아성질소)';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_NTR_NTG                       IS '수질항목 WT_NTR_NTG (질산성질소)';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_CDM                           IS '수질항목 WT_CDM (카드뮴)';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_BOR                           IS '수질항목 WT_BOR (붕소)';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_CHR                           IS '수질항목 WT_CHR (크롬)';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_PEN                           IS '수질항목 WT_PEN';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_DZN                           IS '수질항목 WT_DZN';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_PRT                           IS '수질항목 WT_PRT';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_FNT                           IS '수질항목 WT_FNT';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_CBR                           IS '수질항목 WT_CBR';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_111_TCE                       IS '수질항목 WT_111_TCE';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_PCE                           IS '수질항목 WT_PCE';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_TCE                           IS '수질항목 WT_TCE';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_DCM                           IS '수질항목 WT_DCM';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_BEZ                           IS '수질항목 WT_BEZ';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_TLE                           IS '수질항목 WT_TLE';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_EBZ                           IS '수질항목 WT_EBZ';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_CSL                           IS '수질항목 WT_CSL';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_011_DRE                       IS '수질항목 WT_011_DRE';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_CTC                           IS '수질항목 WT_CTC';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_012_DBR_003_CRP               IS '수질항목 WT_012_DBR_003_CRP';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_014_DOX                       IS '수질항목 WT_014_DOX';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_HDN                           IS '수질항목 WT_HDN (경도)';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_PPC                           IS '수질항목 WT_PPC';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_SML                           IS '수질항목 WT_SML';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_FEV                           IS '수질항목 WT_FEV';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_COP                           IS '수질항목 WT_COP (구리)';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_CMC                           IS '수질항목 WT_CMC';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_DTG                           IS '수질항목 WT_DTG';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_HID                           IS '수질항목 WT_HID';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_ZIC                           IS '수질항목 WT_ZIC';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_CRI                           IS '수질항목 WT_CRI';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_EVR                           IS '수질항목 WT_EVR';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_STE                           IS '수질항목 WT_STE';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_MGN                           IS '수질항목 WT_MGN';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_TBD                           IS '수질항목 WT_TBD (탁도)';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_SAI                           IS '수질항목 WT_SAI';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_ALM                           IS '수질항목 WT_ALM';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_ECD                           IS '수질항목 WT_ECD (전기전도도)';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_OGP                           IS '수질항목 WT_OGP';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_006_CHR                       IS '수질항목 WT_006_CHR';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_HID_LBT                       IS '수질항목 WT_HID_LBT';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_TDS                           IS '수질항목 WT_TDS';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_DSO                           IS '수질항목 WT_DSO';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_ORP                           IS '수질항목 WT_ORP';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_EHC                           IS '수질항목 WT_EHC';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_TRT                           IS '수질항목 WT_TRT';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_NTR                           IS '수질항목 WT_NTR';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_KAL                           IS '수질항목 WT_KAL';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_CAL                           IS '수질항목 WT_CAL (칼슘)';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_MGS                           IS '수질항목 WT_MGS (마그네슘)';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_CLR                           IS '수질항목 WT_CLR (색도)';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_BBN                           IS '수질항목 WT_BBN';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_CAI                           IS '수질항목 WT_CAI';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_NTI                           IS '수질항목 WT_NTI';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_SNT                           IS '수질항목 WT_SNT';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_BRM                           IS '수질항목 WT_BRM';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_BRU                           IS '수질항목 WT_BRU';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_ATM                           IS '수질항목 WT_ATM';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_SLC                           IS '수질항목 WT_SLC';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_LTU                           IS '수질항목 WT_LTU';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_MBD                           IS '수질항목 WT_MBD';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_VND                           IS '수질항목 WT_VND';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_GMN                           IS '수질항목 WT_GMN';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_CPE                           IS '수질항목 WT_CPE';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_NKE                           IS '수질항목 WT_NKE';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_EPN                           IS '수질항목 WT_EPN';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_PTA                           IS '수질항목 WT_PTA';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_MST                           IS '수질항목 WT_MST';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_CRF                           IS '수질항목 WT_CRF';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_012_DRE                       IS '수질항목 WT_012_DRE';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_TOC                           IS '수질항목 WT_TOC (총유기탄소)';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_BTR                           IS '수질항목 WT_BTR';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_MBE                           IS '수질항목 WT_MBE';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_SSC                           IS '수질항목 WT_SSC';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_SDM                           IS '수질항목 WT_SDM';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_SMN                           IS '수질항목 WT_SMN';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_SGL                           IS '수질항목 WT_SGL';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_AHP                           IS '수질항목 WT_AHP';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_YNE                           IS '수질항목 WT_YNE';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_NTN                           IS '수질항목 WT_NTN';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_COI                           IS '수질항목 WT_COI';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_CPM                           IS '수질항목 WT_CPM';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_CTM                           IS '수질항목 WT_CTM';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_012_DCM                       IS '수질항목 WT_012_DCM';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_MTB                           IS '수질항목 WT_MTB';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_ZCM                           IS '수질항목 WT_ZCM';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_MGM                           IS '수질항목 WT_MGM';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_MBM                           IS '수질항목 WT_MBM';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_STM                           IS '수질항목 WT_STM';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_BAM                           IS '수질항목 WT_BAM';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_BSM                           IS '수질항목 WT_BSM';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_ANM                           IS '수질항목 WT_ANM';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_NNM                           IS '수질항목 WT_NNM';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_FRM                           IS '수질항목 WT_FRM';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_TCL                           IS '수질항목 WT_TCL';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_TCM                           IS '수질항목 WT_TCM';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_MTM                           IS '수질항목 WT_MTM';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_THM                           IS '수질항목 WT_THM';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_OPS                           IS '수질항목 WT_OPS';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_DRO                           IS '수질항목 WT_DRO';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_GRC                           IS '수질항목 WT_GRC';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_CBT                           IS '수질항목 WT_CBT';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_CBD                           IS '수질항목 WT_CBD';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_PSP                           IS '수질항목 WT_PSP';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_AMN                           IS '수질항목 WT_AMN';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_HGS                           IS '수질항목 WT_HGS';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_SCD                           IS '수질항목 WT_SCD';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_AMI                           IS '수질항목 WT_AMI';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_TBC                           IS '수질항목 WT_TBC';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_URN                           IS '수질항목 WT_URN (우라늄)';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_RDN                           IS '수질항목 WT_RDN (라돈)';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_FAP                           IS '수질항목 WT_FAP';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_NAI                           IS '수질항목 WT_NAI';
COMMENT ON COLUMN TMP_MEGOKR_API.WT_WTL                           IS '수질항목 WT_WTL';
COMMENT ON COLUMN TMP_MEGOKR_API.LINK_STATUS      IS '연계상태 (PENDING/SUCCESS/FAILED)';
COMMENT ON COLUMN TMP_MEGOKR_API.EXECUTION_ID     IS '실행 ID';
COMMENT ON COLUMN TMP_MEGOKR_API.SOURCE_REFS      IS '소스 참조';
COMMENT ON COLUMN TMP_MEGOKR_API.EXTRACTED_AT     IS '추출 시각';
COMMENT ON COLUMN TMP_MEGOKR_API.UPDATED_AT       IS '갱신 시각';

-- 5. 샘플 데이터 3건 (SN 유일성 보장)
MERGE INTO TMP_MEGOKR_API t
USING (SELECT 1 AS SN FROM DUAL) s ON (t.SN = s.SN)
WHEN NOT MATCHED THEN INSERT (
    SN, QLTWTR_INSPCT_SN, GENNUM, INVSTG_YEAR, ODR, WATSMP_DE, QLTWTR_INSPCT_DE,
    UGRWTR_PRPOS_CODE, DRNK_AT, BRTC_NM, SIGUN_NM, EMD_NM, ADDR, PUBWELL_AT,
    WT_TOT_COL_CNTS, WT_TOT_CLF, WT_HDG, WT_ECD, WT_HDN,
    LINK_STATUS
) VALUES (
    1, 1001, 10001, '2024', 1, '20240115', '20240120',
    '01', 'Y', '대전광역시', '유성구', '궁동', '대전광역시 유성구 궁동 123', 'Y',
    '0', '0', '7.2', '450', '120',
    'PENDING'
);
COMMIT;

MERGE INTO TMP_MEGOKR_API t
USING (SELECT 2 AS SN FROM DUAL) s ON (t.SN = s.SN)
WHEN NOT MATCHED THEN INSERT (
    SN, QLTWTR_INSPCT_SN, GENNUM, INVSTG_YEAR, ODR, WATSMP_DE, QLTWTR_INSPCT_DE,
    UGRWTR_PRPOS_CODE, DRNK_AT, BRTC_NM, SIGUN_NM, EMD_NM, ADDR, PUBWELL_AT,
    WT_TOT_COL_CNTS, WT_HDG, WT_ECD,
    LINK_STATUS
) VALUES (
    2, 1002, 10002, '2024', 2, '20240215', '20240220',
    '02', 'N', '부산광역시', '해운대구', '우동', '부산광역시 해운대구 우동 456', 'N',
    '5', '6.8', '380',
    'PENDING'
);
COMMIT;

MERGE INTO TMP_MEGOKR_API t
USING (SELECT 3 AS SN FROM DUAL) s ON (t.SN = s.SN)
WHEN NOT MATCHED THEN INSERT (
    SN, QLTWTR_INSPCT_SN, GENNUM, INVSTG_YEAR, ODR, WATSMP_DE, QLTWTR_INSPCT_DE,
    UGRWTR_PRPOS_CODE, DRNK_AT, BRTC_NM, SIGUN_NM, EMD_NM, PUBWELL_AT,
    WT_TOT_COL_CNTS, WT_HDG,
    LINK_STATUS
) VALUES (
    3, 1003, 10003, '2024', 1, '20240310', '20240315',
    '01', 'Y', '경상북도', '안동시', '송현동', 'Y',
    '0', '7.5',
    'PENDING'
);
COMMIT;

-- 최종 상태 확인
SELECT LINK_STATUS, COUNT(*) FROM TMP_MEGOKR_API GROUP BY LINK_STATUS;
SELECT SN, QLTWTR_INSPCT_SN, BRTC_NM, SIGUN_NM, LINK_STATUS FROM TMP_MEGOKR_API ORDER BY SN;
