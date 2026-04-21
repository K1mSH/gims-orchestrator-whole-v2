package com.sync.agent.provide.entity.target;

import lombok.*;
import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 식약처 정기수질검사 제공용 테이블 (Type B — B13)
 *
 * 레거시: OPN waterQualityMfdsInfo (TM_GD70201 + TM_GD70202 JOIN + 동적 PIVOT)
 * 원본: Oracle TM_GD70201 (레거시) → TM_GD30350 (표준화)
 *       Oracle TM_GD70202 (레거시) → TM_GD30351 (표준화)
 * 전처리: JOIN + 동적 수질항목 PIVOT + 차수 계산
 * PK: wq_insp_sn
 */
@Entity
@Table(name = "api_prv_water_quality_mfds")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiPrvWaterQualityMfds {

    /** 수질검사일련번호 (PK) — 레거시: QLTWTR_INSPCT_SN → WQ_INSP_SN */
    @Id
    @Column(name = "wq_insp_sn")
    private Long wqInspSn;

    /** 원수분류코드 — 레거시: ORGWATR_CL_CODE → ORGWT_CLSF_CD */
    @Column(name = "orgwt_clsf_cd", length = 1)
    private String orgwtClsfCd;

    /** 상호명 — 레거시: CMPNM_NM → CONM_NM */
    @Column(name = "conm_nm", length = 200)
    private String conmNm;

    /** 시도명 — 레거시: BRTC_NM → CTPV_NM */
    @Column(name = "ctpv_nm", length = 40)
    private String ctpvNm;

    /** 시군구명 — 레거시: SIGUN_NM → SGG_NM */
    @Column(name = "sgg_nm", length = 30)
    private String sggNm;

    /** 읍면동명 — 레거시: EMD_NM */
    @Column(name = "emd_nm", length = 30)
    private String emdNm;

    /** 리명 — 레거시: LI_NM */
    @Column(name = "li_nm", length = 40)
    private String liNm;

    /** 관정주소 — 레거시: WELL_ADDR → GWEL_ADDR */
    @Column(name = "gwel_addr", length = 250)
    private String gwelAddr;

    /** 지하수용도코드 — 레거시: UGRWTR_PRPOS_CODE → UGWTR_USG_CD */
    @Column(name = "ugwtr_usg_cd", length = 2)
    private String ugwtrUsgCd;

    /** 조사연도 — 레거시: INVSTG_YEAR (REQUST_DE 앞4자리) */
    @Column(name = "exmn_yr", length = 4)
    private String exmnYr;

    /** 차수 — 레거시: ODR (REQUST_DE 월 기반 계산) */
    @Column(name = "cycl")
    private Integer cycl;

    /** 결과통보일자 — 레거시: RESULT_DSPTH_DE → RSLT_NTFCTN_YMD */
    @Column(name = "rslt_ntfctn_ymd", length = 8)
    private String rsltNtfctnYmd;

    /** 수질검사일자 — 레거시: QLTWTR_INSPCT_DE → WQ_INSP_YMD */
    @Column(name = "wq_insp_ymd", length = 8)
    private String wqInspYmd;

    /** 요청일자 — 레거시: REQUST_DE → DMND_YMD */
    @Column(name = "dmnd_ymd", length = 8)
    private String dmndYmd;

    /** 수질기준용도코드 — 레거시: QLTWTR_STDR_PRPOS_CODE → WQ_CRTR_USG_CD */
    @Column(name = "wq_crtr_usg_cd", length = 2)
    private String wqCrtrUsgCd;

    /** 수질검사결과코드 — 레거시: QLTWTR_INSPCT_RESULT_CODE → WQ_INSP_RSLT_CD */
    @Column(name = "wq_insp_rslt_cd", length = 1)
    private String wqInspRsltCd;

    /** 수질검사기타내용 — 레거시: QLTWTR_INSPCT_ETC_CTNT → WQ_INSP_ETC_CN */
    @Column(name = "wq_insp_etc_cn", length = 100)
    private String wqInspEtcCn;

    /** 수질검사목적내용 — 레거시: QLTWTR_INSPCT_PURPS_CTNT → WQ_INSP_PRPS_CN */
    @Column(name = "wq_insp_prps_cn", length = 100)
    private String wqInspPrpsCn;

    /** 허가신고번호 — 레거시: PRMISN_DCLR_NO → PRMSN_DCLR_NO */
    @Column(name = "prmsn_dclr_no", length = 30)
    private String prmsnDclrNo;

    /** 사용자명 — 레거시: USR_NM */
    @Column(name = "usr_nm", length = 100)
    private String usrNm;

    // ── 동적 수질항목 (주요 항목만 — 추후 확장) ──

    /** 수질항목 0001 */
    @Column(name = "c0001", length = 100)
    private String c0001;

    /** 수질항목 0002 */
    @Column(name = "c0002", length = 100)
    private String c0002;

    /** 수질항목 0005 */
    @Column(name = "c0005", length = 100)
    private String c0005;

    /** 수질항목 0006 */
    @Column(name = "c0006", length = 100)
    private String c0006;

    /** 수질항목 0009 */
    @Column(name = "c0009", length = 100)
    private String c0009;

    /** 수질항목 0012 */
    @Column(name = "c0012", length = 100)
    private String c0012;

    // TODO: 추가 수질항목 컬럼 — iterate 기반 동적 PIVOT이므로 요청 항목에 따라 확장

    // ── 추적 컬럼 ──

    @Column(name = "execution_id", length = 100)
    private String executionId;

    @Column(name = "source_refs", length = 4000)
    private String sourceRefs;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
