package com.infolink.agent.bojo.entity.target;

import lombok.*;
import org.hibernate.annotations.Comment;

import javax.persistence.*;
import java.sql.Date;

/**
 * Target DB 제원(관측소 기본정보) 엔티티.
 *
 * <p>Loader 파이프라인에서 IF_RSV의 데이터를 UPSERT하는 최종 목적지이다.
 * {@code source_refs}로 원본 Source를 추적하며, {@code execution_id}로
 * 실행 이력을 조회할 수 있다.</p>
 *
 * <p>테이블: {@code sec_jewon} (UK: source_refs, IDX: execution_id)</p>
 *
 * @see com.infolink.agent.bojo.loader.repository.TargetRepositoryService
 */
@Entity
@Table(name = "sec_jewon",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_sec_jewon_source_refs",
           columnNames = {"source_refs"}
       ),
       indexes = @Index(name = "idx_sec_jewon_exec_id", columnList = "execution_id"))
@org.hibernate.annotations.Table(appliesTo = "sec_jewon", comment = "Target 제원")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SecJewon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    @Comment("PK")
    private Long id;

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

    // ========== 추적 컬럼 ==========

    @Column(name = "source_refs", columnDefinition = "TEXT")
    @Comment("원본 참조키 (UK)")
    private String sourceRefs;

    @Column(name = "link_status", length = 20)
    @Comment("연계 상태 (SYNCED/PENDING/ERROR)")
    private String linkStatus;

    @Column(name = "execution_id", length = 100)
    @Comment("처리 실행 ID")
    private String executionId;
}
