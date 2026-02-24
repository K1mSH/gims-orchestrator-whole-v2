package com.sync.agent.bojo.config;

import com.sync.agent.common.controller.DataSourceProvider;
import com.sync.agent.common.datasource.DataSourceInfo;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.annotation.PreDestroy;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 통합 Agent - 파이프라인 실행 시 Source/Target DataSource 관리
 *
 * Orchestrator에서 파이프라인 실행 시 연결 정보를 직접 전달받음
 * ThreadLocal에 DataSourceInfo를 저장하고, DataSource 생성 시 사용
 *
 * Fallback 전략:
 * 1. ThreadLocal (파이프라인 실행 중)
 * 2. 메모리 캐시 (이전 실행에서 저장된 정보)
 * 3. Orchestrator API 호출 (캐시 miss 시 동적 조회)
 * 4. Spring 기본 DataSource (Agent 로컬 DB = IF/Target DB)
 */
@Slf4j
@Service
public class SyncDataSourceService implements DataSourceProvider {

    // Spring Boot 기본 DataSource (application.yml에서 설정된 dev DB)
    private final JdbcTemplate defaultJdbcTemplate;

    @Value("${agent.orchestrator-url:http://localhost:8080}")
    private String orchestratorUrl;

    // ThreadLocal로 파이프라인 실행별 datasource 연결 정보 관리
    private static final ThreadLocal<DataSourceInfo> currentSourceDatasource = new ThreadLocal<>();
    private static final ThreadLocal<DataSourceInfo> currentTargetDatasource = new ThreadLocal<>();

    // Orchestrator에서 전달받은 datasource 정보 캐시
    private final Map<String, DataSourceInfo> cachedDataSourceInfos = new ConcurrentHashMap<>();

    private final Map<String, HikariDataSource> dataSources = new ConcurrentHashMap<>();
    private final Map<String, JdbcTemplate> jdbcTemplates = new ConcurrentHashMap<>();

    public SyncDataSourceService(DataSource dataSource) {
        this.defaultJdbcTemplate = new JdbcTemplate(dataSource);
    }

    // ==================== DataSourceProvider 구현 ====================

    @Override
    public String getSourceDatasourceId() {
        DataSourceInfo info = currentSourceDatasource.get();
        if (info != null) return info.getDatasourceId();

        return cachedDataSourceInfos.keySet().stream()
                .filter(id -> id.toLowerCase().contains("source"))
                .findFirst()
                .or(() -> cachedDataSourceInfos.keySet().stream().findFirst())
                .orElseThrow(() -> new IllegalStateException("No SOURCE datasource configured"));
    }

    @Override
    public String getTargetDatasourceId() {
        DataSourceInfo info = currentTargetDatasource.get();
        if (info != null) return info.getDatasourceId();

        return cachedDataSourceInfos.keySet().stream()
                .filter(id -> id.toLowerCase().contains("target"))
                .findFirst()
                .or(() -> cachedDataSourceInfos.keySet().stream().findFirst())
                .orElseThrow(() -> new IllegalStateException("No TARGET datasource configured"));
    }

    @Override
    public String getAgentType() {
        // 통합 Agent - agentType은 PipelineRegistry에서 동적으로 결정
        return "UNIFIED";
    }

    @Override
    public String getDbType(String datasourceId) {
        DataSourceInfo info = findDataSourceInfo(datasourceId);
        return info != null ? info.getDbType() : null;
    }

    @Override
    public JdbcTemplate getJdbcTemplate(String datasourceId) {
        // 1. 캐시에 DataSourceInfo가 있으면 전용 JdbcTemplate 사용
        if (findDataSourceInfo(datasourceId) != null) {
            return jdbcTemplates.computeIfAbsent(datasourceId, this::createJdbcTemplate);
        }

        // 2. Orchestrator에서 연결 정보 조회 시도
        DataSourceInfo fetched = fetchFromOrchestrator(datasourceId);
        if (fetched != null) {
            cachedDataSourceInfos.put(datasourceId, fetched);
            return jdbcTemplates.computeIfAbsent(datasourceId, this::createJdbcTemplate);
        }

        // 3. Spring 기본 DataSource fallback (Agent 로컬 DB = IF/Target DB)
        log.debug("[Bojo] DataSource '{}' not resolved, using default JdbcTemplate (local DB)", datasourceId);
        return defaultJdbcTemplate;
    }

    private JdbcTemplate createJdbcTemplate(String datasourceId) {
        HikariDataSource ds = dataSources.computeIfAbsent(datasourceId, this::createDataSource);
        return new JdbcTemplate(ds);
    }

