package com.sync.agent.bojoint.entity.iftable.saeol;

import java.time.LocalDateTime;
import javax.persistence.*;
import lombok.*;

@Entity
@Table(name = "IF_RSV_RGETNTGMS02")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IfRsvRgetntgms02 {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "REL_TRANS_CGG_CODE", columnDefinition = "CHAR(7)")
    private String relTransCggCode;

    @Column(name = "SF_TEAM_CODE", columnDefinition = "CHAR(7)")
    private String sfTeamCode;

    @Column(name = "CRIT_YY", columnDefinition = "CHAR(4)")
    private String critYy;

    @Column(name = "QW_ISP_SORT_CODE", columnDefinition = "CHAR(1)")
    private String qwIspSortCode;

    @Column(name = "LIST_CODE", length = 20)
    private String listCode;

    @Column(name = "CRIT_VALUE", length = 20)
    private String critValue;

    @Column(name = "FIRST_REG_DTHR")
    private LocalDateTime firstRegDthr;

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
