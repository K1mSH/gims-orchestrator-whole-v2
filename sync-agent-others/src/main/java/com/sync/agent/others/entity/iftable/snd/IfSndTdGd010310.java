package com.sync.agent.others.entity.iftable.snd;

import lombok.*;
import org.hibernate.annotations.Comment;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * IF_SND 약수터 수질검사결과 엔티티.
 *
 * <p>SND 파이프라인에서 api_collector DB의 약수터 수질 데이터를 IF_SND 테이블로 추출할 때 사용된다.</p>
 *
 * <p>테이블: {@code if_snd_td_gd010310}</p>
 */
@Entity
@Table(name = "if_snd_td_gd010310",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_if_snd_td_gd010310_source_refs",
           columnNames = {"source_refs"}
       ),
       indexes = @Index(name = "idx_if_snd_td_gd010310_exec_id", columnList = "execution_id"))
@org.hibernate.annotations.Table(appliesTo = "if_snd_td_gd010310", comment = "IF_SND 약수터수질검사")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IfSndTdGd010310 {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sn")
    @Comment("일련번호 (PK)")
    private Long sn;

    // ========== 기본 정보 ==========

    @Column(name = "brnch_no", length = 10)
    @Comment("지점번호")
    private String brnchNo;

    @Column(name = "brnch_std_cd", length = 20)
    @Comment("지점표준코드")
    private String brnchStdCd;

    @Column(name = "yr", length = 4)
    @Comment("연도")
    private String yr;

    @Column(name = "qtr", length = 10)
    @Comment("분기")
    private String qtr;

    @Column(name = "insp_yn", length = 20)
    @Comment("검사여부")
    private String inspYn;

    @Column(name = "un_insp_rsn", length = 500)
    @Comment("미검사사유")
    private String unInspRsn;

    @Column(name = "stblt_yn", length = 10)
    @Comment("적합여부")
    private String stbltYn;

    @Column(name = "stblt", length = 10)
    @Comment("적합")
    private String stblt;

    @Column(name = "icpt", length = 10)
    @Comment("부적합")
    private String icpt;

    // ========== 세균류 (11개) ==========

    @Column(name = "gnrl_germ_lowtmp", length = 20)
    @Comment("일반세균저온")
    private String gnrlGermLowtmp;

    @Column(name = "gnrl_germ_mesph", length = 20)
    @Comment("일반세균중온")
    private String gnrlGermMesph;

    @Column(name = "cfbctr", length = 20)
    @Comment("총대장균군")
    private String cfbctr;

    @Column(name = "clbcl", length = 20)
    @Comment("대장균")
    private String clbcl;

    @Column(name = "fcfs", length = 20)
    @Comment("분원성대장균군")
    private String fcfs;

    @Column(name = "fcstrcci", length = 20)
    @Comment("분원성연쇄상구균")
    private String fcstrcci;

    @Column(name = "paergns", length = 20)
    @Comment("녹농균")
    private String paergns;

    @Column(name = "slmn", length = 20)
    @Comment("살모넬라")
    private String slmn;

    @Column(name = "shigla", length = 20)
    @Comment("쉬겔라")
    private String shigla;

    @Column(name = "sfsra", length = 20)
    @Comment("아황산환원혐기성포자형성균")
    private String sfsra;

    @Column(name = "yersna", length = 20)
    @Comment("여시니아균")
    private String yersna;

    // ========== 중금속 (11개) ==========

    @Column(name = "pmbm", length = 20)
    @Comment("납")
    private String pmbm;

    @Column(name = "flrn", length = 20)
    @Comment("불소")
    private String flrn;

    @Column(name = "asnc", length = 20)
    @Comment("비소")
    private String asnc;

    @Column(name = "se", length = 20)
    @Comment("셀레늄")
    private String se;

    @Column(name = "mrcr", length = 20)
    @Comment("수은")
    private String mrcr;

    @Column(name = "cyn", length = 20)
    @Comment("시안")
    private String cyn;

    @Column(name = "chrm", length = 20)
    @Comment("크롬")
    private String chrm;

    @Column(name = "amng", length = 20)
    @Comment("암모니아성질소")
    private String amng;

    @Column(name = "ntng", length = 20)
    @Comment("질산성질소")
    private String ntng;

    @Column(name = "cdmm", length = 20)
    @Comment("카드뮴")
    private String cdmm;

    @Column(name = "bor", length = 20)
    @Comment("보론")
    private String bor;

    // ========== 유기화합물 (17개) ==========

    @Column(name = "bro3", length = 20)
    @Comment("브로메이트")
    private String bro3;

    @Column(name = "phnl", length = 20)
    @Comment("페놀")
    private String phnl;

    @Column(name = "dznn", length = 20)
    @Comment("다이아지논")
    private String dznn;

    @Column(name = "parat", length = 20)
    @Comment("파라티온")
    private String parat;

    @Column(name = "fenitro", length = 20)
    @Comment("페니트로티온")
    private String fenitro;

    @Column(name = "carbaryl", length = 20)
    @Comment("카바릴")
    private String carbaryl;

    @Column(name = "tcrn", length = 20)
    @Comment("트리클로로에탄")
    private String tcrn;

    @Column(name = "ttrt", length = 20)
    @Comment("테트라클로로에틸렌")
    private String ttrt;

    @Column(name = "tcrt", length = 20)
    @Comment("트리클로로에틸렌")
    private String tcrt;

    @Column(name = "dcmt", length = 20)
    @Comment("디클로로메탄")
    private String dcmt;

    @Column(name = "bnzn", length = 20)
    @Comment("벤젠")
    private String bnzn;

    @Column(name = "tln", length = 20)
    @Comment("톨루엔")
    private String tln;

    @Column(name = "etbz", length = 20)
    @Comment("에틸벤젠")
    private String etbz;

    @Column(name = "xln", length = 20)
    @Comment("자일렌")
    private String xln;

    @Column(name = "dcty", length = 20)
    @Comment("디클로로에틸렌")
    private String dcty;

    @Column(name = "ccl4", length = 20)
    @Comment("사염화탄소")
    private String ccl4;

    @Column(name = "dbcp", length = 20)
    @Comment("1,2-디브로모-3-클로로프로판")
    private String dbcp;

    // ========== 일반항목 (16개) ==========

    @Column(name = "diox14", length = 20)
    @Comment("다이옥산")
    private String diox14;

    @Column(name = "tds", length = 20)
    @Comment("경도")
    private String tds;

    @Column(name = "ptpm_cnsm_qnt", length = 20)
    @Comment("과망간산칼륨소비량")
    private String ptpmCnsmQnt;

    @Column(name = "smll", length = 20)
    @Comment("냄새")
    private String smll;

    @Column(name = "crmty", length = 25)
    @Comment("색도")
    private String crmty;

    @Column(name = "cppr", length = 20)
    @Comment("구리")
    private String cppr;

    @Column(name = "abs", length = 20)
    @Comment("알킬벤젠소디움설포네이트")
    private String abs;

    @Column(name = "ph", length = 10)
    @Comment("수소이온농도")
    private String ph;

    @Column(name = "zn", length = 20)
    @Comment("아연")
    private String zn;

    @Column(name = "crrd", length = 20)
    @Comment("염소이온")
    private String crrd;

    @Column(name = "iron", length = 20)
    @Comment("철")
    private String iron;

    @Column(name = "mngn", length = 20)
    @Comment("망간")
    private String mngn;

    @Column(name = "trbt", length = 20)
    @Comment("탁도")
    private String trbt;

    @Column(name = "eccbt_ion", length = 20)
    @Comment("황산이온")
    private String eccbtIon;

    @Column(name = "almn", length = 20)
    @Comment("알루미늄")
    private String almn;

    @Column(name = "uran", length = 20)
    @Comment("우라늄")
    private String uran;

    // ========== 결과 정보 ==========

    @Column(name = "icpt_artcl", length = 500)
    @Comment("부적합항목")
    private String icptArtcl;

    @Column(name = "icpt_actn_mttr", length = 500)
    @Comment("부적합조치사항")
    private String icptActnMttr;

    @Column(name = "wtsmp_ymd", length = 10)
    @Comment("채수일자")
    private String wtSmpYmd;

    // ========== IF 메타 컬럼 ==========

    @Column(name = "source_refs", columnDefinition = "TEXT")
    @Comment("원본 참조키 (UK)")
    private String sourceRefs;

    @Builder.Default
    @Column(name = "link_status", length = 20)
    @Comment("연계 상태 (PENDING/SUCCESS/FAILED)")
    private String linkStatus = "PENDING";

    @Column(name = "extracted_at")
    @Comment("추출 시각")
    private LocalDateTime extractedAt;

    @Column(name = "updated_at")
    @Comment("수정 시각")
    private LocalDateTime updatedAt;

    @Column(name = "execution_id")
    @Comment("처리 실행 ID")
    private String executionId;
}
