package com.infolink.collector.repository;

import com.infolink.collector.entity.*;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApiFieldMappingRepository extends JpaRepository<ApiFieldMapping, Long> {
    List<ApiFieldMapping> findByApiEndpointIdOrderByDisplayOrderAsc(Long endpointId);
    void deleteByApiEndpointId(Long endpointId);
}
