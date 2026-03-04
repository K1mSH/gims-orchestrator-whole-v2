package com.sync.proxy.dmz.controller;

import com.sync.agent.common.datasource.DataSourceInfo;
import com.sync.proxy.dmz.config.ProxyDataSourceService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DB 프록시 전용 헬스체크
 */
@RestController
@RequiredArgsConstructor
public class HealthController {

    @Value("${agent.zone}")
    private String zone;

    private final ProxyDataSourceService proxyDataSourceService;

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "UP");
        result.put("appName", "sync-proxy-dmz");
        result.put("type", "DB_CON_PROXY");
        result.put("zone", zone);
        return ResponseEntity.ok(result);
    }

    /**
     * 디버그용: 캐시된 DataSource 설정 확인
     */
    @GetMapping("/debug/datasources")
    public ResponseEntity<Map<String, Object>> debugDatasources() {
        Map<String, Object> result = new LinkedHashMap<>();

        Map<String, DataSourceInfo> cached = proxyDataSourceService.getCachedDataSourceInfos();
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
