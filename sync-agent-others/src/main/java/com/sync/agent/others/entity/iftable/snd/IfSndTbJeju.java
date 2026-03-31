package com.sync.agent.others.entity.iftable.snd;

import lombok.*;
import org.hibernate.annotations.Comment;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * IF_SND 제주 보조관측망 수위 관측 데이터 엔티티.
 *
 * <p>SND 파이프라인에서 Target DB의 제주 관측 데이터를 IF_SND 테이블로 추출할 때 사용된다.
 * {@code source_refs}에 UK, {@code execution_id}에 인덱스가 설정되어 있다.</p>
 *
 * <p>테이블: {@code if_snd_tb_jeju}</p>
 *
 * @see com.infolink.collector.entity.TbJeju
 */
@Entity
@Table(name = "if_snd_tb_jeju",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_if_snd_tb_jeju_source_refs",
           columnNames = {"source_refs"}
       ),
       indexes = @Index(name = "idx_if_snd_tb_jeju_exec_id", columnList = "execution_id"))
@org.hibernate.annotations.Table(appliesTo = "if_snd_tb_jeju", comment = "IF_SND 제주 보조관측망 수위 관측 데이터")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IfSndTbJeju {

    @Id
    @Column(name = "rid")
    @Comment("Source RID 그대로 사용 (PK)")
    private Long rid;

    // ========== 비즈니스 컬럼 ==========

    @Column(name = "obsrvt_id", length = 30)
    @Comment("관측점 ID")
    private String obsrvtId;

    @Column(name = "ymd", length = 8)
    @Comment("관측일 (yyyyMMdd)")
    private String ymd;

    @Column(name = "data_time", length = 6)
    @Comment("관측시각 (HHmmss)")
    private String dataTime;

    @Column(name = "gl", length = 20)
    @Comment("지하수위")
    private String gl;

    @Column(name = "scond", length = 20)
    @Comment("전기전도도")
    private String scond;

    @Column(name = "wtemp", length = 20)
    @Comment("수온")
    private String wtemp;

    @Column(name = "msn", length = 20)
    @Comment("센서 식별")
    private String msn;

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
