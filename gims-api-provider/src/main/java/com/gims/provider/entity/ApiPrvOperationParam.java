package com.gims.provider.entity;

import lombok.*;

import javax.persistence.*;

/**
 * 오퍼레이션 WHERE 파라미터 정의
 * 테이블: api_prv_operation_param
 */
@Entity
@Table(name = "api_prv_operation_param")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiPrvOperationParam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operation_id", nullable = false)
    private ApiPrvOperation operation;

    @Column(name = "param_name", length = 100, nullable = false)
    private String paramName;

    @Column(name = "column_name", length = 100, nullable = false)
    private String columnName;

    @Column(name = "operator", length = 20, nullable = false)
    @Builder.Default
    private String operator = "EQ";

    @Column(name = "is_required")
    @Builder.Default
    private Boolean isRequired = false;

    @Column(name = "default_value", length = 500)
    private String defaultValue;

    @Column(name = "data_type", length = 20)
    @Builder.Default
    private String dataType = "STRING";
}
