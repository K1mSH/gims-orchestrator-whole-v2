package com.gims.provider.controller;

import com.gims.provider.dto.DynamicQueryResult;
import com.gims.provider.entity.ApiPrvOperation;
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

    @GetMapping("/**")
    public ResponseEntity<?> provide(@RequestParam(defaultValue = "1") int page,
                                      @RequestParam(required = false) Integer pageSize,
                                      @RequestParam(value = "apiKey", required = false) String apiKey,
                                      HttpServletRequest request) {
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
            return ResponseEntity.status(404).body(Map.of("error", "비활성 오퍼레이션입니다: " + operationId));
        }

        // 3. API Key 검증
        String clientIp = request.getRemoteAddr();
        String validationError = apiKeyValidationService.validate(apiKey, operationId, clientIp);
        if (validationError != null) {
            log.warn("[Gateway] API Key 검증 실패: {} — {}", operationId, validationError);
            return ResponseEntity.status(401).body(Map.of("error", validationError));
        }

        // 4. pageSize 결정
        int effectivePageSize = pageSize != null
                ? Math.min(pageSize, operation.getMaxPageSize())
                : operation.getPageSize();

        // 5. 요청 파라미터 추출 (page, pageSize 제외)
        Map<String, String> requestParams = new HashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (!"page".equals(key) && !"pageSize".equals(key) && !"apiKey".equals(key) && values.length > 0) {
                requestParams.put(key, values[0]);
            }
        });

        // 6. 실행
        try {
            DynamicQueryResult result = dynamicQueryService.execute(operation, requestParams, page, effectivePageSize);
            // 외부 응답에서는 SQL 미포함
            result.setExecutedSql(null);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            log.warn("[Gateway] 요청 오류: {} — {}", operationId, e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("[Gateway] 실행 오류: {} — {}", operationId, e.getMessage(), e);
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
}
