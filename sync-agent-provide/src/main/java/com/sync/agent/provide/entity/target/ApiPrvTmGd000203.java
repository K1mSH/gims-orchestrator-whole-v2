package com.sync.agent.provide.entity.target;

import lombok.*;
import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 공통가뭄상태 제공용 테이블 (Type A)
 *
 * 레거시: MEGOKR selectNgw08 (공공관정 가뭄지원)
 * 원본: Oracle TM_GD00203 (레거시) → TM_GD000203 (표준화)
 * 표준화 컬럼명 적용 완료
 */
@Entity
@Table(name = "api_prv_tm_gd000203", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"ctpv_nm", "sgg_nm", "emd_nm", "li_nm"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiPrvTmGd000203 {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long sn;

    /** 시도명 (레거시: BRTC_NM) */
    @Column(length = 40)
    private String ctpv_nm;

    /** 시군구명 (레거시: SIGUN_NM) */
    @Column(length = 40)
    private String sgg_nm;

    /** 읍면동명 */
    @Column(length = 30)
    private String emd_nm;

    /** 리명 */
    @Column(length = 40)
    private String li_nm;

    /** 인구수 (레거시: POPLTN_VALUE) */
    private Long ppltn_cnt;

    /** 지역특성내용 (레거시: LPCD_CTNT) */
    @Column(length = 100)
    private String lpcd_cn;

    /** 수요량값 (레거시: DMAND_QUA_VALUE) */
    private Long dmd_qnt_vl;

    /** 공급가능량값 (레거시: SUPLY_PSBLQY_VALUE) */
    private Long sply_psblqy_vl;

    /** 부족량값 (레거시: NSTT_VALUE) */
    private Long ovshrts_qnt_vl;

    /** 총공공관정수 (레거시: TOT_PUBWELL_CO) */
    private Long tot_pub_gwel_cnt;

    /** 가용공공관정수 (레거시: USE_PUBWELL_CO) */
    private Long use_pub_gwel_cnt;

    // ── 추적 컬럼 ──

    @Column(name = "execution_id", length = 100)
    private String executionId;

    @Column(name = "source_refs", length = 4000)
    private String sourceRefs;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
