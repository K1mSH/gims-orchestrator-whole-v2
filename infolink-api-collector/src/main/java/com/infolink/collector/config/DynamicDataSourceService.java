package com.infolink.collector.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import javax.sql.DataSource;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestrator 등록 DB에 대한 동적 DataSource 관리
 * HikariCP 풀을 datasourceId별로 캐싱
 */
@Slf4j
@Service
public class DynamicDataSourceService {

    private final OrchestratorClient orchestratorClient;
    private final ConcurrentHashMap<String, HikariDataSource> cache = new ConcurrentHashMap<>();

    public DynamicDataSourceService(OrchestratorClient orchestratorClient) {
        this.orchestratorClient = orchestratorClient;
    }

    /**
     * datasourceId로 HikariCP DataSource 반환 (캐싱)
     */
    public DataSource getDataSource(String datasourceId) {
        return cache.computeIfAbsent(datasourceId, this::createDataSource);
    }

    private HikariDataSource createDataSource(String datasourceId) {
        log.info("[Collector] Creating DataSource: {}", datasourceId);

        OrchestratorClient.ConnectionInfo info = orchestratorClient.getConnectionInfo(datasourceId);

        HikariConfig config = new HikariConfig();
        config.setPoolName("CollectorPool-" + datasourceId);
        config.setJdbcUrl(info.getJdbcUrl());
        config.setUsername(info.getUsername());
        config.setPassword(info.getPassword());
        config.setDriverClassName(info.getDriverClassName());
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(10_000);
        config.setMaxLifetime(600_000);
        config.setConnectionTestQuery("SELECT 1");

        HikariDataSource ds = new HikariDataSource(config);
        log.info("[Collector] DataSource created: {} -> {}", datasourceId, info.getJdbcUrl());
        return ds;
    }

    /**
     * 캐시에서 특정 DataSource 제거 (설정 변경 시)
     */
    public void evict(String datasourceId) {
        HikariDataSource ds = cache.remove(datasourceId);
        if (ds != null && !ds.isClosed()) {
            ds.close();
            log.info("[Collector] DataSource evicted: {}", datasourceId);
        }
    }

    @PreDestroy
    public void closeAll() {
        cache.forEach((id, ds) -> {
            if (!ds.isClosed()) {
                ds.close();
                log.info("[Collector] DataSource closed: {}", id);
            }
        });
        cache.clear();
    }
}
