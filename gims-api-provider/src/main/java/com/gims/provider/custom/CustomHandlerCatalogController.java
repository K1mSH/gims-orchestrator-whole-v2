package com.gims.provider.custom;

import com.gims.provider.entity.ApiPrvOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 커스텀 핸들러 카탈로그 + 등록 API.
 *
 * Agent 의 discover 패턴 적용:
 *  - GET catalog → 운영자 화면에 "등록 가능한 핸들러 목록" 표시
 *  - GET preview → 등록 화면 readonly 자동 채움용 metadata
 *  - POST register → 운영자 딸깍 등록 → ApiPrvOperation INSERT
 */
@Slf4j
@RestController
@RequestMapping("/api/manage/custom-handlers")
@RequiredArgsConstructor
public class CustomHandlerCatalogController {

    private final CustomHandlerCatalogService catalogService;

    /** 등록 가능한 핸들러 카탈로그 (이름 + 엔드포인트 + 등록여부) */
    @GetMapping("/catalog")
    public ResponseEntity<List<CustomHandlerCatalogService.CatalogEntry>> getCatalog() {
        return ResponseEntity.ok(catalogService.listCatalog());
    }

    /** 핸들러 metadata 미리보기 — 등록 폼 readonly 자동 채움용 (operationId 는 body 에) */
    @PostMapping("/preview")
    public ResponseEntity<CustomOperationMetadata> preview(@RequestBody Map<String, String> body) {
        String operationId = body.get("operationId");
        return ResponseEntity.ok(catalogService.preview(operationId));
    }

    /** 핸들러 등록. operationId 는 핸들러 매칭 키 (handlerKey), customOperationId/customOperationName 은 운영자 변경값 (선택) */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> body) {
        String handlerKey = body.get("operationId");  // 핸들러 metadata 의 operationId = handlerKey
        if (handlerKey == null || handlerKey.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "operationId 필수"));
        }
        String customOperationId = body.get("customOperationId");
        String customOperationName = body.get("customOperationName");
        try {
            ApiPrvOperation saved = catalogService.register(handlerKey, customOperationId, customOperationName);
            return ResponseEntity.ok(saved);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
