package com.sync.agent.bojoint.entity.target;

import javax.persistence.*;
import lombok.*;

@Entity
@Table(name = "TM_GD970130")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(TmGd970130Id.class)
public class TmGd970130 {

    @Id
    @Column(name = "GWEL_NO")
    private Long gwelNo;

    @Id
    @Column(name = "BRNCH_ID")
    private Long brnchId;

    @Column(name = "WTLV_SDND_YN", columnDefinition = "CHAR(1)")
    private String wtlvSdndYn;

    @Column(name = "WTLV_SDNR_YN", columnDefinition = "CHAR(1)")
    private String wtlvSdnrYn;

    @Column(name = "WTLV_NCHNG_YN", columnDefinition = "CHAR(1)")
    private String wtlvNchngYn;

    @Column(name = "WTTM_SDND_YN", columnDefinition = "CHAR(1)")
    private String wttmSdndYn;

    @Column(name = "WTTM_SDNR_YN", columnDefinition = "CHAR(1)")
    private String wttmSdnrYn;

    @Column(name = "WTTM_NCHNG_YN", columnDefinition = "CHAR(1)")
    private String wttmNchngYn;

    @Column(name = "ELCD_SDND_YN", columnDefinition = "CHAR(1)")
    private String elcdSdndYn;

    @Column(name = "ELCD_SDNR_YN", columnDefinition = "CHAR(1)")
    private String elcdSdnrYn;

    @Column(name = "ELCD_NCHNG_YN", columnDefinition = "CHAR(1)")
    private String elcdNchngYn;

    @Column(name = "WTLV_NCHNG_JGMT_YN", columnDefinition = "CHAR(1)")
    private String wtlvNchngJgmtYn;

    @Column(name = "WTTM_NCHNG_JGMT_YN", columnDefinition = "CHAR(1)")
    private String wttmNchngJgmtYn;

    @Column(name = "ELCD_NCHNG_JGMT_YN", columnDefinition = "CHAR(1)")
    private String elcdNchngJgmtYn;

    @Column(name = "WTLV_SAFERT_YN", columnDefinition = "CHAR(1)")
    private String wtlvSafertYn;

    @Column(name = "WTTM_SAFERT_YN", columnDefinition = "CHAR(1)")
    private String wttmSafertYn;

    @Column(name = "ELCD_SAFERT_YN", columnDefinition = "CHAR(1)")
    private String elcdSafertYn;

    @Column(name = "INSTL_DPH_VL")
    private Long instlDphVl;

    @Column(name = "FILE_NM", length = 1000)
    private String fileNm;

    @Column(name = "MAP_URL", length = 250)
    private String mapUrl;

    @Column(name = "UGWTR_BSCS_EXMN_CMPTN_YN", columnDefinition = "CHAR(1)")
    private String ugwtrBscsExmnCmptnYn;

    @Column(name = "STDG_CD", length = 10)
    private String stdgCd;

    @Column(name = "DTL_PSTN_CN", length = 1000)
    private String dtlPstnCn;

    @Column(name = "DPH_VL")
    private Long dphVl;

    @Column(name = "UPPRT_DGG_CALBR")
    private Long upprtDggCalbr;

    @Column(name = "LWRPRT_DGG_CALBR")
    private Long lwrprtDggCalbr;

    @Column(name = "OTSD_CSNG_INSTL_ISEG_CN", length = 100)
    private String otsdCsngInstlIsegCn;

    @Column(name = "OTSD_CSNG_CALBR")
    private Long otsdCsngCalbr;

    @Column(name = "OTSD_CSNG_MQLT_CN", length = 100)
    private String otsdCsngMqltCn;

    @Column(name = "INSD_CSNG_INSTL_ISEG_CN", length = 100)
    private String insdCsngInstlIsegCn;

    @Column(name = "INSD_CSNG_CALBR")
    private Long insdCsngCalbr;

    @Column(name = "INSD_CSNG_MQLT_CN", length = 100)
    private String insdCsngMqltCn;

    @Column(name = "STRENE_INSTL_ISEG_CN", length = 250)
    private String streneInstlIsegCn;

    @Column(name = "STRENE_CALBR")
    private Long streneCalbr;

    @Column(name = "STRENE_MQLT_CN", length = 100)
    private String streneMqltCn;

    @Column(name = "ATMC_OBSRVN_EQPMNT_INSTL_YN", columnDefinition = "CHAR(1)")
    private String atmcObsrvnEqpmntInstlYn;

    @Column(name = "ATMC_OBSRVN_EQPMNT_INSTL_DPTH")
    private Long atmcObsrvnEqpmntInstlDpth;

    @Column(name = "ATMC_OBSRVN_EQPMNT_NM", length = 50)
    private String atmcObsrvnEqpmntNm;

    @Column(name = "WTTM_VL")
    private Long wttmVl;

    @Column(name = "WTLV")
    private Long wtlv;

    @Column(name = "PH")
    private Long ph;

    @Column(name = "ELCD_VL")
    private Long elcdVl;

    @Column(name = "ROK_NM", length = 100)
    private String rokNm;

    @Column(name = "OBSRVN_ARTCL_NM", length = 100)
    private String obsrvnArtclNm;

    @Column(name = "OBSRVN_CYCL_CN", length = 100)
    private String obsrvnCyclCn;

    @Column(name = "HYDCON_CN", length = 100)
    private String hydconCn;

    @Column(name = "REL_OBSVTR_CN", length = 100)
    private String relObsvtrCn;

    @Column(name = "TRSMI_VL", length = 22)
    private String trsmiVl;

    @Column(name = "UGWTR_OBSRVN_MTHD_CD", columnDefinition = "CHAR(1)")
    private String ugwtrObsrvnMthdCd;

    @Column(name = "PRMSN_DCLR_NO", length = 30)
    private String prmsnDclrNo;

    @Column(name = "CSNG_HGT")
    private Long csngHgt;

    @Column(name = "UGWTR_DTL_USG_CD", columnDefinition = "CHAR(2)")
    private String ugwtrDtlUsgCd;

    @Column(name = "DKPP_YN", columnDefinition = "CHAR(1)")
    private String dkppYn;

    @Column(name = "WTLV_CHG_TRND_VL")
    private Long wtlvChgTrndVl;

    @Column(name = "UGWTR_GWEL_TYPE_CD", length = 1)
    private String ugwtrGwelTypeCd;

    @Column(name = "OBSVTR_STD_CD", length = 20)
    private String obsvtrStdCd;

    @Column(name = "EXECUTION_ID")
    private String executionId;

    @Column(name = "SOURCE_REFS", length = 4000)
    private String sourceRefs;

}
