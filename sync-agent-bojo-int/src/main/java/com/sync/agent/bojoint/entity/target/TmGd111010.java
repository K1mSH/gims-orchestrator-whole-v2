package com.sync.agent.bojoint.entity.target;

import java.time.LocalDateTime;
import javax.persistence.*;
import lombok.*;

@Entity
@Table(name = "TM_GD111010")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TmGd111010 {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "BRNCH_ID")
    private Long brnchId;

    @Column(name = "TELNO", length = 100)
    private String telno;

    @Column(name = "EXECUTION_ID")
    private String executionId;

    @Column(name = "SOURCE_REFS", length = 4000)
    private String sourceRefs;

    @Column(name = "INSTL_PSBLTY_FMT_CN", length = 4000)
    private String instlPsbltyFmtCn;

    @Column(name = "GRNDS_GWEL_NO", length = 50)
    private String grndsGwelNo;

    @Column(name = "EXMN_YMD", length = 8)
    private String exmnYmd;

    @Column(name = "EXMNR_NM", length = 30)
    private String exmnrNm;

    @Column(name = "CTPV_NM", length = 40)
    private String ctpvNm;

    @Column(name = "SGG_NM", length = 40)
    private String sggNm;

    @Column(name = "EMD_NM", length = 30)
    private String emdNm;

    @Column(name = "LI_NM", length = 40)
    private String liNm;

    @Column(name = "BNJ", length = 20)
    private String bnj;

    @Column(name = "GRNDS_BNJ_CN", length = 100)
    private String grndsBnjCn;

    @Column(name = "ARND_LOTNO_CN", length = 100)
    private String arndLotnoCn;

    @Column(name = "LDCG_CN", length = 20)
    private String ldcgCn;

    @Column(name = "STDG_CD", length = 10)
    private String stdgCd;

    @Column(name = "PRMSN_DCLR_NO", length = 30)
    private String prmsnDclrNo;

    @Column(name = "SPCITM_CN", length = 1000)
    private String spcitmCn;

    @Column(name = "LAT_DG", length = 20)
    private String latDg;

    @Column(name = "LAT_MI", length = 20)
    private String latMi;

    @Column(name = "LAT_SS", length = 20)
    private String latSs;

    @Column(name = "LOT_DG", length = 20)
    private String lotDg;

    @Column(name = "LOT_MI", length = 20)
    private String lotMi;

    @Column(name = "LOT_SS", length = 20)
    private String lotSs;

    @Column(name = "XCRD")
    private Long xcrd;

    @Column(name = "YCRD")
    private Long ycrd;

    @Column(name = "PRMSN_DCLR_CLSF_CN", length = 20)
    private String prmsnDclrClsfCn;

    @Column(name = "USER_NM", length = 100)
    private String userNm;

    @Column(name = "USER_ADDR", length = 100)
    private String userAddr;

    @Column(name = "GWEL_USE_CN", length = 4000)
    private String gwelUseCn;

    @Column(name = "WTSPL_STTS_CLSF_CN", length = 20)
    private String wtsplSttsClsfCn;

    @Column(name = "EQPMNT_INSTL_YN_CN", length = 10)
    private String eqpmntInstlYnCn;

    @Column(name = "EQPMNT_INSTL_PSBLTY_CN", length = 10)
    private String eqpmntInstlPsbltyCn;

    @Column(name = "EQPMNT_INSTL_IMPS_CN", length = 500)
    private String eqpmntInstlImpsCn;

    @Column(name = "PRMSN_DCLR_USG_CN", length = 100)
    private String prmsnDclrUsgCn;

    @Column(name = "PRMSN_DCLR_DTL_USG_CN", length = 100)
    private String prmsnDclrDtlUsgCn;

    @Column(name = "UGWTR_USG_CN", length = 500)
    private String ugwtrUsgCn;

    @Column(name = "UGWTR_DTL_USG_CN", length = 100)
    private String ugwtrDtlUsgCn;

    @Column(name = "DKPP_YN", columnDefinition = "CHAR(1)")
    private String dkppYn;

    @Column(name = "YR_USQTY_CN", length = 20)
    private String yrUsqtyCn;

    @Column(name = "MAIN_USE_PRD", length = 20)
    private String mainUsePrd;

    @Column(name = "USE_CN", length = 500)
    private String useCn;

    @Column(name = "WTRTRM_CN", length = 8)
    private String wtrtrmCn;

    @Column(name = "RMRK", length = 500)
    private String rmrk;

    @Column(name = "ENG_GRNDS_EXMN_NO", length = 100)
    private String engGrndsExmnNo;

    @Column(name = "FMT_INSTL_CN", length = 1000)
    private String fmtInstlCn;

    @Column(name = "INSTL_YMD", length = 8)
    private String instlYmd;

    @Column(name = "LCLGV_CD", length = 7)
    private String lclgvCd;

    @Column(name = "FRST_REG_DT")
    private LocalDateTime frstRegDt;

    @Column(name = "OBSVTR_ID", length = 30)
    private String obsvtrId;

    @Column(name = "USE_USG_SNTHS_CN", length = 1000)
    private String useUsgSnthsCn;

    @Column(name = "WTOBS_CD", length = 3)
    private String wtobsCd;

    @Column(name = "UGWTR_USE_RT_CNT")
    private Long ugwtrUseRtCnt;

    @Column(name = "FCLT_FRM_CLSF_CN", length = 4000)
    private String fcltFrmClsfCn;

    @Column(name = "PUMP_FRM_CLSF_CN", length = 4000)
    private String pumpFrmClsfCn;

    @Column(name = "FCLT_CL_CN", length = 4000)
    private String fcltClCn;

    @Column(name = "FMT_STTS_CLSF_CN", length = 4000)
    private String fmtSttsClsfCn;

    @Column(name = "DGG_DPTH")
    private Long dggDpth;

    @Column(name = "DGG_CALBR")
    private Long dggCalbr;

    @Column(name = "PUMP_VL")
    private Long pumpVl;

    @Column(name = "FLDEVT_EQPMNT_EN_CN", length = 1000)
    private String fldevtEqpmntEnCn;

    @Column(name = "WPMP_ABLT")
    private Long wpmpAblt;

    @Column(name = "WTRIT_PLNQTY")
    private Long wtritPlnqty;

    @Column(name = "FMT_SPCFCT_VL")
    private Long fmtSpcfctVl;

    @Column(name = "CALBR_MSRMT_MTHD_CN", length = 4000)
    private String calbrMsrmtMthdCn;

    @Column(name = "CALBR")
    private Long calbr;

    @Column(name = "TRSM_EQPMNT_INSTL_MTHD_CN", length = 100)
    private String trsmEqpmntInstlMthdCn;

    @Column(name = "CAL_USQTY_VL")
    private Long calUsqtyVl;

    @Column(name = "USE_YN", columnDefinition = "CHAR(1)")
    private String useYn;

    @Column(name = "REG_ID", length = 50)
    private String regId;

}
