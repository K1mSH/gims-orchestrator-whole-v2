package com.gims.auth.entity;

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
 * Refresh Token 메타.
 *
 * <ul>
 *   <li>1회용 회전 — 사용 시 기존 jti revoked=true + 새 jti INSERT</li>
 *   <li>로그아웃 / 비번 변경 / 본인 탈퇴 시 revoked=true</li>
 *   <li>7일 + 1일 여유 = 8일 보관 (cleanup 은 별 작업 또는 회전 job 에 통합)</li>
 * </ul>
 */
@Entity
@Table(
    name = "auth_refresh_tokens",
    indexes = {
        @Index(name = "idx_auth_refresh_user", columnList = "user_id"),
        @Index(name = "idx_auth_refresh_expires", columnList = "expires_at")
    }
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthRefreshToken {

    /** JWT ID — refresh token 의 고유 식별자 (UUID 문자열) */
    @Id
    @Column(length = 64)
    private String jti;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private boolean revoked = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Builder
    public AuthRefreshToken(String jti, Long userId, LocalDateTime expiresAt) {
        this.jti = jti;
        this.userId = userId;
        this.expiresAt = expiresAt;
        this.revoked = false;
        this.createdAt = LocalDateTime.now();
    }

    public boolean isExpired() {
        return expiresAt.isBefore(LocalDateTime.now());
    }

    public boolean isUsable() {
        return !revoked && !isExpired();
    }
}
