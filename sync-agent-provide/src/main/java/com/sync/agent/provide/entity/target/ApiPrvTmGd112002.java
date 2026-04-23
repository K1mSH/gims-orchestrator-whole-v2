package com.sync.agent.provide.entity.target;

import lombok.*;
import org.hibernate.annotations.Comment;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 드림서비스_공공관정 제공용 테이블 (Type A — A2/A3/A4)
 *
 * 레거시: MEGOKR selectNgw09/09_01 (공공관정 상세), 가뭄119 selectdroght119
 * 원본: Oracle WT_DREAM_PERMWELL_PUBLIC (레거시) → TM_GD112002 (표준화)
 * SDE_NGWS.WT_DREAM_PERMWELL_PUBLIC_21033도 동일 구조 (OBJECTID 추가)
 */
@Entity
@Table(name = "api_prv_tm_gd112002",
       uniqueConstraints = @UniqueConstraint(name = "uk_api_prv_tm_gd112002_source_refs", columnNames = {"source_refs"}))
@org.hibernate.annotations.Table(appliesTo = "api_prv_tm_gd112002", comment = "드림서비스_공공관정 제공")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiPrvTmGd112002 {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("일련번호")
    private Long sn;

    @Column(length = 7)
    @Comment("연계전송시군구코드")
    private String link_trsm_sgg_cd;

    @Column(length = 30)
    @Comment("허가신고번호")
    private String prmsn_dclr_no;

    @Column(length = 1)
    @Comment("허가신고형태코드")
    private String prmsn_dclr_frm_cd;

    @Column(length = 4)
    @Comment("연도구분")
    private String yr_se;

    @Column(length = 10)
    @Comment("지역코드")
    private String rgn_cd;

    @Column(length = 40)
    @Comment("시도명")
    private String ctpv_nm;

    @Column(length = 40)
    @Comment("시군구명")
    private String sgg_nm;

    @Column(length = 30)
    @Comment("읍면동명")
    private String emd_nm;

    @Column(length = 40)
    @Comment("리명")
    private String li_nm;

    @Column(length = 1)
    @Comment("산")
    private String mtn;

    @Column(length = 20)
    @Comment("번지")
    private String bnj;

    @Column(length = 10)
    @Comment("호")
    private String ho;

    @Column(length = 20)
    @Comment("지하수용도")
    private String ugwtr_usg;

    @Column(length = 2)
    @Comment("지하수상세용도코드")
    private String ugwtr_dtl_usg_cd;

    @Column(length = 1)
    @Comment("음용여부")
    private String dkpp_yn;

    @Column(length = 20)
    @Comment("위도도")
    private String lat_dg;

    @Column(length = 20)
    @Comment("위도분")
    private String lat_mi;

    @Column(length = 20)
    @Comment("위도초")
    private String lat_ss;

    @Column(length = 20)
    @Comment("경도도")
    private String lot_dg;

    @Column(length = 20)
    @Comment("경도분")
    private String lot_mi;

    @Column(length = 20)
    @Comment("경도초")
    private String lot_ss;

    @Comment("심도값")
    private Long dph_vl;

    @Comment("굴착구경")
    private Long dgg_calbr;

    @Comment("토출관직경")
    private Long delp_dia;

    @Comment("펌프마력")
    private Long pump_hrspw;

    @Comment("취수계획분기")
    private Long wtrit_plan_qtr;

    @Comment("양수능력")
    private Long wpmp_ablt;

    @Comment("년사용량")
    private Long yr_usqty;

    @Column(length = 1)
    @Comment("공공사설구분")
    private String pub_prvtest_se;

    @Column(length = 8)
    @Comment("수질검사일자")
    private String wq_insp_ymd;

    @Column(length = 100)
    @Comment("수질검사결과")
    private String wq_insp_rslt;

    @Column(length = 19)
    @Comment("PNU")
    private String pnu;

    @Comment("X좌표")
    private Long xcrd;

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
