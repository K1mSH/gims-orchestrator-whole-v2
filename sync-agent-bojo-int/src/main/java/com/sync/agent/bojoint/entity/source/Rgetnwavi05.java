package com.sync.agent.bojoint.entity.source;

import java.time.LocalDateTime;
import javax.persistence.*;
import lombok.*;

@Entity
@Table(name = "RGETNWAVI05")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Rgetnwavi05 {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private String id;

    @Column(name = "REL_TRANS_CGG_CODE", length = 7)
    private String relTransCggCode;

    @Column(name = "PERM_NT_NO", length = 10)
    private String permNtNo;

    @Column(name = "QW_ISP_SNO")
    private Long qwIspSno;

    @Column(name = "QW_ISP_YN", length = 1)
    private String qwIspYn;

    @Column(name = "QW_ISP_SORT_CODE", length = 1)
    private String qwIspSortCode;

    @Column(name = "QW_ISP_RT", length = 1)
    private String qwIspRt;

    @Column(name = "QW_ISP_ORG_NM", length = 60)
    private String qwIspOrgNm;

    @Column(name = "QW_ISP_YMD", length = 8)
    private String qwIspYmd;

    @Column(name = "REM", length = 250)
    private String rem;

    @Column(name = "CHA_DEPT_NM", length = 60)
    private String chaDeptNm;

    @Column(name = "FIRST_REG_DTHR")
    private LocalDateTime firstRegDthr;

    @Column(name = "LAST_MOD_DTHR")
    private LocalDateTime lastModDthr;

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
