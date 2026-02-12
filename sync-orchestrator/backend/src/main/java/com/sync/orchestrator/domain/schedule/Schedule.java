package com.sync.orchestrator.domain.schedule;

import com.sync.orchestrator.domain.agent.Agent;
import javax.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "schedule")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Schedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "schedule_id")
    private Long scheduleId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @Column(name = "cron_expression", length = 50, nullable = false)
    private String cronExpression;

    @Column(name = "is_enabled")
    @Builder.Default
    private Boolean isEnabled = true;

    /**
     * 실행 옵션 JSON (필터 등)
     * 예: {"filters":[{"paramId":"sido","column":"sido","operator":"EQ","value":"경기도"}]}
     */
    @Column(name = "execution_options", columnDefinition = "TEXT")
    private String executionOptions;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
