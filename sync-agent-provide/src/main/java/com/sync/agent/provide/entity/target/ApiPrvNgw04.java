package com.sync.agent.provide.entity.target;

import lombok.*;
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
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiPrvNgw04 {

    /** 수질검사일련번호 (PK) — 레거시: QLTWTR_INSPCT_SN */
    @Id
    @Column(name = "wq_insp_sn")
    private Long wqInspSn;

    // ── PIVOT 결과 항목 (0001 ~ 0010) ──

    /** 0001 — 일반세균수 */
    @Column(length = 100)
    private String wt_tot_col_cnts;

    /** 0002 — 총대장균군 */
    @Column(length = 100)
    private String wt_tot_clf;

    /** 0003 — 분원성대장균군 */
    @Column(length = 100)
    private String wt_fcl_cfs;

    /** 0004 — 대장균 */
    @Column(length = 100)
    private String wt_esc_col;

    /** 0005 — 납 */
    @Column(length = 100)
    private String wt_plb;

    /** 0006 — 불소 */
    @Column(length = 100)
    private String wt_flr;

    /** 0007 — 비소 */
    @Column(length = 100)
    private String wt_asn;

    /** 0008 — 셀레늄 */
    @Column(length = 100)
    private String wt_sln;

    /** 0009 — 수소이온농도 */
    @Column(length = 100)
    private String wt_hdg;

    /** 0010 — 시안 */
    @Column(length = 100)
    private String wt_cya;

    // TODO: 0011 ~ 0125 항목 추가 필요
    // 0011: wt_amn_ntg (암모니아성질소)
    // 0012: wt_ntr_ntg (질산성질소)
    // 0013: wt_cdm (카드뮴)
    // 0014: wt_bor (붕소)
    // 0015: wt_chr (크롬)
    // 0016: wt_pen (페놀)
    // 0017: wt_dzn (다이아지논)
    // 0018: wt_prt (파라티온)
    // 0019: wt_fnt (페니트로티온)
    // 0020: wt_cbr (카바릴)
    // 0021: wt_111_tce (1,1,1-트리클로로에탄)
    // 0022: wt_pce (테트라클로로에틸렌)
    // 0023: wt_tce (트리클로로에틸렌)
    // 0024: wt_dcm (디클로로메탄)
    // 0025: wt_bez (벤젠)
    // 0026: wt_tle (톨루엔)
    // 0027: wt_ebz (에틸벤젠)
    // 0028: wt_csl (크실렌)
    // 0029: wt_011_dre (1,1-디클로로에틸렌)
    // 0030: wt_ctc (사염화탄소)
    // 0031: wt_012_dbr_003_crp
    // 0032: wt_014_dox
    // 0033: wt_hdn (경도)
    // 0034: wt_ppc (과망간산칼륨소비량)
    // 0035: wt_sml (냄새)
    // 0036: wt_fev (철)
    // 0037: wt_cop (구리)
    // 0038: wt_cmc (색도)
    // 0039: wt_dtg (세제)
    // 0040: wt_hid (수소이온)
    // 0041: wt_zic (아연)
    // 0042: wt_cri (염소이온)
    // 0043: wt_evr (증발잔류물)
    // 0044: wt_ste (황산이온)
    // 0045: wt_mgn (망간)
    // 0046: wt_tbd (탁도)
    // 0047: wt_sai (질산이온)
    // 0048: wt_alm (알루미늄)
    // 0049: wt_ecd (전기전도도)
    // 0050: wt_ogp (유기인)
    // 0051 ~ 0125: 나머지 수질검사 항목

    // ── 추적 컬럼 ──

    @Column(name = "execution_id", length = 100)
    private String executionId;

    @Column(name = "source_refs", length = 4000)
    private String sourceRefs;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
