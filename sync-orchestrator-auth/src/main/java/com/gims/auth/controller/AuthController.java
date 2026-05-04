package com.gims.auth.controller;

import com.gims.auth.config.AuthCookieHelper;
import com.gims.auth.dto.LoginRequest;
import com.gims.auth.dto.UserDto;
import com.gims.auth.entity.AuthUser;
import com.gims.auth.repository.AuthUserRepository;
import com.gims.auth.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthCookieHelper cookieHelper;
    private final AuthUserRepository userRepository;

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @RequestBody LoginRequest req,
            HttpServletResponse response) {
        AuthService.LoginResult result = authService.login(req.username(), req.password());
        response.addHeader(HttpHeaders.SET_COOKIE, cookieHelper.accessCookie(result.accessToken()).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, cookieHelper.refreshCookie(result.refreshToken()).toString());
        return ResponseEntity.ok(Map.of("user", result.user()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<Void> refresh(
            @CookieValue(value = AuthCookieHelper.REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletResponse response) {
        if (refreshToken == null) {
            throw new IllegalArgumentException("REFRESH_TOKEN_REQUIRED");
        }
        AuthService.RefreshResult result = authService.refresh(refreshToken);
        response.addHeader(HttpHeaders.SET_COOKIE, cookieHelper.accessCookie(result.accessToken()).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, cookieHelper.refreshCookie(result.refreshToken()).toString());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(value = AuthCookieHelper.REFRESH_COOKIE, required = false) String refreshToken,
            HttpServletResponse response) {
        if (refreshToken != null) {
            authService.logout(refreshToken);
        }
        // 위조 / 만료 / 미첨부 모두 idempotent — cookie 만 만료시킴
        response.addHeader(HttpHeaders.SET_COOKIE, cookieHelper.expireAccessCookie().toString());
        response.addHeader(HttpHeaders.SET_COOKIE, cookieHelper.expireRefreshCookie().toString());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserDto> me() {
        Long userId = currentUserId();
        AuthUser user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("USER_NOT_FOUND"));
        return ResponseEntity.ok(UserDto.from(user));
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (Long) auth.getPrincipal();
    }
}
