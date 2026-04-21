package com.gims.provider.entity.provide;

import lombok.*;
import javax.persistence.*;

/**
 * 드림서비스 공공관정 제공용 테이블
 */
@Entity
@Table(name = "api_prv_tm_gd112002")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiPrvTmGd112002 {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long sn;

    /** 연계전송시군구코드 */
    @Column(length = 7)
    private String link_trsm_sgg_cd;

    /** 허가신고번호 */
    @Column(length = 30)
    private String prmsn_dclr_no;

    /** 허가신고형태코드 */
    @Column(length = 1)
    private String prmsn_dclr_frm_cd;

    /** 연도구분 */
    @Column(length = 4)
    private String yr_se;

    /** 지역코드 */
    @Column(length = 10)
    private String rgn_cd;

    /** 시도명 */
    @Column(length = 40)
    private String ctpv_nm;

    /** 시군구명 */
    @Column(length = 40)
    private String sgg_nm;

    /** 읍면동명 */
    @Column(length = 30)
    private String emd_nm;

    /** 리명 */
    @Column(length = 40)
    private String li_nm;

    /** 산 */
    @Column(length = 1)
    private String mtn;

    /** 번지 */
    @Column(length = 20)
    private String bnj;

    /** 호 */
    @Column(length = 10)
    private String ho;

    /** 지하수용도 */
    @Column(length = 20)
    private String ugwtr_usg;

    /** 지하수상세용도코드 */
    @Column(length = 2)
    private String ugwtr_dtl_usg_cd;

    /** 음용여부 */
    @Column(length = 1)
    private String dkpp_yn;

    /** 위도(도) */
    @Column(length = 20)
    private String lat_dg;

    /** 위도(분) */
    @Column(length = 20)
    private String lat_mi;

    /** 위도(초) */
    @Column(length = 20)
    private String lat_ss;

    /** 경도(도) */
    @Column(length = 20)
    private String lot_dg;

    /** 경도(분) */
    @Column(length = 20)
    private String lot_mi;

    /** 경도(초) */
    @Column(length = 20)
    private String lot_ss;

    /** 심도값 */
    private Long dph_vl;

    /** 굴착구경 */
    private Long dgg_calbr;

    /** 케이싱구경 */
    private Long delp_dia;

    /** 펌프마력 */
    private Long pump_hrspw;

    /** 취수계획량 */
    private Long wtrit_plan_qtr;

    /** 양수능력 */
    private Long wpmp_ablt;

    /** 연간사용량 */
    private Long yr_usqty;

    /** 공공민간구분 */
    @Column(length = 1)
    private String pub_prvtest_se;

    /** 수질검사일자 */
    @Column(length = 8)
    private String wq_insp_ymd;

    /** 수질검사결과 */
    @Column(length = 100)
    private String wq_insp_rslt;

    /** 필지고유번호 */
    @Column(length = 19)
    private String pnu;

    /** X좌표 */
    private Long xcrd;

    /** Y좌표 */
    private Long ycrd;
}
