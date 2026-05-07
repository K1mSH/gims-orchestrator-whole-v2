package com.infolink.provider.service;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.jasypt.iv.RandomIvGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Proxy 경유 DB 접속정보 해석 → JdbcTemplate 캐싱
 * bojo-int SyncDataSourceService 패턴 간소화 버전
 */
@Slf4j
@Service
public class ProviderDataSourceService {

    @Value("${app.proxy.url}")
    private String proxyUrl;

    @Value("${app.proxy.api-key:}")
    private String proxyApiKey;

    private final StandardPBEStringEncryptor encryptor;
    private final RestTemplate restTemplate = new RestTemplate();
    private final Map<String, HikariDataSource> dataSources = new ConcurrentHashMap<>();
    private final Map<String, JdbcTemplate> jdbcTemplates = new ConcurrentHashMap<>();

    public ProviderDataSourceService(@Value("${jasypt.encryptor.password}") String secretKey) {
        this.encryptor = new StandardPBEStringEncryptor();
        this.encryptor.setPassword(secretKey);
        this.encryptor.setAlgorithm("PBEWithHMACSHA512AndAES_256");
        this.encryptor.setIvGenerator(new RandomIvGenerator());
    }

    /**
     * datasourceId로 JdbcTemplate 획득 (캐시 → Proxy 조회 → 생성)
     */
    public JdbcTemplate getJdbcTemplate(String datasourceId) {
        return jdbcTemplates.computeIfAbsent(datasourceId, this::createJdbcTemplate);
    }

    private JdbcTemplate createJdbcTemplate(String datasourceId) {
        HikariDataSource ds = dataSources.computeIfAbsent(datasourceId, this::createDataSource);
        return new JdbcTemplate(ds);
    }

    @SuppressWarnings("unchecked")
    private HikariDataSource createDataSource(String datasourceId) {
        log.info("[Provider] DataSource 생성 시작: {}", datasourceId);

        // 1. Proxy에서 접속정보 조회
        String url = proxyUrl + "/api/datasources/" + datasourceId + "/connection-info";
        HttpHeaders headers = new HttpHeaders();
        if (proxyApiKey != null && !proxyApiKey.isEmpty()) {
            headers.set("X-API-Key", proxyApiKey);
        }
        ResponseEntity<Map> responseEntity = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        Map<String, Object> response = responseEntity.getBody();

        if (response == null || response.isEmpty()) {
            throw new IllegalStateException("[Provider] Proxy에서 빈 응답: " + datasourceId);
        }

        // 2. 복호화
        String dbType = (String) response.get("dbType");
        String host = (String) response.get("host");
        int port = response.get("port") instanceof Integer
                ? (Integer) response.get("port")
                : Integer.parseInt(response.get("port").toString());
        String databaseName = (String) response.get("databaseName");
        String username = decrypt((String) response.get("username"));
        String password = decrypt((String) response.get("password"));

        // 3. JDBC URL 생성
        String jdbcUrl = buildJdbcUrl(dbType, host, port, databaseName);
        String driverClass = resolveDriverClass(dbType);

        // 4. HikariDataSource 생성
        HikariConfig config = new HikariConfig();
        config.setPoolName("ProviderPool-" + datasourceId);
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName(driverClass);
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(10_000);
        config.setMaxLifetime(600_000);
        // connectionTestQuery 미설정 — driver 의 isValid() 사용 (Oracle 의 SELECT 1 FROM DUAL 호환 이슈 회피)
        config.setLeakDetectionThreshold(60_000);

        HikariDataSource ds = new HikariDataSource(config);
        log.info("[Provider] DataSource 생성 완료: {} → {} (maxPool=5)", datasourceId, jdbcUrl);
        return ds;
    }

    private String decrypt(String encrypted) {
        if (encrypted == null || encrypted.isEmpty()) return encrypted;
        String toDecrypt = encrypted;
        if (encrypted.startsWith("ENC(") && encrypted.endsWith(")")) {
            toDecrypt = encrypted.substring(4, encrypted.length() - 1);
        }
        return encryptor.decrypt(toDecrypt);
    }

    private String buildJdbcUrl(String dbType, String host, int port, String databaseName) {
        switch (dbType.toUpperCase()) {
            case "POSTGRESQL":
                return String.format("jdbc:postgresql://%s:%d/%s", host, port, databaseName);
            case "ORACLE":
            case "TIBERO":
                return String.format("jdbc:oracle:thin:@//%s:%d/%s", host, port, databaseName);
            case "MYSQL":
                return String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8", host, port, databaseName);
            default:
                throw new IllegalArgumentException("지원하지 않는 DB 타입: " + dbType);
        }
    }

    private String resolveDriverClass(String dbType) {
        switch (dbType.toUpperCase()) {
            case "POSTGRESQL": return "org.postgresql.Driver";
            case "ORACLE":
            case "TIBERO": return "oracle.jdbc.OracleDriver";
            case "MYSQL": return "com.mysql.cj.jdbc.Driver";
            default: throw new IllegalArgumentException("지원하지 않는 DB 타입: " + dbType);
        }
    }

    /**
     * Proxy의 DB 메타 조회 API 호출 (search-tables, search-columns)
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> proxyPost(String path, Map<String, Object> body) {
        String url = proxyUrl + path;
        ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.POST, new HttpEntity<>(body, jsonHeaders()), Map.class);
        return response.getBody();
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        if (proxyApiKey != null && !proxyApiKey.isEmpty()) {
            headers.set("X-API-Key", proxyApiKey);
        }
        return headers;
    }

    @PreDestroy
    public void closeAll() {
        dataSources.forEach((id, ds) -> {
            if (!ds.isClosed()) {
                ds.close();
                log.info("[Provider] DataSource 종료: {}", id);
            }
        });
        dataSources.clear();
        jdbcTemplates.clear();
    }
}
