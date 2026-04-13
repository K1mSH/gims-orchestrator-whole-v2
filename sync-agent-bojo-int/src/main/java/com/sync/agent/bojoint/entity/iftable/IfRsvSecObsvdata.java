package com.sync.agent.bojoint.entity.iftable;

import java.time.LocalDateTime;
import javax.persistence.*;
import lombok.*;

@Entity
@Table(name = "IF_RSV_SEC_OBSVDATA")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IfRsvSecObsvdata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "OBSV_CODE", nullable = false)
    private String obsvCode;

    @Column(name = "OBSV_DATE", nullable = false)
    private LocalDateTime obsvDate;

    @Column(name = "OBSV_TIME", length = 8)
    private String obsvTime;

    @Column(name = "GWDEP")
    private Long gwdep;

    @Column(name = "GWTEMP")
    private Long gwtemp;

    @Column(name = "EC")
    private Long ec;

    @Column(name = "REMARK")
    private String remark;

    @Column(name = "SOURCE_REFS", length = 4000, nullable = false)
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
