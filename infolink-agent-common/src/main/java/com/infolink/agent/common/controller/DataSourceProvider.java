package com.infolink.agent.common.controller;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Interface for providing datasource access.
 * Agents must implement this to provide source/target datasource connections.
 */
public interface DataSourceProvider {

    /**
     * Get JdbcTemplate for a specific datasource.
     */
    JdbcTemplate getJdbcTemplate(String datasourceId);

    /**
     * Get the source datasource ID configured for this agent.
     */
    String getSourceDatasourceId();

    /**
     * Get the target datasource ID configured for this agent.
     */
    String getTargetDatasourceId();

    /**
     * Get the agent type (RELAY, LOADER_STANDARD, LOADER_CUSTOM).
     * IF 테이블은 통합된 execution_id 컬럼 사용.
     *
     * @return agent type string, or null if not configured
     */
    default String getAgentType() {
        return null;
    }

    /**
     * Get the DB type for a specific datasource.
     * Used for SQL dialect-specific query generation (PostgreSQL vs MySQL etc.)
     *
     * @param datasourceId datasource ID
     * @return DB type string (POSTGRESQL, MYSQL, ORACLE, etc.), or null if unknown
     */
    default String getDbType(String datasourceId) {
        return null;
    }
}
