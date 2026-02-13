package com.sync.orchestrator.domain.agent;

import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    @GetMapping
    public ResponseEntity<List<AgentDto.Response>> getAgents() {
        return ResponseEntity.ok(agentService.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AgentDto.Response> getAgent(@PathVariable Long id) {
        return ResponseEntity.ok(agentService.findById(id));
    }

    /**
     * Agent 프로세스에서 사용 가능한 agentCode 목록 조회
     */
    @GetMapping("/discover")
    public ResponseEntity<Map<String, Object>> discoverAgents(@RequestParam String endpointUrl) {
        return ResponseEntity.ok(agentService.discoverAgents(endpointUrl));
    }

    @PostMapping
    public ResponseEntity<AgentDto.Response> createAgent(@Valid @RequestBody AgentDto.CreateRequest request) {
        AgentDto.Response response = agentService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<AgentDto.Response> updateAgent(
            @PathVariable Long id,
            @RequestBody AgentDto.UpdateRequest request) {
        return ResponseEntity.ok(agentService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAgent(@PathVariable Long id) {
        agentService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/health-check")
    public ResponseEntity<AgentDto.HealthCheckResponse> healthCheck(@PathVariable Long id) {
        return ResponseEntity.ok(agentService.healthCheck(id));
    }

    /**
     * Agent의 내장 실행 옵션 목록 조회 (Agent API 프록시)
     */
    @GetMapping("/{id}/fetch-execution-params")
    public ResponseEntity<List<java.util.Map<String, Object>>> fetchExecutionParams(@PathVariable Long id) {
        return ResponseEntity.ok(agentService.fetchExecutionParamsFromAgent(id));
    }

    /**
     * DB에 저장된 실행 옵션 조회 (프론트엔드 실행 패널용)
     */
    @GetMapping("/{id}/execution-params")
    public ResponseEntity<List<AgentDto.ExecutionParamResponse>> getExecutionParams(@PathVariable Long id) {
        return ResponseEntity.ok(agentService.getExecutionParams(id));
    }

    /**
     * Agent API에서 실행 옵션을 새로 가져와 DB 업데이트
     */
    @PostMapping("/{id}/refresh-execution-params")
    public ResponseEntity<List<AgentDto.ExecutionParamResponse>> refreshExecutionParams(@PathVariable Long id) {
        return ResponseEntity.ok(agentService.refreshExecutionParams(id));
    }

    /**
     * Agent의 source DB에 테스트 데이터 생성 요청
     */
    @PostMapping("/{id}/generate-test-data")
    public ResponseEntity<?> generateTestData(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1000") int count) {
        return ResponseEntity.ok(agentService.generateTestData(id, count));
    }

    /**
     * Agent의 source DB 테스트 데이터 정리 요청
     */
    @DeleteMapping("/{id}/clear-test-data")
    public ResponseEntity<?> clearTestData(@PathVariable Long id) {
        return ResponseEntity.ok(agentService.clearTestData(id));
    }
}
