package com.sync.proxy.dmz.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Proxy API Key 인증 필터
 * /api/** 경로에 대해 X-API-Key 헤더를 검증한다.
 * /health 엔드포인트는 인증 없이 접근 가능.
 */
@Slf4j
@Component
@Order(1)
public class ApiKeyFilter implements Filter {

    private static final String API_KEY_HEADER = "X-API-Key";

    @Value("${agent.api-key:}")
    private String apiKey;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String path = httpRequest.getRequestURI();

        // health 엔드포인트는 인증 제외
        if (path.equals("/health") || path.startsWith("/health/")) {
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
                log.warn("[Proxy] API Key 인증 실패: path={}, remoteAddr={}", path, httpRequest.getRemoteAddr());
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
