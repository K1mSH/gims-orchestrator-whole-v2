package com.sync.agent.bojoint.entity.source;

import java.time.LocalDateTime;
import javax.persistence.*;
import lombok.*;

@Entity
@Table(name = "RGETHKMIR01")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Rgethkmir01 {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private String id;

    @Column(name = "REL_TRANS_CGG_CODE", length = 7)
    private String relTransCggCode;

    @Column(name = "PERM_NT_NO", length = 10)
    private String permNtNo;

    @Column(name = "EXE_HIS_YMD", length = 8)
    private String exeHisYmd;

    @Column(name = "ORD")
    private Long ord;

    @Column(name = "RPRT_DOC_ISSUE_YMD", length = 8)
    private String rprtDocIssueYmd;

    @Column(name = "RPRT_DTL", length = 1000)
    private String rprtDtl;

    @Column(name = "IPRV_ACT_CMPT_CTN", length = 1000)
    private String iprvActCmptCtn;

    @Column(name = "IPRV_ACT_CMPT_YMD", length = 8)
    private String iprvActCmptYmd;

    @Column(name = "EXE_DTL", length = 1000)
    private String exeDtl;

    @Column(name = "PLAN_OP_UPCH_REGNUM", length = 36)
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

    @Column(name = "FIRST_REG_DTHR")
    private LocalDateTime firstRegDthr;

    @Column(name = "LAST_MOD_DTHR")
    private LocalDateTime lastModDthr;

    @Column(name = "MW_TAKE_NO", length = 18)
    private String mwTakeNo;

    @Column(name = "PLAN_OP_UPCH_RDN_WHL_ADDR", length = 500)
    private String planOpUpchRdnWhlAddr;

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
