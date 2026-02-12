package com.sync.agent.common.entity;

import javax.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "execution", indexes = {
    @Index(name = "idx_execution_started", columnList = "started_at DESC"),
    @Index(name = "idx_execution_id", columnList = "execution_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Execution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** 실행 식별자 (UUID 기반) */
    @Column(name = "execution_id", length = 100, nullable = false)
    private String executionId;

    /** 부모 실행 ID (실행 단위 추적용) */
    @Column(name = "parent_execution_id", length = 100)
    private String parentExecutionId;

    /** 실행한 Agent ID (relay-rsv, loader, relay-snd 등) */
    @Column(name = "agent_id", length = 50)
    private String agentId;

    @Column(name = "status", length = 20, nullable = false)
    private String status;  // RUNNING, SUCCESS, FAILED

    @Column(name = "total_read_count")
    private Integer totalReadCount;

    @Column(name = "total_write_count")
    private Integer totalWriteCount;

    @Column(name = "total_skip_count")
    private Integer totalSkipCount;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    /** 이 실행에 사용된 Source Datasource ID */
    @Column(name = "source_datasource_id", length = 100)
    private String sourceDatasourceId;

    /** 이 실행에 사용된 Target Datasource ID */
    @Column(name = "target_datasource_id", length = 100)
    private String targetDatasourceId;
}
