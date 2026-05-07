package com.infolink.agent.bojo.entity.target;

import java.time.LocalDateTime;
import javax.persistence.*;
import lombok.*;

@Entity
@Table(name = "PM_GD970201")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PmGd970201 {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "OBSRVN_DATA_ID")
    private Long obsrvnDataId;

    @Column(name = "RSLT_ID", nullable = false)
    private Long rsltId;

    @Column(name = "OBSRVN_DATA_VL", length = 20)
    private String obsrvnDataVl;

    @Column(name = "OBSRVN_DT", nullable = false)
    private LocalDateTime obsrvnDt;

    @Column(name = "QLT_ID", nullable = false)
    private Long qltId;

    @Column(name = "EXECUTION_ID")
    private String executionId;

    @Column(name = "SOURCE_REFS", length = 4000)
    private String sourceRefs;

}
