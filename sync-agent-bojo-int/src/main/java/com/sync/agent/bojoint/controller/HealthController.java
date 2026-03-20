package com.sync.agent.bojoint.controller;

import com.sync.agent.bojoint.config.pipeline.PipelineRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 내부망 Agent 상태 확인 컨트롤러.
 *
 * <ul>
 *   <li>{@code GET /health} - Agent 기동 상태, 등록된 Agent 목록(RCV/Loader) 반환</li>
 * </ul>
 *
 * <p>Orchestrator 및 운영 모니터링에서 내부망 Agent 생존 확인 용도로 호출된다.</p>
 */
@RestController
@RequiredArgsConstructor
public class HealthController {

    @Value("${agent.zone}")
    private String zone;

    private final PipelineRegistry pipelineRegistry;

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "UP");
        result.put("appName", "sync-agent-bojo-int");
        result.put("zone", zone);
        result.put("registeredAgents", pipelineRegistry.size());

        Set<String> rcvAgents = pipelineRegistry.getAgentCodesByType("RCV");
        result.put("rcvAgents", rcvAgents);

        Set<String> loaderAgents = pipelineRegistry.getAgentCodesByType("LOADER");
        if (!loaderAgents.isEmpty()) {
            result.put("loaderAgents", loaderAgents);
        }

        return ResponseEntity.ok(result);
    }
}
