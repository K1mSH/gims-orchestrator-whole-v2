package com.sync.orchestrator.entity;

import javax.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

@Entity
@Table(name = "datasource_column")
@org.hibernate.annotations.Table(appliesTo = "datasource_column", comment = "데이터소스 테이블 컬럼")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DatasourceColumn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("PK")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "datasource_table_id", nullable = false)
    private DatasourceTable datasourceTable;

    @Column(name = "column_name", length = 100, nullable = false)
    @Comment("컬럼명")
    private String columnName;

    @Column(name = "column_alias", length = 100)
    @Comment("컬럼 별칭")
    private String columnAlias;

    @Column(name = "data_type", length = 50)
    @Comment("데이터 타입")
    private String dataType;

    @Column(name = "is_primary_key")
    @Comment("PK 여부")
    @Builder.Default
    private Boolean isPrimaryKey = false;

    @Column(name = "is_nullable")
    @Comment("NULL 허용 여부")
    @Builder.Default
    private Boolean isNullable = true;

    @Column(name = "description", length = 500)
    @Comment("설명")
    private String description;
}
