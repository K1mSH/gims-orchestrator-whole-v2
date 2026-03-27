package com.sync.agent.common.controller;

import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Datasource 관련 API (공통)
 *
 * Orchestrator에서 zone별 master Agent에게 DB 작업을 위임할 때 사용
 * - 연결 테스트
 * - 테이블 검색
 * - 컬럼 검색
 * 각 망별 Agent에서 공통으로 사용
 */
@Slf4j
@RestController
@RequestMapping("/api/datasource")
@ConditionalOnProperty(name = "common.controller.datasource.enabled", havingValue = "true")
public class DatasourceController {

    private final DataSourceProvider dataSourceProvider;

    public DatasourceController(@org.springframework.lang.Nullable DataSourceProvider dataSourceProvider) {
        this.dataSourceProvider = dataSourceProvider;
    }

    /**
     * DB 연결 테스트
     * Orchestrator가 자신이 접근할 수 없는 zone의 DB 테스트를 위임
     */
    @PostMapping("/test-connection")
    public ResponseEntity<ConnectionTestResponse> testConnection(@RequestBody ConnectionTestRequest request) {
        long startTime = System.currentTimeMillis();
        String jdbcUrl = buildJdbcUrl(request.getDbType(), request.getHost(), request.getPort(), request.getDatabaseName());

        try {
            // 드라이버 로드
            String driverClassName = getDriverClassName(request.getDbType());
            Class.forName(driverClassName);

            // 연결 시도
            try (Connection conn = DriverManager.getConnection(jdbcUrl, request.getUsername(), request.getPassword())) {
                long responseTime = System.currentTimeMillis() - startTime;
                log.info("Connection test successful: {} ({}ms)", jdbcUrl, responseTime);

                return ResponseEntity.ok(ConnectionTestResponse.builder()
                        .success(true)
                        .message("Connection successful")
                        .responseTimeMs(responseTime)
                        .build());
            }
        } catch (ClassNotFoundException e) {
            log.error("Driver not found for dbType: {}", request.getDbType());
            return ResponseEntity.ok(ConnectionTestResponse.builder()
                    .success(false)
                    .message("Driver not found: " + request.getDbType())
                    .responseTimeMs(System.currentTimeMillis() - startTime)
                    .build());
        } catch (Exception e) {
            log.error("Connection test failed: {} - {}", jdbcUrl, e.getMessage());
            return ResponseEntity.ok(ConnectionTestResponse.builder()
                    .success(false)
                    .message("Connection failed: " + e.getMessage())
                    .responseTimeMs(System.currentTimeMillis() - startTime)
                    .build());
        }
    }

    private String buildJdbcUrl(String dbType, String host, int port, String databaseName) {
        return switch (dbType.toUpperCase()) {
            case "POSTGRESQL" -> String.format("jdbc:postgresql://%s:%d/%s", host, port, databaseName);
            case "ORACLE", "TIBERO" -> String.format("jdbc:oracle:thin:@//%s:%d/%s", host, port, databaseName);
            case "MYSQL" -> String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true", host, port, databaseName);
            case "MARIADB" -> String.format("jdbc:mariadb://%s:%d/%s", host, port, databaseName);
            case "MSSQL" -> String.format("jdbc:sqlserver://%s:%d;databaseName=%s", host, port, databaseName);
            default -> throw new IllegalArgumentException("Unsupported DB type: " + dbType);
        };
    }

    private String getDriverClassName(String dbType) {
        return switch (dbType.toUpperCase()) {
            case "POSTGRESQL" -> "org.postgresql.Driver";
            case "ORACLE", "TIBERO" -> "oracle.jdbc.OracleDriver";
            case "MYSQL" -> "com.mysql.cj.jdbc.Driver";
            case "MARIADB" -> "org.mariadb.jdbc.Driver";
            case "MSSQL" -> "com.microsoft.sqlserver.jdbc.SQLServerDriver";
            default -> throw new IllegalArgumentException("Unsupported DB type: " + dbType);
        };
    }

