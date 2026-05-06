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
 * API Key 인증 필터 (공통).
 *
 * <p>/api/** 경로에 대해 X-API-Key 헤더를 검증한다.
 * /health 엔드포인트는 인증 없이 접근 가능.
 *
 * <h3>활성화 토글</h3>
 * <ul>
 *   <li>{@code common.filter.api-key.enabled=true} — 필터 빈 등록</li>
 *   <li>{@code common.filter.api-key.soft-mode=true} — Backend 처럼 cookie 흐름 양보 모드 (default false=strict)</li>
 *   <li>{@code agent.api-key} — 비교할 키 값 (jasypt ENC). 빈 문자열이면 필터 통과 (개발 편의)</li>
 * </ul>
 *
 * <h3>모드별 동작 (X-API-Key 헤더 상태에 따라)</h3>
 * <table>
 *   <tr><th>헤더 상태</th><th>strict (default)</th><th>soft</th></tr>
 *   <tr><td>있음 + 매치</td><td>통과 (서블릿 chain)</td><td>통과 + SecurityContext.ROLE_SYSTEM 박음</td></tr>
 *   <tr><td>있음 + 불일치</td><td>401</td><td>401</td></tr>
 *   <tr><td>없음</td><td>401</td><td>통과 (cookie 흐름 양보)</td></tr>
 * </table>
 *
 * <p>soft 모드는 Backend mixed 케이스 (운영자 cookie + 시스템 X-API-Key 동시 받음) 전용.
 * SecurityContext 박는 분기는 softMode=true 일 때만 호출되어, strict 모듈 (Spring Security 미포함)
 * 에서는 lazy class resolution 덕에 NoClassDefFoundError 발생 0.
 */
@Slf4j
@Component
@Order(1)
@ConditionalOnProperty(name = "common.filter.api-key.enabled", havingValue = "true")
public class ApiKeyFilter implements Filter {

    private static final String API_KEY_HEADER = "X-API-Key";

    @Value("${agent.api-key:}")
    private String apiKey;

    @Value("${common.filter.api-key.soft-mode:false}")
    private boolean softMode;

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
            if (requestApiKey != null) {
                // 키 박혀있음 — 매치 검증
                if (!requestApiKey.equals(apiKey)) {
                    log.warn("[ApiKey] 인증 실패 (불일치): path={}, remoteAddr={}", path, httpRequest.getRemoteAddr());
                    sendUnauthorized((HttpServletResponse) response);
                    return;
                }
                // 매치 — soft 모드면 Spring Security SecurityContext 에 SYSTEM 인증 박음
                if (softMode) {
                    SystemAuthenticationSetter.setSystemAuthentication();
                }
            } else {
                // 키 없음 — 모드 분기
                if (!softMode) {
                    log.warn("[ApiKey] 인증 실패 (헤더 없음, strict): path={}, remoteAddr={}", path, httpRequest.getRemoteAddr());
                    sendUnauthorized((HttpServletResponse) response);
                    return;
                }
                // soft — 통과, SecurityFilterChain 의 다음 filter 가 cookie 등 검증
            }
        }

        chain.doFilter(request, response);
    }

    private void sendUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Invalid or missing API Key\"}");
    }
}
