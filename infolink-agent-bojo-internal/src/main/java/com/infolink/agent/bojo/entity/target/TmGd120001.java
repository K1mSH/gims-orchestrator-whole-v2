package com.infolink.agent.bojo.entity.target;

import javax.persistence.*;
import lombok.*;

@Entity
@Table(name = "TM_GD120001")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TmGd120001 {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "GWEL_NO")
    private Long gwelNo;

    @Column(name = "UGWTR_EXMN_CD", length = 3)
    private String ugwtrExmnCd;

    @Column(name = "BRNCH_NM", length = 100)
    private String brnchNm;

    @Column(name = "CTPV_NM", length = 40)
    private String ctpvNm;

    @Column(name = "SGG_NM", length = 40)
    private String sggNm;

    @Column(name = "EMD_NM", length = 30)
    private String emdNm;

    @Column(name = "LI_NM", length = 40)
    private String liNm;

    @Column(name = "ADDR", length = 250)
    private String addr;

    @Column(name = "CDSSTM_CN", length = 4000)
    private String cdsstmCn;

    @Column(name = "TRGNPT_CD", columnDefinition = "CHAR(1)")
    private String trgnptCd;

    @Column(name = "LOT", length = 20)
    private String lot;

    @Column(name = "LAT", length = 20)
    private String lat;

    @Column(name = "XCRD")
    private Long xcrd;

    @Column(name = "YCRD")
    private Long ycrd;

    @Column(name = "ALTD_VL")
    private Long altdVl;

    @Column(name = "DTL_PSTN_CN", length = 1000)
    private String dtlPstnCn;

    @Column(name = "GRNDS_GWEL_NO", length = 50)
    private String grndsGwelNo;

    @Column(name = "USER_NM", length = 100)
    private String userNm;

    @Column(name = "OWNR_NM", length = 40)
    private String ownrNm;

    @Column(name = "USE_YN", columnDefinition = "CHAR(1)")
    private String useYn;

    @Column(name = "WTLV_DATA_YN", columnDefinition = "CHAR(1)")
    private String wtlvDataYn;

    @Column(name = "ELCRST_DATA_YN", columnDefinition = "CHAR(1)")
    private String elcrstDataYn;

    @Column(name = "DRLL_DATA_YN", columnDefinition = "CHAR(1)")
    private String drllDataYn;

    @Column(name = "BRNG_DATA_YN", columnDefinition = "CHAR(1)")
    private String brngDataYn;

    @Column(name = "WPMP_DTA_YN", columnDefinition = "CHAR(1)")
    private String wpmpDtaYn;

    @Column(name = "WQ_DATA_YN", columnDefinition = "CHAR(1)")
    private String wqDataYn;

    @Column(name = "PRMTV_DATA_NM", length = 100)
    private String prmtvDataNm;

    @Column(name = "PRMTV_DATA_INST_NM", length = 100)
    private String prmtvDataInstNm;

    @Column(name = "DATA_CRTR_YR", columnDefinition = "CHAR(4)")
    private String dataCrtrYr;

    @Column(name = "RMRK", length = 500)
    private String rmrk;

    @Column(name = "BSN_NM", length = 50)
    private String bsnNm;

    @Column(name = "STDG_CD", length = 10)
    private String stdgCd;

    @Column(name = "GWEL_FRM_CD", columnDefinition = "CHAR(1)")
    private String gwelFrmCd;

    @Column(name = "EXECUTION_ID")
    private String executionId;

    @Column(name = "SOURCE_REFS", length = 4000)
    private String sourceRefs;

}