    private HikariDataSource createDataSource(String datasourceId) {
        log.info("[Bojo] Creating DataSource: {}", datasourceId);

        DataSourceInfo info = findDataSourceInfo(datasourceId);
        if (info == null) {
            throw new IllegalArgumentException("DataSource info not found: " + datasourceId);
        }

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setPoolName("BojoPool-" + datasourceId);
        hikariConfig.setJdbcUrl(info.getJdbcUrl());
        hikariConfig.setUsername(info.getUsername());
        hikariConfig.setPassword(info.getPassword());
        hikariConfig.setDriverClassName(info.getDriverClassName());
        hikariConfig.setMaximumPoolSize(5);
        hikariConfig.setMinimumIdle(1);

        HikariDataSource ds = new HikariDataSource(hikariConfig);
        log.info("[Bojo] DataSource created: {} -> {}", datasourceId, info.getJdbcUrl());
        return ds;
    }

    // ==================== Orchestrator 연결 정보 조회 ====================

    /**
     * Orchestrator API에서 datasource 연결 정보 동적 조회
     * Agent 재시작 후 캐시 소실된 외부 DB 접속에 사용
     */
    private DataSourceInfo fetchFromOrchestrator(String datasourceId) {
        try {
            String url = orchestratorUrl + "/api/datasources/" + datasourceId + "/connection-info";
            log.info("[Bojo] Fetching datasource info from Orchestrator: {}", datasourceId);

            RestTemplate restTemplate = new RestTemplate();
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);

            if (response == null || response.isEmpty()) {
                log.warn("[Bojo] Empty response from Orchestrator for datasource: {}", datasourceId);
                return null;
            }

            DataSourceInfo info = DataSourceInfo.builder()
                    .datasourceId((String) response.get("datasourceId"))
                    .dbType((String) response.get("dbType"))
                    .host((String) response.get("host"))
                    .port(response.get("port") instanceof Integer ? (Integer) response.get("port")
                            : Integer.parseInt(response.get("port").toString()))
                    .databaseName((String) response.get("databaseName"))
                    .username((String) response.get("username"))
                    .password((String) response.get("password"))
                    .build();

            log.info("[Bojo] Fetched datasource from Orchestrator: {} ({}:{})",
                    datasourceId, info.getHost(), info.getPort());
            return info;
        } catch (Exception e) {
            log.warn("[Bojo] Failed to fetch datasource from Orchestrator: {} - {}", datasourceId, e.getMessage());
            return null;
        }
    }

    // ==================== ThreadLocal 관리 ====================

    public void setCurrentDatasources(DataSourceInfo sourceDatasource, DataSourceInfo targetDatasource) {
        if (sourceDatasource != null) {
            currentSourceDatasource.set(sourceDatasource);
            cachedDataSourceInfos.put(sourceDatasource.getDatasourceId(), sourceDatasource);
            log.info("Set current source datasource: {} ({}:{})",
                    sourceDatasource.getDatasourceId(), sourceDatasource.getHost(), sourceDatasource.getPort());
        }
        if (targetDatasource != null) {
            currentTargetDatasource.set(targetDatasource);
            cachedDataSourceInfos.put(targetDatasource.getDatasourceId(), targetDatasource);
            log.info("Set current target datasource: {} ({}:{})",
                    targetDatasource.getDatasourceId(), targetDatasource.getHost(), targetDatasource.getPort());
        }
    }

    public void clearCurrentDatasources() {
        currentSourceDatasource.remove();
        currentTargetDatasource.remove();
        log.debug("Cleared current datasource info");
    }

    // ==================== DataSourceInfo 조회 (DynamicEntityManagerService용) ====================

    public DataSourceInfo getSourceDatasourceInfoOrNull() {
        DataSourceInfo info = currentSourceDatasource.get();
        if (info != null) return info;
        try {
            String dsId = getSourceDatasourceId();
            return cachedDataSourceInfos.get(dsId);
        } catch (Exception e) {
            return null;
        }
    }

    public DataSourceInfo getTargetDatasourceInfoOrNull() {
        DataSourceInfo info = currentTargetDatasource.get();
        if (info != null) return info;
        try {
            String dsId = getTargetDatasourceId();
            return cachedDataSourceInfos.get(dsId);
        } catch (Exception e) {
            return null;
        }
    }

    private DataSourceInfo findDataSourceInfo(String datasourceId) {
        DataSourceInfo sourceInfo = currentSourceDatasource.get();
        if (sourceInfo != null && datasourceId.equals(sourceInfo.getDatasourceId())) return sourceInfo;

        DataSourceInfo targetInfo = currentTargetDatasource.get();
        if (targetInfo != null && datasourceId.equals(targetInfo.getDatasourceId())) return targetInfo;

        return cachedDataSourceInfos.get(datasourceId);
    }

    public Map<String, DataSourceInfo> getCachedDataSourceInfos() {
        return new ConcurrentHashMap<>(cachedDataSourceInfos);
    }

    @PreDestroy
    public void closeAll() {
        dataSources.forEach((id, ds) -> {
            if (!ds.isClosed()) {
                ds.close();
                log.info("[Bojo] DataSource closed: {}", id);
            }
        });
        dataSources.clear();
        jdbcTemplates.clear();
    }
}
