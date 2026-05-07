package com.infolink.agent.provide.entity.target;

import lombok.*;
import org.hibernate.annotations.Comment;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 공통가뭄상태 제공용 테이블 (Type A — A1)
 *
 * 레거시: MEGOKR selectNgw08 (공공관정 가뭄지원)
 * 원본: Oracle TM_GD00203 (레거시) → TM_GD000203 (표준화)
 */
@Entity
@Table(name = "api_prv_tm_gd000203", uniqueConstraints = {
    @UniqueConstraint(name = "uk_api_prv_tm_gd000203_source_refs", columnNames = {"source_refs"}),
    @UniqueConstraint(name = "uk_api_prv_tm_gd000203_region", columnNames = {"ctpv_nm", "sgg_nm", "emd_nm", "li_nm"})
})
@org.hibernate.annotations.Table(appliesTo = "api_prv_tm_gd000203", comment = "공통가뭄상태 제공")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiPrvTmGd000203 {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("일련번호")
    private Long sn;

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

    @Comment("인구수")
    private Long ppltn_cnt;

    @Column(length = 100)
    @Comment("LPCD내용")
    private String lpcd_cn;

    @Comment("수요량값")
    private Long dmd_qnt_vl;

    @Comment("공급가능량값")
    private Long sply_psblqy_vl;

    @Comment("과부족량값")
    private Long ovshrts_qnt_vl;

    @Comment("총공공관정수")
    private Long tot_pub_gwel_cnt;

    @Comment("사용공공관정수")
    private Long use_pub_gwel_cnt;

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
