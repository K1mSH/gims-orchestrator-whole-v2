package com.infolink.collector.entity;

import lombok.*;
import org.hibernate.annotations.Comment;

import javax.persistence.*;

@Entity
@Table(name = "api_field_mapping")
@org.hibernate.annotations.Table(appliesTo = "api_field_mapping", comment = "API 응답 필드 → DB 컬럼 매핑")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
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

    @Column(name = "source_field_type", length = 20)
    @Comment("응답 필드 타입 (string/number/boolean 등) — 재진입 시 화면 표시용")
    private String sourceFieldType;

    @Column(name = "is_conflict_key")
    @Comment("UPSERT conflict 기준 여부 (ON CONFLICT 대상 컬럼)")
    @Builder.Default
    private Boolean isConflictKey = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "transform_type", length = 20, nullable = false)
    @Comment("변환 유형")
    @Builder.Default
    private TransformType transformType = TransformType.NONE;

    @Column(name = "transform_config", columnDefinition = "TEXT")
    @Comment("변환 설정 JSON")
    private String transformConfig;

    @Column(name = "display_order")
    @Comment("표시 순서")
    @Builder.Default
    private Integer displayOrder = 0;

    // --- 파생 컬럼 여부 ---

    @Column(name = "is_derived")
    @Comment("파생 컬럼 여부 (true=API 응답에 없는 파생 컬럼, false=1:1 매핑)")
    @Builder.Default
    private Boolean isDerived = false;

    // --- LOOKUP 전용 필드 ---

    @Column(name = "extract_pattern", length = 500)
    @Comment("키 추출 정규식 (예: https?://(?:www\\\\.)?([^/]+))")
    private String extractPattern;

    @Column(name = "extract_group")
    @Comment("정규식 캡처 그룹 번호 (기본 1)")
    private Integer extractGroup;

    @Column(name = "lookup_param", length = 100)
    @Comment("공통코드 파라미터 (예: NGW_0118)")
    private String lookupParam;

    @Column(name = "lookup_key_field", length = 100)
    @Comment("LOOKUP 응답 매칭 키 필드 (예: detailCode)")
    private String lookupKeyField;

    @Column(name = "lookup_value_field", length = 100)
    @Comment("LOOKUP 응답 반환 값 필드 (예: detailCodeName)")
    private String lookupValueField;

    @Column(name = "lookup_data_root_path", length = 200)
    @Comment("LOOKUP 응답 데이터 루트 (예: data.common)")
    private String lookupDataRootPath;

    @Column(name = "lookup_match_type", length = 20)
    @Comment("LOOKUP 매칭 방식 (EXACT=기본, 향후 CONTAINS 등 확장)")
    @Builder.Default
    private String lookupMatchType = "EXACT";

    @Column(name = "default_value", length = 200)
    @Comment("LOOKUP 실패 시 기본값")
    private String defaultValue;

    public enum TransformType {
        NONE, DATE_FORMAT, NUMBER, SUBSTRING, TRIM, REPLACE, DEFAULT_VALUE, LOOKUP
    }
}
