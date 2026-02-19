package com.sync.agent.common.datasource;

import lombok.*;

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
            case "ORACLE" -> String.format("jdbc:oracle:thin:@%s:%d:%s", host, port, databaseName);
            case "MYSQL" -> String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8", host, port, databaseName);
            case "MARIADB" -> String.format("jdbc:mariadb://%s:%d/%s", host, port, databaseName);
            case "MSSQL" -> String.format("jdbc:sqlserver://%s:%d;databaseName=%s", host, port, databaseName);
            default -> throw new IllegalArgumentException("Unsupported DB type: " + dbType);
        };
    }

    public String getDriverClassName() {
        return switch (dbType.toUpperCase()) {
            case "POSTGRESQL" -> "org.postgresql.Driver";
            case "ORACLE" -> "oracle.jdbc.OracleDriver";
            case "MYSQL" -> "com.mysql.cj.jdbc.Driver";
            case "MARIADB" -> "org.mariadb.jdbc.Driver";
            case "MSSQL" -> "com.microsoft.sqlserver.jdbc.SQLServerDriver";
            default -> throw new IllegalArgumentException("Unsupported DB type: " + dbType);
        };
    }
}
