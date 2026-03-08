package com.sync.agent.bojo.entity.source;

import lombok.*;
import org.hibernate.annotations.Comment;

import javax.persistence.*;
import java.sql.Date;
import java.sql.Time;

@Entity
@Table(name = "sec_obsvdata_view")
@org.hibernate.annotations.Table(appliesTo = "sec_obsvdata_view", comment = "외부 소스 관측데이터 뷰")
@IdClass(SecObsvdataViewId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SecObsvdataView {

    @Id
    @Column(name = "obsv_code")
    @Comment("관측소 코드 (복합PK)")
    private String obsvCode;

    @Id
    @Column(name = "obsv_date")
    @Comment("관측 일자 (복합PK)")
    private Date obsvDate;

    @Id
    @Column(name = "obsv_time")
    @Comment("관측 시각 (복합PK)")
    private Time obsvTime;

    @Column(name = "gwdep")
    @Comment("지하수위 (m)")
    private Double gwdep;

    @Column(name = "gwtemp")
    @Comment("지하수온도 (°C)")
    private Double gwtemp;

    @Column(name = "ec")
    @Comment("전기전도도 (μS/cm)")
    private Double ec;

    @Column(name = "remark")
    @Comment("비고")
    private String remark;
}
