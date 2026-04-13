package com.sync.agent.bojoint.entity.iftable;

import java.time.LocalDateTime;
import javax.persistence.*;
import lombok.*;

@Entity
@Table(name = "IF_RSV_USE_LEGACY_DATA")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IfRsvUseLegacyData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "SN")
    private Long sn;

    @Column(name = "TELNO")
    private String telno;

    @Column(name = "OBSR_DT")
    private LocalDateTime obsrDt;

    @Column(name = "LAST_MEASURE_VALUE")
    private Long lastMeasureValue;

    @Column(name = "USGQTY")
    private Long usgqty;

    @Column(name = "LAST_CHANGE_DT")
    private LocalDateTime lastChangeDt;

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
