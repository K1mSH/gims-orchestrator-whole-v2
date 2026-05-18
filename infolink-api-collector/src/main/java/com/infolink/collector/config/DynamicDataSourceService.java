package com.infolink.collector.config;

import com.infolink.agent.common.client.ProxyConnectionInfoClient;
import com.infolink.agent.common.datasource.PasswordEncryptor;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PreDestroy;
import javax.sql.DataSource;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestrator 등록 DB 에 대한 동적 DataSource 관리.
 *
 * <p>HikariCP 풀을 datasourceId 별로 캐싱한다.
 * Proxy 경유로 connection-info 를 받아 (암호문 그대로) {@link PasswordEncryptor} 로 복호화 후 풀 구성.
 *
 * <p>5/18 정정: 옛 자체 {@code OrchestratorClient} 제거, common 의 {@link ProxyConnectionInfoClient} 첫 사용자.
 * 다른 6 모듈도 후속 사이클에 같은 패턴으로 통일 예정.
 *
 * @see com.infolink.agent.common.client.ProxyConnectionInfoClient
 */
@Slf4j
@Service
public class DynamicDataSourceService {

    private final ProxyConnectionInfoClient proxyClient;
    private final PasswordEncryptor passwordEncryptor;
    private final ConcurrentHashMap<String, HikariDataSource> cache = new ConcurrentHashMap<>();

    public DynamicDataSourceService(
            @Value("${agent.proxy-url}") String proxyUrl,
            @Value("${agent.api-key:}") String apiKey,
            @Value("${jasypt.encryptor.password}") String jasyptKey) {
        this.proxyClient = new ProxyConnectionInfoClient(new RestTemplate(), proxyUrl, apiKey);
        this.passwordEncryptor = new PasswordEncryptor(jasyptKey);
    }

    /**
     * datasourceId 로 HikariCP DataSource 반환 (캐싱)
     */
    public DataSource getDataSource(String datasourceId) {
        return cache.computeIfAbsent(datasourceId, this::createDataSource);
    }

    private HikariDataSource createDataSource(String datasourceId) {
        log.info("[Collector] Creating DataSource: {}", datasourceId);

        Map<String, Object> response = proxyClient.fetchEncrypted(datasourceId);
        String dbType = (String) response.get("dbType");
        String host = (String) response.get("host");
        int port = response.get("port") instanceof Integer
                ? (Integer) response.get("port")
                : Integer.parseInt(response.get("port").toString());
        String databaseName = (String) response.get("databaseName");
        String username = passwordEncryptor.decrypt((String) response.get("username"));
        String password = passwordEncryptor.decrypt((String) response.get("password"));

        String jdbcUrl = buildJdbcUrl(dbType, host, port, databaseName);
        String driverClass = resolveDriverClass(dbType);

        HikariConfig config = new HikariConfig();
        config.setPoolName("CollectorPool-" + datasourceId);
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName(driverClass);
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(10_000);
        config.setMaxLifetime(600_000);
        config.setConnectionTestQuery("SELECT 1");

        HikariDataSource ds = new HikariDataSource(config);
        log.info("[Collector] DataSource created: {} -> {}", datasourceId, jdbcUrl);
        return ds;
    }

    private String buildJdbcUrl(String dbType, String host, int port, String databaseName) {
        if ("MYSQL".equalsIgnoreCase(dbType)) {
            return String.format(
                    "jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul",
                    host, port, databaseName);
        }
        return String.format("jdbc:postgresql://%s:%d/%s", host, port, databaseName);
    }

    private String resolveDriverClass(String dbType) {
        if ("MYSQL".equalsIgnoreCase(dbType)) {
            return "com.mysql.cj.jdbc.Driver";
        }
        return "org.postgresql.Driver";
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
