package com.sync.agent.bojoint.entity.iftable.saeol;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import javax.persistence.*;
import lombok.*;

@Entity
@Table(name = "IF_RSV_RGETNSCKT01")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IfRsvRgetnsckt01 {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "REL_TRANS_CGG_CODE", length = 7)
    private String relTransCggCode;

    @Column(name = "MW_TAKE_NO", length = 18)
    private String mwTakeNo;

    @Column(name = "PERM_NT_NO", length = 10)
    private String permNtNo;

    @Column(name = "OP_UPCH_REGNUM", length = 36)
    private String opUpchRegnum;

    @Column(name = "OP_UPCH_SNO", length = 3)
    private String opUpchSno;

    @Column(name = "PERM_YMD", length = 8)
    private String permYmd;

    @Column(name = "DVOP_LOC_REGN_CODE", length = 10)
    private String dvopLocRegnCode;

    @Column(name = "DVOP_LOC_SAN", length = 1)
    private String dvopLocSan;

    @Column(name = "DVOP_LOC_BUNJI", length = 20)
    private String dvopLocBunji;

    @Column(name = "DVOP_LOC_HO", length = 4)
    private String dvopLocHo;

    @Column(name = "DVOP_LOC_SPEC_ADDR", length = 90)
    private String dvopLocSpecAddr;

    @Column(name = "DVOP_LOC_SPEC_DNG", length = 20)
    private String dvopLocSpecDng;

    @Column(name = "DVOP_LOC_SPEC_HO", length = 10)
    private String dvopLocSpecHo;

    @Column(name = "DVOP_LOC_TONG", length = 3)
    private String dvopLocTong;

    @Column(name = "DVOP_LOC_BAN", length = 3)
    private String dvopLocBan;

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

    @Column(name = "SRV", length = 30)
    private String srv;

    @Column(name = "DIG_DPH")
    private BigDecimal digDph;

    @Column(name = "DIG_DBT")
    private BigDecimal digDbt;

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

    @Column(name = "MG_NO", length = 20)
    private String mgNo;

    @Column(name = "RWT_CAPB_SUM_YN", length = 1)
    private String rwtCapbSumYn;

    @Column(name = "SUM_FACIL_REM", length = 250)
    private String sumFacilRem;

    @Column(name = "ITG_DIST_IN_FACIL_YN", length = 1)
    private String itgDistInFacilYn;

    @Column(name = "DIST_IN_FACIL_REM", length = 250)
    private String distInFacilRem;

    @Column(name = "CHA_DEPT_NM", length = 60)
    private String chaDeptNm;

    @Column(name = "LAST_MOD_DTHR")
    private LocalDateTime lastModDthr;

    @Column(name = "FIRST_REG_DTHR")
    private LocalDateTime firstRegDthr;

    @Column(name = "DEAL_WHY", length = 250)
    private String dealWhy;

    @Column(name = "DVOP_LOC_ADMDNG_CODE", length = 10)
    private String dvopLocAdmdngCode;

    @Column(name = "UWATER_POTA_YN", length = 1)
    private String uwaterPotaYn;

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
