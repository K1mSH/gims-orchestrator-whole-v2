package com.sync.agent.bojo.entity.source;

import lombok.*;

import java.io.Serializable;
import java.sql.Date;
import java.sql.Time;

/**
 * SecObsvdataView 복합 PK 클래스
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class SecObsvdataViewId implements Serializable {
    private String obsvCode;
    private Date obsvDate;
    private Time obsvTime;
}
