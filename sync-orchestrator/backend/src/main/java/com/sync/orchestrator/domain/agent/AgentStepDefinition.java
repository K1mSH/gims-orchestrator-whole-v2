package com.sync.orchestrator.domain.agent;

import javax.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Agent의 Step 메타데이터
 * Agent API에서 조회한 Step 정의를 Orchestrator DB에 저장/관리
 */
@Entity
@Table(name = "agent_step_definition",
        uniqueConstraints = @UniqueConstraint(columnNames = {"agent_id", "step_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentStepDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @Column(name = "step_id", length = 50, nullable = false)
    private String stepId;          // "jewon-extract"

    @Column(name = "step_name", length = 100)
    private String stepName;        // "제원 데이터 추출"

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;     // "IF_SND → IF_RSV 제원 복사"

    @Column(name = "display_order")
    @Builder.Default
    private Integer displayOrder = 0;

    @Column(name = "enabled_by_default")
    @Builder.Default
    private Boolean enabledByDefault = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
