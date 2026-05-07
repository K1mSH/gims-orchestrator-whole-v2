package com.infolink.auth.service;

import com.infolink.auth.dto.UserDto;
import com.infolink.auth.entity.AuthUser;
import com.infolink.auth.repository.AuthRefreshTokenRepository;
import com.infolink.auth.repository.AuthUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 사용자 CRUD.
 *
 * <ul>
 *   <li><b>addUser</b> — peer multiplication: 로그인 사용자 누구나 새 계정 추가</li>
 *   <li><b>listUsers</b> — 누구나 조회 (민감정보 제외)</li>
 *   <li><b>deleteMe</b> — 본인만 / 마지막 1명 차단 / refresh 일괄 revoke</li>
 *   <li><b>changeMyPassword</b> — 본인만 / current 검증 / refresh 일괄 revoke</li>
 * </ul>
 *
 * 모든 메서드는 IllegalStateException / IllegalArgumentException 으로 실패 신호.
 * Controller (Step 7) 에서 HTTP 코드 매핑 (409/400/404 등).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private static final int MIN_PASSWORD_LENGTH = 8;

    private final AuthUserRepository userRepository;
    private final AuthRefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    // ============================================================
    // 사용자 추가 (peer multiplication)
    // ============================================================

    public UserDto addUser(String username, String password, String name) {
        validateUsername(username);
        validatePassword(password);
        validateName(name);

        if (userRepository.existsByAuthUsersId(username)) {
            throw new IllegalStateException("AUTH_USERS_ID_DUPLICATE");
        }

        AuthUser user = AuthUser.builder()
            .authUsersId(username)
            .passwordHash(passwordEncoder.encode(password))
            .name(name)
            .build();
        AuthUser saved = userRepository.save(user);

        log.info("[UserService] user added: id={}, username={}", saved.getId(), saved.getAuthUsersId());
        return UserDto.from(saved);
    }

    // ============================================================
    // 목록
    // ============================================================

    @Transactional(readOnly = true)
    public List<UserDto> listUsers() {
        return userRepository.findAll(Sort.by(Sort.Direction.ASC, "createdAt")).stream()
            .map(UserDto::from)
            .toList();
    }

    // ============================================================
    // 본인 탈퇴
    // ============================================================

    public void deleteMe(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("USER_NOT_FOUND");
        }
        long total = userRepository.count();
        if (total <= 1) {
            throw new IllegalStateException("LAST_USER_CANNOT_DELETE");
        }

        int revoked = refreshTokenRepository.revokeAllByUserId(userId);
        userRepository.deleteById(userId);

        log.info("[UserService] user deleted (self): userId={}, revokedRefreshTokens={}", userId, revoked);
    }

    // ============================================================
    // 본인 비밀번호 변경
    // ============================================================

    public void changeMyPassword(Long userId, String currentPassword, String newPassword) {
        AuthUser user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("USER_NOT_FOUND"));

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("CURRENT_PASSWORD_MISMATCH");
        }
        validatePassword(newPassword);

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(LocalDateTime.now());

        // 비번 변경 = 탈취 cookie 무력화 의도 → 모든 refresh revoke (다른 디바이스 강제 로그아웃)
        int revoked = refreshTokenRepository.revokeAllByUserId(userId);

        log.info("[UserService] password changed (self): userId={}, revokedRefreshTokens={}", userId, revoked);
    }

    // ============================================================
    // 검증 헬퍼
    // ============================================================

    private void validateUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("AUTH_USERS_ID_REQUIRED");
        }
        if (username.length() > 50) {
            throw new IllegalArgumentException("AUTH_USERS_ID_TOO_LONG");
        }
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("PASSWORD_TOO_SHORT");
        }
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("NAME_REQUIRED");
        }
        if (name.length() > 50) {
            throw new IllegalArgumentException("NAME_TOO_LONG");
        }
    }
}
