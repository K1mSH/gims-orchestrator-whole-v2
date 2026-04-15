package com.gims.provider.entity;

import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * API 제공 오퍼레이션 (API 단위)
 * 테이블: api_prv_operation
 */
@Entity
@Table(name = "api_prv_operation", uniqueConstraints = {
        @UniqueConstraint(name = "uk_api_prv_op_id", columnNames = "operation_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiPrvOperation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "operation_id", length = 100, nullable = false)
    private String operationId;

    @Column(name = "operation_name", length = 200, nullable = false)
    private String operationName;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "datasource_id", length = 50, nullable = false)
    private String datasourceId;

    @Column(name = "table_name", length = 100, nullable = false)
    private String tableName;

    @Column(name = "response_format", length = 10)
    @Builder.Default
    private String responseFormat = "JSON";

    @Column(name = "page_size")
    @Builder.Default
    private Integer pageSize = 100;

    @Column(name = "max_page_size")
    @Builder.Default
    private Integer maxPageSize = 1000;

    @Column(name = "order_by_column", length = 100)
    private String orderByColumn;

    @Column(name = "order_by_direction", length = 4)
    @Builder.Default
    private String orderByDirection = "ASC";

    @Column(name = "is_published")
    @Builder.Default
    private Boolean isPublished = false;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @OneToMany(mappedBy = "operation", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ApiPrvOperationColumn> columns = new ArrayList<>();

    @OneToMany(mappedBy = "operation", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ApiPrvOperationParam> params = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
