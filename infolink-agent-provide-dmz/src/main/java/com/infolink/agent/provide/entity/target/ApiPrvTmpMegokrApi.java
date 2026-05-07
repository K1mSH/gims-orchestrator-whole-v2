package com.infolink.agent.provide.entity.target;

import lombok.*;
import org.hibernate.annotations.Comment;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * MEGOKR 수질검사결과 임시 테이블 제공용 (Type A — A7)
 *
 * 레거시: MEGOKR selectNgw04_01 (수질검사결과 TMP)
 * 원본: Oracle NGW.TMP_MEGOKR_API (149컬럼, 이미 PIVOT 완료된 임시 테이블)
 *
 * ── A7 특이점 ──
 * Oracle 원본의 자연키 이름이 "SN" — 다른 provide 엔티티의 통일 PK 필드명(sn IDENTITY)과 충돌.
 * 본 엔티티만 예외로 PG PK 필드명을 "id" 로 사용.
 *  - id: PG 자동채번 IDENTITY (제공 응답에 쓰이지 않음, 내부 식별용)
 *  - sn: Oracle 원본 SN 값 그대로 저장 (레거시 응답의 SN 값 보존용)
 * 동일한 이유로 YAML 에서 exclude-insert-columns: ["id"] 지정 — 기본 [id,sn] 오버라이드하여 sn 을 INSERT 포함.
 *
 * Oracle 원본에 공식 PK 제약 없음 (149컬럼 전부 NULLABLE). 실 데이터상 SN 유일+NOT NULL 조건 만족.
 * 추적용 source_refs 는 YAML primary-key: SN 으로 구성됨.
 */
@Entity
@Table(name = "api_prv_tmp_megokr_api",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_api_prv_tmp_megokr_api_source_refs",
           columnNames = {"source_refs"}))
