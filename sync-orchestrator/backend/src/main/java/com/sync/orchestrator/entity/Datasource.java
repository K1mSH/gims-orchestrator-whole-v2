package com.sync.orchestrator.entity;

import javax.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "datasource")
@org.hibernate.annotations.Table(appliesTo = "datasource", comment = "데이터소스 연결정보")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Datasource {

    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", insertable = false, updatable = false)
    @Comment("자동생성 시퀀스")
    private Long id;

    @Id
    @Column(name = "datasource_id", length = 50)
    @Comment("데이터소스 고유 ID (PK)")
    private String datasourceId;

    @Column(name = "datasource_name", length = 100, nullable = false)
    @Comment("데이터소스명")
    private String datasourceName;

    @Enumerated(EnumType.STRING)
    @Column(name = "db_type", length = 20, nullable = false)
    @Comment("DB 유형 (POSTGRESQL/MYSQL/ORACLE)")
    private DbType dbType;

    @Column(name = "host", length = 255, nullable = false)
    @Comment("호스트 주소")
    private String host;

    @Column(name = "port", nullable = false)
    @Comment("포트 번호")
    private Integer port;

    @Column(name = "database_name", length = 100, nullable = false)
    @Comment("데이터베이스명")
    private String databaseName;

    @Column(name = "username", length = 512, nullable = false)
    @Comment("접속 계정 (암호화)")
    private String username;

    @Column(name = "password", length = 1024, nullable = false)
    @Comment("접속 비밀번호 (암호화)")
    private String password;

    @Column(name = "description", columnDefinition = "TEXT")
    @Comment("설명")
    private String description;

    @Column(name = "zone", length = 50)
    @Comment("네트워크 존")
    private String zone;

    @Column(name = "is_active")
    @Comment("활성화 여부")
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    @Comment("생성 시각")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    @Comment("수정 시각")
    private LocalDateTime updatedAt;

    public String getJdbcUrl() {
        return dbType.buildJdbcUrl(host, port, databaseName);
    }

    public String getDriverClassName() {
        return dbType.getDriverClassName();
    }
}
