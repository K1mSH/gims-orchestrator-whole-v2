package com.infolink.collector.entity;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.Comment;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 제주 이용시설 허가신고정보
 * - 원본: RGETNPMMS01 (새올 DB)
 * - 레거시: RgetstgmsProgram → selectJejuUse.json → MERGE INTO RGETNPMMS01
 * - PK: PERM_NT_NO (허가신고번호)
 */
@Entity
@Table(name = "rgetnpmms01")
@org.hibernate.annotations.Table(appliesTo = "rgetnpmms01", comment = "제주 이용시설 허가신고정보")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Rgetnpmms01 {

    @Id
    @Column(name = "perm_nt_no", length = 50)
    @Comment("허가신고번호 (PK)")
    private String permNtNo;

    @Column(name = "rel_trans_cgg_code", length = 10)
    @Comment("시군구코드 (제주시=6510000, 서귀포시=6520000)")
    private String relTransCggCode;

    @Column(name = "sf_team_code", length = 10)
    @Comment("담당팀코드 (= rel_trans_cgg_code)")
    private String sfTeamCode;

    @Column(name = "perm_nt_form_code", length = 5)
    @Comment("허가/신고 구분 (permit=1, report=2, else=0)")
    private String permNtFormCode;

    @Column(name = "aplr_gbn_code", length = 5)
    @Comment("공공/민간 구분 (pub=05, else=01)")
    private String aplrGbnCode;

    @Column(name = "perm_yn", length = 5)
    @Comment("허가여부")
    private String permYn;

    @Column(name = "lnho_raise_yn", length = 2)
    @Comment("양도여부")
    private String lnhoRaiseYn;

    @Column(name = "end_nt_yn", length = 2)
    @Comment("폐공여부")
    private String endNtYn;

    @Column(name = "perm_cancel_yn", length = 2)
    @Comment("허가취소여부")
    private String permCancelYn;

    @Column(name = "jgong_deal_yn", length = 2)
    @Comment("제공처리여부 (고정값 1)")
    private String jgongDealYn;

    @Column(name = "first_reg_dthr")
    @Comment("최초등록일시")
    private LocalDateTime firstRegDthr;

    @Column(name = "dvop_loc_regn_code", length = 50)
    @Comment("개발위치지역코드")
    private String dvopLocRegnCode;

    @Column(name = "dvop_loc_san", length = 10)
    @Comment("개발위치 산")
    private String dvopLocSan;

    @Column(name = "dvop_loc_bunji", length = 20)
    @Comment("개발위치 번지")
    private String dvopLocBunji;

    @Column(name = "dvop_loc_ho", length = 10)
    @Comment("개발위치 호")
    private String dvopLocHo;

    @Column(name = "org_sno", length = 5)
    @Comment("기관번호 (고정값 16)")
    private String orgSno;

    @Column(name = "perm_nt_ymd", length = 10)
    @Comment("허가일 (하이픈 제거)")
    private String permNtYmd;

    @Column(name = "uwater_srv", length = 20)
    @Comment("용도명 (생활용/공업용/농어업용/기타)")
    private String uwaterSrv;

    @Column(name = "uwater_srv_code", length = 5)
    @Comment("용도코드 (1/2/3/4)")
    private String uwaterSrvCode;

    @Column(name = "uwater_pota_yn", length = 2)
    @Comment("음용여부 (0/1)")
    private String uwaterPotaYn;

    @Column(name = "dig_dph", length = 20)
    @Comment("굴착심도")
    private String digDph;

    @Column(name = "dig_diam", length = 20)
    @Comment("굴착구경")
    private String digDiam;

    @Column(name = "litd_dg", length = 10)
    @Comment("경도 도")
    private String litdDg;

    @Column(name = "litd_mint", length = 10)
    @Comment("경도 분")
    private String litdMint;

    @Column(name = "litd_sc", length = 10)
    @Comment("경도 초")
    private String litdSc;

    @Column(name = "lttd_dg", length = 10)
    @Comment("위도 도")
    private String lttdDg;

    @Column(name = "lttd_mint", length = 10)
    @Comment("위도 분")
    private String lttdMint;

    @Column(name = "lttd_sc", length = 10)
    @Comment("위도 초")
    private String lttdSc;

    @Column(name = "frw_pln_qua", length = 20)
    @Comment("양수계획량")
    private String frwPlnQua;

    @Column(name = "dyn_eqn_hrp", length = 20)
    @Comment("동력장비마력")
    private String dynEqnHrp;

    @Column(name = "pipe_diam", length = 20)
    @Comment("관경")
    private String pipeDiam;

    @Column(name = "rwt_cap", length = 20)
    @Comment("저수량")
    private String rwtCap;

    @Column(name = "uwater_dtl_srv_code", length = 5)
    @Comment("세부용도코드")
    private String uwaterDtlSrvCode;

    @Column(name = "last_mod_dthr")
    @Comment("최종수정일시 (= first_reg_dthr)")
    private LocalDateTime lastModDthr;
}
