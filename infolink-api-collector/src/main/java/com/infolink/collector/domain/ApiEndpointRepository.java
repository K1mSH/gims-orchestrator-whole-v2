package com.infolink.collector.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ApiEndpointRepository extends JpaRepository<ApiEndpoint, Long> {
    Optional<ApiEndpoint> findByApiCode(String apiCode);
    boolean existsByApiCode(String apiCode);
}
