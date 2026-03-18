package com.sync.agent.bojo.entity.source;

import lombok.*;
import org.hibernate.annotations.Comment;

import javax.persistence.*;
import java.sql.Date;
import java.sql.Time;

/**
 * 외부 소스 DB의 관측데이터 뷰 엔티티 (읽기 전용).
 *
 * <p>RCV 파이프라인에서 외부 업체 DB의 관측 시계열 데이터를 SELECT할 때 사용된다.
 * 복합PK: {@code obsv_code} + {@code obsv_date} + {@code obsv_time}.</p>
 *
 * <p>테이블(뷰): {@code sec_obsvdata_view}</p>
 *
 * @see SecObsvdataViewId
 */
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
