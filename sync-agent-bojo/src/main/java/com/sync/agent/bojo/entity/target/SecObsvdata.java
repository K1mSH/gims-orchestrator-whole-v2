package com.sync.agent.bojo.entity.target;

import lombok.*;
import org.hibernate.annotations.Comment;

import javax.persistence.*;
import java.sql.Date;
import java.sql.Time;

/**
 * Target DB 관측데이터 엔티티.
 *
 * <p>Loader 파이프라인에서 IF_RSV의 관측 시계열 데이터를 UPSERT하는 최종 목적지이다.
 * 수위(gwdep), 수온(gwtemp), 전기전도도(ec) 등을 저장하며,
 * {@code source_refs}로 원본 추적, {@code execution_id}로 실행 이력 조회가 가능하다.</p>
 *
 * <p>테이블: {@code sec_obsvdata} (UK: source_refs, IDX: execution_id)</p>
 */
@Entity
@Table(name = "sec_obsvdata",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_sec_obsvdata_source_refs",
           columnNames = {"source_refs"}
       ),
       indexes = @Index(name = "idx_sec_obsvdata_exec_id", columnList = "execution_id"))
@org.hibernate.annotations.Table(appliesTo = "sec_obsvdata", comment = "Target 관측데이터")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SecObsvdata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    @Comment("PK")
    private Integer id;

    // ========== 비즈니스 컬럼 ==========

    @Column(name = "obsv_code")
    @Comment("관측소 코드")
    private String obsvCode;

    @Column(name = "obsv_date")
    @Comment("관측 일자")
    private Date obsvDate;

    @Column(name = "obsv_time")
    @Comment("관측 시각")
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
