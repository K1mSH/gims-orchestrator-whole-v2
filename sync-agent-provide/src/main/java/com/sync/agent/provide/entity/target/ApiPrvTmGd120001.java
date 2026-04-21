package com.sync.agent.provide.entity.target;

import lombok.*;
import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 관정 제공용 테이블 (Type A — A5)
 *
 * 레거시: OPN info_general (관측망 상세)
 * 원본: Oracle TM_GD10001 (레거시) → TM_GD120001 (표준화)
 * 표준화 전체 컬럼 복사 — 외부 노출 컬럼은 api-provider 오퍼레이션에서 설정
 */
@Entity
@Table(name = "api_prv_tm_gd120001")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiPrvTmGd120001 {

    @Id
    @Column(name = "gwel_no")
    private Long gwelNo;

    /** 지하수조사코드 (레거시: JOSACODE) */
    @Column(name = "ugwtr_exmn_cd", length = 3)
    private String ugwtrExmnCd;

    /** 지점명 (레거시: SPOT_NM) */
    @Column(name = "brnch_nm", length = 100)
    private String brnchNm;

    /** 시도명 (레거시: BRTC_NM) */
    @Column(name = "ctpv_nm", length = 40)
    private String ctpvNm;

    /** 시군구명 (레거시: SIGUN_NM) */
    @Column(name = "sgg_nm", length = 40)
    private String sggNm;

    /** 읍면동명 (레거시: EMD_NM) */
    @Column(name = "emd_nm", length = 30)
    private String emdNm;

    /** 리명 (레거시: LI_NM) */
    @Column(name = "li_nm", length = 40)
    private String liNm;

    /** 주소 (레거시: ADDR) */
    @Column(name = "addr", length = 250)
    private String addr;

    /** 좌표계내용 (레거시: WNS_CTNT) */
    @Column(name = "cdsstm_cn", length = 4000)
    private String cdsstmCn;

    /** 원점코드 (레거시: ORIG_CODE) */
    @Column(name = "trgnpt_cd", length = 1)
    private String trgnptCd;

    /** 경도 (레거시: LO_VALUE) */
    @Column(name = "lot", length = 20)
    private String lot;

    /** 위도 (레거시: LA_VALUE) */
    @Column(name = "lat", length = 20)
    private String lat;

    /** X좌표 (레거시: TMX_VALUE) */
    @Column(name = "xcrd")
    private Long xcrd;

    /** Y좌표 (레거시: TMY_VALUE) */
    @Column(name = "ycrd")
    private Long ycrd;

    /** 표고값 (레거시: AL_VALUE) */
    @Column(name = "altd_vl")
    private Long altdVl;

    /** 상세위치내용 (레거시: DTL_LC_CTNT) */
    @Column(name = "dtl_pstn_cn", length = 1000)
    private String dtlPstnCn;

    /** 현장관정번호 (레거시: SPT_GENNUM) */
    @Column(name = "grnds_gwel_no", length = 50)
    private String grndsGwelNo;

    /** 사용자명 (레거시: USR_NM) */
    @Column(name = "user_nm", length = 100)
    private String userNm;

    /** 소유자명 (레거시: OWNER_NM) */
    @Column(name = "ownr_nm", length = 40)
    private String ownrNm;

    /** 사용여부 (레거시: USE_AT) */
    @Column(name = "use_yn", length = 1)
    private String useYn;

    /** 수위자료여부 (레거시: WAL_DTA_AT) */
    @Column(name = "wtlv_data_yn", length = 1)
    private String wtlvDataYn;

    /** 전기탐사자료여부 (레거시: SOUND_DTA_AT) */
    @Column(name = "elcrst_data_yn", length = 1)
    private String elcrstDataYn;

    /** 시추자료여부 (레거시: DLL_DTA_AT) */
    @Column(name = "drll_data_yn", length = 1)
    private String drllDataYn;

    /** 착정자료여부 (레거시: BORE_DTA_AT) */
    @Column(name = "brng_data_yn", length = 1)
    private String brngDataYn;

    /** 양수자료여부 (레거시: WP_DTA_AT) */
    @Column(name = "wpmp_dta_yn", length = 1)
    private String wpmpDtaYn;

    /** 수질자료여부 (레거시: QLTWTR_DTA_AT) */
    @Column(name = "wq_data_yn", length = 1)
    private String wqDataYn;

    /** 원시자료명 (레거시: SDTA_NM) */
    @Column(name = "prmtv_data_nm", length = 100)
    private String prmtvDataNm;

    /** 원시자료기관명 (레거시: SDTA_INSTT_NM) */
    @Column(name = "prmtv_data_inst_nm", length = 100)
    private String prmtvDataInstNm;

    /** 자료기준연도 (레거시: DTA_STDR_YEAR) */
    @Column(name = "data_crtr_yr", length = 4)
    private String dataCrtrYr;

    /** 비고 (레거시: REMARK) */
    @Column(name = "rmrk", length = 500)
    private String rmrk;

    /** 유역명 (레거시: DGR_NM) */
    @Column(name = "bsn_nm", length = 50)
    private String bsnNm;

    /** 법정동코드 (레거시: LEGALDONG_CODE) */
    @Column(name = "stdg_cd", length = 10)
    private String stdgCd;

    /** 관정형태코드 (레거시: WELL_STLE_CODE) */
    @Column(name = "gwel_frm_cd", length = 1)
    private String gwelFrmCd;

    // ── 추적 컬럼 ──

    @Column(name = "execution_id", length = 100)
    private String executionId;

    @Column(name = "source_refs", length = 4000)
    private String sourceRefs;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
