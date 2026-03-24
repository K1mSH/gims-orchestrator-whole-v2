package com.infolink.collector.entity;

import lombok.*;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "api_schedule")
@org.hibernate.annotations.Table(appliesTo = "api_schedule", comment = "API 수집 스케줄")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("PK")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "api_endpoint_id", nullable = false)
    private ApiEndpoint apiEndpoint;

    @Column(name = "cron_expression", length = 50, nullable = false)
    @Comment("Cron 표현식 (Spring 6자리)")
    private String cronExpression;

    @Column(name = "is_enabled")
    @Comment("활성화 여부")
    @Builder.Default
    private Boolean isEnabled = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    @Comment("생성 시각")
    private LocalDateTime createdAt;
}
