package com.sync.agent.common.service;

import com.sync.agent.common.controller.DataSourceProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * IF 테이블 공통 처리 서비스
 *
 * 모든 IF 테이블의 메타 컬럼 처리를 캡슐화:
 * - link_status: PENDING, SUCCESS, FAILED
 * - execution_id: 데이터를 쓴 agent의 execution (relay가 설정)
 * - updated_at: 마지막 수정 시각
 *
 * 개별 에이전트에서 JDBC를 직접 사용하지 않고 이 서비스를 통해 IF 테이블 처리
 */
@Slf4j
@Service
public class IfTableService {

    private final DataSourceProvider dataSourceProvider;

    public IfTableService(DataSourceProvider dataSourceProvider) {
        this.dataSourceProvider = dataSourceProvider;
    }

    // ==================== Relay용 (IF에 데이터 넣을 때) ====================

    /**
     * IF 테이블 상태 업데이트 - relay가 IF에 데이터를 쓴 후 호출
     * execution_id는 INSERT 시에 설정됨
     */
    public void markAsReceived(String tableName, String pkColumn, Object pkValue,
                                String status, String executionId) {
        JdbcTemplate jdbc = getTargetJdbcTemplate();
        updateStatus(jdbc, tableName, pkColumn, pkValue, status);
    }

    /**
     * IF 테이블 배치 상태 업데이트 - relay용
     */
    public int batchMarkAsReceived(String tableName, String pkColumn, List<?> pkValues,
                                    String status, String executionId) {
        if (pkValues == null || pkValues.isEmpty()) return 0;
        JdbcTemplate jdbc = getTargetJdbcTemplate();
        return batchUpdateStatus(jdbc, tableName, pkColumn, pkValues, status);
    }

    // ==================== Loader용 (IF에서 데이터 뺄 때) ====================

    /**
     * IF 테이블 상태 업데이트 - loader가 IF에서 데이터를 읽은 후 호출
     * link_status와 updated_at만 업데이트 (execution_id는 원본 Relay 것 유지)
     */
    public void markAsProcessed(String tableName, String pkColumn, Object pkValue,
                                 String status, String executionId) {
        JdbcTemplate jdbc = getTargetJdbcTemplate();
        // execution_id는 건드리지 않음 - 원본 Relay의 executionId 유지
        updateStatus(jdbc, tableName, pkColumn, pkValue, status);
    }

    /**
     * IF 테이블 배치 상태 업데이트 - loader용
     */
    public int batchMarkAsProcessed(String tableName, String pkColumn, List<?> pkValues,
                                     String status, String executionId) {
        if (pkValues == null || pkValues.isEmpty()) return 0;
        JdbcTemplate jdbc = getTargetJdbcTemplate();
        // execution_id는 건드리지 않음 - 원본 Relay의 executionId 유지
        return batchUpdateStatus(jdbc, tableName, pkColumn, pkValues, status);
    }

    // ==================== 공통 내부 메서드 ====================

    private JdbcTemplate getTargetJdbcTemplate() {
        String targetDsId = dataSourceProvider.getTargetDatasourceId();
        return dataSourceProvider.getJdbcTemplate(targetDsId);
    }

    /**
     * IF 테이블 상태 업데이트 (단건)
     * link_status와 updated_at만 업데이트
     */
    private void updateStatus(JdbcTemplate jdbc, String tableName,
                               String pkColumn, Object pkValue,
                               String status) {
        String sql = String.format(
                "UPDATE %s SET link_status = ?, updated_at = ? WHERE %s = ?",
                tableName.toLowerCase(),
                pkColumn.toLowerCase());

        int updated = jdbc.update(sql, status, Timestamp.valueOf(LocalDateTime.now()), pkValue);
        log.debug("Updated IF table {}: pk={}, status={}, updated={}", tableName, pkValue, status, updated);
    }

    /**
     * IF 테이블 배치 상태 업데이트
     * link_status와 updated_at만 업데이트
     */
    private int batchUpdateStatus(JdbcTemplate jdbc, String tableName,
                                   String pkColumn, List<?> pkValues,
                                   String status) {
        // IN 절용 placeholder 생성
        String placeholders = String.join(", ", pkValues.stream().map(v -> "?").toList());

        List<Object> params = new ArrayList<>();
        params.add(status);
        params.add(Timestamp.valueOf(LocalDateTime.now()));
        params.addAll(pkValues);

        String sql = String.format(
                "UPDATE %s SET link_status = ?, updated_at = ? WHERE %s IN (%s)",
                tableName.toLowerCase(),
                pkColumn.toLowerCase(),
                placeholders);

        int updated = jdbc.update(sql, params.toArray());
        log.debug("Batch updated IF table {}: count={}, status={}", tableName, updated, status);
        return updated;
    }

    /**
     * IF 테이블 상태 업데이트 (단건, execution_id 포함)
     * link_status, updated_at, execution_id 업데이트
     */
    private void updateStatusWithExecutionId(JdbcTemplate jdbc, String tableName,
                                              String pkColumn, Object pkValue,
                                              String status, String executionId) {
        String sql = String.format(
                "UPDATE %s SET link_status = ?, updated_at = ?, execution_id = ? WHERE %s = ?",
                tableName.toLowerCase(),
                pkColumn.toLowerCase());

        int updated = jdbc.update(sql, status, Timestamp.valueOf(LocalDateTime.now()), executionId, pkValue);
        log.debug("Updated IF table {}: pk={}, status={}, executionId={}, updated={}",
                tableName, pkValue, status, executionId, updated);
    }

    /**
     * IF 테이블 배치 상태 업데이트 (execution_id 포함)
     * link_status, updated_at, execution_id 업데이트
     */
    private int batchUpdateStatusWithExecutionId(JdbcTemplate jdbc, String tableName,
                                                  String pkColumn, List<?> pkValues,
                                                  String status, String executionId) {
        // IN 절용 placeholder 생성
        String placeholders = String.join(", ", pkValues.stream().map(v -> "?").toList());

        List<Object> params = new ArrayList<>();
        params.add(status);
        params.add(Timestamp.valueOf(LocalDateTime.now()));
        params.add(executionId);
        params.addAll(pkValues);

        String sql = String.format(
                "UPDATE %s SET link_status = ?, updated_at = ?, execution_id = ? WHERE %s IN (%s)",
                tableName.toLowerCase(),
                pkColumn.toLowerCase(),
                placeholders);

        int updated = jdbc.update(sql, params.toArray());
        log.debug("Batch updated IF table {}: count={}, status={}, executionId={}", tableName, updated, status, executionId);
        return updated;
    }
}
