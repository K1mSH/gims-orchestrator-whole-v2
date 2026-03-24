package com.sync.agent.bojoint.config;

import com.sync.agent.common.controller.DataSourceProvider;
import com.sync.agent.common.datasource.DataSourceInfo;
import com.sync.agent.common.datasource.PasswordEncryptor;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.annotation.PreDestroy;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 내부망 Agent - 파이프라인 실행 시 Source/Target DataSource 관리
 */
@Slf4j
@Service
public class SyncDataSourceService implements DataSourceProvider {

    private final JdbcTemplate defaultJdbcTemplate;
    private final PasswordEncryptor passwordEncryptor;

    @Value("${agent.orchestrator-url:http://localhost:8080}")
    private String orchestratorUrl;

    @Value("${agent.proxy-url:}")
    private String proxyUrl;

    @Value("${agent.api-key:}")
    private String proxyApiKey;

    private static final ThreadLocal<DataSourceInfo> currentSourceDatasource = new ThreadLocal<>();
    private static final ThreadLocal<DataSourceInfo> currentTargetDatasource = new ThreadLocal<>();

    private final Map<String, DataSourceInfo> cachedDataSourceInfos = new ConcurrentHashMap<>();
    private final Map<String, HikariDataSource> dataSources = new ConcurrentHashMap<>();
    private final Map<String, JdbcTemplate> jdbcTemplates = new ConcurrentHashMap<>();

    public SyncDataSourceService(DataSource dataSource,
                                  @Value("${jasypt.encryptor.password}") String secretKey) {
        this.defaultJdbcTemplate = new JdbcTemplate(dataSource);
        this.passwordEncryptor = new PasswordEncryptor(secretKey);
    }

    @Override
    public String getSourceDatasourceId() {
        DataSourceInfo info = currentSourceDatasource.get();
        if (info != null) return info.getDatasourceId();

        return cachedDataSourceInfos.keySet().stream()
                .filter(id -> id.toLowerCase().contains("source"))
                .findFirst()
                .or(() -> cachedDataSourceInfos.keySet().stream().findFirst())
                .orElseThrow(() -> new IllegalStateException("SOURCE 데이터소스가 설정되지 않았습니다"));
    }

    @Override
    public String getTargetDatasourceId() {
        DataSourceInfo info = currentTargetDatasource.get();
        if (info != null) return info.getDatasourceId();

        return cachedDataSourceInfos.keySet().stream()
                .filter(id -> id.toLowerCase().contains("target"))
                .findFirst()
                .or(() -> cachedDataSourceInfos.keySet().stream().findFirst())
                .orElseThrow(() -> new IllegalStateException("TARGET 데이터소스가 설정되지 않았습니다"));
    }

    @Override
    public String getAgentType() {
        return "UNIFIED";
    }

    @Override
    public String getDbType(String datasourceId) {
        DataSourceInfo info = findDataSourceInfo(datasourceId);
        return info != null ? info.getDbType() : null;
    }

    @Override
    public JdbcTemplate getJdbcTemplate(String datasourceId) {
        if (findDataSourceInfo(datasourceId) != null) {
            return jdbcTemplates.computeIfAbsent(datasourceId, this::createJdbcTemplate);
        }

        log.debug("[BojoInt] DataSource '{}' 미해석, 기본 JdbcTemplate (로컬 DB) 사용", datasourceId);
        return defaultJdbcTemplate;
    }

    private JdbcTemplate createJdbcTemplate(String datasourceId) {
        HikariDataSource ds = dataSources.computeIfAbsent(datasourceId, this::createDataSource);
        return new JdbcTemplate(ds);
    }

    private HikariDataSource createDataSource(String datasourceId) {
        log.info("[BojoInt] DataSource 생성: {}", datasourceId);

        DataSourceInfo info = findDataSourceInfo(datasourceId);
        if (info == null) {
            throw new IllegalArgumentException("DataSource 정보를 찾을 수 없습니다: " + datasourceId);
        }

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setPoolName("BojoIntPool-" + datasourceId);
        hikariConfig.setJdbcUrl(info.getJdbcUrl());
        hikariConfig.setUsername(info.getUsername());
        hikariConfig.setPassword(info.getPassword());
        hikariConfig.setDriverClassName(info.getDriverClassName());
        hikariConfig.setMaximumPoolSize(10);
        hikariConfig.setMinimumIdle(2);
        hikariConfig.setConnectionTimeout(10_000);
        hikariConfig.setMaxLifetime(600_000);
        hikariConfig.setKeepaliveTime(120_000);
        hikariConfig.setConnectionTestQuery("SELECT 1");
        hikariConfig.setLeakDetectionThreshold(60_000);

        HikariDataSource ds = new HikariDataSource(hikariConfig);
        log.info("[BojoInt] DataSource 생성 완료: {} -> {} (maxPool=10, timeout=10s, leak=60s)", datasourceId, info.getJdbcUrl());
        return ds;
    }

