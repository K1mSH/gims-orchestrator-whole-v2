package com.sync.orchestrator.domain.agent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AgentRepository extends JpaRepository<Agent, Long> {

    Optional<Agent> findByAgentCode(String agentCode);

    List<Agent> findByAgentCodeStartingWith(String prefix);

    List<Agent> findByZone(String zone);

    List<Agent> findByStatus(AgentStatus status);

    List<Agent> findByStatusNot(AgentStatus status);

    List<Agent> findByZoneAndStatusNot(String zone, AgentStatus status);

    /**
     * 활성화된 Agent만 조회
     */
    List<Agent> findByIsActiveTrue();

    /**
     * 상태별 Agent 수
     */
    long countByStatus(AgentStatus status);
}
