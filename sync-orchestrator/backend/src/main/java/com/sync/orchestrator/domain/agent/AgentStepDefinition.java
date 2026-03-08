package com.sync.orchestrator.domain.agent;

import javax.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "agent_step_definition",
        uniqueConstraints = @UniqueConstraint(columnNames = {"agent_id", "step_id"}))
@org.hibernate.annotations.Table(appliesTo = "agent_step_definition", comment = "에이전트 스텝 정의")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentStepDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("PK")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @Column(name = "step_id", length = 50, nullable = false)
    @Comment("스텝 ID")
    private String stepId;

    @Column(name = "step_name", length = 100)
    @Comment("스텝명")
    private String stepName;

    @Column(name = "description", columnDefinition = "TEXT")
    @Comment("설명")
    private String description;

    @Column(name = "display_order")
    @Comment("표시 순서")
    @Builder.Default
    private Integer displayOrder = 0;

    @Column(name = "enabled_by_default")
    @Comment("기본 활성화 여부")
    @Builder.Default
    private Boolean enabledByDefault = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    @Comment("생성 시각")
    private LocalDateTime createdAt;
}
