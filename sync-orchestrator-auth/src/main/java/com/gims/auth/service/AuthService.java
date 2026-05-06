package com.gims.auth.service;

import com.gims.auth.dto.UserDto;
import com.gims.auth.entity.AuthRefreshToken;
import com.gims.auth.entity.AuthUser;
import com.gims.auth.repository.AuthRefreshTokenRepository;
import com.gims.auth.repository.AuthUserRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;

/**
 * 로그인 / refresh / 로그아웃 비즈니스.
 *
 * <ul>
 *   <li>로그인 5회 연속 실패 → 30분 잠금 (locked_until)</li>
 *   <li>존재하지 않는 사용자도 BCrypt 1회 dummy 매칭 — timing-safe (사용자 존재 여부 leak 방지)</li>
 *   <li>refresh 1회용 회전 — 사용 시 기존 jti revoked + 새 jti INSERT</li>
 *   <li>로그아웃 — refresh jti revoked (idempotent — 위조/만료 토큰이라도 silent OK)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private static final int LOCK_THRESHOLD = 5;
    private static final int LOCK_DURATION_MINUTES = 30;

    private final AuthUserRepository userRepository;
    private final AuthRefreshTokenRepository refreshTokenRepository;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;

    /** timing-safe 더미 BCrypt hash — 사용자 존재 여부 leak 방지용 */
    private String dummyHash;

    @PostConstruct
    void initDummyHash() {
        this.dummyHash = passwordEncoder.encode("__timing_safe_dummy__");
    }

    // ============================================================
    // 로그인
    // ============================================================

    public LoginResult login(String username, String password) {
        AuthUser user = userRepository.findByAuthUsersId(username).orElse(null);
        if (user == null) {
            // 사용자 없음 — BCrypt 1회 dummy 매칭 후 동일한 INVALID_CREDENTIALS 응답
            passwordEncoder.matches(password, dummyHash);
            throw new IllegalArgumentException("INVALID_CREDENTIALS");
        }

        if (user.isLocked()) {
            throw new IllegalStateException("ACCOUNT_LOCKED");
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            user.onLoginFailure(LOCK_THRESHOLD, LOCK_DURATION_MINUTES);
            log.info("[AuthService] login failed: username={}, failCount={}", username, user.getFailCount());
            throw new IllegalArgumentException("INVALID_CREDENTIALS");
        }

        user.onLoginSuccess();

        String access = tokenService.createAccessToken(user.getId(), user.getRole());
        TokenService.RefreshToken refresh = tokenService.createRefreshToken(user.getId());

        refreshTokenRepository.save(AuthRefreshToken.builder()
            .jti(refresh.jti())
            .userId(user.getId())
            .expiresAt(refresh.expiresAt())
            .build());

        log.info("[AuthService] login success: userId={}, username={}", user.getId(), username);
        return new LoginResult(access, refresh.jwt(), refresh.expiresAt(), UserDto.from(user));
    }

    // ============================================================
    // Refresh — 1회용 회전
    // ============================================================

    public RefreshResult refresh(String refreshTokenJwt) {
        Claims claims = tokenService.verifyRefreshToken(refreshTokenJwt);
        String jti = claims.getId();
        Long userId = Long.parseLong(claims.getSubject());

        AuthRefreshToken stored = refreshTokenRepository.findByJti(jti)
            .orElseThrow(() -> new IllegalArgumentException("REFRESH_NOT_FOUND"));

        if (!stored.isUsable()) {
            throw new IllegalArgumentException("REFRESH_REVOKED_OR_EXPIRED");
        }

        AuthUser user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("USER_NOT_FOUND"));

        // 1회용 회전 — 기존 jti revoke + 새 토큰 발급
        stored.setRevoked(true);

        String newAccess = tokenService.createAccessToken(user.getId(), user.getRole());
        TokenService.RefreshToken newRefresh = tokenService.createRefreshToken(user.getId());

        refreshTokenRepository.save(AuthRefreshToken.builder()
            .jti(newRefresh.jti())
            .userId(user.getId())
            .expiresAt(newRefresh.expiresAt())
            .build());

        log.info("[AuthService] refresh rotated: userId={}, oldJti={}, newJti={}", userId, jti, newRefresh.jti());
        return new RefreshResult(newAccess, newRefresh.jwt(), newRefresh.expiresAt());
    }

    // ============================================================
    // 로그아웃 (idempotent)
    // ============================================================

    public void logout(String refreshTokenJwt) {
        try {
            Claims claims = tokenService.verifyRefreshToken(refreshTokenJwt);
            String jti = claims.getId();
            refreshTokenRepository.findByJti(jti).ifPresent(t -> t.setRevoked(true));
            log.info("[AuthService] logout: jti={}", jti);
        } catch (Exception e) {
            // 위조 / 만료 / 이미 revoked 모두 silent — cookie 만료가 본질
            log.debug("[AuthService] logout with invalid token (silent): {}", e.getMessage());
        }
    }

    // ============================================================
    // value objects
    // ============================================================

    public record LoginResult(
        String accessToken,
        String refreshToken,
        LocalDateTime refreshExpiresAt,
        UserDto user
    ) {}

    public record RefreshResult(
        String accessToken,
        String refreshToken,
        LocalDateTime refreshExpiresAt
    ) {}
}
