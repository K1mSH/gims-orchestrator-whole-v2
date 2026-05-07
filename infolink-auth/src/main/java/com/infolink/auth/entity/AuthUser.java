package com.infolink.auth.entity;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import java.time.LocalDateTime;

/**
 * Auth 사용자.
 *
 * <ul>
 *   <li>peer multiplication — 누구나 새 사용자 추가 (등록자 추적 X)</li>
 *   <li>단일 role = "user" (모두 동급)</li>
 *   <li>본인만 비번 변경 / 탈퇴 (마지막 1명 차단)</li>
 * </ul>
 */
@Entity
@Table(name = "auth_users", uniqueConstraints = {
    @UniqueConstraint(name = "uk_auth_users_auth_users_id", columnNames = "auth_users_id")
})
@DynamicUpdate
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 사용자가 입력하는 로그인 ID (auth_refresh_tokens.user_id 와 구분 위해 컬럼명 명시화). */
    @Column(name = "auth_users_id", nullable = false, length = 50)
    private String authUsersId;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    /** 담당자 이름 — UI 입력값 */
    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 20)
    private String role = "user";

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "fail_count", nullable = false)
    private int failCount = 0;

    /** 잠금 해제 시각 — NOW 까지 423 응답 */
    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Builder
    public AuthUser(String authUsersId, String passwordHash, String name) {
        this.authUsersId = authUsersId;
        this.passwordHash = passwordHash;
        this.name = name;
        this.role = "user";
        this.failCount = 0;
        this.createdAt = LocalDateTime.now();
    }

    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now());
    }

    public void onLoginSuccess() {
        this.failCount = 0;
        this.lockedUntil = null;
        this.lastLoginAt = LocalDateTime.now();
    }

    public void onLoginFailure(int lockThreshold, int lockDurationMinutes) {
        this.failCount++;
        if (this.failCount >= lockThreshold) {
            this.lockedUntil = LocalDateTime.now().plusMinutes(lockDurationMinutes);
        }
    }
}
