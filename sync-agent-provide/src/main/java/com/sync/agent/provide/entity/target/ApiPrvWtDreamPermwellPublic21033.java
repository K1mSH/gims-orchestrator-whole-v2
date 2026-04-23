package com.sync.agent.provide.entity.target;

import lombok.*;
import org.hibernate.annotations.Comment;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 가뭄119 인허가관정 제공용 테이블 (Type A — A4)
 *
 * 레거시: OPN selectdroght119 (가뭄119 인허가관정)
 * 원본: Oracle SDE_NGWS.WT_DREAM_PERMWELL_PUBLIC_21033 (2021.03.03 SDE 스냅샷)
 *
 * 구조적으로 WT_DREAM_PERMWELL_PUBLIC(TM_GD112002) 와 동일 + OBJECTID(SDE 관례 PK) 추가.
 * 표준화 매핑 없어 이전 이름 유지 — 소스↔타겟 1:1 (메모리: feedback_provide_target_per_api)
 */
@Entity
@Table(name = "api_prv_wt_dream_permwell_public_21033",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_api_prv_wt_dream_permwell_public_21033_source_refs",
           columnNames = {"source_refs"}))
@org.hibernate.annotations.Table(appliesTo = "api_prv_wt_dream_permwell_public_21033",
       comment = "가뭄119 인허가관정 제공 (SDE 스냅샷)")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiPrvWtDreamPermwellPublic21033 {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("일련번호")
    private Long sn;

    @Column(name = "objectid")
    @Comment("SDE 객체 ID")
    private Long objectid;

    @Column(name = "link_trsm_sgg_cd", length = 7)
    @Comment("연계전송시군구코드")
    private String linkTrsmSggCd;

    @Column(name = "prmsn_dclr_no", length = 30)
    @Comment("허가신고번호")
    private String prmsnDclrNo;

    @Column(name = "prmsn_dclr_frm_cd", length = 1)
    @Comment("허가신고형태코드")
    private String prmsnDclrFrmCd;

    @Column(name = "yr_se", length = 4)
    @Comment("연도구분")
    private String yrSe;

    @Column(name = "rgn_cd", length = 10)
    @Comment("지역코드")
    private String rgnCd;

    @Column(name = "ctpv_nm", length = 40)
    @Comment("시도명")
    private String ctpvNm;

    @Column(name = "sgg_nm", length = 40)
    @Comment("시군구명")
    private String sggNm;

    @Column(name = "emd_nm", length = 30)
    @Comment("읍면동명")
    private String emdNm;

    @Column(name = "li_nm", length = 40)
    @Comment("리명")
    private String liNm;

    @Column(name = "mtn", length = 1)
    @Comment("산")
    private String mtn;

    @Column(name = "bnj", length = 20)
    @Comment("번지")
    private String bnj;

    @Column(name = "ho", length = 10)
    @Comment("호")
    private String ho;

    @Column(name = "ugwtr_usg", length = 20)
    @Comment("지하수용도")
    private String ugwtrUsg;

    @Column(name = "ugwtr_dtl_usg_cd", length = 2)
    @Comment("지하수상세용도코드")
    private String ugwtrDtlUsgCd;

    @Column(name = "dkpp_yn", length = 1)
    @Comment("음용여부")
    private String dkppYn;

    @Column(name = "lat_dg", length = 20)
    @Comment("위도도")
    private String latDg;

    @Column(name = "lat_mi", length = 20)
    @Comment("위도분")
    private String latMi;

    @Column(name = "lat_ss", length = 20)
    @Comment("위도초")
    private String latSs;

    @Column(name = "lot_dg", length = 20)
    @Comment("경도도")
    private String lotDg;

    @Column(name = "lot_mi", length = 20)
    @Comment("경도분")
    private String lotMi;

    @Column(name = "lot_ss", length = 20)
    @Comment("경도초")
    private String lotSs;

    @Column(name = "dph_vl")
    @Comment("심도값")
    private Long dphVl;

    @Column(name = "dgg_calbr")
    @Comment("굴착구경")
    private Long dggCalbr;

    @Column(name = "delp_dia")
    @Comment("토출관직경")
    private Long delpDia;

    @Column(name = "pump_hrspw")
    @Comment("펌프마력")
    private Long pumpHrspw;

    @Column(name = "wtrit_plan_qtr")
    @Comment("취수계획분기")
    private Long wtritPlanQtr;

    @Column(name = "wpmp_ablt")
    @Comment("양수능력")
    private Long wpmpAblt;

    @Column(name = "yr_usqty")
    @Comment("년사용량")
    private Long yrUsqty;

    @Column(name = "pub_prvtest_se", length = 1)
    @Comment("공공사설구분")
    private String pubPrvtestSe;

    @Column(name = "wq_insp_ymd", length = 8)
    @Comment("수질검사일자")
    private String wqInspYmd;

    @Column(name = "wq_insp_rslt", length = 100)
    @Comment("수질검사결과")
    private String wqInspRslt;

    @Column(name = "pnu", length = 19)
    @Comment("PNU")
    private String pnu;

    @Column(name = "xcrd")
    @Comment("X좌표")
    private Long xcrd;

    @Column(name = "ycrd")
    @Comment("Y좌표")
    private Long ycrd;

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
