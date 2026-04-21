package com.gims.provider.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

/**
 * Mock API Key 검증 API
 * 실제 운영에서는 외부 팀이 별도 서비스로 제공할 예정.
 * 테스트용 하드코딩 키로 검증한다.
 */
@Slf4j
@RestController
@RequestMapping("/api/mock/api-key")
public class MockApiKeyController {

    // 테스트용 키 목록
    private static final Map<String, String> TEST_KEYS = Map.of(
            "test-key-2026", "테스트용",
            "gims-provider-key", "GIMS 내부"
    );

    @PostMapping("/validate")
    public Map<String, Object> validate(@RequestBody Map<String, String> request) {
        String apiKey = request.get("apiKey");

        if (apiKey == null || apiKey.isEmpty()) {
            return Map.of("valid", false, "reason", "API Key가 누락되었습니다");
        }

        String clientName = TEST_KEYS.get(apiKey);
        if (clientName == null) {
            log.warn("[MockApiKey] 유효하지 않은 키: {}", apiKey.substring(0, Math.min(8, apiKey.length())) + "...");
            return Map.of("valid", false, "reason", "유효하지 않은 API Key입니다");
        }

        log.info("[MockApiKey] 검증 성공: {} ({})", clientName, request.get("operationId"));
        return Map.of("valid", true, "clientName", clientName);
    }
}
