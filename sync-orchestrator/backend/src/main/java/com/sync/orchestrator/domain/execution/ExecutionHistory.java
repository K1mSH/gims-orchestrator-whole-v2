package com.sync.orchestrator.domain.execution;

import javax.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 실행 이력 (Orchestrator에서 중앙 관리)
 * - Agent callback으로 수신한 실행 결과 요약 저장
 * - 대시보드에서 전체 Agent 실행 현황 모니터링용
 */
@Entity
@Table(name = "execution_history", indexes = {
    @Index(name = "idx_execution_history_agent", columnList = "agent_code"),
    @Index(name = "idx_execution_history_status", columnList = "status"),
    @Index(name = "idx_execution_history_started", columnList = "started_at DESC")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExecutionHistory {

    @Id
    @Column(name = "execution_id", length = 100)
    private String executionId;

    @Column(name = "agent_code", length = 50, nullable = false)
    private String agentCode;

    @Column(name = "agent_name", length = 100)
    private String agentName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private ExecutionStatus status;

    @Column(name = "total_read_count")
    private Long totalReadCount;

    @Column(name = "total_write_count")
    private Long totalWriteCount;

    @Column(name = "total_skip_count")
    private Long totalSkipCount;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "triggered_by", length = 20)
    @Builder.Default
    private String triggeredBy = "MANUAL";  // MANUAL, SCHEDULE, CHAIN

    @Column(name = "agent_type", length = 30)
    private String agentType;  // RCV, SND, LOADER
}
