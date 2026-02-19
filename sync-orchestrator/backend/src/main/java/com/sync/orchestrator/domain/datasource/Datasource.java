package com.sync.orchestrator.domain.datasource;

import javax.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 데이터소스 엔티티
 *
 * 사용자가 등록하는 DB 연결 정보
 * Agent 등록 시 source/target으로 선택됨
 */
@Entity
@Table(name = "datasource")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Datasource {

    /**
     * 내부 숫자 ID (sourceRef 추적용)
     * Auto-increment로 자동 생성
     */
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", insertable = false, updatable = false)
    private Long id;

    @Id
    @Column(name = "datasource_id", length = 50)
    private String datasourceId;

    @Column(name = "datasource_name", length = 100, nullable = false)
    private String datasourceName;

    @Enumerated(EnumType.STRING)
    @Column(name = "db_type", length = 20, nullable = false)
    private DbType dbType;

    @Column(name = "host", length = 255, nullable = false)
    private String host;

    @Column(name = "port", nullable = false)
    private Integer port;

    @Column(name = "database_name", length = 100, nullable = false)
    private String databaseName;

    @Column(name = "username", length = 512, nullable = false)
    private String username;

    @Column(name = "password", length = 1024, nullable = false)
    private String password;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * DB가 위치한 네트워크 존
     * EXTERNAL, DMZ, INTERNAL_COMMON, INTERNAL_SERVICE
     * Connection test 시 해당 zone의 master Agent가 대신 테스트
     */
    @Column(name = "zone", length = 50)
    private String zone;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * JDBC URL 생성
     */
    public String getJdbcUrl() {
        return dbType.buildJdbcUrl(host, port, databaseName);
    }

    /**
     * JDBC Driver 클래스명
     */
    public String getDriverClassName() {
        return dbType.getDriverClassName();
    }
}
