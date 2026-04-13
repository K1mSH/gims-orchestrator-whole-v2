package com.sync.agent.bojoint.entity.target;

import java.time.LocalDateTime;
import javax.persistence.*;
import lombok.*;

@Entity
@Table(name = "PM_GD111021")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(PmGd111021Id.class)
public class PmGd111021 {

    @Id
    @Column(name = "BRNCH_ID")
    private Long brnchId;

    @Id
    @Column(name = "OBSRVN_DT")
    private LocalDateTime obsrvnDt;

    @Column(name = "LAST_MSRMT_VL")
    private Long lastMsrmtVl;

    @Column(name = "USE_QNT")
    private Long useQnt;

    @Column(name = "EXECUTION_ID")
    private String executionId;

    @Column(name = "SOURCE_REFS", length = 4000)
    private String sourceRefs;

}
