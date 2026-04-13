package com.sync.agent.bojoint.entity.iftable;

import java.time.LocalDateTime;
import javax.persistence.*;
import lombok.*;

@Entity
@Table(name = "IF_RSV_USE_JEJU_DAY")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IfRsvUseJejuDay {

    @Column(name = "ID", nullable = false)
    private String id;

    @Column(name = "OBSRVT_ID", length = 30)
    private String obsrvtId;

    @Column(name = "OBSR_DE", length = 8)
    private String obsrDe;

    @Column(name = "USGQTY")
    private Long usgqty;

    @Column(name = "LAST_MESURE_VALUE")
    private Long lastMesureValue;

    @Column(name = "FRST_MESURE_VALUE")
    private Long frstMesureValue;

    @Column(name = "DTA_STTUS_CODE", length = 2)
    private String dtaSttusCode;

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
