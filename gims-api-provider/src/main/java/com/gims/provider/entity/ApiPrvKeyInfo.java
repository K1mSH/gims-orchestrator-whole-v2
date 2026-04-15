package com.gims.provider.entity;

import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * API Key 관리
 * 테이블: api_prv_key_info
 */
@Entity
@Table(name = "api_prv_key_info", uniqueConstraints = {
        @UniqueConstraint(name = "uk_api_prv_key", columnNames = "api_key")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiPrvKeyInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "api_key", length = 200, nullable = false)
    private String apiKey;

    @Column(name = "client_name", length = 200, nullable = false)
    private String clientName;

    @Column(name = "allowed_ips", length = 1000)
    private String allowedIps;

    @Column(name = "allowed_operations", length = 1000)
    private String allowedOperations;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
