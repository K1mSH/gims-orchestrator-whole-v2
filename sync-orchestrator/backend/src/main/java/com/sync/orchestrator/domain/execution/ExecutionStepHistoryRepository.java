package com.sync.orchestrator.domain.execution;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExecutionStepHistoryRepository extends JpaRepository<ExecutionStepHistory, Long> {

    List<ExecutionStepHistory> findByExecutionIdOrderByStepOrder(String executionId);
}
