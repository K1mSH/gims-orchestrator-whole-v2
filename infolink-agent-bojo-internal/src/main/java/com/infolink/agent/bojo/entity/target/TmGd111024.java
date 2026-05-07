package com.infolink.agent.bojo.entity.target;

import java.time.LocalDateTime;
import javax.persistence.*;
import lombok.*;

@Entity
@Table(name = "TM_GD111024")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TmGd111024 {

    @Id
    @Column(name = "BRNCH_ID")
    private Long brnchId;

    @Column(name = "OBSRVN_DT", nullable = false)
    private LocalDateTime obsrvnDt;

    @Column(name = "EXECUTION_ID")
    private String executionId;

    @Column(name = "SOURCE_REFS", length = 4000)
    private String sourceRefs;

}
