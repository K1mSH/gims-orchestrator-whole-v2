package com.infolink.orchestrator.entity;

import javax.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "datasource_table")
@org.hibernate.annotations.Table(appliesTo = "datasource_table", comment = "데이터소스 등록 테이블")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DatasourceTable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("PK")
    private Long id;

    @Column(name = "datasource_id", length = 50, nullable = false)
    @Comment("데이터소스 ID")
    private String datasourceId;

    @Column(name = "table_name", length = 100, nullable = false)
    @Comment("테이블명")
    private String tableName;

    @Column(name = "table_alias", length = 100)
    @Comment("테이블 별칭")
    private String tableAlias;

    @Column(name = "description", length = 500)
    @Comment("설명")
    private String description;

    @OneToMany(mappedBy = "datasourceTable", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<DatasourceColumn> columns = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    @Comment("생성 시각")
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
