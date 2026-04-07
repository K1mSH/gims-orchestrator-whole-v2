package com.sync.agent.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * API Key 인증 필터 (공통)
 *
 * /api/** 경로에 대해 X-API-Key 헤더를 검증한다.
 * /health 엔드포인트는 인증 없이 접근 가능.
 *
 * 활성화: application.yml에 common.filter.api-key.enabled=true 설정 필요.
 * API Key 값은 agent.api-key 프로퍼티로 주입.
 * API Key가 미설정(빈 문자열)이면 필터를 통과시킨다 (개발 편의).
 */
@Slf4j
@Component
@Order(1)
@ConditionalOnProperty(name = "common.filter.api-key.enabled", havingValue = "true")
public class ApiKeyFilter implements Filter {

    private static final String API_KEY_HEADER = "X-API-Key";

    @Value("${agent.api-key:}")
    private String apiKey;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String path = httpRequest.getRequestURI();

        // health, pipeline info 엔드포인트는 인증 제외 (읽기 전용 메타데이터)
        if (path.equals("/health") || path.startsWith("/health/")
                || path.equals("/api/pipeline/info")) {
            chain.doFilter(request, response);
            return;
        }

        // /api/** 경로만 인증 적용
        if (path.startsWith("/api/")) {
            // API Key가 설정되지 않은 경우 필터 비활성 (개발 편의)
            if (apiKey == null || apiKey.isEmpty()) {
                chain.doFilter(request, response);
                return;
            }

            String requestApiKey = httpRequest.getHeader(API_KEY_HEADER);
            if (requestApiKey == null || !requestApiKey.equals(apiKey)) {
                log.warn("[ApiKey] 인증 실패: path={}, remoteAddr={}", path, httpRequest.getRemoteAddr());
                HttpServletResponse httpResponse = (HttpServletResponse) response;
                httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                httpResponse.setContentType("application/json");
                httpResponse.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Invalid or missing API Key\"}");
                return;
            }
        }

        chain.doFilter(request, response);
    }
}
