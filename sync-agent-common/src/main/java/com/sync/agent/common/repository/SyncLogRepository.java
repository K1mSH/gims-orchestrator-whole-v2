package com.sync.agent.common.repository;

import com.sync.agent.common.entity.SyncLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SyncLogRepository extends JpaRepository<SyncLog, Long> {

    /** 실행별 전체 로그 조회 */
    List<SyncLog> findByExecutionId(String executionId);

    /** 실행+테이블별 로그 조회 */
    Optional<SyncLog> findByExecutionIdAndTableName(String executionId, String tableName);

    /** 실행+스텝+테이블별 로그 조회 */
    Optional<SyncLog> findByExecutionIdAndStepIdAndTableName(String executionId, String stepId, String tableName);

    /** 실행별 테이블 타입으로 조회 */
    List<SyncLog> findByExecutionIdAndTableType(String executionId, String tableType);

    /** 실패 건이 있는 로그만 조회 */
    @Query("SELECT s FROM SyncLog s WHERE s.executionId = :executionId AND s.failedCount > 0")
    List<SyncLog> findFailedByExecutionId(@Param("executionId") String executionId);

    /** 실행별 테이블명 목록 */
    @Query("SELECT DISTINCT s.tableName FROM SyncLog s WHERE s.executionId = :executionId ORDER BY s.tableName")
    List<String> findDistinctTableNamesByExecutionId(@Param("executionId") String executionId);

    /** 실행별 총 성공/실패/스킵 건수 합계 */
    @Query("SELECT COALESCE(SUM(s.successCount), 0), COALESCE(SUM(s.failedCount), 0), COALESCE(SUM(s.skipCount), 0) " +
           "FROM SyncLog s WHERE s.executionId = :executionId")
    Object[] sumCountsByExecutionId(@Param("executionId") String executionId);

    /** 실행별 총 성공/실패/스킵 건수 합계 (LINK 타입 제외) */
    @Query("SELECT COALESCE(SUM(s.successCount), 0), COALESCE(SUM(s.failedCount), 0), COALESCE(SUM(s.skipCount), 0) " +
           "FROM SyncLog s WHERE s.executionId = :executionId AND (s.tableType IS NULL OR s.tableType <> 'LINK')")
    Object[] sumCountsByExecutionIdExcludeLink(@Param("executionId") String executionId);
}
