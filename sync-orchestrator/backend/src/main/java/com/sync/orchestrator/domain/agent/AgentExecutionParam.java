package com.sync.orchestrator.domain.agent;

import javax.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "agent_execution_param",
        uniqueConstraints = @UniqueConstraint(columnNames = {"agent_id", "param_id"}))
@org.hibernate.annotations.Table(appliesTo = "agent_execution_param", comment = "에이전트 실행 파라미터")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentExecutionParam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("PK")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @Column(name = "param_id", length = 50, nullable = false)
    @Comment("파라미터 ID")
    private String paramId;

    @Column(name = "label", length = 100)
    @Comment("표시 라벨")
    private String label;

    @Column(name = "description", columnDefinition = "TEXT")
    @Comment("설명")
    private String description;

    @Column(name = "data_type", length = 20)
    @Comment("데이터 타입 (STRING/DATETIME 등)")
    @Builder.Default
    private String dataType = "STRING";

    @Column(name = "default_value", length = 255)
    @Comment("기본값")
    private String defaultValue;

    @Column(name = "is_enabled")
    @Comment("활성화 여부")
    @Builder.Default
    private Boolean isEnabled = true;

    @Column(name = "display_order")
    @Comment("표시 순서")
    @Builder.Default
    private Integer displayOrder = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    @Comment("생성 시각")
    private LocalDateTime createdAt;
}
