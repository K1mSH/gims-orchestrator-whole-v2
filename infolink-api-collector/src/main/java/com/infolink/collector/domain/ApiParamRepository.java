package com.infolink.collector.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApiParamRepository extends JpaRepository<ApiParam, Long> {
    List<ApiParam> findByApiEndpointIdOrderByDisplayOrderAsc(Long endpointId);
    void deleteByApiEndpointId(Long endpointId);
}
