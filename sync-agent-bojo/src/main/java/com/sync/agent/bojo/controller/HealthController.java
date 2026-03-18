package com.sync.agent.bojo.controller;

import com.sync.agent.common.datasource.DataSourceInfo;
import com.sync.agent.bojo.config.PipelineRegistry;
import com.sync.agent.bojo.config.SyncDataSourceService;
import com.sync.agent.bojo.pipeline.PipelineService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * DMZ Agent 상태 확인 및 디버그 정보 제공 컨트롤러.
 *
 * <ul>
 *   <li>{@code GET /health} - Agent 기동 상태, 등록된 Agent 목록(RCV/Loader/SND), 실행 중 Agent 반환</li>
 *   <li>{@code GET /debug/datasources} - 캐시된 DataSource 설정 정보 조회 (디버그용)</li>
 * </ul>
 *
 * <p>Orchestrator 및 운영 모니터링에서 Agent 생존 확인 용도로 호출된다.</p>
 */
@RestController
@RequiredArgsConstructor
public class HealthController {

    @Value("${agent.zone}")
    private String zone;

    private final PipelineRegistry pipelineRegistry;
    private final PipelineService pipelineService;
    private final SyncDataSourceService syncDataSourceService;

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "UP");
        result.put("appName", "sync-agent-bojo");
        result.put("zone", zone);
        result.put("registeredAgents", pipelineRegistry.size());

        // Agent 목록 (타입별)
        Set<String> rcvAgents = pipelineRegistry.getAgentCodesByType("RCV");
        Set<String> loaderAgents = pipelineRegistry.getAgentCodesByType("LOADER");
        Set<String> sndAgents = pipelineRegistry.getAgentCodesByType("SND");

        result.put("rcvAgents", rcvAgents);
        result.put("loaderAgents", loaderAgents);
        result.put("sndAgents", sndAgents);
        result.put("runningAgents", pipelineService.getRunningAgentCodes());

        return ResponseEntity.ok(result);
    }

    /**
     * 디버그용: 캐시된 DataSource 설정 확인
     */
    @GetMapping("/debug/datasources")
    public ResponseEntity<Map<String, Object>> debugDatasources() {
        Map<String, Object> result = new LinkedHashMap<>();

        Map<String, DataSourceInfo> cached = syncDataSourceService.getCachedDataSourceInfos();
        result.put("cachedCount", cached.size());
        result.put("cachedDatasources", cached.entrySet().stream()
                .collect(LinkedHashMap::new,
                        (m, e) -> m.put(e.getKey(), Map.of(
                                "datasourceId", e.getValue().getDatasourceId(),
                                "dbType", e.getValue().getDbType() != null ? e.getValue().getDbType() : "",
                                "host", e.getValue().getHost() != null ? e.getValue().getHost() : "",
                                "port", e.getValue().getPort() != null ? e.getValue().getPort() : 0,
                                "databaseName", e.getValue().getDatabaseName() != null ? e.getValue().getDatabaseName() : ""
                        )),
                        LinkedHashMap::putAll));

        return ResponseEntity.ok(result);
    }
}
