package com.infolink.agent.bojo.entity.source;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import javax.persistence.*;
import lombok.*;

@Entity
@Table(name = "RGETNWAVI06")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Rgetnwavi06 {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "REL_TRANS_CGG_CODE", columnDefinition = "CHAR(7)")
    private String relTransCggCode;

    @Column(name = "PERM_NT_NO", length = 10)
    private String permNtNo;

    @Column(name = "QW_ISP_SNO")
    private Long qwIspSno;

    @Column(name = "QW_ISP_SORT_CODE", columnDefinition = "CHAR(1)")
    private String qwIspSortCode;

    @Column(name = "LIST_CODE", length = 20)
    private String listCode;

    @Column(name = "RT", length = 50)
    private String rt;

    @Column(name = "RT_VLU")
    private BigDecimal rtVlu;

    @Column(name = "ELIG_YN", columnDefinition = "CHAR(1)")
    private String eligYn;

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
