package com.sync.orchestrator.repository;

import com.sync.orchestrator.entity.DatasourceTable;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DatasourceTableRepository extends JpaRepository<DatasourceTable, Long> {

    @EntityGraph(attributePaths = {"columns"})
    List<DatasourceTable> findByDatasourceId(String datasourceId);

    Optional<DatasourceTable> findByDatasourceIdAndTableName(String datasourceId, String tableName);

    boolean existsByDatasourceIdAndTableName(String datasourceId, String tableName);
}
