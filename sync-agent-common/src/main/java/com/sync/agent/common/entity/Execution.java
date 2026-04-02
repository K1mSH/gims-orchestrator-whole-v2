package com.sync.agent.common.entity;

import javax.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

import java.time.LocalDateTime;

/**
 * 파이프라인 실행 이력 JPA 엔티티 (Agent 로컬 DB)
 *
 * ExecutionService.recordExecutionStart()에서 INSERT, recordExecutionFinish()에서 UPDATE.
 * Orchestrator 중앙 DB의 ExecutionHistory와 별개로, 각 Agent가 자신의 실행 이력을 독립 관리.
 */
@Entity
@Table(name = "execution", indexes = {
    @Index(name = "idx_execution_started", columnList = "started_at DESC"),
    @Index(name = "idx_execution_id", columnList = "execution_id")
})
@org.hibernate.annotations.Table(appliesTo = "execution", comment = "파이프라인 실행 이력 (Agent 단위 실행 추적)")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Execution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    @Comment("자동 증가 PK")
    private Long id;

    @Column(name = "execution_id", length = 100, nullable = false)
    @Comment("실행 고유 ID (UUID)")
    private String executionId;

    @Column(name = "parent_execution_id", length = 100)
    @Comment("부모 실행 ID (Orchestrator 발행)")
    private String parentExecutionId;

    @Column(name = "agent_id", length = 50)
    @Comment("Agent 코드")
    private String agentId;

    @Column(name = "status", length = 20, nullable = false)
    @Comment("실행 상태 (RUNNING/SUCCESS/FAILED)")
    private String status;

    @Column(name = "total_read_count")
    @Comment("총 읽기 건수")
    private Integer totalReadCount;

    @Column(name = "total_write_count")
    @Comment("총 쓰기 건수")
    private Integer totalWriteCount;

    @Column(name = "total_skip_count")
    @Comment("총 스킵 건수")
    private Integer totalSkipCount;

    @Column(name = "duration_ms")
    @Comment("실행 소요 시간 (ms)")
    private Long durationMs;

    @Column(name = "error_message", length = 4000)
    @Comment("오류 메시지")
    private String errorMessage;

    @Column(name = "started_at")
    @Comment("실행 시작 시각")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    @Comment("실행 완료 시각")
    private LocalDateTime finishedAt;

    @Column(name = "source_datasource_id", length = 100)
    @Comment("소스 데이터소스 ID")
    private String sourceDatasourceId;

    @Column(name = "target_datasource_id", length = 100)
    @Comment("타겟 데이터소스 ID")
    private String targetDatasourceId;
}
