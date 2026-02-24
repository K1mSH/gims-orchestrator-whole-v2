package com.sync.agent.bojoint.controller;

import com.sync.agent.common.datasource.DataSourceInfo;
import com.sync.agent.bojoint.config.PipelineRegistry;
import com.sync.agent.bojoint.config.SyncDataSourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@RestController
@RequiredArgsConstructor
public class HealthController {

    @Value("${agent.zone}")
    private String zone;

    private final PipelineRegistry pipelineRegistry;
    private final SyncDataSourceService syncDataSourceService;

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "UP");
        result.put("appName", "sync-agent-bojo-int");
        result.put("zone", zone);
        result.put("registeredAgents", pipelineRegistry.size());

        Set<String> rcvAgents = pipelineRegistry.getAgentCodesByType("RCV");
        result.put("rcvAgents", rcvAgents);

        return ResponseEntity.ok(result);
    }

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
