package com.sync.agent.bojoint.entity.source;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import javax.persistence.*;
import lombok.*;

@Entity
@Table(name = "RGETNYCSG01")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Rgetnycsg01 {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "REL_TRANS_CGG_CODE", length = 7)
    private String relTransCggCode;

    @Column(name = "MW_TAKE_NO", length = 18)
    private String mwTakeNo;

    @Column(name = "UWATER_OVF_QUA")
    private BigDecimal uwaterOvfQua;

    @Column(name = "USE_PLN_QUA")
    private BigDecimal usePlnQua;

    @Column(name = "CGONG_PLAN_YMD", length = 8)
    private String cgongPlanYmd;

    @Column(name = "JGONG_PLAN_YMD", length = 8)
    private String jgongPlanYmd;

    @Column(name = "CSTW_BRF", length = 100)
    private String cstwBrf;

    @Column(name = "PLAN_OP_UPCH_REGNUM", length = 36)
    private String planOpUpchRegnum;

    @Column(name = "PLAN_OP_UPCH_SNO", length = 3)
    private String planOpUpchSno;

    @Column(name = "PLAN_OP_UPCH_NM", length = 60)
    private String planOpUpchNm;

    @Column(name = "PLAN_OP_UPCH_REP_NM", length = 60)
    private String planOpUpchRepNm;

    @Column(name = "PLAN_OP_UPCH_TELNO", length = 30)
    private String planOpUpchTelno;

    @Column(name = "PLAN_OP_UPCH_ADDR", length = 250)
    private String planOpUpchAddr;

    @Column(name = "WATER_LEAK_PREACT", length = 500)
    private String waterLeakPreact;

    @Column(name = "CST_LOC_ADDR", length = 500)
    private String cstLocAddr;

    @Column(name = "FACIL_GBN", length = 100)
    private String facilGbn;

    @Column(name = "FIRE_USE_QUA")
    private Long fireUseQua;

    @Column(name = "CLS_USE_QUA")
    private Long clsUseQua;

    @Column(name = "LSAR_USE_QUA")
    private Long lsarUseQua;

    @Column(name = "CST_USE_QUA")
    private Long cstUseQua;

    @Column(name = "TOIL_USE_QUA")
    private Long toilUseQua;

    @Column(name = "PARK_USE_QUA")
    private Long parkUseQua;

    @Column(name = "AIRCON_HEATNG_USE_QUA")
    private Long airconHeatngUseQua;

    @Column(name = "IDS_USE_QUA")
    private Long idsUseQua;

    @Column(name = "AGC_USE_QUA")
    private Long agcUseQua;

    @Column(name = "FSHRY_USE_QUA")
    private Long fshryUseQua;

    @Column(name = "ETC_USE_QUA")
    private Long etcUseQua;

    @Column(name = "ETC_USE_CTN", length = 1000)
    private String etcUseCtn;

    @Column(name = "SOURCE_REFS", length = 4000)
    private String sourceRefs;

    @Column(name = "LINK_STATUS", length = 20)
    @Builder.Default
    private String linkStatus = "PENDING";

    @Column(name = "EXTRACTED_AT")
    private LocalDateTime extractedAt;

    @Column(name = "UPDATED_AT")
    private LocalDateTime updatedAt;

    @Column(name = "EXECUTION_ID")
    private String executionId;

}
