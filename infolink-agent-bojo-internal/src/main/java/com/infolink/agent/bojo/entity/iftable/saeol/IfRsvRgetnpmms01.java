package com.infolink.agent.bojo.entity.iftable.saeol;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import javax.persistence.*;
import lombok.*;

@Entity
@Table(name = "IF_RSV_RGETNPMMS01")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IfRsvRgetnpmms01 {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "REL_TRANS_CGG_CODE", columnDefinition = "CHAR(7)")
    private String relTransCggCode;

    @Column(name = "PERM_NT_NO", length = 10)
    private String permNtNo;

    @Column(name = "OP_UPCH_REGNUM", length = 36)
    private String opUpchRegnum;

    @Column(name = "OP_UPCH_SNO", length = 3)
    private String opUpchSno;

    @Column(name = "ORG_SNO", length = 3)
    private String orgSno;

    @Column(name = "PRFCN_UPCH_SNO", length = 3)
    private String prfcnUpchSno;

    @Column(name = "SF_TEAM_CODE", columnDefinition = "CHAR(7)")
    private String sfTeamCode;

    @Column(name = "PERM_NT_FORM_CODE", columnDefinition = "CHAR(1)")
    private String permNtFormCode;

    @Column(name = "PERM_NT_YMD", columnDefinition = "CHAR(8)")
    private String permNtYmd;

    @Column(name = "APLR_GBN_CODE", columnDefinition = "CHAR(2)")
    private String aplrGbnCode;

    @Column(name = "DVOP_LOC_REGN_CODE", columnDefinition = "CHAR(10)")
    private String dvopLocRegnCode;

    @Column(name = "DVOP_LOC_SAN", columnDefinition = "CHAR(1)")
    private String dvopLocSan;

    @Column(name = "DVOP_LOC_BUNJI", length = 20)
    private String dvopLocBunji;

    @Column(name = "DVOP_LOC_HO", length = 4)
    private String dvopLocHo;

    @Column(name = "LITD_DG")
    private Long litdDg;

    @Column(name = "LITD_MINT")
    private Long litdMint;

    @Column(name = "LITD_SC")
    private BigDecimal litdSc;

    @Column(name = "LTTD_DG")
    private Long lttdDg;

    @Column(name = "LTTD_MINT")
    private Long lttdMint;

    @Column(name = "LTTD_SC")
    private BigDecimal lttdSc;

    @Column(name = "UWATER_SRV", length = 30)
    private String uwaterSrv;

    @Column(name = "UWATER_SRV_CODE", columnDefinition = "CHAR(1)")
    private String uwaterSrvCode;

    @Column(name = "UWATER_DTL_SRV_CODE", columnDefinition = "CHAR(2)")
    private String uwaterDtlSrvCode;

    @Column(name = "UWATER_POTA_YN", columnDefinition = "CHAR(1)")
    private String uwaterPotaYn;

    @Column(name = "DIG_DPH")
    private BigDecimal digDph;

    @Column(name = "DIG_DIAM")
    private BigDecimal digDiam;

    @Column(name = "FRW_PLN_QUA")
    private BigDecimal frwPlnQua;

    @Column(name = "ND_QT")
    private Long ndQt;

    @Column(name = "DYN_EQN_HRP")
    private BigDecimal dynEqnHrp;

    @Column(name = "PIPE_DIAM")
    private BigDecimal pipeDiam;

    @Column(name = "ESB_DPH")
    private BigDecimal esbDph;

    @Column(name = "RWT_CAP")
    private BigDecimal rwtCap;

    @Column(name = "DVUS_ST_PLAN_YMD", columnDefinition = "CHAR(8)")
    private String dvusStPlanYmd;

    @Column(name = "DVUS_END_PLAN_YMD", columnDefinition = "CHAR(8)")
    private String dvusEndPlanYmd;

    @Column(name = "DVUS_ENDDT", columnDefinition = "CHAR(8)")
    private String dvusEnddt;

    @Column(name = "DVUS_END_NT_YMD", columnDefinition = "CHAR(8)")
    private String dvusEndNtYmd;

    @Column(name = "DVUS_END_TKIT_NT_NO", length = 20)
    private String dvusEndTkitNtNo;

    @Column(name = "END_NTKIT_ISSUE_YMD", columnDefinition = "CHAR(8)")
    private String endNtkitIssueYmd;

    @Column(name = "UWATER_OVF_QUA")
    private BigDecimal uwaterOvfQua;

    @Column(name = "USE_PLN_QUA")
    private BigDecimal usePlnQua;

    @Column(name = "CGONG_PLAN_YMD", columnDefinition = "CHAR(8)")
    private String cgongPlanYmd;

    @Column(name = "JGONG_PLAN_YMD", columnDefinition = "CHAR(8)")
    private String jgongPlanYmd;

    @Column(name = "PERM_EF_STDT", columnDefinition = "CHAR(8)")
    private String permEfStdt;

    @Column(name = "PERM_EF_ENDDT", columnDefinition = "CHAR(8)")
    private String permEfEnddt;

    @Column(name = "DVUS_SRV", length = 30)
    private String dvusSrv;

    @Column(name = "FACIL_CTN", length = 250)
    private String facilCtn;

    @Column(name = "PLVT_FACIL_YN", columnDefinition = "CHAR(1)")
    private String plvtFacilYn;

    @Column(name = "PERM_YN", columnDefinition = "CHAR(1)")
    private String permYn;

    @Column(name = "LNHO_RAISE_YN", columnDefinition = "CHAR(1)")
    private String lnhoRaiseYn;

    @Column(name = "LNHO_RAISE_YMD", columnDefinition = "CHAR(8)")
    private String lnhoRaiseYmd;

    @Column(name = "LNHO_DEAL_YMD", columnDefinition = "CHAR(8)")
    private String lnhoDealYmd;

    @Column(name = "LNHO_RAISE_CAU_CODE", columnDefinition = "CHAR(1)")
    private String lnhoRaiseCauCode;

    @Column(name = "OSTRS_MET_CODE", columnDefinition = "CHAR(1)")
    private String ostrsMetCode;

    @Column(name = "LNHO_DEAL_MET_CODE", columnDefinition = "CHAR(1)")
    private String lnhoDealMetCode;

    @Column(name = "QW_ISP_EXM_TGT", columnDefinition = "CHAR(1)")
    private String qwIspExmTgt;

    @Column(name = "END_NT_YN", columnDefinition = "CHAR(1)")
    private String endNtYn;

    @Column(name = "EXT_CHG_PERM_NT_YMD", columnDefinition = "CHAR(8)")
    private String extChgPermNtYmd;

    @Column(name = "DIGBH_NTKIT_GNT_YMD", columnDefinition = "CHAR(8)")
    private String digbhNtkitGntYmd;

    @Column(name = "VFUWAT_NTKIT_GNT_YMD", columnDefinition = "CHAR(8)")
    private String vfuwatNtkitGntYmd;

    @Column(name = "ISSUE_YMD", columnDefinition = "CHAR(8)")
    private String issueYmd;

    @Column(name = "MG_NO", length = 20)
    private String mgNo;

    @Column(name = "RWT_CAPB_SUM_YN", columnDefinition = "CHAR(1)")
    private String rwtCapbSumYn;

    @Column(name = "ITG_DIST_IN_FACIL_YN", columnDefinition = "CHAR(1)")
    private String itgDistInFacilYn;

    @Column(name = "PERM_CANCEL_YN", columnDefinition = "CHAR(1)")
    private String permCancelYn;

    @Column(name = "PERM_CANCEL_YMD", columnDefinition = "CHAR(8)")
    private String permCancelYmd;

    @Column(name = "LAST_QW_ISP_RT", columnDefinition = "CHAR(1)")
    private String lastQwIspRt;

    @Column(name = "CHA_DEPT_NM", length = 60)
    private String chaDeptNm;

    @Column(name = "DIG_LNHO_RAISE_CAU", length = 250)
    private String digLnhoRaiseCau;

    @Column(name = "DIG_OSTRS_MET", length = 250)
    private String digOstrsMet;

    @Column(name = "JGONG_DEAL_YN", columnDefinition = "CHAR(1)")
    private String jgongDealYn;

    @Column(name = "LAST_CHG_YMD", columnDefinition = "CHAR(8)")
    private String lastChgYmd;

    @Column(name = "FIRST_REG_DTHR")
    private LocalDateTime firstRegDthr;

    @Column(name = "LAST_MOD_DTHR")
    private LocalDateTime lastModDthr;

    @Column(name = "BF_PERM_NT_NO", length = 10)
    private String bfPermNtNo;

    @Column(name = "BF_JGONG_YMD", columnDefinition = "CHAR(8)")
    private String bfJgongYmd;

    @Column(name = "DEL_YN", columnDefinition = "CHAR(1)")
    private String delYn;

    @Column(name = "JUNSU", length = 600)
    private String junsu;

    @Column(name = "UNLAW_YN", columnDefinition = "CHAR(1)")
    private String unlawYn;

    @Column(name = "DVOP_LOC_RDN_CGG_CODE", length = 5)
    private String dvopLocRdnCggCode;

    @Column(name = "DVOP_LOC_RDN_CODE", length = 7)
    private String dvopLocRdnCode;

    @Column(name = "DVOP_LOC_RDN_UMD_GBN", length = 1)
    private String dvopLocRdnUmdGbn;

    @Column(name = "DVOP_LOC_RDN_UMD_CODE", length = 10)
    private String dvopLocRdnUmdCode;

    @Column(name = "DVOP_LOC_RDN_BDNG_ORI_NO", length = 5)
    private String dvopLocRdnBdngOriNo;

    @Column(name = "DVOP_LOC_RDN_BDNG_SUB_NO", length = 5)
    private String dvopLocRdnBdngSubNo;

    @Column(name = "DVOP_LOC_RDN_BDNG_FLR_GBN", length = 1)
    private String dvopLocRdnBdngFlrGbn;

    @Column(name = "DVOP_LOC_RDN_SPEC_ADDR", length = 200)
    private String dvopLocRdnSpecAddr;

    @Column(name = "DVOP_LOC_RDN_WHL_ADDR", length = 500)
    private String dvopLocRdnWhlAddr;

    @Column(name = "DVOP_LOC_RDN_POST_NO", length = 6)
    private String dvopLocRdnPostNo;

    @Column(name = "DVOP_LOC_RDN_ADMDNG_CODE", length = 10)
    private String dvopLocRdnAdmdngCode;

    @Column(name = "DVOP_LOC_RDN_NM", length = 80)
    private String dvopLocRdnNm;

    @Column(name = "DVOP_LOC_RDN_UMD_NM", length = 30)
    private String dvopLocRdnUmdNm;

    @Column(name = "DVOP_LOC_RDN_ADMDNG_NM", length = 30)
    private String dvopLocRdnAdmdngNm;

    @Column(name = "PLAN_RDN_WHL_ADDR", length = 500)
    private String planRdnWhlAddr;

    @Column(name = "PERM_CANCEL_WHY", length = 250)
    private String permCancelWhy;

    @Column(name = "ORST_RTOR_PLAN_CODE", length = 2)
    private String orstRtorPlanCode;

    @Column(name = "SOURCE_REFS", length = 4000)
    private String sourceRefs;

    @Column(name = "LINK_STATUS", length = 20)
    @Builder.Default
    private String linkStatus = "PENDING";

    @Column(name = "EXTRACTED_AT")
    private LocalDateTime extractedAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @Column(name = "EXECUTION_ID")
    private String executionId;

}
