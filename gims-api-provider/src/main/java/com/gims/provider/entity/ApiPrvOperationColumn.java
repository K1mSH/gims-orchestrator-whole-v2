package com.gims.provider.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;

import javax.persistence.*;

/**
 * 오퍼레이션 제공 컬럼 정의
 * 테이블: api_prv_operation_column
 */
@Entity
@Table(name = "api_prv_operation_column")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiPrvOperationColumn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operation_id", nullable = false)
    private ApiPrvOperation operation;

    /** DB컬럼명 */
    @Column(name = "column_name", length = 100, nullable = false)
    private String columnName;

    /** 응답필드명(별칭) */
    @Column(name = "alias_name", length = 100)
    private String aliasName;

    /** 표시순서 */
    @Column(name = "display_order")
    @Builder.Default
    private Integer displayOrder = 0;

    /** 가공타입 (NONE/ROUND/DATE_FORMAT/COALESCE/SUBSTRING) */
    @Column(name = "transform_type", length = 20)
    @Builder.Default
    private String transformType = "NONE";

    /** 가공파라미터 */
    @Column(name = "transform_param", length = 100)
    private String transformParam;
}
