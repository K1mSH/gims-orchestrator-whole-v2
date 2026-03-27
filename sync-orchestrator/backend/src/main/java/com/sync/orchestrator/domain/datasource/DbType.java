package com.sync.orchestrator.domain.datasource;

/**
 * 지원하는 DB 타입
 */
public enum DbType {
    POSTGRESQL("org.postgresql.Driver") {
        @Override
        public String buildJdbcUrl(String host, int port, String databaseName) {
            return String.format("jdbc:postgresql://%s:%d/%s", host, port, databaseName);
        }
    },
    ORACLE("oracle.jdbc.OracleDriver") {
        @Override
        public String buildJdbcUrl(String host, int port, String databaseName) {
            // 서비스명 형식: jdbc:oracle:thin:@//host:port/serviceName (PDB 지원)
            return String.format("jdbc:oracle:thin:@//%s:%d/%s", host, port, databaseName);
        }
    },
    MYSQL("com.mysql.cj.jdbc.Driver") {
        @Override
        public String buildJdbcUrl(String host, int port, String databaseName) {
            return String.format("jdbc:mysql://%s:%d/%s", host, port, databaseName);
        }
    },
    MARIADB("org.mariadb.jdbc.Driver") {
        @Override
        public String buildJdbcUrl(String host, int port, String databaseName) {
            return String.format("jdbc:mariadb://%s:%d/%s", host, port, databaseName);
        }
    },
    MSSQL("com.microsoft.sqlserver.jdbc.SQLServerDriver") {
        @Override
        public String buildJdbcUrl(String host, int port, String databaseName) {
            return String.format("jdbc:sqlserver://%s:%d;databaseName=%s", host, port, databaseName);
        }
    };

    private final String driverClassName;

    DbType(String driverClassName) {
        this.driverClassName = driverClassName;
    }

    public String getDriverClassName() {
        return driverClassName;
    }

    public abstract String buildJdbcUrl(String host, int port, String databaseName);
}
