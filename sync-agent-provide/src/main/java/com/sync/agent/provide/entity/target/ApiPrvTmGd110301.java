package com.sync.agent.provide.entity.target;

import lombok.*;
import org.hibernate.annotations.Comment;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 수질측정망검사개요 제공용 테이블 (Type B — B1)
 *
 * 레거시: MEGOKR selectNgw03 (수질측정망검사 개요)
 * 원본: Oracle TM_GD30301 (레거시) → TM_GD110301 (표준화)
 * 전처리: 2중 서브쿼리 (TM_GD10001 JOSACODE 필터)
 */
@Entity
@Table(name = "api_prv_tm_gd110301",
       uniqueConstraints = @UniqueConstraint(name = "uk_api_prv_tm_gd110301_source_refs", columnNames = {"source_refs"}))
@org.hibernate.annotations.Table(appliesTo = "api_prv_tm_gd110301", comment = "수질측정망검사개요 제공")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiPrvTmGd110301 {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("일련번호")
    private Long sn;

    @Column(nullable = false)
    @Comment("수질검사일련번호")
    private Long wq_insp_sn;

    @Column(nullable = false)
    @Comment("관정번호")
    private Long gwel_no;

    @Column(length = 4)
    @Comment("조사연도")
    private String exmn_yr;

    @Comment("주기")
    private Long cycl;

    @Column(length = 1)
    @Comment("심도분류코드")
    private String dph_clsf_cd;

    @Comment("심도값")
    private Long dph_vl;

    @Column(length = 8)
    @Comment("채수일자")
    private String wtsmp_ymd;

    @Column(length = 8)
    @Comment("수질검사일자")
    private String wq_insp_ymd;

    @Column(length = 8)
    @Comment("자료입력일자")
    private String data_inpt_ymd;

    @Column(length = 8)
    @Comment("확정일자")
    private String cfmtn_ymd;

    @Comment("최초등록일시")
    private LocalDateTime frst_reg_dt;

    @Comment("최종변경일시")
    private LocalDateTime last_chg_dt;

    @Column(length = 2)
    @Comment("지하수용도코드")
    private String ugwtr_usg_cd;

    @Column(length = 1)
    @Comment("음용여부")
    private String dkpp_yn;

    @Column(length = 5)
    @Comment("지하수수질측정망입력기관코드")
    private String ugwtr_wqmn_inpt_inst_cd;

    @Column(length = 4000)
    @Comment("수질검사불가사유내용")
    private String wq_insp_imps_rsn_cn;

    @Column(length = 40)
    @Comment("시도명")
    private String ctpv_nm;

    @Column(length = 30)
    @Comment("시군구명")
    private String sgg_nm;

    @Column(length = 30)
    @Comment("읍면동명")
    private String emd_nm;

    @Column(length = 40)
    @Comment("리명")
    private String li_nm;

    @Column(length = 250)
    @Comment("주소")
    private String addr;

    @Column(length = 1)
    @Comment("공공관정여부")
    private String pub_gwel_yn;

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
