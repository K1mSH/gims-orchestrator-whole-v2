package com.sync.agent.provide.entity.target;

import lombok.*;
import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 인허가관정 제공용 테이블 (Type B — B4)
 *
 * 레거시: OPN info_permwell (RGETNPMMS01 + TC_GD00100 JOIN + 함수 변환)
 * 원본: Oracle RGETNPMMS01 (레거시, 표준화 매핑 없음)
 * 전처리: JOIN(주소 결합) + FN_GD_GET_GUBUN/FN_GD_GET_CMMTNDCODE 함수 변환
 * 복합 PK: rel_trsm_sgg_cd + prmsn_dclr_no
 */
@Entity
@Table(name = "api_prv_permwell")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(ApiPrvPermwell.PK.class)
public class ApiPrvPermwell {

    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class PK implements Serializable {
        private String relTrsmSggCd;
        private String prmsnDclrNo;
    }

    /** 연관전송시군구코드 — 레거시: REL_TRANS_CGG_CODE */
    @Id
    @Column(name = "rel_trsm_sgg_cd", length = 7)
    private String relTrsmSggCd;

    /** 허가신고번호 — 레거시: PERM_NT_NO → PRMSN_DCLR_NO */
    @Id
    @Column(name = "prmsn_dclr_no", length = 30)
    private String prmsnDclrNo;

    /** 허가형태명 — 레거시: FN_GD_GET_GUBUN(PERM_NT_FORM_CODE, 1) 결과 */
    @Column(name = "prmsn_dclr_frm_nm", length = 100)
    private String prmsnDclrFrmNm;

    /** 주소 — 레거시: BRTC_NM || SIGUN_NM || EMD_NM || LI_NM 결합 */
    @Column(name = "addr", length = 500)
    private String addr;

    /** 용도명 — 레거시: FN_GD_GET_CMMTNDCODE('NGW_0003', UWATER_SRV_CODE) 결과 */
    @Column(name = "ugwtr_usg_nm", length = 100)
    private String ugwtrUsgNm;

    /** 상세용도명 — 레거시: FN_GD_GET_CMMTNDCODE('NGW_0013', UWATER_DTL_SRV_CODE) 결과 */
    @Column(name = "ugwtr_dtl_usg_nm", length = 100)
    private String ugwtrDtlUsgNm;

    /** 음용여부 — 레거시: UWATER_POTA_YN */
    @Column(name = "dkpp_yn", length = 1)
    private String dkppYn;

    /** 굴착심도 — 레거시: DIG_DPH */
    @Column(name = "dgg_dph", length = 20)
    private String dggDph;

    /** 굴착구경 — 레거시: DIG_DIAM → DGG_CALBR */
    @Column(name = "dgg_calbr", length = 20)
    private String dggCalbr;

    /** 양수기심도 — 레거시: ESB_DPH */
    @Column(name = "esb_dph", length = 20)
    private String esbDph;

    /** 수량 — 레거시: ND_QT → REQ_QTY */
    @Column(name = "req_qty", length = 20)
    private String reqQty;

    /** 계획양수량 — 레거시: FRW_PLN_QUA → WTRIT_PLNQTY */
    @Column(name = "wtrit_plnqty", length = 20)
    private String wtritPlnqty;

    /** 양수능력 — 레거시: RWT_CAP → WPMP_ABLT */
    @Column(name = "wpmp_ablt", length = 20)
    private String wpmpAblt;

    /** 동수위마력 — 레거시: DYN_EQN_HRP (표준화 매핑 없음) */
    @Column(name = "dyn_eqn_hrp", length = 20)
    private String dynEqnHrp;

    /** 관경 — 레거시: PIPE_DIAM → DELP_DIA */
    @Column(name = "delp_dia", length = 20)
    private String delpDia;

    // ── 추적 컬럼 ──

    @Column(name = "execution_id", length = 100)
    private String executionId;

    @Column(name = "source_refs", length = 4000)
    private String sourceRefs;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
