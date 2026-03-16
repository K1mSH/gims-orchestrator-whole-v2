package com.infolink.collector.domain;

import lombok.*;
import org.hibernate.annotations.Comment;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "api_execution_history")
@org.hibernate.annotations.Table(appliesTo = "api_execution_history", comment = "API 수집 실행 이력")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ApiExecutionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("PK")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "api_endpoint_id", nullable = false)
    private ApiEndpoint apiEndpoint;

    @Column(name = "execution_id", length = 36, nullable = false)
    @Comment("실행 UUID")
    private String executionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @Comment("실행 상태 (RUNNING/SUCCESS/FAILED)")
    private Status status;

    @Column(name = "http_status_code")
    @Comment("HTTP 응답 코드")
    private Integer httpStatusCode;

    @Column(name = "response_count")
    @Comment("파싱된 레코드 수")
    private Integer responseCount;

    @Column(name = "insert_count")
    @Comment("적재 성공 수")
    private Integer insertCount;

    @Column(name = "skip_count")
    @Comment("스킵 수")
    private Integer skipCount;

    @Column(name = "error_message", columnDefinition = "TEXT")
    @Comment("에러 메시지")
    private String errorMessage;

    @Column(name = "started_at")
    @Comment("시작 시각")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    @Comment("종료 시각")
    private LocalDateTime finishedAt;

    @Column(name = "duration_ms")
    @Comment("소요 시간 (ms)")
    private Long durationMs;

    @Enumerated(EnumType.STRING)
    @Column(name = "triggered_by", length = 20, nullable = false)
    @Comment("트리거 유형 (MANUAL/SCHEDULE)")
    private TriggeredBy triggeredBy;

    public enum Status {
        RUNNING, SUCCESS, FAILED
    }

    public enum TriggeredBy {
        MANUAL, SCHEDULE
    }
}
