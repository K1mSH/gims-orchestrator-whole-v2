package com.infolink.agent.bojo.entity.iftable.saeol;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import javax.persistence.*;
import lombok.*;

@Entity
@Table(name = "IF_RSV_RGETSTGMS01")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IfRsvRgetstgms01 {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "REL_TRANS_CGG_CODE", columnDefinition = "CHAR(7)")
    private String relTransCggCode;

    @Column(name = "PERM_NT_NO", length = 10)
    private String permNtNo;

    @Column(name = "YY_GBN", columnDefinition = "CHAR(4)")
    private String yyGbn;

    @Column(name = "OP_UPCH_REGNUM", length = 36)
    private String opUpchRegnum;

    @Column(name = "OP_UPCH_SNO", length = 3)
    private String opUpchSno;

    @Column(name = "SF_TEAM_CODE", columnDefinition = "CHAR(7)")
    private String sfTeamCode;

    @Column(name = "PERM_NT_FORM_CODE", columnDefinition = "CHAR(1)")
    private String permNtFormCode;

    @Column(name = "RELL_LAW_CODE", columnDefinition = "CHAR(2)")
    private String rellLawCode;

    @Column(name = "REGN_CODE", columnDefinition = "CHAR(10)")
    private String regnCode;

    @Column(name = "SAN", columnDefinition = "CHAR(1)")
    private String san;

    @Column(name = "BUNJI", length = 20)
    private String bunji;

    @Column(name = "HO", length = 4)
    private String ho;

    @Column(name = "SPEC_ADDR", length = 90)
    private String specAddr;

    @Column(name = "SPEC_DNG", length = 20)
    private String specDng;

    @Column(name = "SPEC_HO", length = 10)
    private String specHo;

    @Column(name = "TONG", length = 3)
    private String tong;

    @Column(name = "BAN", length = 3)
    private String ban;

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

    @Column(name = "ELEV")
    private Long elev;

    @Column(name = "UWATER_SRV_CODE", columnDefinition = "CHAR(1)")
    private String uwaterSrvCode;

    @Column(name = "UWATER_DTL_SRV_CODE", columnDefinition = "CHAR(2)")
    private String uwaterDtlSrvCode;

    @Column(name = "PUB_PRI_GBN", columnDefinition = "CHAR(1)")
    private String pubPriGbn;

    @Column(name = "USE_GIGAN_ST")
    private Long useGiganSt;

    @Column(name = "USE_GIGAN_END")
    private Long useGiganEnd;

    @Column(name = "USE_DAYCNT")
    private Long useDaycnt;

    @Column(name = "POTA_YN", columnDefinition = "CHAR(1)")
    private String potaYn;

    @Column(name = "FEED_FAM")
    private Long feedFam;

    @Column(name = "FEED_PPT")
    private Long feedPpt;

    @Column(name = "INDI_PER_FEED_QUA")
    private BigDecimal indiPerFeedQua;

    @Column(name = "MONG_AREA")
    private BigDecimal mongArea;

    @Column(name = "STNUM_DEAL_YN", columnDefinition = "CHAR(1)")
    private String stnumDealYn;

    @Column(name = "UWATER_SOUC_CODE", columnDefinition = "CHAR(1)")
    private String uwaterSoucCode;

    @Column(name = "DAY_USE_QUA")
    private BigDecimal dayUseQua;

    @Column(name = "MON_USE_QUA")
    private BigDecimal monUseQua;

    @Column(name = "Y_USE_QUA")
    private BigDecimal yUseQua;

    @Column(name = "CQL_CRIT_CODE", columnDefinition = "CHAR(1)")
    private String cqlCritCode;

    @Column(name = "JNHO_FORM_CODE", columnDefinition = "CHAR(1)")
    private String jnhoFormCode;

    @Column(name = "AUV_BDR_GBN", columnDefinition = "CHAR(1)")
    private String auvBdrGbn;

    @Column(name = "DVOP_YMD", columnDefinition = "CHAR(8)")
    private String dvopYmd;

    @Column(name = "DPH")
    private BigDecimal dph;

    @Column(name = "DIG_DIAM")
    private BigDecimal digDiam;

    @Column(name = "PUMP_HRP")
    private BigDecimal pumpHrp;

    @Column(name = "RWT_CAP")
    private BigDecimal rwtCap;

    @Column(name = "PIPE_DIAM")
    private BigDecimal pipeDiam;

    @Column(name = "OGG_ESB_YN", columnDefinition = "CHAR(1)")
    private String oggEsbYn;

    @Column(name = "CHSU_EQN_ESB_YN", columnDefinition = "CHAR(1)")
    private String chsuEqnEsbYn;

    @Column(name = "GRWT_ESB_YN", columnDefinition = "CHAR(1)")
    private String grwtEsbYn;

    @Column(name = "UPPRHO_ESB_YN", columnDefinition = "CHAR(1)")
    private String upprhoEsbYn;

    @Column(name = "WTMSKW_ESB_YN", columnDefinition = "CHAR(1)")
    private String wtmskwEsbYn;

    @Column(name = "CSI_ESB_YN", columnDefinition = "CHAR(1)")
    private String csiEsbYn;

    @Column(name = "ELEC_TEMPE_YN", columnDefinition = "CHAR(1)")
    private String elecTempeYn;

    @Column(name = "NAT_WTLV")
    private BigDecimal natWtlv;

    @Column(name = "STB_WTLV")
    private BigDecimal stbWtlv;

    @Column(name = "FRW_PLN_QUA")
    private BigDecimal frwPlnQua;

    @Column(name = "LNHO_RAISE_YN", columnDefinition = "CHAR(1)")
    private String lnhoRaiseYn;

    @Column(name = "LNHO_RAISE_CAU_CODE", columnDefinition = "CHAR(1)")
    private String lnhoRaiseCauCode;

    @Column(name = "LNHO_RAISE_YMD", columnDefinition = "CHAR(8)")
    private String lnhoRaiseYmd;

    @Column(name = "LNHO_DEAL_YMD", columnDefinition = "CHAR(8)")
    private String lnhoDealYmd;

    @Column(name = "OSTRS_MET_CODE", columnDefinition = "CHAR(1)")
    private String ostrsMetCode;

    @Column(name = "LNHO_DEAL_MET_CODE", columnDefinition = "CHAR(1)")
    private String lnhoDealMetCode;

    @Column(name = "END_NT_YN", columnDefinition = "CHAR(1)")
    private String endNtYn;

    @Column(name = "DVUS_ENDDT", columnDefinition = "CHAR(8)")
    private String dvusEnddt;

    @Column(name = "DVUS_END_WHY", length = 250)
    private String dvusEndWhy;

    @Column(name = "DVUS_END_NT_YMD", columnDefinition = "CHAR(8)")
    private String dvusEndNtYmd;

    @Column(name = "DVUS_END_APLR_NM", length = 60)
    private String dvusEndAplrNm;

    @Column(name = "DVUS_END_NT_NO", length = 20)
    private String dvusEndNtNo;

    @Column(name = "END_NTKIT_ISSUE_YMD", columnDefinition = "CHAR(8)")
    private String endNtkitIssueYmd;

    @Column(name = "LAST_QW_ISP_RT", columnDefinition = "CHAR(1)")
    private String lastQwIspRt;

    @Column(name = "SRCH_RT_REFL_YN", columnDefinition = "CHAR(1)")
    private String srchRtReflYn;

    @Column(name = "FIRST_REG_DTHR")
    private LocalDateTime firstRegDthr;

    @Column(name = "LAST_MOD_DTHR")
    private LocalDateTime lastModDthr;

    @Column(name = "RDN_CGG_CODE", length = 5)
    private String rdnCggCode;

    @Column(name = "RDN_CODE", length = 7)
    private String rdnCode;

    @Column(name = "RDN_UMD_GBN", length = 1)
    private String rdnUmdGbn;

    @Column(name = "RDN_UMD_CODE", length = 10)
    private String rdnUmdCode;

    @Column(name = "RDN_BDNG_ORI_NO", length = 5)
    private String rdnBdngOriNo;

    @Column(name = "RDN_BDNG_SUB_NO", length = 5)
    private String rdnBdngSubNo;

    @Column(name = "RDN_BDNG_FLR_GBN", length = 1)
    private String rdnBdngFlrGbn;

    @Column(name = "RDN_SPEC_ADDR", length = 200)
    private String rdnSpecAddr;

    @Column(name = "RDN_WHL_ADDR", length = 500)
    private String rdnWhlAddr;

    @Column(name = "RDN_POST_NO", length = 6)
    private String rdnPostNo;

    @Column(name = "RDN_ADMDNG_CODE", length = 10)
    private String rdnAdmdngCode;

    @Column(name = "RDN_NM", length = 80)
    private String rdnNm;

    @Column(name = "RDN_UMD_NM", length = 30)
    private String rdnUmdNm;

    @Column(name = "RDN_ADMDNG_NM", length = 30)
    private String rdnAdmdngNm;

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
