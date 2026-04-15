package com.gims.provider.entity.provide;

import lombok.*;
import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 수질측정망검사개요 제공용 테이블
 * 원본: TM_GD30301 → 표준화: TM_GD110301
 */
@Entity
@Table(name = "API_PRV_TM_GD110301")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiPrvTmGd110301 {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "SN")
    private Long sn;

    @Column(name = "WQ_INSP_SN", nullable = false)
    private Long wqInspSn;

    @Column(name = "GWEL_NO", nullable = false)
    private Long gwelNo;

    @Column(name = "EXMN_YR", length = 4)
    private String exmnYr;

    @Column(name = "CYCL")
    private Long cycl;

    @Column(name = "DPH_CLSF_CD", length = 1)
    private String dphClsfCd;

    @Column(name = "DPH_VL")
    private Long dphVl;

    @Column(name = "WTSMP_YMD", length = 8)
    private String wtSmpYmd;

    @Column(name = "WQ_INSP_YMD", length = 8)
    private String wqInspYmd;

    @Column(name = "DATA_INPT_YMD", length = 8)
    private String dataInptYmd;

    @Column(name = "CFMTN_YMD", length = 8)
    private String cfmtnYmd;

    @Column(name = "FRST_REG_DT")
    private LocalDateTime frstRegDt;

    @Column(name = "LAST_CHG_DT")
    private LocalDateTime lastChgDt;

    @Column(name = "UGWTR_USG_CD", length = 2)
    private String ugwtrUsgCd;

    @Column(name = "DKPP_YN", length = 1)
    private String dkppYn;

    @Column(name = "UGWTR_WQMN_INPT_INST_CD", length = 5)
    private String ugwtrWqmnInptInstCd;

    @Column(name = "WQ_INSP_IMPS_RSN_CN", length = 4000)
    private String wqInspImpsRsnCn;

    @Column(name = "CTPV_NM", length = 40)
    private String ctpvNm;

    @Column(name = "SGG_NM", length = 30)
    private String sggNm;

    @Column(name = "EMD_NM", length = 30)
    private String emdNm;

    @Column(name = "LI_NM", length = 40)
    private String liNm;

    @Column(name = "ADDR", length = 250)
    private String addr;

    @Column(name = "PUB_GWEL_YN", length = 1)
    private String pubGwelYn;
}
