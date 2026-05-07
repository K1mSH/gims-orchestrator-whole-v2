package com.infolink.orchestrator.repository;

import com.infolink.orchestrator.entity.Datasource;
import com.infolink.orchestrator.entity.DbType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DatasourceRepository extends JpaRepository<Datasource, String> {

    /**
     * 활성화된 데이터소스 목록
     */
    List<Datasource> findByIsActiveTrue();

    /**
     * DB 타입으로 조회
     */
    List<Datasource> findByDbType(DbType dbType);

    /**
     * 활성화된 특정 DB 타입 조회
     */
    List<Datasource> findByDbTypeAndIsActiveTrue(DbType dbType);
}
