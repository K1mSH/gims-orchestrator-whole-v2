package com.infolink.collector.repository;

import com.infolink.collector.entity.*;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface ApiExecutionHistoryRepository extends JpaRepository<ApiExecutionHistory, Long> {
    Page<ApiExecutionHistory> findByApiEndpointIdOrderByStartedAtDesc(Long endpointId, Pageable pageable);
    Page<ApiExecutionHistory> findByApiEndpointIdAndStartedAtBetweenOrderByStartedAtDesc(
            Long endpointId, LocalDateTime from, LocalDateTime to, Pageable pageable);
    void deleteByApiEndpointId(Long endpointId);
}
