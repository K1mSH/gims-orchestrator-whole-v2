package com.sync.agent.common.repository;

import com.sync.agent.common.entity.Execution;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExecutionRepository extends JpaRepository<Execution, Long> {

    List<Execution> findAllByOrderByStartedAtDesc();

    List<Execution> findTop10ByOrderByStartedAtDesc();

    /** executionId로 조회 (기존 호환) */
    Execution findByExecutionId(String executionId);

    /** 부모 executionId로 자식 실행 목록 조회 */
    List<Execution> findByParentExecutionId(String parentExecutionId);
}
