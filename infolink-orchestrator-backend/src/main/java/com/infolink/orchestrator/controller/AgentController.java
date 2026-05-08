package com.infolink.orchestrator.controller;

import com.infolink.orchestrator.dto.AgentDto;
import com.infolink.orchestrator.scheduler.DataRetentionScheduler;
import com.infolink.orchestrator.service.AgentService;
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
    private final DataRetentionScheduler dataRetentionScheduler;

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
     * Agent 테이블 갱신 (pipeline/info 기반 agent_table 동기화)
     */
    @PostMapping("/{id}/sync-tables")
    public ResponseEntity<Map<String, Object>> syncAgentTables(@PathVariable Long id) {
        return ResponseEntity.ok(agentService.syncAgentTables(id));
    }

    /**
     * Agent의 retention(자동삭제) 설정 조회 (DB)
     */
    @GetMapping("/{id}/retention")
    public ResponseEntity<?> getRetentionConfig(@PathVariable Long id) {
        return ResponseEntity.ok(agentService.getRetentionConfig(id));
    }

    /**
     * Agent의 retention(자동삭제) 설정 저장
     */
    @PutMapping("/{id}/retention")
    public ResponseEntity<?> updateRetentionConfig(@PathVariable Long id, @RequestBody String body) {
        return ResponseEntity.ok(agentService.updateRetentionConfig(id, body));
    }

    /**
     * Agent YML의 select-tables 조회 (WHERE 조건 드롭다운용)
     */
    @GetMapping("/{id}/select-tables")
    public ResponseEntity<?> getSelectTables(@PathVariable Long id) {
        return ResponseEntity.ok(agentService.getSelectTables(id));
    }

    /**
     * Agent YML의 retention-candidates 조회 (보존 정책 dropdown 용 단일 진실원).
     * 빈 배열 = 해당 Agent 는 retention 비대상 (마스터 / Link / 메타 데이터).
     * dev_plan/2026_05/08/retention-candidates-safety.md
     */
    @GetMapping("/{id}/retention-candidates")
    public ResponseEntity<?> getRetentionCandidates(@PathVariable Long id) {
        return ResponseEntity.ok(agentService.getRetentionCandidates(id));
    }

    /**
     * Retention 자동 cleanup 즉시 트리거 — DataRetentionScheduler.executeRetentionCleanup() 동일.
     * 운영자 수동 호출 + 검증 시나리오용.
     */
    @PostMapping("/retention/trigger")
    public ResponseEntity<?> triggerRetentionCleanup() {
        dataRetentionScheduler.executeRetentionCleanup();
        return ResponseEntity.ok(Map.of("status", "triggered"));
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
