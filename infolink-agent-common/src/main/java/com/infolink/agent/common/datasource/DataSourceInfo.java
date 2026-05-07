package com.infolink.agent.common.datasource;

import lombok.*;

/**
 * 데이터소스 연결 정보 DTO
 *
 * Orchestrator → Proxy → Agent로 전달되는 DB 연결 정보를 담는다.
 * Proxy에서 수신 시 username/password는 암호문 상태이며,
 * Agent의 SyncDataSourceService.fetchConnectionInfoFromProxy()에서 PasswordEncryptor로 복호화 후 저장.
 * getJdbcUrl()/getDriverClassName()으로 dbType에 맞는 JDBC URL/드라이버를 자동 생성.
 *
 * 지원 DB: PostgreSQL, Oracle, MySQL, MariaDB, MSSQL
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DataSourceInfo {

    private String datasourceId;
    private String datasourceName;
    private String zone;
    private String dbType;      // POSTGRESQL, ORACLE, etc.
    private String host;
    private Integer port;
    private String databaseName;
    private String username;
    private String password;    // 암호화된 상태로 전달

    public String getJdbcUrl() {
        return switch (dbType.toUpperCase()) {
            case "POSTGRESQL" -> String.format("jdbc:postgresql://%s:%d/%s", host, port, databaseName);
            case "ORACLE", "TIBERO" -> String.format("jdbc:oracle:thin:@//%s:%d/%s", host, port, databaseName);
            case "MYSQL" -> String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8", host, port, databaseName);
            case "MARIADB" -> String.format("jdbc:mariadb://%s:%d/%s", host, port, databaseName);
            case "MSSQL" -> String.format("jdbc:sqlserver://%s:%d;databaseName=%s", host, port, databaseName);
            default -> throw new IllegalArgumentException("Unsupported DB type: " + dbType);
        };
    }

    public String getDriverClassName() {
        return switch (dbType.toUpperCase()) {
            case "POSTGRESQL" -> "org.postgresql.Driver";
            case "ORACLE", "TIBERO" -> "oracle.jdbc.OracleDriver";
            case "MYSQL" -> "com.mysql.cj.jdbc.Driver";
            case "MARIADB" -> "org.mariadb.jdbc.Driver";
            case "MSSQL" -> "com.microsoft.sqlserver.jdbc.SQLServerDriver";
            default -> throw new IllegalArgumentException("Unsupported DB type: " + dbType);
        };
    }
}
