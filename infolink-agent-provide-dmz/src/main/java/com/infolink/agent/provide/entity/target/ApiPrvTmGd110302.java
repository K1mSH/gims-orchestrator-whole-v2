package com.infolink.agent.provide.entity.target;

import lombok.*;
import org.hibernate.annotations.Comment;

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
@Table(name = "api_prv_tm_gd110302",
       uniqueConstraints = @UniqueConstraint(name = "uk_api_prv_tm_gd110302_source_refs", columnNames = {"source_refs"}))
@org.hibernate.annotations.Table(appliesTo = "api_prv_tm_gd110302", comment = "수질측정망검사결과 제공 (EAV)")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiPrvTmGd110302 {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("일련번호")
    private Long sn;

    @Column(nullable = false)
    @Comment("수질검사일련번호")
    private Long wq_insp_sn;

    @Column(length = 4, nullable = false)
    @Comment("수질검사항목코드")
    private String wq_insp_artcl_cd;

    @Column(length = 20)
    @Comment("결과값")
    private String rslt_vl;

    // ── 추적 컬럼 ──

    @Column(name = "execution_id", length = 100)
    @Comment("실행 ID")
    private String executionId;

    @Column(name = "source_refs", length = 4000)
    @Comment("소스 참조")
    private String sourceRefs;

    @Column(name = "updated_at")
    @Comment("갱신 시각")
    private LocalDateTime updatedAt;
}
