package com.sync.orchestrator.domain.agent;

import javax.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Agent의 실행 파라미터 메타데이터
 * Agent API에서 조회한 파라미터를 Orchestrator DB에 저장/관리
 */
@Entity
@Table(name = "agent_execution_param",
        uniqueConstraints = @UniqueConstraint(columnNames = {"agent_id", "param_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentExecutionParam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @Column(name = "param_id", length = 50, nullable = false)
    private String paramId;         // "sido"

    @Column(name = "label", length = 100)
    private String label;           // "시도"

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;     // "시도 단위 필터링"

    @Column(name = "data_type", length = 20)
    @Builder.Default
    private String dataType = "STRING";

    @Column(name = "default_value", length = 255)
    private String defaultValue;

    @Column(name = "is_enabled")
    @Builder.Default
    private Boolean isEnabled = true;

    @Column(name = "display_order")
    @Builder.Default
    private Integer displayOrder = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
