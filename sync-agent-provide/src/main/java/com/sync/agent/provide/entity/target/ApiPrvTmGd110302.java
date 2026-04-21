package com.sync.agent.provide.entity.target;

import lombok.*;
import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 수질측정망검사결과 제공용 테이블 (Type B — B3)
 *
 * 레거시: MEGOKR selectNgw04 (수질측정망검사 결과)
 * 원본: Oracle TM_GD30302 (레거시) → TM_GD110302 (표준화)
 * 전처리: PIVOT 125컬럼 (WLTTS_ID_CODE → 항목별 컬럼 전치)
 *
 * 이 엔티티는 PIVOT 전 원본(EAV) 구조.
 * PIVOT 후 flat 테이블은 별도 엔티티(ApiPrvNgw04)로 정의 필요.
 */
@Entity
@Table(name = "api_prv_tm_gd110302")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiPrvTmGd110302 {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long sn;

    /** 수질검사일련번호 (레거시: QLTWTR_INSPCT_SN) */
    @Column(nullable = false)
    private Long wq_insp_sn;

    /** 수질검사항목코드 (레거시: WLTTS_ID_CODE) */
    @Column(length = 4, nullable = false)
    private String wq_insp_artcl_cd;

    /** 결과값 (레거시: RESULT_VALUE) */
    @Column(length = 20)
    private String rslt_vl;

    // ── 추적 컬럼 ──

    @Column(name = "execution_id", length = 100)
    private String executionId;

    @Column(name = "source_refs", length = 4000)
    private String sourceRefs;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
