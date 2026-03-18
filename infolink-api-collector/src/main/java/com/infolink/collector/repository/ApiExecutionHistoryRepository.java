package com.infolink.collector.repository;

import com.infolink.collector.entity.*;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiExecutionHistoryRepository extends JpaRepository<ApiExecutionHistory, Long> {
    Page<ApiExecutionHistory> findByApiEndpointIdOrderByStartedAtDesc(Long endpointId, Pageable pageable);
    void deleteByApiEndpointId(Long endpointId);
}
