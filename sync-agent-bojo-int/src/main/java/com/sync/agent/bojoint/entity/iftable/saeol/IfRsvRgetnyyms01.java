package com.sync.agent.bojoint.entity.iftable.saeol;

import java.time.LocalDateTime;
import javax.persistence.*;
import lombok.*;

@Entity
@Table(name = "IF_RSV_RGETNYYMS01")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IfRsvRgetnyyms01 {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "REL_TRANS_CGG_CODE", length = 7)
    private String relTransCggCode;

    @Column(name = "ORG_REGNUM", length = 13)
    private String orgRegnum;

    @Column(name = "ORG_SNO", length = 3)
    private String orgSno;

    @Column(name = "INDI_SID", length = 36)
    private String indiSid;

    @Column(name = "INDI_SID_SNO", length = 3)
    private String indiSidSno;

    @Column(name = "SF_TEAM_CODE", length = 7)
    private String sfTeamCode;

    @Column(name = "APPL_YMD", length = 8)
    private String applYmd;

    @Column(name = "NM", length = 60)
    private String nm;

    @Column(name = "REG_YMD", length = 8)
    private String regYmd;

    @Column(name = "COR_ISSUE_NO", length = 100)
    private String corIssueNo;

    @Column(name = "SPEC_MPW_TOT_NUM")
    private Long specMpwTotNum;

    @Column(name = "COR_ISSUE_YMD", length = 8)
    private String corIssueYmd;

    @Column(name = "OTR_ORG_TRANS_YN", length = 1)
    private String otrOrgTransYn;

    @Column(name = "REG_CANCEL_YMD", length = 8)
    private String regCancelYmd;

    @Column(name = "REG_CANCEL_WHY", length = 250)
    private String regCancelWhy;

    @Column(name = "CHG_NOTC_ISSUE_YMD", length = 8)
    private String chgNotcIssueYmd;

    @Column(name = "TRD_STP_YN", length = 1)
    private String trdStpYn;

    @Column(name = "REM", length = 250)
    private String rem;

    @Column(name = "CHA_DEPT_NM", length = 60)
    private String chaDeptNm;

    @Column(name = "FIRST_REG_DTHR")
    private LocalDateTime firstRegDthr;

    @Column(name = "LAST_MOD_DTHR")
    private LocalDateTime lastModDthr;

    @Column(name = "USR_ID", length = 20)
    private String usrId;

    @Column(name = "LAST_CORT_ID", length = 20)
    private String lastCortId;

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