@org.hibernate.annotations.Table(appliesTo = "api_prv_tmp_megokr_api",
       comment = "MEGOKR 수질검사결과 임시 테이블 제공 (PIVOT 완료)")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiPrvTmpMegokrApi {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    @Comment("PG 일련번호 (자동채번, 내부 식별용)")
    private Long id;

    @Column(name = "sn")
    @Comment("일련번호 (Oracle 원본 SN 값)")
    private Long sn;

    @Column(name = "josacode", length = 3)
    @Comment("조사코드")
    private String josacode;

    @Column(name = "qltwtr_inspct_sn")
    @Comment("수질검사일련번호")
    private Long qltwtrInspctSn;

    @Column(name = "gennum")
    @Comment("관정일련번호")
    private Long gennum;

    @Column(name = "invstg_year", length = 4)
    @Comment("조사연도")
    private String invstgYear;

    @Column(name = "odr")
    @Comment("순번")
    private Long odr;

    @Column(name = "dph_cl_code", length = 1)
    @Comment("심도분류코드")
    private String dphClCode;

    @Column(name = "dph_value")
    @Comment("심도값")
    private Long dphValue;

    @Column(name = "watsmp_de", length = 8)
    @Comment("채수일자")
    private String watsmpDe;

    @Column(name = "qltwtr_inspct_de", length = 8)
    @Comment("수질검사일자")
    private String qltwtrInspctDe;

    @Column(name = "dta_input_de", length = 8)
    @Comment("자료입력일자")
    private String dtaInputDe;

    @Column(name = "dcsn_de", length = 8)
    @Comment("확정일자")
    private String dcsnDe;

    @Column(name = "frst_regist_dt")
    @Comment("최초등록일시")
    private LocalDateTime frstRegistDt;

    @Column(name = "last_change_dt")
    @Comment("최종변경일시")
    private LocalDateTime lastChangeDt;

    @Column(name = "ugrwtr_prpos_code", length = 2)
    @Comment("지하수용도코드")
    private String ugrwtrPrposCode;

    @Column(name = "drnk_at", length = 1)
    @Comment("음용여부")
    private String drnkAt;

    @Column(name = "ugrwtr_wqn_input_instt_code", length = 5)
    @Comment("지하수수질입력기관코드")
    private String ugrwtrWqnInputInsttCode;

    @Column(name = "qltwtr_inspct_imprty_resn_ctnt", length = 4000)
    @Comment("수질검사불가사유내용")
    private String qltwtrInspctImprtyResnCtnt;

    @Column(name = "brtc_nm", length = 40)
    @Comment("시도명")
    private String brtcNm;

    @Column(name = "sigun_nm", length = 508)
    @Comment("시군구명")
    private String sigunNm;

    @Column(name = "emd_nm", length = 40)
    @Comment("읍면동명")
    private String emdNm;

    @Column(name = "li_nm", length = 40)
    @Comment("리명")
    private String liNm;

    @Column(name = "addr", length = 1000)
    @Comment("주소")
    private String addr;

    @Column(name = "pubwell_at", length = 1)
    @Comment("공공관정여부")
    private String pubwellAt;

    @Column(name = "wt_tot_col_cnts", length = 100)
    @Comment("수질항목 WT_TOT_COL_CNTS (일반세균수)")
    private String wtTotColCnts;

    @Column(name = "wt_tot_clf", length = 100)
    @Comment("수질항목 WT_TOT_CLF (총대장균군)")
    private String wtTotClf;

    @Column(name = "wt_fcl_cfs", length = 100)
    @Comment("수질항목 WT_FCL_CFS (분원성대장균군)")
    private String wtFclCfs;

    @Column(name = "wt_esc_col", length = 100)
    @Comment("수질항목 WT_ESC_COL (대장균)")
    private String wtEscCol;

    @Column(name = "wt_plb", length = 100)
    @Comment("수질항목 WT_PLB (납)")
    private String wtPlb;

    @Column(name = "wt_flr", length = 100)
    @Comment("수질항목 WT_FLR (불소)")
    private String wtFlr;

    @Column(name = "wt_asn", length = 100)
    @Comment("수질항목 WT_ASN (비소)")
    private String wtAsn;

    @Column(name = "wt_sln", length = 100)
    @Comment("수질항목 WT_SLN (셀레늄)")
    private String wtSln;

    @Column(name = "wt_hdg", length = 100)
    @Comment("수질항목 WT_HDG (수소이온농도)")
    private String wtHdg;

    @Column(name = "wt_cya", length = 100)
    @Comment("수질항목 WT_CYA (시안)")
    private String wtCya;

    @Column(name = "wt_amn_ntg", length = 100)
    @Comment("수질항목 WT_AMN_NTG (암모니아성질소)")
    private String wtAmnNtg;

    @Column(name = "wt_ntr_ntg", length = 100)
    @Comment("수질항목 WT_NTR_NTG (질산성질소)")
    private String wtNtrNtg;

    @Column(name = "wt_cdm", length = 100)
    @Comment("수질항목 WT_CDM (카드뮴)")
    private String wtCdm;

    @Column(name = "wt_bor", length = 100)
    @Comment("수질항목 WT_BOR (붕소)")
    private String wtBor;

    @Column(name = "wt_chr", length = 100)
    @Comment("수질항목 WT_CHR (크롬)")
    private String wtChr;

    @Column(name = "wt_pen", length = 100)
    @Comment("수질항목 WT_PEN")
    private String wtPen;

    @Column(name = "wt_dzn", length = 100)
    @Comment("수질항목 WT_DZN")
    private String wtDzn;

    @Column(name = "wt_prt", length = 100)
    @Comment("수질항목 WT_PRT")
    private String wtPrt;

    @Column(name = "wt_fnt", length = 100)
    @Comment("수질항목 WT_FNT")
    private String wtFnt;

    @Column(name = "wt_cbr", length = 100)
    @Comment("수질항목 WT_CBR")
    private String wtCbr;

    @Column(name = "wt_111_tce", length = 100)
    @Comment("수질항목 WT_111_TCE")
    private String wt111Tce;

    @Column(name = "wt_pce", length = 100)
    @Comment("수질항목 WT_PCE")
    private String wtPce;

    @Column(name = "wt_tce", length = 100)
    @Comment("수질항목 WT_TCE")
    private String wtTce;

    @Column(name = "wt_dcm", length = 100)
    @Comment("수질항목 WT_DCM")
    private String wtDcm;

    @Column(name = "wt_bez", length = 100)
    @Comment("수질항목 WT_BEZ")
    private String wtBez;

    @Column(name = "wt_tle", length = 100)
    @Comment("수질항목 WT_TLE")
    private String wtTle;

    @Column(name = "wt_ebz", length = 100)
    @Comment("수질항목 WT_EBZ")
    private String wtEbz;

    @Column(name = "wt_csl", length = 100)
    @Comment("수질항목 WT_CSL")
    private String wtCsl;

    @Column(name = "wt_011_dre", length = 100)
    @Comment("수질항목 WT_011_DRE")
    private String wt011Dre;

    @Column(name = "wt_ctc", length = 100)
    @Comment("수질항목 WT_CTC")
    private String wtCtc;

    @Column(name = "wt_012_dbr_003_crp", length = 100)
    @Comment("수질항목 WT_012_DBR_003_CRP")
    private String wt012Dbr003Crp;

    @Column(name = "wt_014_dox", length = 100)
    @Comment("수질항목 WT_014_DOX")
    private String wt014Dox;

    @Column(name = "wt_hdn", length = 100)
    @Comment("수질항목 WT_HDN (경도)")
    private String wtHdn;

    @Column(name = "wt_ppc", length = 100)
    @Comment("수질항목 WT_PPC")
    private String wtPpc;

    @Column(name = "wt_sml", length = 100)
    @Comment("수질항목 WT_SML")
    private String wtSml;

    @Column(name = "wt_fev", length = 100)
    @Comment("수질항목 WT_FEV")
    private String wtFev;

    @Column(name = "wt_cop", length = 100)
    @Comment("수질항목 WT_COP (구리)")
    private String wtCop;

    @Column(name = "wt_cmc", length = 100)
    @Comment("수질항목 WT_CMC")
    private String wtCmc;

    @Column(name = "wt_dtg", length = 100)
    @Comment("수질항목 WT_DTG")
    private String wtDtg;

    @Column(name = "wt_hid", length = 100)
    @Comment("수질항목 WT_HID")
    private String wtHid;

    @Column(name = "wt_zic", length = 100)
    @Comment("수질항목 WT_ZIC")
    private String wtZic;

    @Column(name = "wt_cri", length = 100)
    @Comment("수질항목 WT_CRI")
    private String wtCri;

    @Column(name = "wt_evr", length = 100)
    @Comment("수질항목 WT_EVR")
    private String wtEvr;

    @Column(name = "wt_ste", length = 100)
    @Comment("수질항목 WT_STE")
    private String wtSte;

    @Column(name = "wt_mgn", length = 100)
    @Comment("수질항목 WT_MGN")
    private String wtMgn;

    @Column(name = "wt_tbd", length = 100)
    @Comment("수질항목 WT_TBD (탁도)")
    private String wtTbd;

    @Column(name = "wt_sai", length = 100)
    @Comment("수질항목 WT_SAI")
    private String wtSai;

    @Column(name = "wt_alm", length = 100)
    @Comment("수질항목 WT_ALM")
    private String wtAlm;

    @Column(name = "wt_ecd", length = 100)
    @Comment("수질항목 WT_ECD (전기전도도)")
    private String wtEcd;

    @Column(name = "wt_ogp", length = 100)
    @Comment("수질항목 WT_OGP")
    private String wtOgp;

    @Column(name = "wt_006_chr", length = 100)
    @Comment("수질항목 WT_006_CHR")
    private String wt006Chr;

    @Column(name = "wt_hid_lbt", length = 100)
    @Comment("수질항목 WT_HID_LBT")
    private String wtHidLbt;

    @Column(name = "wt_tds", length = 100)
    @Comment("수질항목 WT_TDS")
    private String wtTds;

    @Column(name = "wt_dso", length = 100)
    @Comment("수질항목 WT_DSO")
    private String wtDso;

    @Column(name = "wt_orp", length = 100)
    @Comment("수질항목 WT_ORP")
    private String wtOrp;

    @Column(name = "wt_ehc", length = 100)
    @Comment("수질항목 WT_EHC")
    private String wtEhc;

    @Column(name = "wt_trt", length = 100)
    @Comment("수질항목 WT_TRT")
    private String wtTrt;

    @Column(name = "wt_ntr", length = 100)
    @Comment("수질항목 WT_NTR")
    private String wtNtr;

    @Column(name = "wt_kal", length = 100)
    @Comment("수질항목 WT_KAL")
    private String wtKal;

    @Column(name = "wt_cal", length = 100)
    @Comment("수질항목 WT_CAL (칼슘)")
    private String wtCal;

    @Column(name = "wt_mgs", length = 100)
    @Comment("수질항목 WT_MGS (마그네슘)")
    private String wtMgs;

    @Column(name = "wt_clr", length = 100)
    @Comment("수질항목 WT_CLR (색도)")
    private String wtClr;

    @Column(name = "wt_bbn", length = 100)
    @Comment("수질항목 WT_BBN")
    private String wtBbn;

    @Column(name = "wt_cai", length = 100)
    @Comment("수질항목 WT_CAI")
    private String wtCai;

    @Column(name = "wt_nti", length = 100)
    @Comment("수질항목 WT_NTI")
    private String wtNti;

    @Column(name = "wt_snt", length = 100)
    @Comment("수질항목 WT_SNT")
    private String wtSnt;

    @Column(name = "wt_brm", length = 100)
    @Comment("수질항목 WT_BRM")
    private String wtBrm;

    @Column(name = "wt_bru", length = 100)
    @Comment("수질항목 WT_BRU")
    private String wtBru;

    @Column(name = "wt_atm", length = 100)
    @Comment("수질항목 WT_ATM")
    private String wtAtm;

    @Column(name = "wt_slc", length = 100)
    @Comment("수질항목 WT_SLC")
    private String wtSlc;

    @Column(name = "wt_ltu", length = 100)
    @Comment("수질항목 WT_LTU")
    private String wtLtu;

    @Column(name = "wt_mbd", length = 100)
    @Comment("수질항목 WT_MBD")
    private String wtMbd;

    @Column(name = "wt_vnd", length = 100)
    @Comment("수질항목 WT_VND")
    private String wtVnd;

    @Column(name = "wt_gmn", length = 100)
    @Comment("수질항목 WT_GMN")
    private String wtGmn;

    @Column(name = "wt_cpe", length = 100)
    @Comment("수질항목 WT_CPE")
    private String wtCpe;

    @Column(name = "wt_nke", length = 100)
    @Comment("수질항목 WT_NKE")
    private String wtNke;

    @Column(name = "wt_epn", length = 100)
    @Comment("수질항목 WT_EPN")
    private String wtEpn;

    @Column(name = "wt_pta", length = 100)
    @Comment("수질항목 WT_PTA")
    private String wtPta;

    @Column(name = "wt_mst", length = 100)
    @Comment("수질항목 WT_MST")
    private String wtMst;

    @Column(name = "wt_crf", length = 100)
    @Comment("수질항목 WT_CRF")
    private String wtCrf;

    @Column(name = "wt_012_dre", length = 100)
    @Comment("수질항목 WT_012_DRE")
    private String wt012Dre;

    @Column(name = "wt_toc", length = 100)
    @Comment("수질항목 WT_TOC (총유기탄소)")
    private String wtToc;

    @Column(name = "wt_btr", length = 100)
    @Comment("수질항목 WT_BTR")
    private String wtBtr;

    @Column(name = "wt_mbe", length = 100)
    @Comment("수질항목 WT_MBE")
    private String wtMbe;

    @Column(name = "wt_ssc", length = 100)
    @Comment("수질항목 WT_SSC")
    private String wtSsc;

    @Column(name = "wt_sdm", length = 100)
    @Comment("수질항목 WT_SDM")
    private String wtSdm;

    @Column(name = "wt_smn", length = 100)
    @Comment("수질항목 WT_SMN")
    private String wtSmn;

    @Column(name = "wt_sgl", length = 100)
    @Comment("수질항목 WT_SGL")
    private String wtSgl;

    @Column(name = "wt_ahp", length = 100)
    @Comment("수질항목 WT_AHP")
    private String wtAhp;

    @Column(name = "wt_yne", length = 100)
    @Comment("수질항목 WT_YNE")
    private String wtYne;

    @Column(name = "wt_ntn", length = 100)
    @Comment("수질항목 WT_NTN")
    private String wtNtn;

    @Column(name = "wt_coi", length = 100)
    @Comment("수질항목 WT_COI")
    private String wtCoi;

    @Column(name = "wt_cpm", length = 100)
    @Comment("수질항목 WT_CPM")
    private String wtCpm;

    @Column(name = "wt_ctm", length = 100)
    @Comment("수질항목 WT_CTM")
    private String wtCtm;

    @Column(name = "wt_012_dcm", length = 100)
    @Comment("수질항목 WT_012_DCM")
    private String wt012Dcm;

    @Column(name = "wt_mtb", length = 100)
    @Comment("수질항목 WT_MTB")
    private String wtMtb;

    @Column(name = "wt_zcm", length = 100)
    @Comment("수질항목 WT_ZCM")
    private String wtZcm;

    @Column(name = "wt_mgm", length = 100)
    @Comment("수질항목 WT_MGM")
    private String wtMgm;

    @Column(name = "wt_mbm", length = 100)
    @Comment("수질항목 WT_MBM")
    private String wtMbm;

    @Column(name = "wt_stm", length = 100)
    @Comment("수질항목 WT_STM")
    private String wtStm;

    @Column(name = "wt_bam", length = 100)
    @Comment("수질항목 WT_BAM")
    private String wtBam;

    @Column(name = "wt_bsm", length = 100)
    @Comment("수질항목 WT_BSM")
    private String wtBsm;

    @Column(name = "wt_anm", length = 100)
    @Comment("수질항목 WT_ANM")
    private String wtAnm;

    @Column(name = "wt_nnm", length = 100)
    @Comment("수질항목 WT_NNM")
    private String wtNnm;

    @Column(name = "wt_frm", length = 100)
    @Comment("수질항목 WT_FRM")
    private String wtFrm;

    @Column(name = "wt_tcl", length = 100)
    @Comment("수질항목 WT_TCL")
    private String wtTcl;

    @Column(name = "wt_tcm", length = 100)
    @Comment("수질항목 WT_TCM")
    private String wtTcm;

    @Column(name = "wt_mtm", length = 100)
    @Comment("수질항목 WT_MTM")
    private String wtMtm;

    @Column(name = "wt_thm", length = 100)
    @Comment("수질항목 WT_THM")
    private String wtThm;

    @Column(name = "wt_ops", length = 100)
    @Comment("수질항목 WT_OPS")
    private String wtOps;

    @Column(name = "wt_dro", length = 100)
    @Comment("수질항목 WT_DRO")
    private String wtDro;

    @Column(name = "wt_grc", length = 100)
    @Comment("수질항목 WT_GRC")
    private String wtGrc;

    @Column(name = "wt_cbt", length = 100)
    @Comment("수질항목 WT_CBT")
    private String wtCbt;

    @Column(name = "wt_cbd", length = 100)
    @Comment("수질항목 WT_CBD")
    private String wtCbd;

    @Column(name = "wt_psp", length = 100)
    @Comment("수질항목 WT_PSP")
    private String wtPsp;

    @Column(name = "wt_amn", length = 100)
    @Comment("수질항목 WT_AMN")
    private String wtAmn;

    @Column(name = "wt_hgs", length = 100)
    @Comment("수질항목 WT_HGS")
    private String wtHgs;

    @Column(name = "wt_scd", length = 100)
    @Comment("수질항목 WT_SCD")
    private String wtScd;

    @Column(name = "wt_ami", length = 100)
    @Comment("수질항목 WT_AMI")
    private String wtAmi;

    @Column(name = "wt_tbc", length = 100)
    @Comment("수질항목 WT_TBC")
    private String wtTbc;

    @Column(name = "wt_urn", length = 100)
    @Comment("수질항목 WT_URN (우라늄)")
    private String wtUrn;

    @Column(name = "wt_rdn", length = 100)
    @Comment("수질항목 WT_RDN (라돈)")
    private String wtRdn;

    @Column(name = "wt_fap", length = 100)
    @Comment("수질항목 WT_FAP")
    private String wtFap;

    @Column(name = "wt_nai", length = 100)
    @Comment("수질항목 WT_NAI")
    private String wtNai;

    @Column(name = "wt_wtl", length = 100)
    @Comment("수질항목 WT_WTL")
    private String wtWtl;

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
