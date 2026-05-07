package com.infolink.agent.provide.controller;

import com.infolink.agent.provide.config.pipeline.PipelineRegistry;
import com.infolink.agent.provide.pipeline.PipelineService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Provide Agent 상태 확인 컨트롤러.
 *
 * <ul>
 *   <li>{@code GET /health} - Agent 기동 상태, 등록된 Agent 목록(RCV/Loader), 실행 중 Agent 반환</li>
 * </ul>
 *
 * <p>Orchestrator 및 운영 모니터링에서 Provide Agent 생존 확인 용도로 호출된다.</p>
 */
@RestController
@RequiredArgsConstructor
public class HealthController {

    @Value("${agent.zone}")
    private String zone;

    private final PipelineRegistry pipelineRegistry;
    private final PipelineService pipelineService;

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "UP");
        result.put("appName", "sync-agent-provide");
        result.put("zone", zone);
        result.put("registeredAgents", pipelineRegistry.size());

        Set<String> rcvAgents = pipelineRegistry.getAgentCodesByType("RCV");
        Set<String> loaderAgents = pipelineRegistry.getAgentCodesByType("LOADER");

        result.put("rcvAgents", rcvAgents);
        result.put("loaderAgents", loaderAgents);
        result.put("runningAgents", pipelineService.getRunningAgentCodes());

        return ResponseEntity.ok(result);
    }
}
