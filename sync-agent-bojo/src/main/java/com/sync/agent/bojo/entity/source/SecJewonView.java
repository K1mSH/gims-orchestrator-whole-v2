package com.sync.agent.bojo.entity.source;

import lombok.*;

import javax.persistence.*;
import java.sql.Date;

@Entity
@Table(name = "sec_jewon_view")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SecJewonView {

    @Id
    @Column(name = "obsv_code")
    private String obsvCode;

    @Column(name = "obsv_name")
    private String obsvName;

    @Column(name = "well")
    private Integer well;

    @Column(name = "sido")
    private String sido;

    @Column(name = "sigungu")
    private String sigungu;

    @Column(name = "upmyundo")
    private String upmyundo;

    @Column(name = "bunji")
    private String bunji;

    @Column(name = "ri")
    private String ri;

    @Column(name = "x")
    private String x;

    @Column(name = "y")
    private String y;

    @Column(name = "pyogo")
    private Double pyogo;

    @Column(name = "insdate")
    private Date insdate;

    @Column(name = "guldep")
    private Double guldep;

    @Column(name = "guldia")
    private Double guldia;

    @Column(name = "regdate")
    private Date regdate;

    @Column(name = "casing_height")
    private Double casingHeight;
}
