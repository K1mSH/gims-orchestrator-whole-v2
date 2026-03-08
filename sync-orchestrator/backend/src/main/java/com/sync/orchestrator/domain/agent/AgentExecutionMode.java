package com.sync.orchestrator.domain.agent;

import javax.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "agent_execution_mode",
        uniqueConstraints = @UniqueConstraint(columnNames = {"agent_id", "mode_id"}))
@org.hibernate.annotations.Table(appliesTo = "agent_execution_mode", comment = "에이전트 실행 모드")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentExecutionMode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("PK")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @Column(name = "mode_id", length = 50, nullable = false)
    @Comment("실행 모드 ID")
    private String modeId;

    @Column(name = "mode_name", length = 100, nullable = false)
    @Comment("실행 모드명")
    private String modeName;

    @Column(name = "description", columnDefinition = "TEXT")
    @Comment("설명")
    private String description;

    @Column(name = "display_order")
    @Comment("표시 순서")
    @Builder.Default
    private Integer displayOrder = 0;

    @Column(name = "is_default")
    @Comment("기본 모드 여부")
    @Builder.Default
    private Boolean isDefault = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    @Comment("생성 시각")
    private LocalDateTime createdAt;
}
