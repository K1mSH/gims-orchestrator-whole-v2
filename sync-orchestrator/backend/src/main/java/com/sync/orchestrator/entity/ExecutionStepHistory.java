package com.sync.orchestrator.entity;

import javax.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

@Entity
@Table(name = "execution_step_history", indexes = {
    @Index(name = "idx_step_history_execution", columnList = "execution_id")
})
@org.hibernate.annotations.Table(appliesTo = "execution_step_history", comment = "실행 스텝 이력")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExecutionStepHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("PK")
    private Long id;

    @Column(name = "execution_id", length = 100, nullable = false)
    @Comment("실행 이력 FK")
    private String executionId;

    @Column(name = "step_id", length = 100, nullable = false)
    @Comment("스텝 ID")
    private String stepId;

    @Column(name = "status", length = 20)
    @Comment("스텝 상태 (SUCCESS/FAILED/SKIPPED)")
    private String status;

    @Column(name = "read_count")
    @Comment("읽기 건수")
    private Integer readCount;

    @Column(name = "write_count")
    @Comment("쓰기 건수")
    private Integer writeCount;

    @Column(name = "skip_count")
    @Comment("스킵 건수")
    private Integer skipCount;

    @Column(name = "duration_ms")
    @Comment("소요 시간 (ms)")
    private Long durationMs;

    @Column(name = "error_message", columnDefinition = "TEXT")
    @Comment("오류 메시지")
    private String errorMessage;

    @Column(name = "step_order")
    @Comment("스텝 순서")
    private Integer stepOrder;
}
