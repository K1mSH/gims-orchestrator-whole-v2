package com.gims.provider.entity;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operation_id", nullable = false)
    private ApiPrvOperation operation;

    @Column(name = "column_name", length = 100, nullable = false)
    private String columnName;

    @Column(name = "alias_name", length = 100)
    private String aliasName;

    @Column(name = "display_order")
    @Builder.Default
    private Integer displayOrder = 0;
}
