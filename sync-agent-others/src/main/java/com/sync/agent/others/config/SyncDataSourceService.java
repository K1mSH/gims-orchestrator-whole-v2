package com.sync.agent.others.config;

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
    private final PasswordEncryptor passwordEncryptor;

    @Value("${agent.orchestrator-url:http://localhost:8080}")
    private String orchestratorUrl;

    @Value("${agent.proxy-url:}")
    private String proxyUrl;

    @Value("${agent.api-key:}")
    private String proxyApiKey;

    // ThreadLocal로 파이프라인 실행별 datasource 연결 정보 관리
    private static final ThreadLocal<DataSourceInfo> currentSourceDatasource = new ThreadLocal<>();
    private static final ThreadLocal<DataSourceInfo> currentTargetDatasource = new ThreadLocal<>();

    // Orchestrator에서 전달받은 datasource 정보 캐시
    private final Map<String, DataSourceInfo> cachedDataSourceInfos = new ConcurrentHashMap<>();

    private final Map<String, HikariDataSource> dataSources = new ConcurrentHashMap<>();
    private final Map<String, JdbcTemplate> jdbcTemplates = new ConcurrentHashMap<>();

    public SyncDataSourceService(DataSource dataSource,
                                  @Value("${jasypt.encryptor.password}") String secretKey) {
        this.defaultJdbcTemplate = new JdbcTemplate(dataSource);
        this.passwordEncryptor = new PasswordEncryptor(secretKey);
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

        // 2. Spring 기본 DataSource fallback (Agent 로컬 DB = IF/Target DB)
        log.debug("[Others] 데이터소스 '{}' 해석 불가, 기본 JdbcTemplate 사용 (로컬 DB)", datasourceId);
        return defaultJdbcTemplate;
    }

    private JdbcTemplate createJdbcTemplate(String datasourceId) {
        HikariDataSource ds = dataSources.computeIfAbsent(datasourceId, this::createDataSource);
        return new JdbcTemplate(ds);
    }

    private HikariDataSource createDataSource(String datasourceId) {
        log.info("[Others] 데이터소스 생성: {}", datasourceId);

        DataSourceInfo info = findDataSourceInfo(datasourceId);
        if (info == null) {
            throw new IllegalArgumentException("데이터소스 정보를 찾을 수 없습니다: " + datasourceId);
        }

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setPoolName("OthersPool-" + datasourceId);
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
        log.info("[Others] 데이터소스 생성 완료: {} -> {} (maxPool=10, timeout=10s, leak=60s)", datasourceId, info.getJdbcUrl());
        return ds;
    }

    // ==================== Proxy/Orchestrator 연결 정보 조회 ====================

    /**
     * Proxy 경유로 datasource 연결 정보 해석
     * Proxy가 Orchestrator의 connection-info를 암호문 그대로 패스스루한다.
     * Agent가 직접 PasswordEncryptor로 복호화하여 DataSource를 생성한다.
     * Orchestrator 직접 조회는 허용하지 않음 (Proxy 필수).
     */
    public DataSourceInfo resolveFromProxy(String datasourceId) {
        // 1. 캐시에서 조회
        DataSourceInfo cached = cachedDataSourceInfos.get(datasourceId);
        if (cached != null) {
            log.debug("[Others] 캐시에서 데이터소스 해석: {}", datasourceId);
            return cached;
        }

        // 2. Proxy에 요청 (필수)
        if (proxyUrl == null || proxyUrl.isEmpty()) {
            throw new IllegalStateException("[Others] proxy-url 미설정. 자격증명 해석 불가: " + datasourceId);
        }

        DataSourceInfo info = fetchConnectionInfoFromProxy(datasourceId);
        if (info != null) {
            cachedDataSourceInfos.put(datasourceId, info);
            return info;
        }

        throw new IllegalStateException("[Others] Proxy에서 datasource 해석 실패: " + datasourceId);
    }

    private DataSourceInfo fetchConnectionInfoFromProxy(String datasourceId) {
        try {
            String url = proxyUrl + "/api/datasources/" + datasourceId + "/connection-info";
            log.info("[Others] Proxy에서 데이터소스 정보 조회: {}", datasourceId);

            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            if (proxyApiKey != null && !proxyApiKey.isEmpty()) {
                headers.set("X-API-Key", proxyApiKey);
            }
            ResponseEntity<Map> responseEntity = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> response = responseEntity.getBody();

            if (response == null || response.isEmpty()) {
                log.warn("[Others] Proxy 응답이 비어있습니다 (datasource: {})", datasourceId);
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

            log.info("[Others] Proxy에서 데이터소스 조회 완료: {} ({}:{})",
                    datasourceId, info.getHost(), info.getPort());
            return info;
        } catch (Exception e) {
            log.warn("[Others] Proxy에서 데이터소스 조회 실패: {} - {}", datasourceId, e.getMessage());
            return null;
        }
    }

    // ==================== Connection 풀 상태 검사 ====================

    /**
     * HikariCP 풀 상태 검사 — 실행 전 호출하여 풀 여유 확인
     * @return null이면 정상, 문자열이면 거부 사유
     */
    public String checkPoolHealth(String datasourceId) {
        HikariDataSource ds = dataSources.get(datasourceId);
        if (ds == null) {
            // 아직 풀이 없으면 (첫 실행) → 정상
            return null;
        }

        if (ds.isClosed()) {
            return "Connection pool 비활성 (datasource: " + datasourceId + ")";
        }

        var pool = ds.getHikariPoolMXBean();
        if (pool == null) {
            return null; // MXBean 미등록 시 검사 스킵
        }

        int active = pool.getActiveConnections();
        int waiting = pool.getThreadsAwaitingConnection();
        int max = ds.getMaximumPoolSize();
        int total = pool.getTotalConnections();

        log.info("[Others] 풀 상태 검사 '{}': active={}/{}, waiting={}, total={}",
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

        return null; // 정상
    }

    // ==================== ThreadLocal 관리 ====================

    public void setCurrentDatasources(DataSourceInfo sourceDatasource, DataSourceInfo targetDatasource) {
        if (sourceDatasource != null) {
            currentSourceDatasource.set(sourceDatasource);
            cachedDataSourceInfos.put(sourceDatasource.getDatasourceId(), sourceDatasource);
            log.info("[Others] 현재 source 데이터소스 설정: {} ({}:{})",
                    sourceDatasource.getDatasourceId(), sourceDatasource.getHost(), sourceDatasource.getPort());
        }
        if (targetDatasource != null) {
            currentTargetDatasource.set(targetDatasource);
            cachedDataSourceInfos.put(targetDatasource.getDatasourceId(), targetDatasource);
            log.info("[Others] 현재 target 데이터소스 설정: {} ({}:{})",
                    targetDatasource.getDatasourceId(), targetDatasource.getHost(), targetDatasource.getPort());
        }
    }

    public void clearCurrentDatasources() {
        currentSourceDatasource.remove();
        currentTargetDatasource.remove();
        log.debug("[Others] 현재 데이터소스 정보 초기화");
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
                log.info("[Others] 데이터소스 종료: {}", id);
            }
        });
        dataSources.clear();
        jdbcTemplates.clear();
    }
}
