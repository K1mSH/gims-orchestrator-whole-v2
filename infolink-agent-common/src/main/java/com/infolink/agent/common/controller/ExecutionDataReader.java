package com.infolink.agent.common.controller;

import com.infolink.agent.common.entity.Execution;
import com.infolink.agent.common.entity.SyncLog;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;

/**
 * Execution/SyncLog 관리 테이블을 JdbcTemplate로 직접 조회하는 공통 유틸.
 *
 * Proxy의 헤더 기반 DataSource 라우팅이 JPA에서 동작하지 않기 때문에
 * (JPA는 초기화 시점 DataSource 고정) 조회는 JdbcTemplate으로 처리한다.
 * Agent 내부 쓰기(recordExecutionStart/Finish)는 JPA save() 그대로 유지.
 */
public final class ExecutionDataReader {

    private ExecutionDataReader() {}

    // ==================== RowMapper ====================

    public static final RowMapper<Execution> EXECUTION_ROW_MAPPER = (rs, rowNum) -> Execution.builder()
            .id(getLong(rs, "id"))
            .executionId(rs.getString("execution_id"))
            .parentExecutionId(rs.getString("parent_execution_id"))
            .agentId(rs.getString("agent_id"))
            .status(rs.getString("status"))
            .totalReadCount(getInteger(rs, "total_read_count"))
            .totalWriteCount(getInteger(rs, "total_write_count"))
            .totalSkipCount(getInteger(rs, "total_skip_count"))
            .durationMs(getLong(rs, "duration_ms"))
            .errorMessage(rs.getString("error_message"))
            .startedAt(toLocalDateTime(rs.getTimestamp("started_at")))
            .finishedAt(toLocalDateTime(rs.getTimestamp("finished_at")))
            .sourceDatasourceId(rs.getString("source_datasource_id"))
            .targetDatasourceId(rs.getString("target_datasource_id"))
            .build();

    public static final RowMapper<SyncLog> SYNC_LOG_ROW_MAPPER = (rs, rowNum) -> SyncLog.builder()
            .id(getLong(rs, "id"))
            .executionId(rs.getString("execution_id"))
            .stepId(rs.getString("step_id"))
            .mappingName(rs.getString("mapping_name"))
            .sourceTables(rs.getString("source_tables"))
            .targetTables(rs.getString("target_tables"))
            .readCount(getLongDefaultZero(rs, "read_count"))
            .writeCount(getLongDefaultZero(rs, "write_count"))
            .failedCount(getLongDefaultZero(rs, "failed_count"))
            .skipCount(getLongDefaultZero(rs, "skip_count"))
            .failedKeys(rs.getString("failed_keys"))
            .errorSummary(rs.getString("error_summary"))
            .sourcePkColumn(rs.getString("source_pk_column"))
            .createdAt(toLocalDateTime(rs.getTimestamp("created_at")))
            .build();

    // ==================== Execution 조회 ====================

    private static final String EXECUTION_SELECT_COLS =
            "id, execution_id, parent_execution_id, agent_id, status, " +
            "total_read_count, total_write_count, total_skip_count, " +
            "duration_ms, error_message, started_at, finished_at, " +
            "source_datasource_id, target_datasource_id";

