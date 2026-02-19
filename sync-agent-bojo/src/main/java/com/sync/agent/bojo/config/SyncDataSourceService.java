package com.sync.agent.bojo.config;

import com.sync.agent.common.controller.DataSourceProvider;
import com.sync.agent.common.datasource.DataSourceInfo;
import com.sync.agent.bojo.entity.local.DataSourceConfig;
import com.sync.agent.bojo.entity.repository.DataSourceConfigRepository;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.jasypt.encryption.pbe.PooledPBEStringEncryptor;
import org.jasypt.iv.RandomIvGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 통합 Agent - 파이프라인 실행 시 Source/Target DataSource 관리
 *
 * Orchestrator에서 파이프라인 실행 시 연결 정보를 직접 전달받음
 * ThreadLocal에 DataSourceInfo를 저장하고, DataSource 생성 시 사용
 *
 * RSV + Loader 버전 통합
 */
@Slf4j
@Service
public class SyncDataSourceService implements DataSourceProvider {

    private final DataSourceConfigRepository dataSourceConfigRepository;
    private PooledPBEStringEncryptor stringEncryptor;

    @Value("${jasypt.encryptor.password}")
    private String encryptorPassword;

    // ThreadLocal로 파이프라인 실행별 datasource 연결 정보 관리
    private static final ThreadLocal<DataSourceInfo> currentSourceDatasource = new ThreadLocal<>();
    private static final ThreadLocal<DataSourceInfo> currentTargetDatasource = new ThreadLocal<>();

    // DB에서 로드한 datasource 정보 캐시
    private final Map<String, DataSourceInfo> cachedDataSourceInfos = new ConcurrentHashMap<>();

    // Role별 datasourceId 캐시 (SOURCE, TARGET)
    private final Map<String, String> roleToDataSourceId = new ConcurrentHashMap<>();

    private final Map<String, HikariDataSource> dataSources = new ConcurrentHashMap<>();
    private final Map<String, JdbcTemplate> jdbcTemplates = new ConcurrentHashMap<>();

    @Autowired
    public SyncDataSourceService(DataSourceConfigRepository dataSourceConfigRepository) {
        this.dataSourceConfigRepository = dataSourceConfigRepository;
    }

    private PooledPBEStringEncryptor createEncryptor() {
        PooledPBEStringEncryptor encryptor = new PooledPBEStringEncryptor();
        encryptor.setPoolSize(1);
        encryptor.setPassword(encryptorPassword);
        encryptor.setAlgorithm("PBEWithHMACSHA512AndAES_256");
        encryptor.setIvGenerator(new RandomIvGenerator());
        return encryptor;
    }

    @PostConstruct
    public void loadDataSourceConfigsFromDb() {
        log.info("========== [Bojo Agent] Loading datasource configs from DB ==========");

        try {
            this.stringEncryptor = createEncryptor();
            log.info("Jasypt encryptor initialized");
        } catch (Exception e) {
            log.error("Failed to initialize Jasypt encryptor: {}", e.getMessage(), e);
        }

        try {
            long totalCount = dataSourceConfigRepository.count();
            log.info("Total datasource_config records in DB: {}", totalCount);

            List<DataSourceConfig> configs = dataSourceConfigRepository.findByIsActiveTrue();
            log.info("Found {} active datasource configs in DB", configs.size());

            if (configs.isEmpty() && totalCount > 0) {
                log.warn("No active configs found but {} total records exist. Check is_active column!", totalCount);
                configs = dataSourceConfigRepository.findAll();
                log.info("Loading all {} configs regardless of is_active", configs.size());
            }

            for (DataSourceConfig config : configs) {
                log.info("Processing datasource config: id={}, name={}, dbType={}, host={}, port={}, role={}, isActive={}",
                        config.getDatasourceId(), config.getDatasourceName(),
                        config.getDbType(), config.getHost(), config.getPort(),
                        config.getRole(), config.getIsActive());

                DataSourceInfo info = convertToDataSourceInfo(config);
                cachedDataSourceInfos.put(config.getDatasourceId(), info);

                if (config.getRole() != null && !config.getRole().isBlank()) {
                    roleToDataSourceId.put(config.getRole().toUpperCase(), config.getDatasourceId());
                    log.info("Mapped role {} -> datasourceId {}",
                            config.getRole().toUpperCase(), config.getDatasourceId());
                }
            }
            log.info("========== Total {} datasource configs loaded ==========", cachedDataSourceInfos.size());
        } catch (Exception e) {
            log.error("========== Failed to load datasource configs from DB ==========", e);
        }
    }

    private DataSourceInfo convertToDataSourceInfo(DataSourceConfig config) {
        String username = config.getUsername();
        String password = config.getPassword();

        if (username != null && username.startsWith("ENC(") && username.endsWith(")")) {
            if (stringEncryptor != null) {
                try {
                    username = stringEncryptor.decrypt(username.substring(4, username.length() - 1));
                } catch (Exception e) {
                    log.error("Failed to decrypt username for datasource {}: {}", config.getDatasourceId(), e.getMessage());
                }
            }
        }

        if (password != null && password.startsWith("ENC(") && password.endsWith(")")) {
            if (stringEncryptor != null) {
                try {
                    password = stringEncryptor.decrypt(password.substring(4, password.length() - 1));
                } catch (Exception e) {
                    log.error("Failed to decrypt password for datasource {}: {}", config.getDatasourceId(), e.getMessage());
                }
            }
        }

        return DataSourceInfo.builder()
                .datasourceId(config.getDatasourceId())
                .datasourceName(config.getDatasourceName())
                .dbType(config.getDbType())
                .host(config.getHost())
                .port(config.getPort())
                .databaseName(config.getDatabaseName())
                .username(username)
                .password(password)
                .build();
    }

    // ==================== DataSourceProvider 구현 ====================

    @Override
    public String getSourceDatasourceId() {
        DataSourceInfo info = currentSourceDatasource.get();
        if (info != null) return info.getDatasourceId();

        String sourceId = roleToDataSourceId.get("SOURCE");
        if (sourceId != null) return sourceId;

        // SOURCE role 없으면 TARGET을 fallback으로 사용
        String targetId = roleToDataSourceId.get("TARGET");
        if (targetId != null) {
            log.debug("No SOURCE role configured, using TARGET as fallback: {}", targetId);
            return targetId;
        }

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

        String targetId = roleToDataSourceId.get("TARGET");
        if (targetId != null) return targetId;

        return cachedDataSourceInfos.keySet().stream()
                .filter(id -> id.toLowerCase().contains("target"))
                .findFirst()
                .or(() -> cachedDataSourceInfos.keySet().stream().skip(1).findFirst())
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
        return jdbcTemplates.computeIfAbsent(datasourceId, this::createJdbcTemplate);
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

        DataSourceInfo cachedInfo = cachedDataSourceInfos.get(datasourceId);
        if (cachedInfo != null) return cachedInfo;

        try {
            DataSourceConfig config = dataSourceConfigRepository.findById(datasourceId).orElse(null);
            if (config != null && config.getIsActive()) {
                DataSourceInfo info = convertToDataSourceInfo(config);
                cachedDataSourceInfos.put(datasourceId, info);
                return info;
            }
        } catch (Exception e) {
            log.warn("Failed to load datasource config from DB: {}", e.getMessage());
        }

        return null;
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
