package com.sync.agent.bojo.entity.iftable.snd;

import lombok.*;
import org.hibernate.annotations.Comment;

import javax.persistence.*;
import java.sql.Date;
import java.sql.Time;
import java.time.LocalDateTime;

/**
 * IF_SND 관측데이터 엔티티.
 *
 * <p>SND 파이프라인에서 Target DB의 관측데이터를 IF_SND 테이블로 추출할 때 사용된다.
 * 수위(gwdep), 수온(gwtemp), 전기전도도(ec) 등의 시계열 관측값을 보관한다.</p>
 *
 * <p>테이블: {@code if_snd_sec_obsvdata}</p>
 *
 * @see com.sync.agent.bojo.entity.target.SecObsvdata
 */
@Entity
@Table(name = "if_snd_sec_obsvdata",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_if_snd_sec_obsvdata_source_refs",
           columnNames = {"source_refs"}
       ),
       indexes = @Index(name = "idx_if_snd_sec_obsvdata_exec_id", columnList = "execution_id"))
@org.hibernate.annotations.Table(appliesTo = "if_snd_sec_obsvdata", comment = "IF_SND 송신 관측데이터")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IfSndSecObsvdata {

    @Id
    @Column(name = "id")
    @Comment("Source ID 그대로 사용 (PK)")
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
