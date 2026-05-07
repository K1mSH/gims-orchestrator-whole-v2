package com.infolink.orchestrator.entity;


import javax.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "schedule")
@org.hibernate.annotations.Table(appliesTo = "schedule", comment = "스케줄 설정")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Schedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "schedule_id")
    @Comment("PK")
    private Long scheduleId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @Column(name = "cron_expression", length = 50, nullable = false)
    @Comment("Cron 표현식")
    private String cronExpression;

    @Column(name = "is_enabled")
    @Comment("활성화 여부")
    @Builder.Default
    private Boolean isEnabled = true;

    @Column(name = "execution_options", columnDefinition = "TEXT")
    @Comment("조건실행 옵션 JSON")
    private String executionOptions;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    @Comment("생성 시각")
    private LocalDateTime createdAt;
}
