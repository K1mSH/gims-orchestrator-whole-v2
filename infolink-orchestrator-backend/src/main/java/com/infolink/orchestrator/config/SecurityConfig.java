package com.infolink.orchestrator.config;

import com.infolink.agent.common.config.ApiKeyFilter;
import com.infolink.agent.common.config.JwtCookieAuthFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextPersistenceFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtCookieAuthFilter jwtCookieAuthFilter;
    private final ApiKeyFilter apiKeyFilter;

    public SecurityConfig(JwtCookieAuthFilter jwtCookieAuthFilter, ApiKeyFilter apiKeyFilter) {
        this.jwtCookieAuthFilter = jwtCookieAuthFilter;
        this.apiKeyFilter = apiKeyFilter;
    }

    /**
     * ApiKeyFilter 의 servlet 자동 등록을 끔 — SecurityFilterChain 안으로 옮겨서
     * SecurityContextPersistenceFilter 이후에 동작하도록 (박은 SecurityContext 보존).
     */
    @Bean
    public FilterRegistrationBean<ApiKeyFilter> apiKeyFilterRegistration() {
        FilterRegistrationBean<ApiKeyFilter> reg = new FilterRegistrationBean<>(apiKeyFilter);
        reg.setEnabled(false);
        return reg;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .antMatchers("/actuator/**").permitAll()
                .antMatchers("/api/callback/**").permitAll()                       // Agent → Backend callback (시스템 간, X-API-Key 강화는 별 후속)
                .anyRequest().authenticated()                                      // 시스템 간 X-API-Key (ApiKeyFilter soft-mode 가 ROLE_SYSTEM 박음) 또는 운영자 JWT cookie
            )
            .exceptionHandling(eh -> eh
                .authenticationEntryPoint((req, res, ex) -> {
                    res.setStatus(401);
                    res.setContentType("application/json;charset=UTF-8");
                    res.getWriter().write("{\"error\":\"AUTH_REQUIRED\"}");
                })
                .accessDeniedHandler((req, res, ex) -> {
                    res.setStatus(403);
                    res.setContentType("application/json;charset=UTF-8");
                    res.getWriter().write("{\"error\":\"FORBIDDEN\"}");
                })
            )
            .addFilterAfter(apiKeyFilter, SecurityContextPersistenceFilter.class)        // 시스템 X-API-Key 검증 (soft-mode = 키 없으면 통과, 매치 시 ROLE_SYSTEM). SecurityContext 박는 게 persistence 직후라야 보존.
            .addFilterBefore(jwtCookieAuthFilter, UsernamePasswordAuthenticationFilter.class)   // 운영자 cookie 검증
            .build();
    }
}
