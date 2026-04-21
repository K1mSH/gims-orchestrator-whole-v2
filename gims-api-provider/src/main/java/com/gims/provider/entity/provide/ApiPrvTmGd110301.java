package com.gims.provider.entity.provide;

import lombok.*;
import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 수질측정망검사개요 제공용 테이블
 */
@Entity
@Table(name = "api_prv_tm_gd110301")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiPrvTmGd110301 {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long sn;

    /** 수질검사일련번호 */
    @Column(nullable = false)
    private Long wq_insp_sn;

    /** 관정번호 */
    @Column(nullable = false)
    private Long gwel_no;

    /** 검사년도 */
    @Column(length = 4)
    private String exmn_yr;

    /** 차수 */
    private Long cycl;

    /** 심도구분코드 */
    @Column(length = 1)
    private String dph_clsf_cd;

    /** 심도값 */
    private Long dph_vl;

    /** 시료채취일자 */
    @Column(length = 8)
    private String wtsmp_ymd;

    /** 수질검사일자 */
    @Column(length = 8)
    private String wq_insp_ymd;

    /** 자료입력일자 */
    @Column(length = 8)
    private String data_inpt_ymd;

    /** 확인일자 */
    @Column(length = 8)
    private String cfmtn_ymd;

    /** 최초등록일시 */
    private LocalDateTime frst_reg_dt;

    /** 최종변경일시 */
    private LocalDateTime last_chg_dt;

    /** 지하수용도코드 */
    @Column(length = 2)
    private String ugwtr_usg_cd;

    /** 음용여부 */
    @Column(length = 1)
    private String dkpp_yn;

    /** 지하수수질측정망입력기관코드 */
    @Column(length = 5)
    private String ugwtr_wqmn_inpt_inst_cd;

    /** 수질검사불가사유내용 */
    @Column(length = 4000)
    private String wq_insp_imps_rsn_cn;

    /** 시도명 */
    @Column(length = 40)
    private String ctpv_nm;

    /** 시군구명 */
    @Column(length = 30)
    private String sgg_nm;

    /** 읍면동명 */
    @Column(length = 30)
    private String emd_nm;

    /** 리명 */
    @Column(length = 40)
    private String li_nm;

    /** 주소 */
    @Column(length = 250)
    private String addr;

    /** 공공관정여부 */
    @Column(length = 1)
    private String pub_gwel_yn;
}
