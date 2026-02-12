package com.sync.agent.bojo.controller;

import com.sync.agent.common.datasource.DataSourceInfo;
import com.sync.agent.bojo.config.PipelineRegistry;
import com.sync.agent.bojo.config.SyncDataSourceService;
import com.sync.agent.bojo.entity.local.DataSourceConfig;
import com.sync.agent.bojo.entity.repository.DataSourceConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequiredArgsConstructor
public class HealthController {

    @Value("${agent.zone}")
    private String zone;

    private final PipelineRegistry pipelineRegistry;
    private final SyncDataSourceService syncDataSourceService;
    private final DataSourceConfigRepository dataSourceConfigRepository;

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "UP");
        result.put("appName", "sync-agent-bojo");
        result.put("zone", zone);
        result.put("registeredAgents", pipelineRegistry.size());

        // Agent 목록 (타입별)
        Set<String> rcvAgents = pipelineRegistry.getAgentIdsByType("RCV");
        Set<String> loaderAgents = pipelineRegistry.getAgentIdsByType("LOADER");
        Set<String> sndAgents = pipelineRegistry.getAgentIdsByType("SND");

        result.put("rcvAgents", rcvAgents);
        result.put("loaderAgents", loaderAgents);
        result.put("sndAgents", sndAgents);

        return ResponseEntity.ok(result);
    }

    /**
     * 디버그용: 캐시된 DataSource 설정 확인
     */
    @GetMapping("/debug/datasources")
    public ResponseEntity<Map<String, Object>> debugDatasources() {
        Map<String, Object> result = new LinkedHashMap<>();

        List<DataSourceConfig> allConfigs = dataSourceConfigRepository.findAll();
        List<DataSourceConfig> activeConfigs = dataSourceConfigRepository.findByIsActiveTrue();

        result.put("dbTotalCount", allConfigs.size());
        result.put("dbActiveCount", activeConfigs.size());
        result.put("dbConfigs", allConfigs.stream().map(c -> Map.of(
                "datasourceId", c.getDatasourceId(),
                "datasourceName", c.getDatasourceName() != null ? c.getDatasourceName() : "",
                "dbType", c.getDbType() != null ? c.getDbType() : "",
                "host", c.getHost() != null ? c.getHost() : "",
                "port", c.getPort() != null ? c.getPort() : 0,
                "databaseName", c.getDatabaseName() != null ? c.getDatabaseName() : "",
                "isActive", c.getIsActive() != null ? c.getIsActive() : false
        )).toList());

        Map<String, DataSourceInfo> cached = syncDataSourceService.getCachedDataSourceInfos();
        result.put("cachedCount", cached.size());
        result.put("cachedKeys", cached.keySet());

        return ResponseEntity.ok(result);
    }

    /**
     * 디버그용: DataSource 설정 다시 로드
     */
    @PostMapping("/debug/datasources/reload")
    public ResponseEntity<Map<String, Object>> reloadDatasources() {
        syncDataSourceService.loadDataSourceConfigsFromDb();
        return debugDatasources();
    }
}
