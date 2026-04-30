package com.sync.agent.bojoint.entity.target;

import javax.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

/**
 * 약수터 수질검사결과 Target 엔티티.
 * 테이블: TD_GD010310
 * PK/UK 없음 (레거시 동일). SN은 JPA @Id용 IDENTITY.
 */
@Entity
@Table(name = "TD_GD010310",
       indexes = @Index(name = "idx_td_gd010310_exec_id", columnList = "execution_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TdGd010310 {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "SN")
    @Comment("일련번호 (PK)")
    private Long sn;

    // ========== 기본 정보 ==========

    @Column(name = "BRNCH_NO", length = 10)
    @Comment("지점번호")
    private String brnchNo;

    @Column(name = "BRNCH_STD_CD", length = 20)
    @Comment("지점표준코드")
    private String brnchStdCd;

    @Column(name = "YR", length = 4)
    @Comment("연도")
    private String yr;

    @Column(name = "QTR", length = 10)
    @Comment("분기")
    private String qtr;

    @Column(name = "INSP_YN", length = 20)
    @Comment("검사여부")
    private String inspYn;

    @Column(name = "UN_INSP_RSN", length = 500)
    @Comment("미검사사유")
    private String unInspRsn;

    @Column(name = "STBLT_YN", length = 10)
    @Comment("적합여부")
    private String stbltYn;

    @Column(name = "STBLT", length = 10)
    @Comment("적합")
    private String stblt;

    @Column(name = "ICPT", length = 10)
    @Comment("부적합")
    private String icpt;

    // ========== 세균류 (11개) ==========

    @Column(name = "GNRL_GERM_LOWTMP", length = 20)
    @Comment("일반세균저온")
    private String gnrlGermLowtmp;

    @Column(name = "GNRL_GERM_MESPH", length = 20)
    @Comment("일반세균중온")
    private String gnrlGermMesph;

    @Column(name = "CFBCTR", length = 20)
    @Comment("총대장균군")
    private String cfbctr;

    @Column(name = "CLBCL", length = 20)
    @Comment("대장균")
    private String clbcl;

    @Column(name = "FCFS", length = 20)
    @Comment("분원성대장균군")
    private String fcfs;

    @Column(name = "FCSTRCCI", length = 20)
    @Comment("분원성연쇄상구균")
    private String fcstrcci;

    @Column(name = "PAERGNS", length = 20)
    @Comment("녹농균")
    private String paergns;

    @Column(name = "SLMN", length = 20)
    @Comment("살모넬라")
    private String slmn;

    @Column(name = "SHIGLA", length = 20)
    @Comment("쉬겔라")
    private String shigla;

    @Column(name = "SFSRA", length = 20)
    @Comment("아황산환원혐기성포자형성균")
    private String sfsra;

    @Column(name = "YERSNA", length = 20)
    @Comment("여시니아균")
    private String yersna;

    // ========== 중금속 (11개) ==========

    @Column(name = "PMBM", length = 20)
    @Comment("납")
    private String pmbm;

    @Column(name = "FLRN", length = 20)
    @Comment("불소")
    private String flrn;

    @Column(name = "ASNC", length = 20)
    @Comment("비소")
    private String asnc;

    @Column(name = "SE", length = 20)
    @Comment("셀레늄")
    private String se;

    @Column(name = "MRCR", length = 20)
    @Comment("수은")
    private String mrcr;

    @Column(name = "CYN", length = 20)
    @Comment("시안")
    private String cyn;

    @Column(name = "CHRM", length = 20)
    @Comment("크롬")
    private String chrm;

    @Column(name = "AMNG", length = 20)
    @Comment("암모니아성질소")
    private String amng;

    @Column(name = "NTNG", length = 20)
    @Comment("질산성질소")
    private String ntng;

    @Column(name = "CDMM", length = 20)
    @Comment("카드뮴")
    private String cdmm;

    @Column(name = "BOR", length = 20)
    @Comment("보론")
    private String bor;

    // ========== 유기화합물 (17개) ==========

    @Column(name = "BRO3", length = 20)
    @Comment("브로메이트")
    private String bro3;

    @Column(name = "PHNL", length = 20)
    @Comment("페놀")
    private String phnl;

    @Column(name = "DZNN", length = 20)
    @Comment("다이아지논")
    private String dznn;

    @Column(name = "PARAT", length = 20)
    @Comment("파라티온")
    private String parat;

    @Column(name = "FENITRO", length = 20)
    @Comment("페니트로티온")
    private String fenitro;

    @Column(name = "CARBARYL", length = 20)
    @Comment("카바릴")
    private String carbaryl;

    @Column(name = "TCRN", length = 20)
    @Comment("트리클로로에탄")
    private String tcrn;

    @Column(name = "TTRT", length = 20)
    @Comment("테트라클로로에틸렌")
    private String ttrt;

    @Column(name = "TCRT", length = 20)
    @Comment("트리클로로에틸렌")
    private String tcrt;

    @Column(name = "DCMT", length = 20)
    @Comment("디클로로메탄")
    private String dcmt;

    @Column(name = "BNZN", length = 20)
    @Comment("벤젠")
    private String bnzn;

    @Column(name = "TLN", length = 20)
    @Comment("톨루엔")
    private String tln;

    @Column(name = "ETBZ", length = 20)
    @Comment("에틸벤젠")
    private String etbz;

    @Column(name = "XLN", length = 20)
    @Comment("자일렌")
    private String xln;

    @Column(name = "DCTY", length = 20)
    @Comment("디클로로에틸렌")
    private String dcty;

    @Column(name = "CCL4", length = 20)
    @Comment("사염화탄소")
    private String ccl4;

    @Column(name = "DBCP", length = 20)
    @Comment("1,2-디브로모-3-클로로프로판")
    private String dbcp;

    // ========== 일반항목 (16개) ==========

    @Column(name = "DIOX14", length = 20)
    @Comment("다이옥산")
    private String diox14;

    @Column(name = "TDS", length = 20)
    @Comment("경도")
    private String tds;

    @Column(name = "PTPM_CNSM_QNT", length = 20)
    @Comment("과망간산칼륨소비량")
    private String ptpmCnsmQnt;

    @Column(name = "SMLL", length = 20)
    @Comment("냄새")
    private String smll;

    @Column(name = "CRMTY", length = 25)
    @Comment("색도")
    private String crmty;

    @Column(name = "CPPR", length = 20)
    @Comment("구리")
    private String cppr;

    @Column(name = "ABS", length = 20)
    @Comment("알킬벤젠소디움설포네이트")
    private String abs;

    @Column(name = "PH", length = 10)
    @Comment("수소이온농도")
    private String ph;

    @Column(name = "ZN", length = 20)
    @Comment("아연")
    private String zn;

    @Column(name = "CRRD", length = 20)
    @Comment("염소이온")
    private String crrd;

    @Column(name = "IRON", length = 20)
    @Comment("철")
    private String iron;

    @Column(name = "MNGN", length = 20)
    @Comment("망간")
    private String mngn;

    @Column(name = "TRBT", length = 20)
    @Comment("탁도")
    private String trbt;

    @Column(name = "ECCBT_ION", length = 20)
    @Comment("황산이온")
    private String eccbtIon;

    @Column(name = "ALMN", length = 20)
    @Comment("알루미늄")
    private String almn;

    @Column(name = "URAN", length = 20)
    @Comment("우라늄")
    private String uran;

    // ========== 결과 정보 ==========

    @Column(name = "ICPT_ARTCL", length = 500)
    @Comment("부적합항목")
    private String icptArtcl;

    @Column(name = "ICPT_ACTN_MTTR", length = 500)
    @Comment("부적합조치사항")
    private String icptActnMttr;

    @Column(name = "WTSMP_YMD", length = 10)
    @Comment("채수일자")
    private String wtSmpYmd;

    // ========== 추적 메타 ==========

    @Column(name = "EXECUTION_ID")
    @Comment("처리 실행 ID")
    private String executionId;

    @Column(name = "SOURCE_REFS", length = 4000)
    @Comment("원본 참조키")
    private String sourceRefs;
}
