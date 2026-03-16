package com.infolink.collector.domain;

import lombok.*;
import org.hibernate.annotations.Comment;

import javax.persistence.*;

@Entity
@Table(name = "api_field_mapping")
@org.hibernate.annotations.Table(appliesTo = "api_field_mapping", comment = "API 응답 필드 → DB 컬럼 매핑")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ApiFieldMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("PK")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "api_endpoint_id", nullable = false)
    private ApiEndpoint apiEndpoint;

    @Column(name = "source_field_path", length = 200, nullable = false)
    @Comment("응답 필드 경로 (data_root 기준 상대경로)")
    private String sourceFieldPath;

    @Column(name = "target_column_name", length = 100, nullable = false)
    @Comment("DB 컬럼명")
    private String targetColumnName;

    @Column(name = "is_pk")
    @Comment("UPSERT conflict 기준 여부")
    @Builder.Default
    private Boolean isPk = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "transform_type", length = 20, nullable = false)
    @Comment("변환 유형 (NONE/DATE_FORMAT)")
    @Builder.Default
    private TransformType transformType = TransformType.NONE;

    @Column(name = "transform_config", columnDefinition = "TEXT")
    @Comment("변환 설정 JSON")
    private String transformConfig;

    @Column(name = "display_order")
    @Comment("표시 순서")
    @Builder.Default
    private Integer displayOrder = 0;

    public enum TransformType {
        NONE, DATE_FORMAT, NUMBER, SUBSTRING
    }
}
