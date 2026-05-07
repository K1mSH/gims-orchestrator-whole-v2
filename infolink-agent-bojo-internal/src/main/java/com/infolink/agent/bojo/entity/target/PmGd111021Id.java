package com.infolink.agent.bojo.entity.target;

import lombok.*;
import java.time.LocalDateTime;
import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class PmGd111021Id implements Serializable {

    private Long brnchId;
    private LocalDateTime obsrvnDt;

}
