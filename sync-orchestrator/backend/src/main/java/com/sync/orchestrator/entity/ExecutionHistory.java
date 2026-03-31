package com.sync.orchestrator.entity;

import javax.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;

@Entity
@Table(name = "execution_history", indexes = {
    @Index(name = "idx_execution_history_agent", columnList = "agent_code"),
    @Index(name = "idx_execution_history_status", columnList = "status"),
    @Index(name = "idx_execution_history_started", columnList = "started_at DESC")
})
@org.hibernate.annotations.Table(appliesTo = "execution_history", comment = "실행 이력")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExecutionHistory {

    @Id
    @Column(name = "execution_id", length = 100)
    @Comment("실행 ID (PK)")
    private String executionId;

    @Column(name = "agent_code", length = 50, nullable = false)
    @Comment("에이전트 코드")
    private String agentCode;

    @Column(name = "agent_name", length = 100)
    @Comment("에이전트명")
    private String agentName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    @Comment("실행 상태 (RUNNING/SUCCESS/FAILED)")
    private ExecutionStatus status;

    @Column(name = "total_read_count")
    @Comment("읽기 건수")
    private Long totalReadCount;

    @Column(name = "total_write_count")
    @Comment("쓰기 건수")
    private Long totalWriteCount;

    @Column(name = "total_skip_count")
    @Comment("스킵 건수")
    private Long totalSkipCount;

    @Column(name = "duration_ms")
    @Comment("소요 시간 (ms)")
    private Long durationMs;

    @Column(name = "error_message", columnDefinition = "TEXT")
    @Comment("오류 메시지")
    private String errorMessage;

    @Column(name = "started_at")
    @Comment("실행 시작 시각")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    @Comment("실행 종료 시각")
    private LocalDateTime finishedAt;

    @Column(name = "triggered_by", length = 20)
    @Comment("실행 주체 (MANUAL/SCHEDULE/CHAIN)")
    @Builder.Default
    private String triggeredBy = "MANUAL";

    @Column(name = "agent_type", length = 30)
    @Comment("에이전트 유형")
    private String agentType;
}
