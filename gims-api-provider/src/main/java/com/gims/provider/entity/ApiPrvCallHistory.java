package com.gims.provider.entity;

import lombok.*;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * API 호출 이력
 * 테이블: api_prv_call_history
 */
@Entity
@Table(name = "api_prv_call_history", indexes = {
        @Index(name = "idx_api_prv_call_op", columnList = "operation_id"),
        @Index(name = "idx_api_prv_call_at", columnList = "called_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiPrvCallHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operation_id")
    private ApiPrvOperation operation;

    @Column(name = "api_key", length = 200)
    private String apiKey;

    @Column(name = "client_ip", length = 50)
    private String clientIp;

    @Column(name = "request_params", length = 2000)
    private String requestParams;

    @Column(name = "response_count")
    private Integer responseCount;

    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "called_at")
    private LocalDateTime calledAt;
}
