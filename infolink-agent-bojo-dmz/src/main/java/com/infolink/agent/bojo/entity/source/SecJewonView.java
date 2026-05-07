package com.infolink.agent.bojo.entity.source;

import lombok.*;
import org.hibernate.annotations.Comment;

import javax.persistence.*;
import java.sql.Date;

/**
 * 외부 소스 DB의 제원(관측소 기본정보) 뷰 엔티티 (읽기 전용).
 *
 * <p>RCV 파이프라인에서 외부 업체 DB의 제원 데이터를 SELECT할 때 사용된다.
 * PK는 {@code obsv_code}이며, 뷰이므로 INSERT/UPDATE 대상이 아니다.</p>
 *
 * <p>테이블(뷰): {@code sec_jewon_view}</p>
 */
@Entity
@Table(name = "sec_jewon_view")
@org.hibernate.annotations.Table(appliesTo = "sec_jewon_view", comment = "외부 소스 제원 뷰")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SecJewonView {

    @Id
    @Column(name = "obsv_code")
    @Comment("관측소 코드 (PK)")
    private String obsvCode;

    @Column(name = "obsv_name")
    @Comment("관측소 명칭")
    private String obsvName;

    @Column(name = "well")
    @Comment("관정 번호")
    private Integer well;

    @Column(name = "sido")
    @Comment("시도")
    private String sido;

    @Column(name = "sigungu")
    @Comment("시군구")
    private String sigungu;

    @Column(name = "upmyundo")
    @Comment("읍면동")
    private String upmyundo;

    @Column(name = "bunji")
    @Comment("번지")
    private String bunji;

    @Column(name = "ri")
    @Comment("리")
    private String ri;

    @Column(name = "x")
    @Comment("경도 (X좌표)")
    private String x;

    @Column(name = "y")
    @Comment("위도 (Y좌표)")
    private String y;

    @Column(name = "pyogo")
    @Comment("표고 (지반고, m)")
    private Double pyogo;

    @Column(name = "insdate")
    @Comment("설치일")
    private Date insdate;

    @Column(name = "guldep")
    @Comment("굴착 깊이 (m)")
    private Double guldep;

    @Column(name = "guldia")
    @Comment("굴착 지름 (mm)")
    private Double guldia;

    @Column(name = "regdate")
    @Comment("등록일")
    private Date regdate;

    @Column(name = "casing_height")
    @Comment("케이싱 높이 (m)")
    private Double casingHeight;
}
