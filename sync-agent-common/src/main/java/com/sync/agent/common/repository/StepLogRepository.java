package com.sync.agent.common.repository;

import com.sync.agent.common.entity.StepLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StepLogRepository extends JpaRepository<StepLog, Long> {

    List<StepLog> findByExecutionIdOrderByStepOrderAsc(String executionId);

    Optional<StepLog> findByExecutionIdAndStepId(String executionId, String stepId);
}
