package com.infolink.collector.controller;

import com.infolink.collector.domain.ApiExecutionHistory;
import com.infolink.collector.dto.ApiEndpointDto.*;
import com.infolink.collector.dto.ApiExecutionHistoryDto;
import com.infolink.collector.dto.TestCallDto;
import com.infolink.collector.service.ApiEndpointService;
import com.infolink.collector.service.ApiExecutionService;
import com.infolink.collector.service.ApiTestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/endpoints")
@RequiredArgsConstructor
public class ApiEndpointController {

    private final ApiEndpointService endpointService;
    private final ApiTestService testService;
    private final ApiExecutionService executionService;

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
}
