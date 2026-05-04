package com.sync.agent.common.auth;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 인증 실패 시 응답 헬퍼 (ApiKeyFilter 와 일관 JSON 형식).
 *
 * 401: {"error": "AUTH_REQUIRED", "message": "인증이 필요합니다"}
 * 503: {"error": "AUTH_SERVICE_UNAVAILABLE", "message": "..."} + Retry-After: 30
 */
public final class AuthErrorResponse {

    private AuthErrorResponse() {}

    public static void write(HttpServletResponse response, AuthErrorCode code) throws IOException {
        response.setStatus(code.httpStatus());
        response.setContentType("application/json;charset=UTF-8");
        if (code == AuthErrorCode.AUTH_SERVICE_UNAVAILABLE) {
            response.setHeader("Retry-After", "30");
        }
        String json = String.format(
            "{\"error\":\"%s\",\"message\":\"%s\"}",
            code.name(), code.message()
        );
        response.getWriter().write(json);
    }
}
