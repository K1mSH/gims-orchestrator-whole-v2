package com.infolink.auth.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * accessToken / refreshToken cookie 생성 헬퍼.
 *
 * <ul>
 *   <li>HttpOnly — JS 접근 차단 (XSS 방어)</li>
 *   <li>SameSite=Strict — cross-site 요청에 cookie 자동 차단 (CSRF 대체)</li>
 *   <li>Secure — 운영 HTTPS 만 전송 (yml `auth.cookie.secure`)</li>
 *   <li>Refresh cookie path=/api/auth — 다른 endpoint 에 안 보냄</li>
 * </ul>
 */
@Component
public class AuthCookieHelper {

    public static final String ACCESS_COOKIE = "accessToken";
    public static final String REFRESH_COOKIE = "refreshToken";
    private static final String REFRESH_PATH = "/api/auth";
    private static final String DEFAULT_PATH = "/";

    @Value("${auth.cookie.secure:false}")
    private boolean secure;

    @Value("${auth.cookie.same-site:Strict}")
    private String sameSite;

    @Value("${auth.access-token-ttl-minutes:15}")
    private int accessTtlMin;

    @Value("${auth.refresh-token-ttl-days:7}")
    private int refreshTtlDays;

    public ResponseCookie accessCookie(String token) {
        return baseCookie(ACCESS_COOKIE, token, DEFAULT_PATH)
            .maxAge(Duration.ofMinutes(accessTtlMin))
            .build();
    }

    public ResponseCookie refreshCookie(String token) {
        return baseCookie(REFRESH_COOKIE, token, REFRESH_PATH)
            .maxAge(Duration.ofDays(refreshTtlDays))
            .build();
    }

    public ResponseCookie expireAccessCookie() {
        return baseCookie(ACCESS_COOKIE, "", DEFAULT_PATH)
            .maxAge(0)
            .build();
    }

    public ResponseCookie expireRefreshCookie() {
        return baseCookie(REFRESH_COOKIE, "", REFRESH_PATH)
            .maxAge(0)
            .build();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String name, String value, String path) {
        return ResponseCookie.from(name, value)
            .httpOnly(true)
            .secure(secure)
            .sameSite(sameSite)
            .path(path);
    }
}
