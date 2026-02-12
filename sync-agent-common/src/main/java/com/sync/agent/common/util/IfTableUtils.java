package com.sync.agent.common.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * IF 테이블 공통 유틸리티
 *
 * 모든 IF 테이블은 동일한 메타 컬럼 구조를 가짐:
 * - link_status: PENDING, SUCCESS, FAILED
 * - execution_id: 데이터를 처리한 agent의 execution ID
 * - updated_at: 마지막 수정 시각
 */
@Slf4j
public class IfTableUtils {

    /**
     * IF 테이블 상태 업데이트
     *
     * @param jdbc        JdbcTemplate
     * @param tableName   IF 테이블명 (예: if_rsv_sec_jewon)
     * @param pkColumn    PK 컬럼명 (예: id)
     * @param pkValue     PK 값
     * @param status      상태 (PENDING, SUCCESS, FAILED)
     * @param executionId agent의 execution_id (null이면 업데이트 안함)
     */
    public static void updateStatus(JdbcTemplate jdbc, String tableName,
                                     String pkColumn, Object pkValue,
                                     String status, String executionId) {
        List<String> setClauses = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        // link_status
        setClauses.add("link_status = ?");
        params.add(status);

        // updated_at
        setClauses.add("updated_at = ?");
        params.add(Timestamp.valueOf(LocalDateTime.now()));

        // execution_id
        if (executionId != null) {
            setClauses.add("execution_id = ?");
            params.add(executionId);
        }

        // WHERE 조건
        params.add(pkValue);

        String sql = String.format("UPDATE %s SET %s WHERE %s = ?",
                tableName.toLowerCase(),
                String.join(", ", setClauses),
                pkColumn.toLowerCase());

        int updated = jdbc.update(sql, params.toArray());
        log.debug("Updated IF table {}: pk={}, status={}, updated={}", tableName, pkValue, status, updated);
    }

    /**
     * IF 테이블 배치 상태 업데이트
     * 여러 레코드를 한번에 업데이트 (IN 절 사용)
     *
     * @param jdbc        JdbcTemplate
     * @param tableName   IF 테이블명
     * @param pkColumn    PK 컬럼명
     * @param pkValues    PK 값 목록
     * @param status      상태
     * @param executionId agent의 execution_id
     * @return 업데이트된 행 수
     */
    public static int batchUpdateStatus(JdbcTemplate jdbc, String tableName,
                                         String pkColumn, List<?> pkValues,
                                         String status, String executionId) {
        if (pkValues == null || pkValues.isEmpty()) {
            return 0;
        }

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
        log.debug("Batch updated IF table {}: count={}, status={}", tableName, updated, status);
        return updated;
    }
}
