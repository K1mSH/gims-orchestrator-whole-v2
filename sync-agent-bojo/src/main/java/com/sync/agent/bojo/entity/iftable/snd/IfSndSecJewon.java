package com.sync.agent.bojo.entity.iftable.snd;

import lombok.*;
import org.hibernate.annotations.Comment;

import javax.persistence.*;
import java.sql.Date;
import java.time.LocalDateTime;

@Entity
@Table(name = "if_snd_sec_jewon",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_if_snd_sec_jewon_source_refs",
           columnNames = {"source_refs"}
       ))
@org.hibernate.annotations.Table(appliesTo = "if_snd_sec_jewon", comment = "IF_SND 송신 제원")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IfSndSecJewon {

    @Id
    @Column(name = "id")
    @Comment("Source ID 그대로 사용 (PK)")
    private Integer id;

    // ========== 비즈니스 컬럼 ==========

    @Column(name = "obsv_code")
    @Comment("관측소 코드")
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

    // ========== IF 메타 컬럼 ==========

    @Column(name = "source_refs", columnDefinition = "TEXT")
    @Comment("원본 참조키 (UK)")
    private String sourceRefs;

    @Builder.Default
    @Column(name = "link_status", length = 20)
    @Comment("연계 상태 (PENDING/SUCCESS/FAILED)")
    private String linkStatus = "PENDING";

    @Column(name = "extracted_at")
    @Comment("추출 시각")
    private LocalDateTime extractedAt;

    @Column(name = "updated_at")
    @Comment("수정 시각")
    private LocalDateTime updatedAt;

    @Column(name = "execution_id")
    @Comment("처리 실행 ID")
    private String executionId;
}
