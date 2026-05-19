package com.infolink.proxy.dmz.config;

import com.infolink.agent.common.controller.DataSourceProvider;
import com.infolink.agent.common.datasource.DataSourceInfo;
import com.infolink.agent.common.datasource.PasswordEncryptor;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.annotation.PreDestroy;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DB 프록시 전용 DataSourceProvider 구현
 *
 * 파이프라인 실행이 없으므로 ThreadLocal 불필요.
 * Fallback 전략:
 * 1. 메모리 캐시 (Orchestrator에서 전달받은 정보)
 * 2. Orchestrator API 호출 (캐시 miss 시)
 * 3. Spring 기본 DataSource (Agent 로컬 DB)
 */
@Slf4j
@Service
public class ProxyDataSourceService implements DataSourceProvider {

    private final JdbcTemplate defaultJdbcTemplate;
    private final PasswordEncryptor passwordEncryptor;

    @Value("${agent.orchestrator-url:http://localhost:8080}")
    private String orchestratorUrl;

    @Value("${agent.api-key:}")
    private String apiKey;

    // Orchestrator에서 전달받은 datasource 정보 캐시
    private final Map<String, DataSourceInfo> cachedDataSourceInfos = new ConcurrentHashMap<>();
    private final Map<String, HikariDataSource> dataSources = new ConcurrentHashMap<>();
    private final Map<String, JdbcTemplate> jdbcTemplates = new ConcurrentHashMap<>();

    public ProxyDataSourceService(DataSource dataSource,
                                   @Value("${jasypt.encryptor.password}") String secretKey) {
        this.defaultJdbcTemplate = new JdbcTemplate(dataSource);
        this.passwordEncryptor = new PasswordEncryptor(secretKey);
    }

    @Override
    public String getSourceDatasourceId() {
        return cachedDataSourceInfos.keySet().stream()
                .filter(id -> id.toLowerCase().contains("source"))
                .findFirst()
                .or(() -> cachedDataSourceInfos.keySet().stream().findFirst())
                .orElseThrow(() -> new IllegalStateException("No SOURCE datasource configured"));
    }

    @Override
    public String getTargetDatasourceId() {
        return cachedDataSourceInfos.keySet().stream()
                .filter(id -> id.toLowerCase().contains("target"))
                .findFirst()
                .or(() -> cachedDataSourceInfos.keySet().stream().findFirst())
                .orElseThrow(() -> new IllegalStateException("No TARGET datasource configured"));
    }

    @Override
    public String getAgentType() {
        return "PROXY";
    }

    @Override
    public String getDbType(String datasourceId) {
        if (datasourceId == null) return null;
        DataSourceInfo info = cachedDataSourceInfos.get(datasourceId);
        return info != null ? info.getDbType() : null;
    }

    @Override
    public JdbcTemplate getJdbcTemplate(String datasourceId) {
        // null/blank → 기본 DataSource fallback (backend 가 X-Manage 헤더 안 보내는 경우 — sync_log + execution = JPA primary 룰)
        if (datasourceId == null || datasourceId.isBlank()) {
            return defaultJdbcTemplate;
        }
        // 1. 캐시에 있으면 전용 JdbcTemplate 사용
        if (cachedDataSourceInfos.containsKey(datasourceId)) {
            return jdbcTemplates.computeIfAbsent(datasourceId, this::createJdbcTemplate);
        }

        // 2. Orchestrator에서 연결 정보 조회
        DataSourceInfo fetched = fetchFromOrchestrator(datasourceId);
        if (fetched != null) {
            cachedDataSourceInfos.put(datasourceId, fetched);
            return jdbcTemplates.computeIfAbsent(datasourceId, this::createJdbcTemplate);
        }

        // 3. Spring 기본 DataSource fallback (프록시 로컬 DB)
        log.debug("[Proxy] DataSource '{}' not resolved, using default JdbcTemplate", datasourceId);
        return defaultJdbcTemplate;
    }

