package com.sync.agent.bojo.entity.repository;

import com.sync.agent.bojo.entity.local.DataSourceConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DataSourceConfigRepository extends JpaRepository<DataSourceConfig, String> {

    List<DataSourceConfig> findByZone(String zone);

    List<DataSourceConfig> findByIsActiveTrue();

    List<DataSourceConfig> findByRoleAndIsActiveTrue(String role);
}
