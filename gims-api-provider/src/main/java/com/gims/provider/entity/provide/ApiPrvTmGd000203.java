package com.gims.provider.entity.provide;

import lombok.*;
import javax.persistence.*;

/**
 * 공통가뭄상태 제공용 테이블
 * 원본: TM_GD00203 → 표준화: TM_GD000203
 */
@Entity
@Table(name = "API_PRV_TM_GD000203")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiPrvTmGd000203 {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "SN")
    private Long sn;

    @Column(name = "CTPV_NM", length = 40)
    private String ctpvNm;

    @Column(name = "SGG_NM", length = 40)
    private String sggNm;

    @Column(name = "EMD_NM", length = 30)
    private String emdNm;

    @Column(name = "LI_NM", length = 40)
    private String liNm;

    @Column(name = "PPLTN_CNT")
    private Long ppltnCnt;

    @Column(name = "LPCD_CN", length = 100)
    private String lpcdCn;

    @Column(name = "DMD_QNT_VL")
    private Long dmdQntVl;

    @Column(name = "SPLY_PSBLQY_VL")
    private Long splyPsblqyVl;

    @Column(name = "OVSHRTS_QNT_VL")
    private Long ovshrtsQntVl;

    @Column(name = "TOT_PUB_GWEL_CNT")
    private Long totPubGwelCnt;

    @Column(name = "USE_PUB_GWEL_CNT")
    private Long usePubGwelCnt;
}
