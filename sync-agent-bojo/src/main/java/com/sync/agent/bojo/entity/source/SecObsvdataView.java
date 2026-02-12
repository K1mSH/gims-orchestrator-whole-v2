package com.sync.agent.bojo.entity.source;

import lombok.*;

import javax.persistence.*;
import java.sql.Date;
import java.sql.Time;

/**
 * SEC_OBSVDATA_VIEW - Source 관측데이터 테이블 (External Zone)
 *
 * 사용: relay-dmz-rsv-bojo (읽기)
 * 흐름: sec_obsvdata_view (External) → if_sec_obsvdata_view (DMZ)
 *
 * 복합 PK: obsv_code + obsv_date + obsv_time
 */
@Entity
@Table(name = "sec_obsvdata_view")
@IdClass(SecObsvdataViewId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SecObsvdataView {

    @Id
    @Column(name = "obsv_code")
    private String obsvCode;

    @Id
    @Column(name = "obsv_date")
    private Date obsvDate;

    @Id
    @Column(name = "obsv_time")
    private Time obsvTime;

    @Column(name = "gwdep")
    private Double gwdep;

    @Column(name = "gwtemp")
    private Double gwtemp;

    @Column(name = "ec")
    private Double ec;

    @Column(name = "remark")
    private String remark;
}
