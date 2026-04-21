package com.gims.provider.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
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

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operation_id")
    private ApiPrvOperation operation;

    /** 사용API키 */
    @Column(name = "api_key", length = 200)
    private String apiKey;

    /** 요청IP */
    @Column(name = "client_ip", length = 50)
    private String clientIp;

    /** 요청파라미터(JSON) */
    @Column(name = "request_params", length = 2000)
    private String requestParams;

    /** 응답건수 */
    @Column(name = "response_count")
    private Integer responseCount;

    /** 상태 (SUCCESS/FAILED) */
    @Column(name = "status", length = 20)
    private String status;

    /** 에러메시지 */
    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    /** 처리시간(ms) */
    @Column(name = "duration_ms")
    private Long durationMs;

    /** 호출시각 */
    @Column(name = "called_at")
    private LocalDateTime calledAt;
}
