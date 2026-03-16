package com.infolink.collector.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApiScheduleRepository extends JpaRepository<ApiSchedule, Long> {
    List<ApiSchedule> findByApiEndpointId(Long endpointId);
    List<ApiSchedule> findByIsEnabledTrue();
}
