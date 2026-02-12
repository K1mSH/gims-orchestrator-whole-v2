package com.sync.orchestrator.domain.datasource;

import javax.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Datasource에서 사용할 테이블 정보
 */
@Entity
@Table(name = "datasource_table")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DatasourceTable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "datasource_id", length = 50, nullable = false)
    private String datasourceId;

    @Column(name = "table_name", length = 100, nullable = false)
    private String tableName;

    @Column(name = "table_alias", length = 100)
    private String tableAlias;

    @Column(name = "description", length = 500)
    private String description;

    @OneToMany(mappedBy = "datasourceTable", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<DatasourceColumn> columns = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public void addColumn(DatasourceColumn column) {
        columns.add(column);
        column.setDatasourceTable(this);
    }

    public void removeColumn(DatasourceColumn column) {
        columns.remove(column);
        column.setDatasourceTable(null);
    }
}
