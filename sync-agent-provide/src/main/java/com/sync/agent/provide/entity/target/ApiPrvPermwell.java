package com.sync.agent.provide.entity.target;

import lombok.*;
import org.hibernate.annotations.Comment;

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
@org.hibernate.annotations.Table(appliesTo = "api_prv_permwell", comment = "인허가관정 제공")
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

    @Id
    @Column(name = "rel_trsm_sgg_cd", length = 7)
    @Comment("연관전송시군구코드")
    private String relTrsmSggCd;

    @Id
    @Column(name = "prmsn_dclr_no", length = 30)
    @Comment("허가신고번호")
    private String prmsnDclrNo;

    @Column(name = "prmsn_dclr_frm_nm", length = 100)
    @Comment("허가형태명")
    private String prmsnDclrFrmNm;

    @Column(name = "addr", length = 500)
    @Comment("주소")
    private String addr;

    @Column(name = "ugwtr_usg_nm", length = 100)
    @Comment("지하수용도명")
    private String ugwtrUsgNm;

    @Column(name = "ugwtr_dtl_usg_nm", length = 100)
    @Comment("지하수상세용도명")
    private String ugwtrDtlUsgNm;

    @Column(name = "dkpp_yn", length = 1)
    @Comment("음용여부")
    private String dkppYn;

    @Column(name = "dgg_dph", length = 20)
    @Comment("굴착심도")
    private String dggDph;

    @Column(name = "dgg_calbr", length = 20)
    @Comment("굴착구경")
    private String dggCalbr;

    @Column(name = "esb_dph", length = 20)
    @Comment("양수기심도")
    private String esbDph;

    @Column(name = "req_qty", length = 20)
    @Comment("수량")
    private String reqQty;

    @Column(name = "wtrit_plnqty", length = 20)
    @Comment("계획양수량")
    private String wtritPlnqty;

    @Column(name = "wpmp_ablt", length = 20)
    @Comment("양수능력")
    private String wpmpAblt;

    @Column(name = "dyn_eqn_hrp", length = 20)
    @Comment("동수위마력")
    private String dynEqnHrp;

    @Column(name = "delp_dia", length = 20)
    @Comment("관경")
    private String delpDia;

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
