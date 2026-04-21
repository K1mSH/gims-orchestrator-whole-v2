package com.gims.provider.controller;

import com.gims.provider.dto.DynamicQueryResult;
import com.gims.provider.entity.ApiPrvOperation;
import com.gims.provider.entity.ApiPrvOperationColumn;
import com.gims.provider.entity.ApiPrvOperationParam;
import com.gims.provider.repository.ApiPrvCallHistoryRepository;
import com.gims.provider.service.ApiPrvOperationService;
import com.gims.provider.service.DynamicQueryService;
import com.gims.provider.service.ProviderDataSourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * API Provider 관리 API
 * 오퍼레이션 CRUD, 컬럼/파라미터 관리, 테스트 호출, DB 메타 조회
 */
@Slf4j
@RestController
@RequestMapping("/api/manage")
@RequiredArgsConstructor
public class ApiPrvManageController {

    private final ApiPrvOperationService operationService;
    private final ApiPrvCallHistoryRepository callHistoryRepository;
    private final DynamicQueryService dynamicQueryService;
    private final ProviderDataSourceService dataSourceService;

    // ========== 오퍼레이션 ==========

    @GetMapping("/operations")
    public ResponseEntity<List<ApiPrvOperation>> listOperations() {
        return ResponseEntity.ok(operationService.findAll());
    }

    @GetMapping("/operations/{id}")
    public ResponseEntity<ApiPrvOperation> getOperation(@PathVariable Long id) {
        return ResponseEntity.ok(operationService.findById(id));
    }

    @PostMapping("/operations")
    public ResponseEntity<ApiPrvOperation> createOperation(@RequestBody ApiPrvOperation operation) {
        return ResponseEntity.ok(operationService.create(operation));
    }

    @PutMapping("/operations/{id}")
    public ResponseEntity<ApiPrvOperation> updateOperation(@PathVariable Long id, @RequestBody ApiPrvOperation operation) {
        return ResponseEntity.ok(operationService.update(id, operation));
    }

    @DeleteMapping("/operations/{id}")
    public ResponseEntity<Void> deleteOperation(@PathVariable Long id) {
        operationService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/operations/{id}/publish")
    public ResponseEntity<ApiPrvOperation> togglePublish(@PathVariable Long id) {
        return ResponseEntity.ok(operationService.togglePublish(id));
    }

    // ========== 컬럼 ==========

    @PutMapping("/operations/{id}/columns")
    public ResponseEntity<Map<String, String>> saveColumns(@PathVariable Long id, @RequestBody List<ApiPrvOperationColumn> columns) {
        operationService.saveColumns(id, columns);
        return ResponseEntity.ok(Map.of("message", "컬럼 저장 완료"));
    }

    // ========== 파라미터 ==========

    @PutMapping("/operations/{id}/params")
    public ResponseEntity<Map<String, String>> saveParams(@PathVariable Long id, @RequestBody List<ApiPrvOperationParam> params) {
        operationService.saveParams(id, params);
        return ResponseEntity.ok(Map.of("message", "파라미터 저장 완료"));
    }

    // ========== 테스트 호출 ==========

    @PostMapping("/operations/{id}/test")
    public ResponseEntity<?> testOperation(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        try {
            ApiPrvOperation operation = operationService.findById(id);

            @SuppressWarnings("unchecked")
            Map<String, String> params = body != null && body.containsKey("params")
                    ? (Map<String, String>) body.get("params")
                    : Map.of();
            int page = body != null && body.containsKey("page")
                    ? ((Number) body.get("page")).intValue() : 1;
            int maxPageSize = operation.getMaxPageSize() != null ? operation.getMaxPageSize() : 1000;
            int pageSize = body != null && body.containsKey("pageSize")
                    ? Math.min(((Number) body.get("pageSize")).intValue(), maxPageSize) : operation.getPageSize();

            DynamicQueryResult result = dynamicQueryService.execute(operation, params, page, pageSize);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("[Provider] 테스트 호출 실패: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ========== DB 메타 조회 (Proxy 경유) ==========

    @GetMapping("/meta/tables")
    public ResponseEntity<?> getTableList(@RequestParam String datasourceId,
                                           @RequestParam(required = false, defaultValue = "") String query) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("datasourceId", datasourceId);
            body.put("query", query);
            Map<String, Object> result = dataSourceService.proxyPost("/api/datasource/search-tables", body);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("[Provider] 테이블 목록 조회 실패: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/meta/tables/{tableName}/columns")
    public ResponseEntity<?> getTableColumns(@PathVariable String tableName,
                                              @RequestParam String datasourceId) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("datasourceId", datasourceId);
            body.put("tableName", tableName);
            Map<String, Object> result = dataSourceService.proxyPost("/api/datasource/search-columns", body);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("[Provider] 컬럼 목록 조회 실패: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ========== 호출 이력 ==========

    @GetMapping("/operations/{id}/history")
    public ResponseEntity<?> getCallHistory(@PathVariable Long id,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(callHistoryRepository.findByOperationIdOrderByCalledAtDesc(id, PageRequest.of(page, size)));
    }
}
