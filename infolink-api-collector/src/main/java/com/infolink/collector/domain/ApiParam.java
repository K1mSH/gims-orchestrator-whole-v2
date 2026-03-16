package com.infolink.collector.domain;

import lombok.*;
import org.hibernate.annotations.Comment;

import javax.persistence.*;

@Entity
@Table(name = "api_param")
@org.hibernate.annotations.Table(appliesTo = "api_param", comment = "API 호출 파라미터")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ApiParam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("PK")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "api_endpoint_id", nullable = false)
    private ApiEndpoint apiEndpoint;

    @Column(name = "param_name", length = 100, nullable = false)
    @Comment("파라미터명")
    private String paramName;

    @Enumerated(EnumType.STRING)
    @Column(name = "param_type", length = 20, nullable = false)
    @Comment("파라미터 위치 (QUERY/BODY/PATH/HEADER)")
    private ParamType paramType;

    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", length = 20, nullable = false)
    @Comment("값 유형 (STATIC/DYNAMIC)")
    private ValueType valueType;

    @Column(name = "static_value", length = 500)
    @Comment("고정값 (STATIC일 때)")
    private String staticValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "dynamic_type", length = 20)
    @Comment("동적 유형 (TODAY/NOW/CUSTOM)")
    private DynamicType dynamicType;

    @Column(name = "dynamic_format", length = 50)
    @Comment("날짜 포맷 (예: yyyyMMdd)")
    private String dynamicFormat;

    @Column(name = "dynamic_offset")
    @Comment("오프셋 (예: -1 = 어제)")
    private Integer dynamicOffset;

    @Column(name = "description", length = 200)
    @Comment("설명")
    private String description;

    @Column(name = "display_order")
    @Comment("표시 순서")
    @Builder.Default
    private Integer displayOrder = 0;

    public enum ParamType {
        QUERY, BODY, PATH, HEADER
    }

    public enum ValueType {
        STATIC, DYNAMIC
    }

    public enum DynamicType {
        TODAY, NOW, CUSTOM
    }
}