    public static Optional<Execution> findExecutionById(JdbcTemplate jdbc, String executionId) {
        String sql = "SELECT " + EXECUTION_SELECT_COLS + " FROM execution WHERE execution_id = ?";
        try {
            List<Execution> results = jdbc.query(sql, EXECUTION_ROW_MAPPER, executionId);
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public static List<Execution> findRecentExecutions(JdbcTemplate jdbc, int limit) {
        // DB별 LIMIT 구문 차이 대응 (Oracle은 FETCH FIRST, PG/MySQL은 LIMIT)
        String dbProduct = detectDbProduct(jdbc);
        String sql;
        if (isOracleFamily(dbProduct)) {
            sql = "SELECT " + EXECUTION_SELECT_COLS +
                  " FROM execution ORDER BY started_at DESC FETCH FIRST " + limit + " ROWS ONLY";
        } else {
            sql = "SELECT " + EXECUTION_SELECT_COLS +
                  " FROM execution ORDER BY started_at DESC LIMIT " + limit;
        }
        return jdbc.query(sql, EXECUTION_ROW_MAPPER);
    }

    public static List<Execution> findAllExecutions(JdbcTemplate jdbc) {
        String sql = "SELECT " + EXECUTION_SELECT_COLS + " FROM execution ORDER BY started_at DESC";
        return jdbc.query(sql, EXECUTION_ROW_MAPPER);
    }

    // ==================== SyncLog 조회 ====================

    private static final String SYNC_LOG_SELECT_COLS =
            "id, execution_id, step_id, mapping_name, source_tables, target_tables, " +
            "read_count, write_count, failed_count, skip_count, " +
            "failed_keys, error_summary, source_pk_column, created_at";

    public static List<SyncLog> findSyncLogsByExecutionId(JdbcTemplate jdbc, String executionId) {
        String sql = "SELECT " + SYNC_LOG_SELECT_COLS + " FROM sync_log WHERE execution_id = ? ORDER BY id";
        return jdbc.query(sql, SYNC_LOG_ROW_MAPPER, executionId);
    }

    public static List<SyncLog> findFailedSyncLogsByExecutionId(JdbcTemplate jdbc, String executionId) {
        String sql = "SELECT " + SYNC_LOG_SELECT_COLS + " FROM sync_log " +
                     "WHERE execution_id = ? AND failed_count > 0 ORDER BY id";
        return jdbc.query(sql, SYNC_LOG_ROW_MAPPER, executionId);
    }

    public static Optional<SyncLog> findByExecutionIdAndMappingName(JdbcTemplate jdbc, String executionId, String mappingName) {
        String sql = "SELECT " + SYNC_LOG_SELECT_COLS + " FROM sync_log " +
                     "WHERE execution_id = ? AND mapping_name = ?";
        List<SyncLog> results = jdbc.query(sql, SYNC_LOG_ROW_MAPPER, executionId, mappingName);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /** [read, write, failed, skip] 합계 반환. 행이 없으면 모두 0. */
    public static long[] sumCountsByExecutionId(JdbcTemplate jdbc, String executionId) {
        String sql = "SELECT COALESCE(SUM(read_count),0), COALESCE(SUM(write_count),0), " +
                     "COALESCE(SUM(failed_count),0), COALESCE(SUM(skip_count),0) " +
                     "FROM sync_log WHERE execution_id = ?";
        return jdbc.query(sql, rs -> {
            if (rs.next()) {
                return new long[]{
                        rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getLong(4)
                };
            }
            return new long[]{0L, 0L, 0L, 0L};
        }, executionId);
    }

    // ==================== datasource_table 레지스트리 조회 ====================
    // source_refs 의 tableId 는 datasource_table.id — 추적 시 이름 추측 대신 이걸로 정확히 해석.

    /** datasource_table.id → table_name. 없거나 조회 실패면 null. */
    public static String findTableNameById(JdbcTemplate jdbc, long datasourceTableId) {
        try {
            List<String> r = jdbc.query("SELECT table_name FROM datasource_table WHERE id = ?",
                    (rs, n) -> rs.getString(1), datasourceTableId);
            return r.isEmpty() ? null : r.get(0);
        } catch (Exception e) {
            return null;
        }
    }

    /** (table_name, datasourceId) → datasource_table.id. datasourceId 없으면 이름만으로 first. 없거나 실패면 null. */
    public static Long findTableIdByName(JdbcTemplate jdbc, String tableName, String datasourceId) {
        if (tableName == null || tableName.isBlank()) return null;
        try {
            List<Long> r;
            if (datasourceId != null && !datasourceId.isBlank()) {
                r = jdbc.query("SELECT id FROM datasource_table WHERE lower(table_name) = lower(?) AND datasource_id = ? ORDER BY id",
                        (rs, n) -> rs.getLong(1), tableName, datasourceId);
            } else {
                r = jdbc.query("SELECT id FROM datasource_table WHERE lower(table_name) = lower(?) ORDER BY id",
                        (rs, n) -> rs.getLong(1), tableName);
            }
            return r.isEmpty() ? null : r.get(0);
        } catch (Exception e) {
            return null;
        }
    }

    // ==================== Helper ====================

    private static Long getLong(ResultSet rs, String col) throws SQLException {
        long v = rs.getLong(col);
        return rs.wasNull() ? null : v;
    }

    private static Long getLongDefaultZero(ResultSet rs, String col) throws SQLException {
        long v = rs.getLong(col);
        return rs.wasNull() ? 0L : v;
    }

    private static Integer getInteger(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }

    private static java.time.LocalDateTime toLocalDateTime(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }

    private static String detectDbProduct(JdbcTemplate jdbc) {
        try {
            return jdbc.execute((java.sql.Connection conn) -> conn.getMetaData().getDatabaseProductName());
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    private static boolean isOracleFamily(String product) {
        if (product == null) return false;
        String p = product.toUpperCase(Locale.ROOT);
        return p.contains("ORACLE") || p.contains("TIBERO");
    }
}
