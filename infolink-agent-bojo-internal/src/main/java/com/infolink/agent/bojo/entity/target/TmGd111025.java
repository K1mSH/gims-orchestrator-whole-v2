package com.infolink.agent.bojo.entity.target;

import java.time.LocalDateTime;
import javax.persistence.*;
import lombok.*;

@Entity
@Table(name = "TM_GD111025")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TmGd111025 {

    @Id
    @Column(name = "SN")
    private Long sn;

    @Column(name = "TELNO", length = 100)
    private String telno;

    @Column(name = "OBSRVN_DT")
    private LocalDateTime obsrvnDt;

    @Column(name = "LAST_CHG_DT")
    private LocalDateTime lastChgDt;

    @Column(name = "EXECUTION_ID")
    private String executionId;

    @Column(name = "SOURCE_REFS", length = 4000)
    private String sourceRefs;

}
