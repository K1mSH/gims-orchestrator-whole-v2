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
        if ("POSTGRESQL".equalsIgnoreCase(dbType)) {
            return String.format("jdbc:postgresql://%s:%d/%s", host, port, databaseName);
        } else if ("ORACLE".equalsIgnoreCase(dbType)) {
            return String.format("jdbc:oracle:thin:@%s:%d:%s", host, port, databaseName);
        }
        throw new IllegalArgumentException("Unsupported DB type: " + dbType);
    }

    public String getDriverClassName() {
        if ("POSTGRESQL".equalsIgnoreCase(dbType)) {
            return "org.postgresql.Driver";
        } else if ("ORACLE".equalsIgnoreCase(dbType)) {
            return "oracle.jdbc.OracleDriver";
        }
        throw new IllegalArgumentException("Unsupported DB type: " + dbType);
    }
}
