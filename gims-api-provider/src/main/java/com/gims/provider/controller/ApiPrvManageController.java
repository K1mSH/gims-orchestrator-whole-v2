package com.gims.provider.controller;

import com.gims.provider.entity.ApiPrvKeyInfo;
import com.gims.provider.entity.ApiPrvOperation;
import com.gims.provider.entity.ApiPrvOperationColumn;
import com.gims.provider.entity.ApiPrvOperationParam;
import com.gims.provider.repository.ApiPrvCallHistoryRepository;
import com.gims.provider.service.ApiPrvKeyService;
import com.gims.provider.service.ApiPrvOperationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * API Provider 관리 API
 * 오퍼레이션 CRUD, 컬럼/파라미터 관리, API Key 관리
 */
@RestController
@RequestMapping("/api/manage")
@RequiredArgsConstructor
public class ApiPrvManageController {

    private final ApiPrvOperationService operationService;
    private final ApiPrvKeyService keyService;
    private final ApiPrvCallHistoryRepository callHistoryRepository;

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

    // ========== 호출 이력 ==========

    @GetMapping("/operations/{id}/history")
    public ResponseEntity<?> getCallHistory(@PathVariable Long id,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(callHistoryRepository.findByOperationIdOrderByCalledAtDesc(id, PageRequest.of(page, size)));
    }

    // ========== API Key ==========

    @GetMapping("/api-keys")
    public ResponseEntity<List<ApiPrvKeyInfo>> listApiKeys() {
        return ResponseEntity.ok(keyService.findAll());
    }

    @PostMapping("/api-keys")
    public ResponseEntity<ApiPrvKeyInfo> createApiKey(@RequestBody ApiPrvKeyInfo keyInfo) {
        return ResponseEntity.ok(keyService.create(keyInfo));
    }

    @DeleteMapping("/api-keys/{id}")
    public ResponseEntity<Void> deleteApiKey(@PathVariable Long id) {
        keyService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
