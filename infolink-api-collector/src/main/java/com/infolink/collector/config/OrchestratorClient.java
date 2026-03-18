package com.infolink.collector.config;

import com.sync.agent.common.datasource.PasswordEncryptor;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Orchestrator API 클라이언트
 * connection-info 조회 전용 (테이블/컬럼은 프론트에서 직접 호출)
 */
@Slf4j
@Component
public class OrchestratorClient {

    private final RestTemplate restTemplate;
    private final PasswordEncryptor passwordEncryptor;

    @Value("${orchestrator.url:http://localhost:8080}")
    private String orchestratorUrl;

    public OrchestratorClient(RestTemplate restTemplate,
                              @Value("${jasypt.encryptor.password}") String secretKey) {
        this.restTemplate = restTemplate;
        this.passwordEncryptor = new PasswordEncryptor(secretKey);
    }

    /**
     * Orchestrator에서 datasource 연결 정보 조회 (암호문 수신 → 복호화)
     */
    @SuppressWarnings("unchecked")
    public ConnectionInfo getConnectionInfo(String datasourceId) {
        String url = orchestratorUrl + "/api/datasources/" + datasourceId + "/connection-info";
        log.info("[Collector] Fetching connection-info from Orchestrator: {}", datasourceId);

        Map<String, Object> response = restTemplate.getForObject(url, Map.class);
        if (response == null || response.isEmpty()) {
            throw new RuntimeException("Empty response from Orchestrator for datasource: " + datasourceId);
        }

        return ConnectionInfo.builder()
                .datasourceId((String) response.get("datasourceId"))
                .dbType((String) response.get("dbType"))
                .host((String) response.get("host"))
                .port(response.get("port") instanceof Integer ? (Integer) response.get("port")
                        : Integer.parseInt(response.get("port").toString()))
                .databaseName((String) response.get("databaseName"))
                .username(passwordEncryptor.decrypt((String) response.get("username")))
                .password(passwordEncryptor.decrypt((String) response.get("password")))
                .build();
    }

    @Getter
    @Builder
    public static class ConnectionInfo {
        private String datasourceId;
        private String dbType;
        private String host;
        private int port;
        private String databaseName;
        private String username;
        private String password;

        public String getJdbcUrl() {
            if ("MYSQL".equalsIgnoreCase(dbType)) {
                return String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul",
                        host, port, databaseName);
            }
            return String.format("jdbc:postgresql://%s:%d/%s", host, port, databaseName);
        }

        public String getDriverClassName() {
            if ("MYSQL".equalsIgnoreCase(dbType)) {
                return "com.mysql.cj.jdbc.Driver";
            }
            return "org.postgresql.Driver";
        }
    }
}
