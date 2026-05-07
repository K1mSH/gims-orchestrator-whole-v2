package com.infolink.auth.config;

import com.infolink.auth.service.TokenService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;

/**
 * auth 모듈 자체용 access cookie 인증 필터.
 *
 * <ul>
 *   <li>cookie 의 accessToken → TokenService 직접 검증 (auth 모듈은 자기 자신이 발급기 — JWKS 호출 X)</li>
 *   <li>검증 성공 → SecurityContext 에 userId / role 박음</li>
 *   <li>실패 / cookie 없음 → SecurityContext 비워둠 (Spring Security 가 401 처리)</li>
 * </ul>
 *
 * 검증자 모듈 (Backend / api-provider 등) 은 infolink-agent-common 의 JwtCookieAuthFilter 사용 — JwksClient 통해 검증.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthCookieFilter extends OncePerRequestFilter {

    private final TokenService tokenService;

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        Cookie cookie = WebUtils.getCookie(req, AuthCookieHelper.ACCESS_COOKIE);
        if (cookie != null && cookie.getValue() != null && !cookie.getValue().isBlank()) {
            try {
                Claims claims = tokenService.verifyAccessToken(cookie.getValue());
                Long userId = Long.parseLong(claims.getSubject());
                String role = claims.get("role", String.class);

                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    userId,
                    null,
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + (role == null ? "USER" : role.toUpperCase())))
                );
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (Exception e) {
                log.debug("[AuthCookieFilter] access token invalid: {}", e.getMessage());
                // SecurityContext 비워둠 — Spring Security 가 401
            }
        }

        chain.doFilter(req, res);
    }
}
