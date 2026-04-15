package com.gims.provider.entity.provide;

import lombok.*;
import javax.persistence.*;

/**
 * 드림서비스 공공관정 제공용 테이블
 * 원본: WT_DREAM_PERMWELL_PUBLIC → 표준화: TM_GD112002
 */
@Entity
@Table(name = "API_PRV_TM_GD112002")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiPrvTmGd112002 {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "SN")
    private Long sn;

    @Column(name = "LINK_TRSM_SGG_CD", length = 7)
    private String linkTrsmSggCd;

    @Column(name = "PRMSN_DCLR_NO", length = 30)
    private String prmsnDclrNo;

    @Column(name = "PRMSN_DCLR_FRM_CD", length = 1)
    private String prmsnDclrFrmCd;

    @Column(name = "YR_SE", length = 4)
    private String yrSe;

    @Column(name = "RGN_CD", length = 10)
    private String rgnCd;

    @Column(name = "CTPV_NM", length = 40)
    private String ctpvNm;

    @Column(name = "SGG_NM", length = 40)
    private String sggNm;

    @Column(name = "EMD_NM", length = 30)
    private String emdNm;

    @Column(name = "LI_NM", length = 40)
    private String liNm;

    @Column(name = "MTN", length = 1)
    private String mtn;

    @Column(name = "BNJ", length = 20)
    private String bnj;

    @Column(name = "HO", length = 10)
    private String ho;

    @Column(name = "UGWTR_USG", length = 20)
    private String ugwtrUsg;

    @Column(name = "UGWTR_DTL_USG_CD", length = 2)
    private String ugwtrDtlUsgCd;

    @Column(name = "DKPP_YN", length = 1)
    private String dkppYn;

    @Column(name = "LAT_DG", length = 20)
    private String latDg;

    @Column(name = "LAT_MI", length = 20)
    private String latMi;

    @Column(name = "LAT_SS", length = 20)
    private String latSs;

    @Column(name = "LOT_DG", length = 20)
    private String lotDg;

    @Column(name = "LOT_MI", length = 20)
    private String lotMi;

    @Column(name = "LOT_SS", length = 20)
    private String lotSs;

    @Column(name = "DPH_VL")
    private Long dphVl;

    @Column(name = "DGG_CALBR")
    private Long dggCalbr;

    @Column(name = "DELP_DIA")
    private Long delpDia;

    @Column(name = "PUMP_HRSPW")
    private Long pumpHrspw;

    @Column(name = "WTRIT_PLAN_QTR")
    private Long wtritPlanQtr;

    @Column(name = "WPMP_ABLT")
    private Long wpmpAblt;

    @Column(name = "YR_USQTY")
    private Long yrUsqty;

    @Column(name = "PUB_PRVTEST_SE", length = 1)
    private String pubPrvtestSe;

    @Column(name = "WQ_INSP_YMD", length = 8)
    private String wqInspYmd;

    @Column(name = "WQ_INSP_RSLT", length = 100)
    private String wqInspRslt;

    @Column(name = "PNU", length = 19)
    private String pnu;

    @Column(name = "XCRD")
    private Long xcrd;

    @Column(name = "YCRD")
    private Long ycrd;
}
