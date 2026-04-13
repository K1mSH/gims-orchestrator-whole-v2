package com.sync.agent.bojoint.entity.target;

import javax.persistence.*;
import lombok.*;

@Entity
@Table(name = "PM_GD111022")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(PmGd111022Id.class)
public class PmGd111022 {

    @Id
    @Column(name = "BRNCH_ID")
    private Long brnchId;

    @Id
    @Column(name = "OBSRVN_YMD", length = 8)
    private String obsrvnYmd;

    @Column(name = "LAST_MSRMT_VL")
    private Long lastMsrmtVl;

    @Column(name = "USE_QNT")
    private Long useQnt;

    @Column(name = "EXECUTION_ID")
    private String executionId;

    @Column(name = "SOURCE_REFS", length = 4000)
    private String sourceRefs;

}
