package com.sync.agent.bojoint.entity.source;

import java.time.LocalDateTime;
import javax.persistence.*;
import lombok.*;

@Entity
@Table(name = "RGETNSIMS01")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Rgetnsims01 {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "REL_TRANS_CGG_CODE", columnDefinition = "CHAR(7)")
    private String relTransCggCode;

    @Column(name = "OP_UPCH_REGNUM", length = 36)
    private String opUpchRegnum;

    @Column(name = "OP_UPCH_SNO", length = 3)
    private String opUpchSno;

    @Column(name = "INDI_SID", length = 36)
    private String indiSid;

    @Column(name = "INDI_SID_SNO", length = 3)
    private String indiSidSno;

    @Column(name = "SF_TEAM_CODE", columnDefinition = "CHAR(7)")
    private String sfTeamCode;

    @Column(name = "NM", length = 60)
    private String nm;

    @Column(name = "APPL_YMD", columnDefinition = "CHAR(8)")
    private String applYmd;

    @Column(name = "REG_YMD", columnDefinition = "CHAR(8)")
    private String regYmd;

    @Column(name = "COR_ISSUE_NO", length = 100)
    private String corIssueNo;

    @Column(name = "TEC_CAP_TOT_NUM")
    private Long tecCapTotNum;

    @Column(name = "COR_PRN_YMD", columnDefinition = "CHAR(8)")
    private String corPrnYmd;

    @Column(name = "TRF_GBN", columnDefinition = "CHAR(1)")
    private String trfGbn;

    @Column(name = "UNION_GBN", columnDefinition = "CHAR(1)")
    private String unionGbn;

    @Column(name = "SUC_GBN", columnDefinition = "CHAR(1)")
    private String sucGbn;

    @Column(name = "OTR_ORG_TRANS_YN", columnDefinition = "CHAR(1)")
    private String otrOrgTransYn;

    @Column(name = "REG_CANCEL_YMD", columnDefinition = "CHAR(8)")
    private String regCancelYmd;

    @Column(name = "TRD_STP_YMD", columnDefinition = "CHAR(8)")
    private String trdStpYmd;

    @Column(name = "REG_CRIT_FTNS_YN", columnDefinition = "CHAR(1)")
    private String regCritFtnsYn;

    @Column(name = "DISNT_ISSUE_YMD", columnDefinition = "CHAR(8)")
    private String disntIssueYmd;

    @Column(name = "CHA_DEPT_NM", length = 60)
    private String chaDeptNm;

    @Column(name = "LAST_CHG_YMD", columnDefinition = "CHAR(8)")
    private String lastChgYmd;

    @Column(name = "FIRST_REG_DTHR")
    private LocalDateTime firstRegDthr;

    @Column(name = "LAST_MOD_DTHR")
    private LocalDateTime lastModDthr;

    @Column(name = "RGT_MBD_NM", length = 60)
    private String rgtMbdNm;

    @Column(name = "TELNO", length = 60)
    private String telno;

    @Column(name = "ADDR", length = 1000)
    private String addr;

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
