package com.sync.agent.bojoint.entity.source;

import java.time.LocalDateTime;
import javax.persistence.*;
import lombok.*;

@Entity
@Table(name = "RGETNWAMS01")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Rgetnwams01 {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "REL_TRANS_CGG_CODE", columnDefinition = "CHAR(7)")
    private String relTransCggCode;

    @Column(name = "QW_ISP_SORT_CODE", columnDefinition = "CHAR(1)")
    private String qwIspSortCode;

    @Column(name = "LIST_CODE", length = 20)
    private String listCode;

    @Column(name = "IPT_ORD", columnDefinition = "CHAR(2)")
    private String iptOrd;

    @Column(name = "ISP_CRIT", length = 50)
    private String ispCrit;

    @Column(name = "ELIG_CRIT", length = 20)
    private String eligCrit;

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