    /**
     * Proxy 경유로 datasource 연결 정보 해석
     * Proxy가 Orchestrator의 connection-info를 암호문 그대로 패스스루한다.
     * Agent가 직접 PasswordEncryptor로 복호화하여 DataSource를 생성한다.
     * Orchestrator 직접 조회는 허용하지 않음 (Proxy 필수).
     */
    public DataSourceInfo resolveFromProxy(String datasourceId) {
        DataSourceInfo cached = cachedDataSourceInfos.get(datasourceId);
        if (cached != null) {
            log.debug("[BojoInt] DataSource 캐시에서 해석: {}", datasourceId);
            return cached;
        }

        if (proxyUrl == null || proxyUrl.isEmpty()) {
            throw new IllegalStateException("[BojoInt] proxy-url 미설정. 자격증명 해석 불가: " + datasourceId);
        }

        DataSourceInfo info = fetchConnectionInfoFromProxy(datasourceId);
        if (info != null) { // 캐싱
            cachedDataSourceInfos.put(datasourceId, info);
            return info;
        }

        throw new IllegalStateException("[BojoInt] Proxy에서 datasource 해석 실패: " + datasourceId);
    }

    private DataSourceInfo fetchConnectionInfoFromProxy(String datasourceId) {
        try {
            String url = proxyUrl + "/api/datasources/" + datasourceId + "/connection-info";
            log.info("[BojoInt] Proxy에서 데이터소스 정보 조회: {}", datasourceId);

            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            if (proxyApiKey != null && !proxyApiKey.isEmpty()) {
                headers.set("X-API-Key", proxyApiKey);
            }
            ResponseEntity<Map> responseEntity = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> response = responseEntity.getBody();

            if (response == null || response.isEmpty()) {
                log.warn("[BojoInt] Proxy에서 빈 응답 수신 (datasource: {})", datasourceId);
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

            log.info("[BojoInt] Proxy에서 데이터소스 조회 완료: {} ({}:{})",
                    datasourceId, info.getHost(), info.getPort());
            return info;
        } catch (Exception e) {
            log.warn("[BojoInt] Proxy에서 데이터소스 조회 실패: {} - {}", datasourceId, e.getMessage());
            return null;
        }
    }

    public String checkPoolHealth(String datasourceId) {
        HikariDataSource ds = dataSources.get(datasourceId);
        if (ds == null) return null;

        if (ds.isClosed()) {
            return "Connection pool 비활성 (datasource: " + datasourceId + ")";
        }

        var pool = ds.getHikariPoolMXBean();
        if (pool == null) return null;

        int active = pool.getActiveConnections();
        int waiting = pool.getThreadsAwaitingConnection();
        int max = ds.getMaximumPoolSize();
        int total = pool.getTotalConnections();

        log.info("[BojoInt] Pool 상태 점검 '{}': active={}/{}, waiting={}, total={}",
                datasourceId, active, max, waiting, total);

        if (waiting > 0) {
            return String.format("Connection pool 대기열 존재 (datasource: %s, waiting: %d)", datasourceId, waiting);
        }
        if (active >= max * 0.8) {
            return String.format("Connection pool 사용률 과다 (datasource: %s, active: %d/%d)", datasourceId, active, max);
        }
        if (total == 0 && active == 0) {
            return String.format("Connection pool 비활성 — DB 연결 확인 필요 (datasource: %s)", datasourceId);
        }

        return null;
    }

    public void setCurrentDatasources(DataSourceInfo sourceDatasource, DataSourceInfo targetDatasource) {
        if (sourceDatasource != null) {
            currentSourceDatasource.set(sourceDatasource);
            cachedDataSourceInfos.put(sourceDatasource.getDatasourceId(), sourceDatasource);
            log.info("[BojoInt] 현재 source 데이터소스 설정: {} ({}:{})",
                    sourceDatasource.getDatasourceId(), sourceDatasource.getHost(), sourceDatasource.getPort());
        }
        if (targetDatasource != null) {
            currentTargetDatasource.set(targetDatasource);
            cachedDataSourceInfos.put(targetDatasource.getDatasourceId(), targetDatasource);
            log.info("[BojoInt] 현재 target 데이터소스 설정: {} ({}:{})",
                    targetDatasource.getDatasourceId(), targetDatasource.getHost(), targetDatasource.getPort());
        }
    }

    public void clearCurrentDatasources() {
        currentSourceDatasource.remove();
        currentTargetDatasource.remove();
        log.debug("[BojoInt] 현재 데이터소스 정보 초기화");
    }

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
                log.info("[BojoInt] DataSource 종료: {}", id);
            }
        });
        dataSources.clear();
        jdbcTemplates.clear();
    }
}
