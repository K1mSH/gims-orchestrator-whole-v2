package com.infolink.agent.common.auth;

/**
 * JWKS endpoint fetch 실패 (auth 모듈 다운 등).
 * 검증자 측 JwtCookieAuthFilter 가 잡아 503 응답 변환.
 */
public class AuthFetchFailedException extends RuntimeException {
    public AuthFetchFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
