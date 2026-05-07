package com.infolink.auth.entity;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;
import java.time.LocalDateTime;

/**
 * RSA 키 페어 메타.
 *
 * <ul>
 *   <li>매일 자정 회전 — 새 페어 INSERT, 기존 active 는 false 로 (검증용 보관)</li>
 *   <li>active=true = 1쌍만 (서명용)</li>
 *   <li>만료 안 된 비활성 ~7쌍 = 검증용</li>
 *   <li>8일 보관 (refresh 7일 + 1일 여유) → expires_at 지나면 cleanup</li>
 *   <li>private_pem_enc = jasypt ENC 적용 (마스터 PW 노출 시에만 복호화 가능)</li>
 *   <li>public_pem = JWKS endpoint 로 노출 (안전)</li>
 * </ul>
 */
@Entity
@Table(
    name = "auth_rsa_keys",
    indexes = {
        @Index(name = "idx_auth_rsa_active", columnList = "active"),
        @Index(name = "idx_auth_rsa_expires", columnList = "expires_at")
    }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthRsaKey {

    /** 키 식별자 (UUID) — JWT header 의 kid 와 매칭 */
    @Id
    @Column(length = 64)
    private String kid;

    @Column(name = "public_pem", nullable = false, columnDefinition = "TEXT")
    private String publicPem;

    /** 개인키 PEM — jasypt ENC 적용된 형태로 저장 */
    @Column(name = "private_pem_enc", nullable = false, columnDefinition = "TEXT")
    private String privatePemEnc;

    @Column(nullable = false)
    private boolean active = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Builder
    public AuthRsaKey(String kid, String publicPem, String privatePemEnc, boolean active, LocalDateTime expiresAt) {
        this.kid = kid;
        this.publicPem = publicPem;
        this.privatePemEnc = privatePemEnc;
        this.active = active;
        this.expiresAt = expiresAt;
        this.createdAt = LocalDateTime.now();
    }

    public boolean isExpired() {
        return expiresAt.isBefore(LocalDateTime.now());
    }
}
