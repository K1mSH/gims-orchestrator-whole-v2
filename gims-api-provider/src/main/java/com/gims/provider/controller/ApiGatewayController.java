package com.gims.provider.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gims.provider.custom.CustomHandlerRegistry;
import com.gims.provider.custom.CustomOperationHandler;
import com.gims.provider.dto.DynamicQueryResult;
import com.gims.provider.entity.ApiPrvCallHistory;
import com.gims.provider.entity.ApiPrvOperation;
import com.gims.provider.repository.ApiPrvCallHistoryRepository;
import com.gims.provider.repository.ApiPrvOperationRepository;
import com.gims.provider.service.ApiKeyValidationService;
import com.gims.provider.service.DynamicQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 외부 제공 API Gateway
 * GET /api/provide/{operationId}?param1=value1&page=1&pageSize=100
 * Header: X-API-Key: {발급받은 키}
 *
 * operationId 는 슬래시(/) 포함 가능 — 레거시 URL 그대로 재현 위해
 *  예: /api/provide/megokrApi/ngw08 → operationId = "megokrApi/ngw08"
 */
@Slf4j
@RestController
@RequestMapping("/api/provide")
@RequiredArgsConstructor
public class ApiGatewayController {

    private static final String BASE_PREFIX = "/api/provide/";

    private final ApiPrvOperationRepository operationRepository;
    private final DynamicQueryService dynamicQueryService;
    private final ApiKeyValidationService apiKeyValidationService;
    private final CustomHandlerRegistry customHandlerRegistry;
    private final ApiPrvCallHistoryRepository callHistoryRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping("/**")
    public ResponseEntity<?> provide(@RequestParam(defaultValue = "1") int page,
                                      @RequestParam(required = false) Integer pageSize,
                                      @RequestParam(value = "apiKey", required = false) String apiKey,
                                      HttpServletRequest request) {
        long startTime = System.currentTimeMillis();
        String clientIp = request.getRemoteAddr();

        // 0. operationId 추출 (슬래시 포함 지원)
        String operationId = extractOperationId(request);
        if (operationId == null || operationId.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "operationId 가 필요합니다"));
        }

        // 1. 오퍼레이션 조회
        Optional<ApiPrvOperation> optOp = operationRepository.findByOperationId(operationId);
        if (optOp.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "오퍼레이션을 찾을 수 없습니다: " + operationId));
        }

        ApiPrvOperation operation = optOp.get();

        // 2. 활성 여부 확인
        if (!Boolean.TRUE.equals(operation.getIsPublished())) {
            saveHistory(operation, apiKey, clientIp, null, 0, "FAILED", "비활성 오퍼레이션", startTime);
            return ResponseEntity.status(404).body(Map.of("error", "비활성 오퍼레이션입니다: " + operationId));
        }

        // 3. 요청 파라미터 추출 (page, pageSize 제외)
        Map<String, String> requestParams = new HashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (!"page".equals(key) && !"pageSize".equals(key) && !"apiKey".equals(key) && values.length > 0) {
                requestParams.put(key, values[0]);
            }
        });
        String requestParamsJson = paramsToJson(requestParams);

        // 4. API Key 검증
        String validationError = apiKeyValidationService.validate(apiKey, operationId, clientIp);
        if (validationError != null) {
            log.warn("[Gateway] API Key 검증 실패: {} — {}", operationId, validationError);
            saveHistory(operation, apiKey, clientIp, requestParamsJson, 0, "FAILED", validationError, startTime);
            return ResponseEntity.status(401).body(Map.of("error", validationError));
        }

        // 5. pageSize 결정
        int effectivePageSize = pageSize != null
                ? Math.min(pageSize, operation.getMaxPageSize())
                : operation.getPageSize();

        // 6. 실행 — META(메타등록형) / CUSTOM(내장 핸들러) 분기
        try {
            DynamicQueryResult result;
            if ("CUSTOM".equals(operation.getOperationType())) {
                String handlerKey = operation.getHandlerKey();
                log.info("[Gateway] CUSTOM 분기: operationId={}, handlerKey={}", operationId, handlerKey);
                CustomOperationHandler handler = handlerKey != null ? customHandlerRegistry.get(handlerKey) : null;
                if (handler == null) {
                    log.error("[Gateway] CUSTOM 핸들러 미등록: operationId={}, handlerKey={}", operationId, handlerKey);
                    saveHistory(operation, apiKey, clientIp, requestParamsJson, 0, "FAILED", "핸들러 미등록", startTime);
                    return ResponseEntity.status(500).body(Map.of("error", "내장 핸들러가 등록되지 않았습니다: " + handlerKey));
                }
                result = handler.handle(requestParams, page, effectivePageSize);
            } else {
                log.info("[Gateway] META 분기: {}", operationId);
                result = dynamicQueryService.execute(operation, requestParams, page, effectivePageSize);
            }
            // 외부 응답에서는 SQL 미포함
            result.setExecutedSql(null);
            int count = result.getData() != null ? result.getData().size() : 0;
            saveHistory(operation, apiKey, clientIp, requestParamsJson, count, "SUCCESS", null, startTime);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            log.warn("[Gateway] 요청 오류: {} — {}", operationId, e.getMessage());
            saveHistory(operation, apiKey, clientIp, requestParamsJson, 0, "FAILED", e.getMessage(), startTime);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[Gateway] 실행 오류: {} — {}", operationId, e.getMessage(), e);
            saveHistory(operation, apiKey, clientIp, requestParamsJson, 0, "FAILED", e.getMessage(), startTime);
            return ResponseEntity.status(500).body(Map.of("error", "서버 오류가 발생했습니다"));
        }
    }

    /**
     * 요청 URI 에서 operationId 추출 (슬래시 포함 허용)
     * /api/provide/megokrApi/ngw08 → "megokrApi/ngw08"
     */
    private String extractOperationId(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null || !uri.startsWith(BASE_PREFIX) || uri.length() <= BASE_PREFIX.length()) {
            return null;
        }
        String raw = uri.substring(BASE_PREFIX.length());
        try {
            return URLDecoder.decode(raw, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("[Gateway] operationId URL 디코딩 실패: {}", raw);
            return raw;
        }
    }

    /** 호출이력 저장 — 실패해도 응답에 영향 없음 */
    private void saveHistory(ApiPrvOperation operation, String apiKey, String clientIp,
                              String requestParamsJson, int responseCount,
                              String status, String errorMessage, long startTime) {
        try {
            ApiPrvCallHistory h = ApiPrvCallHistory.builder()
                    .operation(operation)
                    .apiKey(truncate(apiKey, 200))
                    .clientIp(clientIp)
                    .requestParams(truncate(requestParamsJson, 2000))
                    .responseCount(responseCount)
                    .status(status)
                    .errorMessage(truncate(errorMessage, 2000))
                    .durationMs(System.currentTimeMillis() - startTime)
                    .calledAt(LocalDateTime.now())
                    .build();
            callHistoryRepository.save(h);
        } catch (Exception e) {
            log.warn("[Gateway] 호출이력 저장 실패: {}", e.getMessage());
        }
    }

    private String paramsToJson(Map<String, String> params) {
        try {
            return objectMapper.writeValueAsString(params);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }
}
