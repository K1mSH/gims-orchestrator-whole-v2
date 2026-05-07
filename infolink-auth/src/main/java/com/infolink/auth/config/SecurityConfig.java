package com.infolink.auth.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * auth 모듈 Spring Security 5 설정.
 *
 * Path 정책:
 * <ul>
 *   <li>POST /api/auth/login          permitAll — 로그인 자체</li>
 *   <li>POST /api/auth/refresh        permitAll — refresh cookie 자체 검증</li>
 *   <li>POST /api/auth/logout         permitAll — silent / cookie 만료</li>
 *   <li>GET  /.well-known/jwks.json   permitAll — RFC 표준, 공개키만</li>
 *   <li>GET  /actuator/health         permitAll</li>
 *   <li>그 외 (/api/auth/me, /api/auth/users**) authenticated — accessToken cookie 필수</li>
 * </ul>
 *
 * Stateless — 세션 미사용. CSRF disable — SameSite=Strict 로 대체.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final AuthCookieFilter authCookieFilter;

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .antMatchers(
                    "/api/auth/login",
                    "/api/auth/refresh",
                    "/api/auth/logout",
                    "/.well-known/**",
                    "/actuator/health"
                ).permitAll()
                .anyRequest().authenticated()
            )
            // 인증 누락 / 실패 시 — 401 + AUTH_REQUIRED (AUTH_DESIGN §13.8 응답 매트릭스)
            .exceptionHandling(eh -> eh
                .authenticationEntryPoint((req, res, ex) -> {
                    res.setStatus(HttpStatus.UNAUTHORIZED.value());
                    res.setContentType("application/json;charset=UTF-8");
                    res.getWriter().write("{\"error\":\"AUTH_REQUIRED\"}");
                })
                .accessDeniedHandler((req, res, ex) -> {
                    res.setStatus(HttpStatus.FORBIDDEN.value());
                    res.setContentType("application/json;charset=UTF-8");
                    res.getWriter().write("{\"error\":\"FORBIDDEN\"}");
                })
            )
            .addFilterBefore(authCookieFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }
}
