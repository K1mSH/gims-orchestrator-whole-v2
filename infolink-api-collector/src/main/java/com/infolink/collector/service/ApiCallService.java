package com.infolink.collector.service;

import com.infolink.collector.entity.ApiEndpoint;
import com.infolink.collector.entity.ApiParam;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 범용 HTTP 호출 서비스
 * - URL 조립 (PATH/QUERY 파라미터)
 * - Auth 헤더 처리 (BASIC/BEARER)
 * - POST body 조립
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApiCallService {

    private final DynamicParamResolver paramResolver;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${lookup.api-key-url:}")
    private String apiKeyUrl;

    /** API 키 캐시 (실행 단위): id → apiKey */
    private Map<String, String> apiKeyCache;

    public CallResult call(ApiEndpoint endpoint, List<ApiParam> params, Map<String, String> overrides) {
        try {
            // 1. URL 조립
            String url = endpoint.getUrl();

            // PATH 파라미터 치환
            Map<String, String> queryParams = new LinkedHashMap<>();
            Map<String, String> bodyParams = new LinkedHashMap<>();
            Map<String, String> headerParams = new LinkedHashMap<>();

            for (ApiParam param : params) {
                String value;
                // 🔑 API키 참조인 경우: staticValue에 키 ID 저장 → 본체 API에서 실제 값 조회
                if (param.getDescription() != null && param.getDescription().startsWith("🔑")) {
                    value = resolveApiKey(param.getStaticValue());
                } else {
                    value = paramResolver.resolve(param,
                            overrides != null ? overrides.get(param.getParamName()) : null);
                }

                switch (param.getParamType()) {
                    case PATH:
                        url = url.replace("{" + param.getParamName() + "}", value);
                        break;
                    case QUERY:
                        queryParams.put(param.getParamName(), value);
                        break;
                    case BODY:
                        bodyParams.put(param.getParamName(), value);
                        break;
                    case HEADER:
                        headerParams.put(param.getParamName(), value);
                        break;
                }
            }

            // 2. Query string 조립
            if (!queryParams.isEmpty()) {
                UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url);
                queryParams.forEach(builder::queryParam);
                url = builder.build().toUriString();
            }

            // 3. Headers
            HttpHeaders headers = new HttpHeaders();
            headerParams.forEach(headers::set);

            // Auth 처리
            if (endpoint.getAuthType() == ApiEndpoint.AuthType.BASIC && endpoint.getAuthConfig() != null) {
                Map<String, String> authConfig = objectMapper.readValue(endpoint.getAuthConfig(), new TypeReference<>() {});
                String credentials = authConfig.get("username") + ":" + authConfig.get("password");
                headers.set("Authorization", "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
            } else if (endpoint.getAuthType() == ApiEndpoint.AuthType.BEARER && endpoint.getAuthConfig() != null) {
                Map<String, String> authConfig = objectMapper.readValue(endpoint.getAuthConfig(), new TypeReference<>() {});
                headers.set("Authorization", "Bearer " + authConfig.get("token"));
            }

            // 4. 요청 실행
            HttpEntity<?> entity;
            HttpMethod method = "POST".equalsIgnoreCase(endpoint.getHttpMethod()) ? HttpMethod.POST : HttpMethod.GET;

            if (method == HttpMethod.POST && !bodyParams.isEmpty()) {
                if (endpoint.getContentType() != null) {
                    headers.setContentType(MediaType.parseMediaType(endpoint.getContentType()));
                } else {
                    headers.setContentType(MediaType.APPLICATION_JSON);
                }
                entity = new HttpEntity<>(objectMapper.writeValueAsString(bodyParams), headers);
            } else {
                entity = new HttpEntity<>(headers);
            }

            log.info("API 호출: {} {}", method, url);
            ResponseEntity<String> response = restTemplate.exchange(url, method, entity, String.class);

            return new CallResult(response.getStatusCodeValue(), response.getBody(), null);

        } catch (Exception e) {
            log.error("API 호출 실패: {}", e.getMessage(), e);
            return new CallResult(0, null, e.getMessage());
        }
    }

    /**
     * API 키 ID로 본체 API에서 실제 키 값 조회
     */
    private String resolveApiKey(String keyId) {
        if (keyId == null || keyId.isBlank()) return "";

        // 캐시에서 먼저 조회
        if (apiKeyCache == null) {
            apiKeyCache = new HashMap<>();
            loadApiKeyCache();
        }

        String resolved = apiKeyCache.get(keyId);
        if (resolved != null) return resolved;

        log.warn("API 키 조회 실패: id={}", keyId);
        return "";
    }

    /**
     * 본체 API에서 전체 키 목록 로딩 → 캐시
     */
    private void loadApiKeyCache() {
        if (apiKeyUrl == null || apiKeyUrl.isBlank()) {
            log.error("lookup.api-key-url 설정이 없습니다.");
            return;
        }
        try {
            String body = restTemplate.getForObject(apiKeyUrl, String.class);
            Map<String, Object> response = objectMapper.readValue(body, new TypeReference<>() {});
            Map<String, Object> data = (Map<String, Object>) response.get("data");
            Map<String, Object> apis = (Map<String, Object>) data.get("apis");
            List<Map<String, Object>> content = (List<Map<String, Object>>) apis.get("content");

            for (Map<String, Object> item : content) {
                String id = String.valueOf(item.get("id"));
                String apiKey = String.valueOf(item.get("apiKey"));
                apiKeyCache.put(id, apiKey);
            }
            log.info("API 키 캐시 로딩: {}건", apiKeyCache.size());
        } catch (Exception e) {
            log.error("API 키 목록 조회 실패: {}", e.getMessage());
        }
    }

    public record CallResult(int statusCode, String body, String error) {
        public boolean isSuccess() {
            return statusCode >= 200 && statusCode < 300 && error == null;
        }
    }
}
