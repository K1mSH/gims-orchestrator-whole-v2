package com.sync.agent.common.repository;

import com.sync.agent.common.entity.SyncRecordHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SyncRecordHistoryRepository extends JpaRepository<SyncRecordHistory, Long> {

    List<SyncRecordHistory> findByExecutionId(String executionId);

    List<SyncRecordHistory> findByTableNameAndRecordKey(String tableName, String recordKey);

    List<SyncRecordHistory> findByTableNameAndRecordKeyOrderByProcessedAtDesc(String tableName, String recordKey);
}
