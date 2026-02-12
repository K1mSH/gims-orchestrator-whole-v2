package com.sync.agent.bojo.entity.local;

import javax.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * DataSourceConfig - Agent 로컬 설정 테이블
 * Agent가 사용하는 datasource 연결 정보 캐시
 */
@Entity
@Table(name = "datasource_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DataSourceConfig {

    @Id
    @Column(name = "datasource_id")
    private String datasourceId;

    @Column(name = "datasource_name")
    private String datasourceName;

    @Column(name = "zone")
    private String zone;

    @Column(name = "db_type")
    private String dbType;  // POSTGRESQL, ORACLE

    @Column(name = "host")
    private String host;

    @Column(name = "port")
    private Integer port;

    @Column(name = "database_name")
    private String databaseName;

    @Column(name = "username")
    private String username;

    @Column(name = "password")
    private String password;  // Jasypt 암호화 저장

    @Column(name = "schema_name")
    private String schemaName;  // PostgreSQL 스키마 (예: ent1, public)

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    /**
     * 데이터소스 역할: SOURCE 또는 TARGET
     * API 조회 시 ThreadLocal 없이도 올바른 datasource를 선택하기 위함
     */
    @Column(name = "role")
    private String role;  // "SOURCE" or "TARGET"

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
