package com.sync.orchestrator.repository;

import com.sync.orchestrator.entity.ExecutionHistory;
import com.sync.orchestrator.entity.ExecutionStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ExecutionHistoryRepository extends JpaRepository<ExecutionHistory, String>, JpaSpecificationExecutor<ExecutionHistory> {

    /**
     * 최근 실행 이력 조회 (대시보드용)
     */
    List<ExecutionHistory> findTop50ByOrderByStartedAtDesc();

    /**
     * Agent별 실행 이력 조회
     */
    List<ExecutionHistory> findByAgentCodeOrderByStartedAtDesc(String agentCode);

    /**
     * Agent별 최근 N개 실행 이력
     */
    List<ExecutionHistory> findTop10ByAgentCodeOrderByStartedAtDesc(String agentCode);

    /**
     * 상태별 실행 이력 조회
     */
    List<ExecutionHistory> findByStatusOrderByStartedAtDesc(ExecutionStatus status);

    /**
     * 기간별 실행 이력 조회
     */
    List<ExecutionHistory> findByStartedAtBetweenOrderByStartedAtDesc(
            LocalDateTime start, LocalDateTime end);

    /**
     * 실행 중인 이력 조회
     */
    List<ExecutionHistory> findByStatusOrderByStartedAtAsc(ExecutionStatus status);

    /**
     * 페이징 조회
     */
    Page<ExecutionHistory> findAllByOrderByStartedAtDesc(Pageable pageable);

    /**
     * Agent별 페이징 조회
     */
    Page<ExecutionHistory> findByAgentCodeOrderByStartedAtDesc(String agentCode, Pageable pageable);

    /**
     * 오늘 실행 횟수
     */
    @Query("SELECT COUNT(e) FROM ExecutionHistory e WHERE e.startedAt >= :today")
    long countTodayExecutions(@Param("today") LocalDateTime today);

    /**
     * 오늘 실패 횟수
     */
    @Query("SELECT COUNT(e) FROM ExecutionHistory e WHERE e.startedAt >= :today AND e.status = 'FAILED'")
    long countTodayFailedExecutions(@Param("today") LocalDateTime today);

    /**
     * 현재 실행 중인 개수
     */
    long countByStatus(ExecutionStatus status);
}
