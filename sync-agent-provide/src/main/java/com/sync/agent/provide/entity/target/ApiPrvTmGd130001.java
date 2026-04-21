package com.sync.agent.provide.entity.target;

import lombok.*;
import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 영향조사보고서 제공용 테이블 (Type A — A6)
 *
 * 레거시: OPN info_yhjs_info (영향조사 상세)
 * 원본: Oracle TM_GD50001 (레거시) → TM_GD130001 (표준화)
 * 표준화 전체 컬럼 복사
 */
@Entity
@Table(name = "api_prv_tm_gd130001")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiPrvTmGd130001 {

    @Id
    @Column(name = "isvr_no", length = 10)
    private String isvrNo;

    /** 지자체코드 (레거시: SF_ASSCT_CODE) */
    @Column(name = "lclgv_cd", length = 7)
    private String lclgvCd;

    /** 영향조사보고서명 (레거시: ISVR_NM) */
    @Column(name = "isvr_nm", length = 200)
    private String isvrNm;

    /** 원시자료기관명 (레거시: SDTA_INSTT_NM) */
    @Column(name = "prmtv_data_inst_nm", length = 100)
    private String prmtvDataInstNm;

    /** 자료기준연도 (레거시: DTA_STDR_YEAR) */
    @Column(name = "data_crtr_yr", length = 4)
    private String dataCrtrYr;

    /** 발행월 (레거시: ISU_MT) */
    @Column(name = "pblcn_mm", length = 2)
    private String pblcnMm;

    /** 영향조사보고서분류코드 (레거시: ISVR_CCD) */
    @Column(name = "isvr_ccd", length = 1)
    private String isvrCcd;

    /** 연장일련번호 (레거시: ET_SN) */
    @Column(name = "prlg_sn")
    private Long prlgSn;

    /** 영향조사보고서자료형태코드 (레거시: ISVR_DTA_STLE_CODE) */
    @Column(name = "isvr_data_frm_cd", length = 1)
    private String isvrDataFrmCd;

    /** 수집기관명 (레거시: COLCT_INSTT_NM) */
    @Column(name = "clct_inst_nm", length = 100)
    private String clctInstNm;

    /** 자료수집일자 (레거시: DTA_COLCT_DE) */
    @Column(name = "data_clct_ymd", length = 10)
    private String dataClctYmd;

    /** 자료입력일자 (레거시: DTA_INPUT_DE) */
    @Column(name = "data_inpt_ymd", length = 8)
    private String dataInptYmd;

    /** 자료등록자명 (레거시: DTA_REGISTER_NM) */
    @Column(name = "data_rgtr_nm", length = 50)
    private String dataRgtrNm;

    // ── 추적 컬럼 ──

    @Column(name = "execution_id", length = 100)
    private String executionId;

    @Column(name = "source_refs", length = 4000)
    private String sourceRefs;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
