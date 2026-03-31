package com.sync.orchestrator.repository;

import com.sync.orchestrator.entity.ZoneConfig;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ZoneConfigRepository extends JpaRepository<ZoneConfig, String> {

    Optional<ZoneConfig> findByZoneAndIsActiveTrue(String zone);

    /**
     * Zone으로 shortCode 조회
     */
    default String findShortCodeByZone(String zone) {
        return findById(zone)
                .map(ZoneConfig::getShortCode)
                .orElse(null);
    }
}
