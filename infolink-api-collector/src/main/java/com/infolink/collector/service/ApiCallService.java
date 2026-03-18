package com.infolink.collector.service;

import com.infolink.collector.entity.ApiEndpoint;
import com.infolink.collector.entity.ApiParam;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    public CallResult call(ApiEndpoint endpoint, List<ApiParam> params, Map<String, String> overrides) {
        try {
            // 1. URL 조립
            String url = endpoint.getUrl();

            // PATH 파라미터 치환
            Map<String, String> queryParams = new LinkedHashMap<>();
            Map<String, String> bodyParams = new LinkedHashMap<>();
            Map<String, String> headerParams = new LinkedHashMap<>();

            for (ApiParam param : params) {
                String value = paramResolver.resolve(param,
                        overrides != null ? overrides.get(param.getParamName()) : null);

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

            // 추가 헤더 (endpoint.headers JSON)
            if (endpoint.getHeaders() != null && !endpoint.getHeaders().isBlank()) {
                Map<String, String> extraHeaders = objectMapper.readValue(endpoint.getHeaders(), new TypeReference<>() {});
                extraHeaders.forEach(headers::set);
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

    public record CallResult(int statusCode, String body, String error) {
        public boolean isSuccess() {
            return statusCode >= 200 && statusCode < 300 && error == null;
        }
    }
}
