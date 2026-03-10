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

    /** 실행+매핑명별 로그 조회 */
    Optional<SyncLog> findByExecutionIdAndMappingName(String executionId, String mappingName);

    /** 실행+스텝+매핑명별 로그 조회 */
    Optional<SyncLog> findByExecutionIdAndStepIdAndMappingName(String executionId, String stepId, String mappingName);

    /** 실패 건이 있는 로그만 조회 */
    @Query("SELECT s FROM SyncLog s WHERE s.executionId = :executionId AND s.failedCount > 0")
    List<SyncLog> findFailedByExecutionId(@Param("executionId") String executionId);

    /** 실행별 매핑명 목록 */
    @Query("SELECT DISTINCT s.mappingName FROM SyncLog s WHERE s.executionId = :executionId ORDER BY s.mappingName")
    List<String> findDistinctMappingNamesByExecutionId(@Param("executionId") String executionId);

    /** 실행별 총 read/write/failed/skip 건수 합계 */
    @Query("SELECT COALESCE(SUM(s.readCount), 0), COALESCE(SUM(s.writeCount), 0), " +
           "COALESCE(SUM(s.failedCount), 0), COALESCE(SUM(s.skipCount), 0) " +
           "FROM SyncLog s WHERE s.executionId = :executionId")
    Object[] sumCountsByExecutionId(@Param("executionId") String executionId);

    /** source_tables에 특정 테이블명이 포함된 매핑 조회 */
    @Query("SELECT s FROM SyncLog s WHERE s.executionId = :executionId AND LOWER(s.sourceTables) LIKE LOWER(CONCAT('%', :tableName, '%'))")
    List<SyncLog> findByExecutionIdAndSourceTableContaining(@Param("executionId") String executionId, @Param("tableName") String tableName);

    /** target_tables에 특정 테이블명이 포함된 매핑 조회 */
    @Query("SELECT s FROM SyncLog s WHERE s.executionId = :executionId AND LOWER(s.targetTables) LIKE LOWER(CONCAT('%', :tableName, '%'))")
    List<SyncLog> findByExecutionIdAndTargetTableContaining(@Param("executionId") String executionId, @Param("tableName") String tableName);
}
