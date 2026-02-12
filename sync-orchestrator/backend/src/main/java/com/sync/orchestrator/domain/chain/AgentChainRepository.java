package com.sync.orchestrator.domain.chain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AgentChainRepository extends JpaRepository<AgentChain, String> {

    @Query("SELECT DISTINCT c FROM AgentChain c LEFT JOIN FETCH c.members m LEFT JOIN FETCH m.agent")
    List<AgentChain> findAllWithMembers();

    @Query("SELECT c FROM AgentChain c LEFT JOIN FETCH c.members m LEFT JOIN FETCH m.agent WHERE c.chainId = :chainId")
    Optional<AgentChain> findByIdWithMembers(@Param("chainId") String chainId);
}
