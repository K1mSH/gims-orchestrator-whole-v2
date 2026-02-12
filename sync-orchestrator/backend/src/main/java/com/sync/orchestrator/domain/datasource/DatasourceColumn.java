package com.sync.orchestrator.domain.datasource;

import javax.persistence.*;
import lombok.*;

/**
 * Datasource 테이블에서 사용할 컬럼 정보
 */
@Entity
@Table(name = "datasource_column")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DatasourceColumn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "datasource_table_id", nullable = false)
    private DatasourceTable datasourceTable;

    @Column(name = "column_name", length = 100, nullable = false)
    private String columnName;

    @Column(name = "column_alias", length = 100)
    private String columnAlias;

    @Column(name = "data_type", length = 50)
    private String dataType;

    @Column(name = "is_primary_key")
    @Builder.Default
    private Boolean isPrimaryKey = false;

    @Column(name = "is_nullable")
    @Builder.Default
    private Boolean isNullable = true;

    @Column(name = "description", length = 500)
    private String description;
}
