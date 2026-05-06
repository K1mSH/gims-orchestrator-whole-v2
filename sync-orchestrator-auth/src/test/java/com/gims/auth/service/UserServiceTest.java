package com.gims.auth.service;

import com.gims.auth.dto.UserDto;
import com.gims.auth.entity.AuthRefreshToken;
import com.gims.auth.entity.AuthUser;
import com.gims.auth.repository.AuthRefreshTokenRepository;
import com.gims.auth.repository.AuthUserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class UserServiceTest {

    @Autowired UserService userService;
    @Autowired AuthUserRepository userRepository;
    @Autowired AuthRefreshTokenRepository refreshTokenRepository;
    @Autowired PasswordEncoder passwordEncoder;

    // ============================================================
    // addUser
    // ============================================================

    @Test
    @DisplayName("사용자 추가 성공 + listUsers 에 노출 (password_hash 미노출)")
    void addUser_success() {
        UserDto created = userService.addUser("alice", "alicePw!1", "앨리스");
        assertNotNull(created.id());
        assertEquals("alice", created.authUsersId());
        assertEquals("앨리스", created.name());
        assertNotNull(created.createdAt());

        List<UserDto> all = userService.listUsers();
        assertTrue(all.stream().anyMatch(u -> "alice".equals(u.authUsersId())));

        // UserDto 자체에 password_hash 필드 없음 — record 정의로 보장됨
    }

    @Test
    @DisplayName("username 중복 시 IllegalStateException (AUTH_USERS_ID_DUPLICATE)")
    void addUser_duplicate_username() {
        userService.addUser("bob", "bobPwAbc1", "밥");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> userService.addUser("bob", "anotherPw", "다른"));
        assertEquals("AUTH_USERS_ID_DUPLICATE", ex.getMessage());
    }

    @Test
    @DisplayName("PW 8자 미만 거부 (PASSWORD_TOO_SHORT)")
    void addUser_short_password() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> userService.addUser("u1", "short", "사용자"));
        assertEquals("PASSWORD_TOO_SHORT", ex.getMessage());
    }

    @Test
    @DisplayName("name 비어있으면 거부 (NAME_REQUIRED)")
    void addUser_blank_name() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> userService.addUser("u2", "validPw1", "  "));
        assertEquals("NAME_REQUIRED", ex.getMessage());
    }

    // ============================================================
    // deleteMe — 마지막 1명 차단 (§12.6 #23)
    // ============================================================

    @Test
    @DisplayName("마지막 1명 본인 탈퇴 시도 → IllegalStateException (LAST_USER_CANNOT_DELETE)")
    void deleteMe_blocked_when_only_one_user() {
        UserDto solo = userService.addUser("solo", "soloPwAB", "혼자");
        // 트랜잭션 안이라 count = 1

        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> userService.deleteMe(solo.id()));
        assertEquals("LAST_USER_CANNOT_DELETE", ex.getMessage());

        // 사용자 그대로 살아있음
        assertTrue(userRepository.existsById(solo.id()));
    }

    @Test
    @DisplayName("2명 이상이면 본인 탈퇴 정상 + refresh 일괄 revoke")
    void deleteMe_works_when_multiple() {
        UserDto u1 = userService.addUser("multi1", "multiPw1", "사용자1");
        UserDto u2 = userService.addUser("multi2", "multiPw2", "사용자2");

        // u2 의 refresh token 2개 INSERT
        refreshTokenRepository.save(AuthRefreshToken.builder()
            .jti("u2-jti-1").userId(u2.id())
            .expiresAt(LocalDateTime.now().plusDays(7)).build());
        refreshTokenRepository.save(AuthRefreshToken.builder()
            .jti("u2-jti-2").userId(u2.id())
            .expiresAt(LocalDateTime.now().plusDays(7)).build());

        userService.deleteMe(u2.id());

        // u2 삭제됨
        assertFalse(userRepository.existsById(u2.id()));
        // u1 살아있음
        assertTrue(userRepository.existsById(u1.id()));
        // u2 의 refresh 모두 revoked
        assertTrue(refreshTokenRepository.findById("u2-jti-1").orElseThrow().isRevoked());
        assertTrue(refreshTokenRepository.findById("u2-jti-2").orElseThrow().isRevoked());
    }

    @Test
    @DisplayName("존재하지 않는 user 삭제 시도 → IllegalArgumentException")
    void deleteMe_user_not_found() {
        // 다른 사용자도 있는 상태로 만들어 LAST_USER 차단 우회
        userService.addUser("anchor", "anchorPw", "앵커");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> userService.deleteMe(99999L));
        assertEquals("USER_NOT_FOUND", ex.getMessage());
    }

    // ============================================================
    // changeMyPassword — refresh 일괄 revoke (§12.6 #21)
    // ============================================================

    @Test
    @DisplayName("본인 비번 변경 — current 일치 + refresh 일괄 revoke")
    void changeMyPassword_correct_current() {
        UserDto user = userService.addUser("changer", "oldPwAB1", "변경자");

        // refresh token 2개 INSERT (다른 디바이스 가정)
        refreshTokenRepository.save(AuthRefreshToken.builder()
            .jti("dev1-jti").userId(user.id())
            .expiresAt(LocalDateTime.now().plusDays(7)).build());
        refreshTokenRepository.save(AuthRefreshToken.builder()
            .jti("dev2-jti").userId(user.id())
            .expiresAt(LocalDateTime.now().plusDays(7)).build());

        userService.changeMyPassword(user.id(), "oldPwAB1", "newPwXY2");

        AuthUser updated = userRepository.findById(user.id()).orElseThrow();
        assertTrue(passwordEncoder.matches("newPwXY2", updated.getPasswordHash()));
        assertFalse(passwordEncoder.matches("oldPwAB1", updated.getPasswordHash()));

        // 모든 refresh revoked (다른 디바이스 강제 로그아웃)
        assertTrue(refreshTokenRepository.findById("dev1-jti").orElseThrow().isRevoked());
        assertTrue(refreshTokenRepository.findById("dev2-jti").orElseThrow().isRevoked());
    }

    @Test
    @DisplayName("현재 비번 불일치 시 거부 (CURRENT_PASSWORD_MISMATCH)")
    void changeMyPassword_wrong_current() {
        UserDto user = userService.addUser("wrongcur", "rightPw1", "사용자");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> userService.changeMyPassword(user.id(), "wrongCurPw", "newPwAB12"));
        assertEquals("CURRENT_PASSWORD_MISMATCH", ex.getMessage());

        // 비번 그대로
        AuthUser unchanged = userRepository.findById(user.id()).orElseThrow();
        assertTrue(passwordEncoder.matches("rightPw1", unchanged.getPasswordHash()));
    }

    @Test
    @DisplayName("새 비번 8자 미만이면 거부")
    void changeMyPassword_short_new() {
        UserDto user = userService.addUser("shortnew", "rightPw1", "사용자");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
            () -> userService.changeMyPassword(user.id(), "rightPw1", "x"));
        assertEquals("PASSWORD_TOO_SHORT", ex.getMessage());
    }
}
