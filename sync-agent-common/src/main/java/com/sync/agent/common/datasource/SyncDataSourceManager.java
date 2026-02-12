package com.sync.agent.common.datasource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 파이프라인 실행 시점에 대표 Agent로부터 DataSource 정보를 받아와서 관리
 */
@Slf4j
public class SyncDataSourceManager {

    private final ZoneMasterClient zoneMasterClient;
    private final PasswordEncryptor passwordEncryptor;
    private final Map<String, HikariDataSource> dataSources = new ConcurrentHashMap<>();
    private final Map<String, JdbcTemplate> jdbcTemplates = new ConcurrentHashMap<>();

    public SyncDataSourceManager(String zoneMasterUrl, String encryptionKey) {
        this.zoneMasterClient = new ZoneMasterClient(zoneMasterUrl);
        this.passwordEncryptor = new PasswordEncryptor(encryptionKey);
    }

    /**
     * DataSource ID로 JdbcTemplate 획득
     * 최초 호출 시 대표 Agent API 호출해서 연결 정보 받아옴
     */
    public JdbcTemplate getJdbcTemplate(String datasourceId) {
        return jdbcTemplates.computeIfAbsent(datasourceId, this::createJdbcTemplate);
    }

    /**
     * DataSource ID로 DataSource 획득
     */
    public DataSource getDataSource(String datasourceId) {
        return dataSources.computeIfAbsent(datasourceId, this::createDataSource);
    }

    private JdbcTemplate createJdbcTemplate(String datasourceId) {
        DataSource ds = getDataSource(datasourceId);
        return new JdbcTemplate(ds);
    }

    private HikariDataSource createDataSource(String datasourceId) {
        log.info("Fetching DataSource info from Zone Master: {}", datasourceId);

        DataSourceInfo info = zoneMasterClient.getDataSourceInfo(datasourceId);

        HikariConfig config = new HikariConfig();
        config.setPoolName("SyncPool-" + datasourceId);
        config.setJdbcUrl(info.getJdbcUrl());
        config.setUsername(info.getUsername());
        config.setPassword(passwordEncryptor.decrypt(info.getPassword()));
        config.setDriverClassName(info.getDriverClassName());
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);

        HikariDataSource ds = new HikariDataSource(config);
        log.info("DataSource created: {} -> {}", datasourceId, info.getJdbcUrl());

        return ds;
    }

    /**
     * 특정 DataSource 연결 해제
     */
    public void closeDataSource(String datasourceId) {
        HikariDataSource ds = dataSources.remove(datasourceId);
        jdbcTemplates.remove(datasourceId);
        if (ds != null && !ds.isClosed()) {
            ds.close();
            log.info("DataSource closed: {}", datasourceId);
        }
    }

    /**
     * 모든 DataSource 연결 해제
     */
    public void closeAll() {
        dataSources.forEach((id, ds) -> {
            if (!ds.isClosed()) {
                ds.close();
                log.info("DataSource closed: {}", id);
            }
        });
        dataSources.clear();
        jdbcTemplates.clear();
    }

    /**
     * DataSource 연결 테스트
     */
    public boolean testConnection(String datasourceId) {
        try {
            JdbcTemplate jdbc = getJdbcTemplate(datasourceId);
            jdbc.queryForObject("SELECT 1", Integer.class);
            return true;
        } catch (Exception e) {
            log.error("Connection test failed for {}: {}", datasourceId, e.getMessage());
            return false;
        }
    }
}