    private JdbcTemplate createJdbcTemplate(String datasourceId) {
        HikariDataSource ds = dataSources.computeIfAbsent(datasourceId, this::createDataSource);
        return new JdbcTemplate(ds);
    }

    private HikariDataSource createDataSource(String datasourceId) {
        log.info("[Proxy] Creating DataSource: {}", datasourceId);

        DataSourceInfo info = cachedDataSourceInfos.get(datasourceId);
        if (info == null) {
            throw new IllegalArgumentException("DataSource info not found: " + datasourceId);
        }

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setPoolName("ProxyPool-" + datasourceId);
        hikariConfig.setJdbcUrl(info.getJdbcUrl());
        hikariConfig.setUsername(info.getUsername());
        hikariConfig.setPassword(info.getPassword());
        hikariConfig.setDriverClassName(info.getDriverClassName());
        hikariConfig.setMaximumPoolSize(10);
        hikariConfig.setMinimumIdle(2);
        hikariConfig.setConnectionTimeout(10_000);
        hikariConfig.setMaxLifetime(600_000);
        hikariConfig.setKeepaliveTime(120_000);
        String dbType = info.getDbType();
        if ("ORACLE".equalsIgnoreCase(dbType) || "TIBERO".equalsIgnoreCase(dbType)) {
            hikariConfig.setConnectionTestQuery("SELECT 1 FROM DUAL");
        } else {
            hikariConfig.setConnectionTestQuery("SELECT 1");
        }
        hikariConfig.setLeakDetectionThreshold(60_000);

        HikariDataSource ds = new HikariDataSource(hikariConfig);
        log.info("[Proxy] DataSource created: {} -> {} (maxPool=10, timeout=10s, leak=60s)", datasourceId, info.getJdbcUrl());
        return ds;
    }

    /**
     * Orchestrator API에서 datasource 연결 정보 동적 조회
     */
    private DataSourceInfo fetchFromOrchestrator(String datasourceId) {
        try {
            String url = orchestratorUrl + "/api/datasources/" + datasourceId + "/connection-info";
            log.info("[Proxy] Fetching datasource info from Orchestrator: {}", datasourceId);

            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            if (apiKey != null && !apiKey.isEmpty()) {
                headers.set("X-API-Key", apiKey);   // Backend ApiKeyFilter 통과용 (5/6 보강 정합)
            }
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class).getBody();

            if (response == null || response.isEmpty()) {
                log.warn("[Proxy] Empty response from Orchestrator for datasource: {}", datasourceId);
                return null;
            }

            DataSourceInfo info = DataSourceInfo.builder()
                    .datasourceId((String) response.get("datasourceId"))
                    .dbType((String) response.get("dbType"))
                    .host((String) response.get("host"))
                    .port(response.get("port") instanceof Integer ? (Integer) response.get("port")
                            : Integer.parseInt(response.get("port").toString()))
                    .databaseName((String) response.get("databaseName"))
                    .username(passwordEncryptor.decrypt((String) response.get("username")))
                    .password(passwordEncryptor.decrypt((String) response.get("password")))
                    .build();

            log.info("[Proxy] Fetched datasource from Orchestrator: {} ({}:{})",
                    datasourceId, info.getHost(), info.getPort());
            return info;
        } catch (Exception e) {
            log.warn("[Proxy] Failed to fetch datasource from Orchestrator: {} - {}", datasourceId, e.getMessage());
            return null;
        }
    }

    public Map<String, DataSourceInfo> getCachedDataSourceInfos() {
        return new ConcurrentHashMap<>(cachedDataSourceInfos);
    }

    @PreDestroy
    public void closeAll() {
        dataSources.forEach((id, ds) -> {
            if (!ds.isClosed()) {
                ds.close();
                log.info("[Proxy] DataSource closed: {}", id);
            }
        });
        dataSources.clear();
        jdbcTemplates.clear();
    }
}