    /**
     * 테이블 검색
     * DB의 테이블 목록을 검색하여 반환
     */
    @PostMapping("/search-tables")
    public ResponseEntity<List<TableSearchResult>> searchTables(@RequestBody TableSearchRequest request) {
        log.info("=== Agent: searchTables called ===");
        log.info("Request: dbType={}, host={}, port={}, database={}, query={}",
                request.getDbType(), request.getHost(), request.getPort(), request.getDatabaseName(), request.getQuery());

        String jdbcUrl = buildJdbcUrl(request.getDbType(), request.getHost(), request.getPort(), request.getDatabaseName());
        log.info("Built JDBC URL: {}", jdbcUrl);
        List<TableSearchResult> results = new ArrayList<>();

        try {
            Class.forName(getDriverClassName(request.getDbType()));
            log.info("Driver loaded successfully");

            try (Connection conn = DriverManager.getConnection(jdbcUrl, request.getUsername(), request.getPassword())) {
                log.info("Connection established");
                DatabaseMetaData metaData = conn.getMetaData();

                // MySQL은 catalog에 DB명을 지정해야 해당 DB 테이블만 반환
                String catalog = "MYSQL".equalsIgnoreCase(request.getDbType()) ? request.getDatabaseName() : null;
                String[] types = {"TABLE", "VIEW"};
                String searchPattern = request.getQuery() != null && !request.getQuery().isEmpty()
                        ? "%" + request.getQuery().toUpperCase() + "%"
                        : "%";
                log.info("Search pattern: {}, catalog: {}", searchPattern, catalog);

                try (ResultSet rs = metaData.getTables(catalog, null, searchPattern, types)) {
                    int count = 0;
                    while (rs.next() && count < 100) {
                        results.add(TableSearchResult.builder()
                                .tableName(rs.getString("TABLE_NAME"))
                                .tableType(rs.getString("TABLE_TYPE"))
                                .remarks(rs.getString("REMARKS"))
                                .build());
                        count++;
                    }
                }
            }
            log.info("Found {} tables matching '{}' via agent", results.size(), request.getQuery());
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("Failed to search tables: {}", e.getMessage(), e);
            return ResponseEntity.ok(results); // 빈 리스트 반환
        }
    }

    /**
     * 컬럼 검색
     * 특정 테이블의 컬럼 목록을 검색하여 반환
     */
    @PostMapping("/search-columns")
    public ResponseEntity<List<ColumnSearchResult>> searchColumns(@RequestBody ColumnSearchRequest request) {
        log.info("=== Agent: searchColumns called ===");
        log.info("Request: dbType={}, host={}, database={}, tableName={}, query={}",
                request.getDbType(), request.getHost(), request.getDatabaseName(), request.getTableName(), request.getQuery());

        String jdbcUrl = buildJdbcUrl(request.getDbType(), request.getHost(), request.getPort(), request.getDatabaseName());
        List<ColumnSearchResult> results = new ArrayList<>();

        try {
            Class.forName(getDriverClassName(request.getDbType()));
            try (Connection conn = DriverManager.getConnection(jdbcUrl, request.getUsername(), request.getPassword())) {
                log.info("Connection established for column search");
                DatabaseMetaData metaData = conn.getMetaData();

                // MySQL은 catalog에 DB명을 지정해야 해당 DB 테이블만 반환
                String catalog = "MYSQL".equalsIgnoreCase(request.getDbType()) ? request.getDatabaseName() : null;

                // PK 컬럼 조회
                Set<String> pkColumns = new HashSet<>();
                try (ResultSet pkRs = metaData.getPrimaryKeys(catalog, null, request.getTableName())) {
                    while (pkRs.next()) {
                        pkColumns.add(pkRs.getString("COLUMN_NAME"));
                    }
                }
                log.info("Found {} PK columns", pkColumns.size());

                // 컬럼 조회
                String columnPattern = request.getQuery() != null && !request.getQuery().isEmpty()
                        ? "%" + request.getQuery().toUpperCase() + "%"
                        : "%";

                try (ResultSet rs = metaData.getColumns(catalog, null, request.getTableName(), columnPattern)) {
                    while (rs.next()) {
                        String columnName = rs.getString("COLUMN_NAME");
                        results.add(ColumnSearchResult.builder()
                                .columnName(columnName)
                                .dataType(rs.getString("TYPE_NAME"))
                                .columnSize(rs.getInt("COLUMN_SIZE"))
                                .isNullable("YES".equals(rs.getString("IS_NULLABLE")))
                                .isPrimaryKey(pkColumns.contains(columnName))
                                .remarks(rs.getString("REMARKS"))
                                .build());
                    }
                }
            }
            log.info("Found {} columns for table {} via agent", results.size(), request.getTableName());
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("Failed to search columns: {}", e.getMessage(), e);
            return ResponseEntity.ok(results); // 빈 리스트 반환
        }
    }


    // ==================== Request/Response DTOs ====================

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ConnectionTestRequest {
        private String dbType;
        private String host;
        private Integer port;
        private String databaseName;
        private String username;
        private String password;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ConnectionTestResponse {
        private boolean success;
        private String message;
        private Long responseTimeMs;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TableSearchRequest {
        private String dbType;
        private String host;
        private Integer port;
        private String databaseName;
        private String username;
        private String password;
        private String query;  // 검색어
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TableSearchResult {
        private String tableName;
        private String tableType;
        private String remarks;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ColumnSearchRequest {
        private String dbType;
        private String host;
        private Integer port;
        private String databaseName;
        private String username;
        private String password;
        private String tableName;  // 테이블명
        private String query;      // 검색어 (선택)
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ColumnSearchResult {
        private String columnName;
        private String dataType;
        private Integer columnSize;
        private Boolean isNullable;
        private Boolean isPrimaryKey;
        private String remarks;
    }

}
