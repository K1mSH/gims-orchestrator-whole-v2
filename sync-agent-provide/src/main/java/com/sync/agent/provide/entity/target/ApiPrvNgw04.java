package com.sync.agent.provide.entity.target;

import lombok.*;
import org.hibernate.annotations.Comment;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 수질측정망검사결과 PIVOT 제공용 테이블 (Type B — B3)
 *
 * 레거시: MEGOKR selectNgw04 (TM_GD30302 PIVOT)
 * 원본: Oracle TM_GD30302 (레거시) → TM_GD110302 (표준화)
 * 전처리: PIVOT — WLTTS_ID_CODE 기준 125개 항목 컬럼 변환
 */
@Entity
@Table(name = "api_prv_ngw04")
@org.hibernate.annotations.Table(appliesTo = "api_prv_ngw04", comment = "수질측정망검사결과 PIVOT 제공 (125항목)")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiPrvNgw04 {

    @Id
    @Column(name = "wq_insp_sn")
    @Comment("수질검사일련번호")
    private Long wqInspSn;

    // ── PIVOT 결과 항목 (0001 ~ 0010) ──

    @Column(length = 100)
    @Comment("0001 일반세균수")
    private String wt_tot_col_cnts;

    @Column(length = 100)
    @Comment("0002 총대장균군")
    private String wt_tot_clf;

    @Column(length = 100)
    @Comment("0003 분원성대장균군")
    private String wt_fcl_cfs;

    @Column(length = 100)
    @Comment("0004 대장균")
    private String wt_esc_col;

    @Column(length = 100)
    @Comment("0005 납")
    private String wt_plb;

    @Column(length = 100)
    @Comment("0006 불소")
    private String wt_flr;

    @Column(length = 100)
    @Comment("0007 비소")
    private String wt_asn;

    @Column(length = 100)
    @Comment("0008 셀레늄")
    private String wt_sln;

    @Column(length = 100)
    @Comment("0009 수소이온농도")
    private String wt_hdg;

    @Column(length = 100)
    @Comment("0010 시안")
    private String wt_cya;

    // TODO: 0011 ~ 0125 항목 추가 필요

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
