package com.infolink.agent.common.auth;

import com.infolink.agent.common.client.JwksClient;
import com.infolink.agent.common.config.JwtCookieAuthFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 검증자 모듈(Backend / api-provider / api-collector)에 JWKS 클라이언트 + JWT cookie 필터 자동 등록.
 *
 * <p>각 모듈의 메인 패키지(`com.infolink.agent.common.*` 외부)가 common 패키지를 component scan 하지 않으므로
 * Spring Boot Auto Configuration 패턴으로 자동 노출한다.
 *
 * <p>활성 조건 — yml 토글:
 * <ul>
 *   <li>{@code auth.jwks-url} 설정 → {@link JwksClient} 빈 등록</li>
 *   <li>{@code jwt.cookie.enabled=true} 설정 + JwksClient 존재 → {@link JwtCookieAuthFilter} 빈 등록</li>
 * </ul>
 *
 * <p>모듈이 자체 @Bean 으로 등록한 경우 {@link ConditionalOnMissingBean} 으로 회피.
 *
 * <p>등록은 {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports} 참고.
 */
@AutoConfiguration
public class AuthVerifierAutoConfiguration {

    @Bean
    @ConditionalOnProperty(name = "auth.jwks-url")
    @ConditionalOnMissingBean
    public JwksClient jwksClient(@Value("${auth.jwks-url}") String url) {
        return new JwksClient(url);
    }

    @Bean
    @ConditionalOnProperty(name = "jwt.cookie.enabled", havingValue = "true")
    @ConditionalOnBean(JwksClient.class)
    @ConditionalOnMissingBean
    public JwtCookieAuthFilter jwtCookieAuthFilter(JwksClient client,
                                                   @Value("${auth.issuer:orchestrator-auth}") String issuer,
                                                   @Value("${auth.audience:orchestrator}") String audience) {
        return new JwtCookieAuthFilter(client, issuer, audience);
    }

}
