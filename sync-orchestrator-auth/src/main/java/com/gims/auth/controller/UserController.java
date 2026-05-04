package com.gims.auth.controller;

import com.gims.auth.config.AuthCookieHelper;
import com.gims.auth.dto.AddUserRequest;
import com.gims.auth.dto.ChangePasswordRequest;
import com.gims.auth.dto.UserDto;
import com.gims.auth.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.util.List;

@RestController
@RequestMapping("/api/auth/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AuthCookieHelper cookieHelper;

    /** 새 사용자 추가 (peer multiplication) — 등록자가 ID/PW/이름 모두 입력 */
    @PostMapping
    public ResponseEntity<UserDto> add(@RequestBody AddUserRequest req) {
        UserDto created = userService.addUser(req.username(), req.password(), req.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /** 사용자 목록 — 로그인 사용자 누구나 조회 */
    @GetMapping
    public ResponseEntity<List<UserDto>> list() {
        return ResponseEntity.ok(userService.listUsers());
    }

    /** 본인 탈퇴 — 마지막 1명 차단 / refresh 일괄 revoke / cookie 만료 */
    @DeleteMapping("/me")
    public ResponseEntity<Void> deleteMe(HttpServletResponse response) {
        Long userId = currentUserId();
        userService.deleteMe(userId);

        // 본인 탈퇴 = 즉시 cookie 만료
        response.addHeader(HttpHeaders.SET_COOKIE, cookieHelper.expireAccessCookie().toString());
        response.addHeader(HttpHeaders.SET_COOKIE, cookieHelper.expireRefreshCookie().toString());
        return ResponseEntity.noContent().build();
    }

    /** 본인 비번 변경 — current 검증 + 모든 refresh revoke (다른 디바이스 강제 로그아웃) */
    @PatchMapping("/me/password")
    public ResponseEntity<Void> changeMyPassword(@RequestBody ChangePasswordRequest req) {
        Long userId = currentUserId();
        userService.changeMyPassword(userId, req.currentPassword(), req.newPassword());
        return ResponseEntity.noContent().build();
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return (Long) auth.getPrincipal();
    }
}
