package com.gims.provider.entity.provide;

import lombok.*;
import javax.persistence.*;

/**
 * 공통가뭄상태 제공용 테이블
 */
@Entity
@Table(name = "api_prv_tm_gd000203")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiPrvTmGd000203 {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long sn;

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

    /** 인구수 */
    private Long ppltn_cnt;

    /** 지역특성내용 */
    @Column(length = 100)
    private String lpcd_cn;

    /** 수요량값 */
    private Long dmd_qnt_vl;

    /** 공급가능량값 */
    private Long sply_psblqy_vl;

    /** 부족량값 */
    private Long ovshrts_qnt_vl;

    /** 총공공관정수 */
    private Long tot_pub_gwel_cnt;

    /** 가용공공관정수 */
    private Long use_pub_gwel_cnt;
}
