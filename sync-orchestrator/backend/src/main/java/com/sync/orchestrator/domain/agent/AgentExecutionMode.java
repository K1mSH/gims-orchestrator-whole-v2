package com.sync.orchestrator.domain.agent;

import javax.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Agent 실행 모드 메타데이터
 * Agent API에서 조회한 실행 모드 정의를 Orchestrator DB에 저장/관리
 */
@Entity
@Table(name = "agent_execution_mode",
        uniqueConstraints = @UniqueConstraint(columnNames = {"agent_id", "mode_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentExecutionMode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @Column(name = "mode_id", length = 50, nullable = false)
    private String modeId;          // "incremental", "full-reload"

    @Column(name = "mode_name", length = 100, nullable = false)
    private String modeName;        // "증분 적재", "전체 재적재"

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "display_order")
    @Builder.Default
    private Integer displayOrder = 0;

    @Column(name = "is_default")
    @Builder.Default
    private Boolean isDefault = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
