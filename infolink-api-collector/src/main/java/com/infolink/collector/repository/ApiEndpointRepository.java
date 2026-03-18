package com.infolink.collector.repository;

import com.infolink.collector.entity.*;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ApiEndpointRepository extends JpaRepository<ApiEndpoint, Long> {
}
