package com.infolink.auth.controller;

import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Service 계층의 예외 → HTTP 코드 매핑.
 *
 * <p>Service 는 IllegalArgumentException / IllegalStateException 으로 의미 코드만 던지고,
 * 여기서 401/409/423/400 등 HTTP 상태로 변환.
 * AUTH_DESIGN.md §13.8 응답 정책 준수.</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleArg(IllegalArgumentException e) {
        String code = e.getMessage() != null ? e.getMessage() : "BAD_REQUEST";
        HttpStatus status = switch (code) {
            case "INVALID_CREDENTIALS",
                 "REFRESH_NOT_FOUND",
                 "REFRESH_REVOKED_OR_EXPIRED",
                 "REFRESH_TOKEN_REQUIRED" -> HttpStatus.UNAUTHORIZED;
            case "USER_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.BAD_REQUEST;
        };
        log.debug("[GlobalExceptionHandler] IllegalArgument: code={}, status={}", code, status);
        return ResponseEntity.status(status).body(Map.of("error", code));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleState(IllegalStateException e) {
        String code = e.getMessage() != null ? e.getMessage() : "CONFLICT";
        HttpStatus status = switch (code) {
            case "ACCOUNT_LOCKED" -> HttpStatus.LOCKED;        // 423
            case "AUTH_USERS_ID_DUPLICATE",
                 "LAST_USER_CANNOT_DELETE" -> HttpStatus.CONFLICT;  // 409
            default -> HttpStatus.CONFLICT;
        };
        log.debug("[GlobalExceptionHandler] IllegalState: code={}, status={}", code, status);
        return ResponseEntity.status(status).body(Map.of("error", code));
    }

    @ExceptionHandler(JwtException.class)
    public ResponseEntity<Map<String, String>> handleJwt(JwtException e) {
        log.debug("[GlobalExceptionHandler] JwtException: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(Map.of("error", "INVALID_TOKEN"));
    }
}
