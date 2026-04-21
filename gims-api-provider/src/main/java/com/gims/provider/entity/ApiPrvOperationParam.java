package com.gims.provider.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
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

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operation_id", nullable = false)
    private ApiPrvOperation operation;

    /** 요청파라미터명 */
    @Column(name = "param_name", length = 100, nullable = false)
    private String paramName;

    /** WHERE대상컬럼명 */
    @Column(name = "column_name", length = 100, nullable = false)
    private String columnName;

    /** 연산자 (EQ/LIKE/GTE/LTE/IN/BETWEEN) */
    @Column(name = "operator", length = 20, nullable = false)
    @Builder.Default
    private String operator = "EQ";

    /** 필수여부 */
    @Column(name = "is_required")
    @Builder.Default
    private Boolean isRequired = false;

    /** 기본값 */
    @Column(name = "default_value", length = 500)
    private String defaultValue;

    /** 데이터타입 (STRING/NUMBER/DATE) */
    @Column(name = "data_type", length = 20)
    @Builder.Default
    private String dataType = "STRING";

    /** 숨김여부 (외부 미노출, 기본값으로 고정) */
    @Column(name = "is_hidden")
    @Builder.Default
    private Boolean isHidden = false;
}
