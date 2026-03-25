package com.infolink.collector.controller;

import com.infolink.collector.entity.ApiExecutionHistory;
import com.infolink.collector.dto.ApiEndpointDto.*;
import com.infolink.collector.dto.ApiExecutionHistoryDto;
import com.infolink.collector.dto.TestCallDto;
import com.infolink.collector.executor.CustomExecutorRegistry;
import com.infolink.collector.service.ApiEndpointService;
import com.infolink.collector.service.ApiExecutionService;
import com.infolink.collector.service.ApiTestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/endpoints")
@RequiredArgsConstructor
@Slf4j
public class ApiEndpointController {

    private final ApiEndpointService endpointService;
    private final ApiTestService testService;
    private final ApiExecutionService executionService;
    private final CustomExecutorRegistry customExecutorRegistry;
    private final RestTemplate restTemplate;

    @Value("${lookup.api-key-url:}")
    private String apiKeyUrl;

    @GetMapping
    public List<ListResponse> getList() {
        return endpointService.getList();
    }

    @GetMapping("/{id}")
    public DetailResponse getDetail(@PathVariable Long id) {
        return endpointService.getDetail(id);
    }

    @PostMapping
    public ResponseEntity<DetailResponse> create(@Valid @RequestBody CreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(endpointService.create(request));
    }

    @PutMapping("/{id}")
    public DetailResponse update(@PathVariable Long id, @Valid @RequestBody UpdateRequest request) {
        return endpointService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        endpointService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // --- Params ---

    @PutMapping("/{id}/params")
    public List<ParamResponse> saveParams(@PathVariable Long id, @RequestBody List<ParamRequest> requests) {
        return endpointService.saveParams(id, requests);
    }

    // --- Test Call ---

    @PostMapping("/{id}/test")
    public TestCallDto.Response testCall(@PathVariable Long id, @RequestBody(required = false) TestCallDto.Request request) {
        Map<String, String> overrides = request != null ? request.getParamOverrides() : null;
        return testService.testCall(id, overrides);
    }

    /** 저장 없이 인라인 테스트 (등록 전 검증용) */
    @PostMapping("/test-inline")
    public TestCallDto.Response testCallInline(@RequestBody TestCallDto.InlineRequest request) {
        return testService.testCallInline(request);
    }

    // --- Run ---

    @PostMapping("/{id}/run")
    public ApiExecutionHistoryDto.Response run(@PathVariable Long id) {
        return executionService.run(id, ApiExecutionHistory.TriggeredBy.MANUAL);
    }

    // --- Field Mappings ---

    @GetMapping("/{id}/mappings")
    public List<FieldMappingResponse> getMappings(@PathVariable Long id) {
        return endpointService.getDetail(id).getFieldMappings();
    }

    @PutMapping("/{id}/mappings")
    public List<FieldMappingResponse> saveMappings(@PathVariable Long id, @RequestBody List<FieldMappingRequest> requests) {
        return endpointService.saveMappings(id, requests);
    }

    // --- API 키 목록 (GIMS 본체 프록시) ---

    @GetMapping("/api-keys")
    public ResponseEntity<String> getApiKeys() {
        if (apiKeyUrl == null || apiKeyUrl.isBlank()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("{\"error\":\"api-key-url 미설정\"}");
        }
        try {
            String body = restTemplate.getForObject(apiKeyUrl, String.class);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            log.error("API 키 목록 조회 실패: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    // --- 커스텀 실행기 목록 ---

    @GetMapping("/custom-executors")
    public List<Map<String, String>> getCustomExecutors() {
        return customExecutorRegistry.getAll();
    }
}
