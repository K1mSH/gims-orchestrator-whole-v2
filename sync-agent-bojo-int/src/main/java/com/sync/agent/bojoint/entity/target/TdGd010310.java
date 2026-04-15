package com.sync.agent.bojoint.entity.target;

import javax.persistence.*;
import lombok.*;

/**
 * 약수터 수질검사결과 Target 엔티티.
 * 테이블: TD_GD010310
 * PK/UK 없음 (레거시 동일). SN은 JPA @Id용 IDENTITY.
 */
@Entity
@Table(name = "TD_GD010310")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TdGd010310 {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "SN")
    private Long sn;

    // ========== 기본 정보 ==========

    @Column(name = "BRNCH_NO", length = 10, nullable = false)
    private String brnchNo;

    @Column(name = "BRNCH_STD_CD", length = 20, nullable = false)
    private String brnchStdCd;

    @Column(name = "YR", length = 4)
    private String yr;

    @Column(name = "QTR", length = 10, nullable = false)
    private String qtr;

    @Column(name = "INSP_YN", length = 20, nullable = false)
    private String inspYn;

    @Column(name = "UN_INSP_RSN", length = 500, nullable = false)
    private String unInspRsn;

    @Column(name = "STBLT_YN", length = 10)
    private String stbltYn;

    @Column(name = "STBLT", length = 10, nullable = false)
    private String stblt;

    @Column(name = "ICPT", length = 10, nullable = false)
    private String icpt;

    // ========== 세균류 (11개) ==========

    @Column(name = "GNRL_GERM_LOWTMP", length = 20, nullable = false)
    private String gnrlGermLowtmp;

    @Column(name = "GNRL_GERM_MESPH", length = 20)
    private String gnrlGermMesph;

    @Column(name = "CFBCTR", length = 20)
    private String cfbctr;

    @Column(name = "CLBCL", length = 20)
    private String clbcl;

    @Column(name = "FCFS", length = 20)
    private String fcfs;

    @Column(name = "FCSTRCCI", length = 20)
    private String fcstrcci;

    @Column(name = "PAERGNS", length = 20)
    private String paergns;

    @Column(name = "SLMN", length = 20)
    private String slmn;

    @Column(name = "SHIGLA", length = 20)
    private String shigla;

    @Column(name = "SFSRA", length = 20)
    private String sfsra;

    @Column(name = "YERSNA", length = 20)
    private String yersna;

    // ========== 중금속 (11개) ==========

    @Column(name = "PMBM", length = 20)
    private String pmbm;

    @Column(name = "FLRN", length = 20)
    private String flrn;

    @Column(name = "ASNC", length = 20)
    private String asnc;

    @Column(name = "SE", length = 20)
    private String se;

    @Column(name = "MRCR", length = 20)
    private String mrcr;

    @Column(name = "CYN", length = 20)
    private String cyn;

    @Column(name = "CHRM", length = 20)
    private String chrm;

    @Column(name = "AMNG", length = 20)
    private String amng;

    @Column(name = "NTNG", length = 20)
    private String ntng;

    @Column(name = "CDMM", length = 20)
    private String cdmm;

    @Column(name = "BOR", length = 20)
    private String bor;

    // ========== 유기화합물 (17개) ==========

    @Column(name = "BRO3", length = 20)
    private String bro3;

    @Column(name = "PHNL", length = 20)
    private String phnl;

    @Column(name = "DZNN", length = 20)
    private String dznn;

    @Column(name = "PARAT", length = 20)
    private String parat;

    @Column(name = "FENITRO", length = 20)
    private String fenitro;

    @Column(name = "CARBARYL", length = 20)
    private String carbaryl;

    @Column(name = "TCRN", length = 20)
    private String tcrn;

    @Column(name = "TTRT", length = 20)
    private String ttrt;

    @Column(name = "TCRT", length = 20)
    private String tcrt;

    @Column(name = "DCMT", length = 20)
    private String dcmt;

    @Column(name = "BNZN", length = 20, nullable = false)
    private String bnzn;

    @Column(name = "TLN", length = 20)
    private String tln;

    @Column(name = "ETBZ", length = 20)
    private String etbz;

    @Column(name = "XLN", length = 20)
    private String xln;

    @Column(name = "DCTY", length = 20)
    private String dcty;

    @Column(name = "CCL4", length = 20)
    private String ccl4;

    @Column(name = "DBCP", length = 20)
    private String dbcp;

    // ========== 일반항목 (16개) ==========

    @Column(name = "DIOX14", length = 20)
    private String diox14;

    @Column(name = "TDS", length = 20)
    private String tds;

    @Column(name = "PTPM_CNSM_QNT", length = 20)
    private String ptpmCnsmQnt;

    @Column(name = "SMLL", length = 20)
    private String smll;

    @Column(name = "CRMTY", length = 25)
    private String crmty;

    @Column(name = "CPPR", length = 20)
    private String cppr;

    @Column(name = "ABS", length = 20)
    private String abs;

    @Column(name = "PH", length = 10)
    private String ph;

    @Column(name = "ZN", length = 20)
    private String zn;

    @Column(name = "CRRD", length = 20)
    private String crrd;

    @Column(name = "IRON", length = 20)
    private String iron;

    @Column(name = "MNGN", length = 20)
    private String mngn;

    @Column(name = "TRBT", length = 20)
    private String trbt;

    @Column(name = "ECCBT_ION", length = 20)
    private String eccbtIon;

    @Column(name = "ALMN", length = 20)
    private String almn;

    @Column(name = "URAN", length = 20)
    private String uran;

    // ========== 결과 정보 ==========

    @Column(name = "ICPT_ARTCL", length = 100)
    private String icptArtcl;

    @Column(name = "ICPT_ACTN_MTTR", length = 100)
    private String icptActnMttr;

    @Column(name = "WTSMP_YMD", length = 8, nullable = false)
    private String wtSmpYmd;

    // ========== 추적 메타 ==========

    @Column(name = "EXECUTION_ID")
    private String executionId;

    @Column(name = "SOURCE_REFS", length = 4000)
    private String sourceRefs;
}
