package com.sync.orchestrator.domain.execution;

import javax.persistence.*;
import lombok.*;

/**
 * Step별 실행 결과 이력
 * - Agent finished 콜백에서 수신한 Step 결과를 저장
 * - execution_history와 execution_id로 연관
 */
@Entity
@Table(name = "execution_step_history", indexes = {
    @Index(name = "idx_step_history_execution", columnList = "execution_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExecutionStepHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "execution_id", length = 100, nullable = false)
    private String executionId;

    @Column(name = "step_id", length = 100, nullable = false)
    private String stepId;

    @Column(name = "status", length = 20)
    private String status;  // SUCCESS, FAILED, SKIPPED

    @Column(name = "read_count")
    private Integer readCount;

    @Column(name = "write_count")
    private Integer writeCount;

    @Column(name = "skip_count")
    private Integer skipCount;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "step_order")
    private Integer stepOrder;
}
