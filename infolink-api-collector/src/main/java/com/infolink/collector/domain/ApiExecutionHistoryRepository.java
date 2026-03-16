package com.infolink.collector.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiExecutionHistoryRepository extends JpaRepository<ApiExecutionHistory, Long> {
    Page<ApiExecutionHistory> findByApiEndpointIdOrderByStartedAtDesc(Long endpointId, Pageable pageable);
}
