package com.infolink.provider.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * API Key 검증 서비스
 * 외부 검증 API를 호출하여 API Key의 유효성을 확인한다.
 * 현재는 Mock API (자체 /api/mock/api-key/validate)를 호출.
 * 운영 시 외부 팀의 검증 API URL로 교체하면 됨.
 */
@Slf4j
@Service
public class ApiKeyValidationService {

    @Value("${app.api-key-validation.url:http://localhost:8095/api/mock/api-key/validate}")
    private String validationUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * API Key 검증
     * @return null이면 검증 성공, 문자열이면 실패 사유
     */
    @SuppressWarnings("unchecked")
    public String validate(String apiKey, String operationId, String clientIp) {
        if (apiKey == null || apiKey.isEmpty()) {
            return "API Key가 누락되었습니다. X-API-Key 헤더를 확인하세요.";
        }

        try {
            Map<String, String> body = Map.of(
                    "apiKey", apiKey,
                    "operationId", operationId != null ? operationId : "",
                    "clientIp", clientIp != null ? clientIp : ""
            );

            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");

            Map<String, Object> response = restTemplate.postForObject(
                    validationUrl, new HttpEntity<>(body, headers), Map.class);

            if (response == null) {
                return "API Key 검증 서비스 응답 없음";
            }

            boolean valid = Boolean.TRUE.equals(response.get("valid"));
            if (valid) {
                return null; // 성공
            }

            return (String) response.getOrDefault("reason", "API Key 검증 실패");
        } catch (Exception e) {
            log.error("[ApiKeyValidation] 검증 API 호출 실패: {}", e.getMessage());
            return "API Key 검증 서비스 연결 실패";
        }
    }
}
