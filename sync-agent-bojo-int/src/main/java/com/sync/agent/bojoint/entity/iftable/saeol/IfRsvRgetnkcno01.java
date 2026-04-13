package com.sync.agent.bojoint.entity.iftable.saeol;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import javax.persistence.*;
import lombok.*;

@Entity
@Table(name = "IF_RSV_RGETNKCNO01")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IfRsvRgetnkcno01 {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private String id;

    @Column(name = "PERM_NT_NO", length = 10)
    private String permNtNo;

    @Column(name = "DIG_ORD", length = 3)
    private String digOrd;

    @Column(name = "REGN_CODE", length = 10)
    private String regnCode;

    @Column(name = "SAN", length = 1)
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

    @Column(name = "DIG_DPH")
    private BigDecimal digDph;

    @Column(name = "DIG_DBT")
    private BigDecimal digDbt;

    @Column(name = "DIG_OBJ", length = 50)
    private String digObj;

    @Column(name = "FIRST_REG_DTHR")
    private LocalDateTime firstRegDthr;

    @Column(name = "LAST_MOD_DTHR")
    private LocalDateTime lastModDthr;

    @Column(name = "USER_ID", length = 20)
    private String userId;

    @Column(name = "LAST_CORT_ID", length = 20)
    private String lastCortId;

    @Column(name = "DVOP_LOC_ADMDNG_CODE", length = 10)
    private String dvopLocAdmdngCode;

    @Column(name = "CGONG_PLAN_YMD", length = 8)
    private String cgongPlanYmd;

    @Column(name = "JGONG_PLAN_YMD", length = 8)
    private String jgongPlanYmd;

    @Column(name = "OSTRS_PLAN_YMD", length = 8)
    private String ostrsPlanYmd;

    @Column(name = "PLAN_OP_UPCH_REGNUM", length = 50)
    private String planOpUpchRegnum;

    @Column(name = "PLAN_OP_UPCH_SNO", length = 3)
    private String planOpUpchSno;

    @Column(name = "PLAN_OP_UPCH_NM", length = 60)
    private String planOpUpchNm;

    @Column(name = "PLAN_OP_UPCH_REP_NM", length = 60)
    private String planOpUpchRepNm;

    @Column(name = "PLAN_OP_UPCH_TELNO", length = 30)
    private String planOpUpchTelno;

    @Column(name = "PLAN_OP_UPCH_ADDR", length = 250)
    private String planOpUpchAddr;

    @Column(name = "LNHO_RAISE_YMD", length = 8)
    private String lnhoRaiseYmd;

    @Column(name = "LNHO_DEAL_YMD", length = 8)
    private String lnhoDealYmd;

    @Column(name = "LNHO_DEAL_MET_CODE", length = 1)
    private String lnhoDealMetCode;

    @Column(name = "LNHO_DPP_NM", length = 60)
    private String lnhoDppNm;

    @Column(name = "DVUS_ENDDT", length = 8)
    private String dvusEnddt;

    @Column(name = "DIG_LNHO_RAISE_CAU", length = 250)
    private String digLnhoRaiseCau;

    @Column(name = "DIG_OSTRS_MET", length = 250)
    private String digOstrsMet;

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

    @Column(name = "PLAN_RDN_WHL_ADDR", length = 500)
    private String planRdnWhlAddr;

    @Column(name = "REL_TRANS_CGG_CODE", length = 7)
    private String relTransCggCode;

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
