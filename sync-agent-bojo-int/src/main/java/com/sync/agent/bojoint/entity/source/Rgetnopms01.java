package com.sync.agent.bojoint.entity.source;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import javax.persistence.*;
import lombok.*;

@Entity
@Table(name = "RGETNOPMS01")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Rgetnopms01 {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private String id;

    @Column(name = "REL_TRANS_CGG_CODE", length = 7)
    private String relTransCggCode;

    @Column(name = "PERM_NT_NO", length = 10)
    private String permNtNo;

    @Column(name = "JGONG_NT_SNO")
    private Long jgongNtSno;

    @Column(name = "OP_UPCH_REGNUM", length = 36)
    private String opUpchRegnum;

    @Column(name = "OP_UPCH_SNO", length = 3)
    private String opUpchSno;

    @Column(name = "APPL_DOC_GBN_CODE", length = 2)
    private String applDocGbnCode;

    @Column(name = "DIG_DPH")
    private BigDecimal digDph;

    @Column(name = "DIG_DIAM")
    private BigDecimal digDiam;

    @Column(name = "FRW_PLN_QUA")
    private BigDecimal frwPlnQua;

    @Column(name = "ND_QT")
    private Long ndQt;

    @Column(name = "DYN_EQN_HRP")
    private BigDecimal dynEqnHrp;

    @Column(name = "PIPE_DIAM")
    private BigDecimal pipeDiam;

    @Column(name = "ESB_DPH")
    private BigDecimal esbDph;

    @Column(name = "RWT_CAP")
    private BigDecimal rwtCap;

    @Column(name = "UWATER_SRV", length = 30)
    private String uwaterSrv;

    @Column(name = "UWATER_SRV_CODE", length = 1)
    private String uwaterSrvCode;

    @Column(name = "UWATER_DTL_SRV_CODE", length = 2)
    private String uwaterDtlSrvCode;

    @Column(name = "UWATER_POTA_YN", length = 1)
    private String uwaterPotaYn;

    @Column(name = "UWATER_DAY_USE_QUA")
    private BigDecimal uwaterDayUseQua;

    @Column(name = "CGONG_YMD", length = 8)
    private String cgongYmd;

    @Column(name = "JGONG_YMD", length = 8)
    private String jgongYmd;

    @Column(name = "ETC_DTL", length = 1000)
    private String etcDtl;

    @Column(name = "JGONG_APPL_YMD", length = 8)
    private String jgongApplYmd;

    @Column(name = "JGONG_TKIT_MG_NO", length = 100)
    private String jgongTkitMgNo;

    @Column(name = "JGEV_ISSUE_YMD", length = 8)
    private String jgevIssueYmd;

    @Column(name = "OGG_YN", length = 1)
    private String oggYn;

    @Column(name = "CHSU_EQN_YN", length = 1)
    private String chsuEqnYn;

    @Column(name = "GRWT_YN", length = 1)
    private String grwtYn;

    @Column(name = "UPS_PROT_HOLE_YN", length = 1)
    private String upsProtHoleYn;

    @Column(name = "WTLV_MSM_KWAN_YN", length = 1)
    private String wtlvMsmKwanYn;

    @Column(name = "CSI_YN", length = 1)
    private String csiYn;

    @Column(name = "ELEC_TEMPE_YN", length = 1)
    private String elecTempeYn;

    @Column(name = "LID_ESB_YN", length = 1)
    private String lidEsbYn;

    @Column(name = "FACIL_DETAIL_DTL", length = 1000)
    private String facilDetailDtl;

    @Column(name = "JGFACIL_SORT_CODE", length = 1)
    private String jgfacilSortCode;

    @Column(name = "UPS_PROT_HOLE_BRE")
    private BigDecimal upsProtHoleBre;

    @Column(name = "UPPRHO_UPS_HGT")
    private BigDecimal upprhoUpsHgt;

    @Column(name = "UPS_PROT_HOLE_HGT")
    private BigDecimal upsProtHoleHgt;

    @Column(name = "WTLV_MSM_KWAN")
    private BigDecimal wtlvMsmKwan;

    @Column(name = "CSI_UPS_HGT")
    private BigDecimal csiUpsHgt;

    @Column(name = "GRWT_THIK")
    private BigDecimal grwtThik;

    @Column(name = "GRWT_DPH")
    private BigDecimal grwtDph;

    @Column(name = "CHA_DEPT_NM", length = 60)
    private String chaDeptNm;

    @Column(name = "FIRST_REG_DTHR")
    private LocalDateTime firstRegDthr;

    @Column(name = "LAST_MOD_DTHR")
    private LocalDateTime lastModDthr;

    @Column(name = "BF_JGONG_YMD", length = 10)
    private String bfJgongYmd;

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
