package com.gims.auth.service;

import com.gims.auth.dto.UserDto;
import com.gims.auth.entity.AuthRefreshToken;
import com.gims.auth.entity.AuthUser;
import com.gims.auth.repository.AuthRefreshTokenRepository;
import com.gims.auth.repository.AuthRsaKeyRepository;
import com.gims.auth.repository.AuthUserRepository;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class AuthServiceTest {

    @Autowired AuthService authService;
    @Autowired UserService userService;
    @Autowired TokenService tokenService;
    @Autowired KeyService keyService;
    @Autowired AuthUserRepository userRepository;
    @Autowired AuthRefreshTokenRepository refreshTokenRepository;
    @Autowired AuthRsaKeyRepository keyRepository;

    @BeforeEach
    void ensureActiveKey() {
        if (keyRepository.findByActiveTrue().isEmpty()) {
            keyService.generateAndSave(true);
        }
    }

    // ============================================================
    // 로그인 (§12.6 #6)
    // ============================================================

    @Test
    @DisplayName("로그인 성공 — access/refresh 발급 + last_login_at 갱신 + refresh DB INSERT")
    void login_success() {
        UserDto u = userService.addUser("alice", "alicePw1", "앨리스");

        AuthService.LoginResult result = authService.login("alice", "alicePw1");

        assertNotNull(result.accessToken());
        assertNotNull(result.refreshToken());
        assertNotNull(result.refreshExpiresAt());
        assertEquals("alice", result.user().authUsersId());
        assertEquals("앨리스", result.user().name());

        // last_login_at 갱신
        AuthUser updated = userRepository.findById(u.id()).orElseThrow();
        assertNotNull(updated.getLastLoginAt());
        assertEquals(0, updated.getFailCount());

        // refresh DB row INSERT
        Claims claims = tokenService.verifyRefreshToken(result.refreshToken());
        assertTrue(refreshTokenRepository.findByJti(claims.getId()).isPresent());
    }

    // ============================================================
    // 로그인 실패 / 잠금 (§12.6 #7, #24)
    // ============================================================

    @Test
    @DisplayName("잘못된 비번 → fail_count++ + INVALID_CREDENTIALS")
    void login_wrong_password() {
        UserDto u = userService.addUser("fail1", "rightPw1", "사용자");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> authService.login("fail1", "wrong_pw"));
        assertEquals("INVALID_CREDENTIALS", ex.getMessage());

        AuthUser updated = userRepository.findById(u.id()).orElseThrow();
        assertEquals(1, updated.getFailCount());
        assertNull(updated.getLockedUntil());
    }

    @Test
    @DisplayName("연속 5회 실패 → 30분 잠금")
    void login_locks_after_5_failures() {
        UserDto u = userService.addUser("locker", "rightPw1", "사용자");

        for (int i = 0; i < 5; i++) {
            assertThrows(IllegalArgumentException.class,
                () -> authService.login("locker", "wrong_pw"));
        }

        AuthUser updated = userRepository.findById(u.id()).orElseThrow();
        assertEquals(5, updated.getFailCount());
        assertTrue(updated.isLocked());
        assertNotNull(updated.getLockedUntil());
    }

    @Test
    @DisplayName("잠긴 계정은 정상 비번이라도 ACCOUNT_LOCKED 거부")
    void login_locked_account_rejected_even_with_correct_password() {
        UserDto u = userService.addUser("lockedone", "rightPw1", "사용자");
        for (int i = 0; i < 5; i++) {
            assertThrows(IllegalArgumentException.class,
                () -> authService.login("lockedone", "wrong_pw"));
        }

        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> authService.login("lockedone", "rightPw1"));
        assertEquals("ACCOUNT_LOCKED", ex.getMessage());
    }

    @Test
    @DisplayName("존재하지 않는 사용자 → INVALID_CREDENTIALS (timing-safe — LOCKED 아닌 동일 코드)")
    void login_unknown_user() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> authService.login("noexist", "anyPw1234"));
        assertEquals("INVALID_CREDENTIALS", ex.getMessage());
    }

    // ============================================================
    // Refresh — 1회용 회전 (§12.6 #15, #16)
    // ============================================================

    @Test
    @DisplayName("refresh 정상 회전 — 새 토큰 + 기존 jti revoked")
    void refresh_rotates() {
        userService.addUser("ref", "refPwAB1", "사용자");
        AuthService.LoginResult login = authService.login("ref", "refPwAB1");

        // 기존 jti
        Claims oldClaims = tokenService.verifyRefreshToken(login.refreshToken());
        String oldJti = oldClaims.getId();

        AuthService.RefreshResult result = authService.refresh(login.refreshToken());

        // 새 토큰 다름
        assertNotEquals(login.accessToken(), result.accessToken());
        assertNotEquals(login.refreshToken(), result.refreshToken());

        // 기존 jti revoked
        AuthRefreshToken oldRow = refreshTokenRepository.findByJti(oldJti).orElseThrow();
        assertTrue(oldRow.isRevoked());

        // 새 jti DB row 존재
        Claims newClaims = tokenService.verifyRefreshToken(result.refreshToken());
        assertTrue(refreshTokenRepository.findByJti(newClaims.getId()).isPresent());
    }

    @Test
    @DisplayName("같은 refresh 두 번 사용 → 두 번째 거부 (1회용)")
    void refresh_cannot_reuse() {
        userService.addUser("reuse", "rePwAB12", "사용자");
        AuthService.LoginResult login = authService.login("reuse", "rePwAB12");

        // 1회 사용
        authService.refresh(login.refreshToken());

        // 2회 시도 — 거부 (revoked)
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> authService.refresh(login.refreshToken()));
        assertEquals("REFRESH_REVOKED_OR_EXPIRED", ex.getMessage());
    }

    @Test
    @DisplayName("위조 refresh 토큰 → 거부 (jjwt SignatureException 등)")
    void refresh_forged_token() {
        userService.addUser("forge", "fgPwAB12", "사용자");
        AuthService.LoginResult login = authService.login("forge", "fgPwAB12");

        // signature 한 글자 swap
        String[] parts = login.refreshToken().split("\\.");
        char first = parts[2].charAt(0);
        char swap = (first == 'A') ? 'B' : 'A';
        String tampered = parts[0] + "." + parts[1] + "." + swap + parts[2].substring(1);

        // tokenService.verifyRefreshToken 단계에서 SignatureException — RuntimeException 으로 잡음
        assertThrows(RuntimeException.class,
            () -> authService.refresh(tampered));
    }

    // ============================================================
    // 로그아웃 (§12.6 #22)
    // ============================================================

    @Test
    @DisplayName("로그아웃 → refresh jti revoked → 같은 토큰으로 refresh 불가")
    void logout_revokes_refresh() {
        userService.addUser("out", "outPwAB1", "사용자");
        AuthService.LoginResult login = authService.login("out", "outPwAB1");

        Claims claims = tokenService.verifyRefreshToken(login.refreshToken());
        String jti = claims.getId();

        authService.logout(login.refreshToken());

        // jti revoked=true
        AuthRefreshToken row = refreshTokenRepository.findByJti(jti).orElseThrow();
        assertTrue(row.isRevoked());

        // 같은 refresh 로 갱신 시도 거부
        assertThrows(IllegalArgumentException.class,
            () -> authService.refresh(login.refreshToken()));
    }

    @Test
    @DisplayName("로그아웃은 idempotent — 위조 토큰이라도 silent (예외 X)")
    void logout_silent_on_invalid_token() {
        // 잘못된 토큰 — 예외 안 던져야 함
        assertDoesNotThrow(() -> authService.logout("garbage.token.value"));
    }
}
