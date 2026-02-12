package com.sync.agent.common.entity;

import javax.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "step_log", indexes = {
    @Index(name = "idx_step_log_execution", columnList = "execution_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StepLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "execution_id", length = 100, nullable = false)
    private String executionId;

    @Column(name = "step_id", length = 100, nullable = false)
    private String stepId;

    @Column(name = "step_name", length = 100)
    private String stepName;

    @Column(name = "step_order")
    private Integer stepOrder;

    @Column(name = "total_steps")
    private Integer totalSteps;

    @Column(name = "status", length = 20, nullable = false)
    private String status;  // RUNNING, SUCCESS, FAILED

    @Column(name = "read_count")
    private Integer readCount;

    @Column(name = "write_count")
    private Integer writeCount;

    @Column(name = "skip_count")
    private Integer skipCount;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "source_table", length = 100)
    private String sourceTable;

    @Column(name = "target_table", length = 100)
    private String targetTable;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;
}
