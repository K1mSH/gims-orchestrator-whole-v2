package com.sync.orchestrator.domain.agent;

import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    @GetMapping
    public ResponseEntity<List<AgentDto.Response>> getAgents() {
        return ResponseEntity.ok(agentService.findAll());
    }

    @GetMapping("/{agentId}")
    public ResponseEntity<AgentDto.Response> getAgent(@PathVariable String agentId) {
        return ResponseEntity.ok(agentService.findById(agentId));
    }

    @PostMapping
    public ResponseEntity<AgentDto.Response> createAgent(@Valid @RequestBody AgentDto.CreateRequest request) {
        AgentDto.Response response = agentService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{agentId}")
    public ResponseEntity<AgentDto.Response> updateAgent(
            @PathVariable String agentId,
            @RequestBody AgentDto.UpdateRequest request) {
        return ResponseEntity.ok(agentService.update(agentId, request));
    }

    @DeleteMapping("/{agentId}")
    public ResponseEntity<Void> deleteAgent(@PathVariable String agentId) {
        agentService.delete(agentId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{agentId}/health-check")
    public ResponseEntity<AgentDto.HealthCheckResponse> healthCheck(@PathVariable String agentId) {
        return ResponseEntity.ok(agentService.healthCheck(agentId));
    }

    /**
     * Agent의 내장 실행 옵션 목록 조회 (Agent API 프록시)
     */
    @GetMapping("/{agentId}/fetch-execution-params")
    public ResponseEntity<List<java.util.Map<String, Object>>> fetchExecutionParams(@PathVariable String agentId) {
        return ResponseEntity.ok(agentService.fetchExecutionParamsFromAgent(agentId));
    }

    /**
     * DB에 저장된 실행 옵션 조회 (프론트엔드 실행 패널용)
     */
    @GetMapping("/{agentId}/execution-params")
    public ResponseEntity<List<AgentDto.ExecutionParamResponse>> getExecutionParams(@PathVariable String agentId) {
        return ResponseEntity.ok(agentService.getExecutionParams(agentId));
    }

    /**
     * Agent API에서 실행 옵션을 새로 가져와 DB 업데이트
     */
    @PostMapping("/{agentId}/refresh-execution-params")
    public ResponseEntity<List<AgentDto.ExecutionParamResponse>> refreshExecutionParams(@PathVariable String agentId) {
        return ResponseEntity.ok(agentService.refreshExecutionParams(agentId));
    }

    /**
     * Agent의 source DB에 테스트 데이터 생성 요청
     * @param agentId 대상 Agent ID
     * @param count 생성할 데이터 수 (기본 1000건)
     */
    @PostMapping("/{agentId}/generate-test-data")
    public ResponseEntity<?> generateTestData(
            @PathVariable String agentId,
            @RequestParam(defaultValue = "1000") int count) {
        return ResponseEntity.ok(agentService.generateTestData(agentId, count));
    }

    /**
     * Agent의 source DB 테스트 데이터 정리 요청
     */
    @DeleteMapping("/{agentId}/clear-test-data")
    public ResponseEntity<?> clearTestData(@PathVariable String agentId) {
        return ResponseEntity.ok(agentService.clearTestData(agentId));
    }
}
