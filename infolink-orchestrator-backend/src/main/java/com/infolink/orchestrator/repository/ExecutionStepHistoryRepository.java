package com.infolink.orchestrator.repository;

import com.infolink.orchestrator.entity.ExecutionStepHistory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExecutionStepHistoryRepository extends JpaRepository<ExecutionStepHistory, Long> {

    List<ExecutionStepHistory> findByExecutionIdOrderByStepOrder(String executionId);
}
