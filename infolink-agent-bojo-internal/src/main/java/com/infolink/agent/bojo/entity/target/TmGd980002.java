package com.infolink.agent.bojo.entity.target;

import java.time.LocalDateTime;
import javax.persistence.*;
import lombok.*;

@Entity
@Table(name = "TM_GD980002")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TmGd980002 {

    @Column(name = "BRNCH_ID", nullable = false)
    private Long brnchId;

    @Id
    @Column(name = "OBSVTR_ID", length = 30)
    private String obsvtrId;

    @Column(name = "LAST_OBSRVN_YMD", length = 8, nullable = false)
    private String lastObsrvnYmd;

    @Column(name = "LAST_OBSRVN_HR", length = 20, nullable = false)
    private String lastObsrvnHr;

    @Column(name = "CHG_DT")
    private LocalDateTime chgDt;

    @Column(name = "LINK_TRGT_IP", length = 20)
    private String linkTrgtIp;

    @Column(name = "LINK_TRGT_PORT_CN", length = 100)
    private String linkTrgtPortCn;

    @Column(name = "FRST_OBSRVN_YMD", length = 8)
    private String frstObsrvnYmd;

    @Column(name = "FRST_OBSRVN_HR", length = 20)
    private String frstObsrvnHr;

    @Column(name = "EXECUTION_ID")
    private String executionId;

    @Column(name = "SOURCE_REFS", length = 4000)
    private String sourceRefs;

}
