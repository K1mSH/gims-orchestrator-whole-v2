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
 * 제주 이용시설 이용실태정보
 * - 원본: RGETSTGMS01 (새올 DB)
 * - 레거시: RgetstgmsProgram → selectJejuUse.json → MERGE INTO RGETSTGMS01
 * - PK: PERM_NT_NO (허가신고번호)
 */
@Entity
@Table(name = "rgetstgms01")
@org.hibernate.annotations.Table(appliesTo = "rgetstgms01", comment = "제주 이용시설 이용실태정보")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Rgetstgms01 {

    @Id
    @Column(name = "perm_nt_no", length = 50)
    @Comment("허가신고번호 (PK)")
    private String permNtNo;

    @Column(name = "rel_trans_cgg_code", length = 10)
    @Comment("시군구코드")
    private String relTransCggCode;

    @Column(name = "yy_gbn", length = 10)
    @Comment("연도구분")
    private String yyGbn;

    @Column(name = "sf_team_code", length = 10)
    @Comment("담당팀코드 (= rel_trans_cgg_code)")
    private String sfTeamCode;

    @Column(name = "perm_nt_form_code", length = 5)
    @Comment("허가/신고 구분")
    private String permNtFormCode;

    @Column(name = "regn_code", length = 50)
    @Comment("지역코드 (= dvop_loc_regn_code)")
    private String regnCode;

    @Column(name = "san", length = 10)
    @Comment("산")
    private String san;

    @Column(name = "bunji", length = 20)
    @Comment("번지")
    private String bunji;

    @Column(name = "ho", length = 10)
    @Comment("호")
    private String ho;

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

    @Column(name = "elev", length = 20)
    @Comment("표고")
    private String elev;

    @Column(name = "uwater_srv_code", length = 5)
    @Comment("용도코드")
    private String uwaterSrvCode;

    @Column(name = "pub_pri_gbn", length = 5)
    @Comment("공공/민간 (= perm_nt_form_code)")
    private String pubPriGbn;

    @Column(name = "pota_yn", length = 2)
    @Comment("음용여부")
    private String potaYn;

    @Column(name = "y_use_qua", length = 20)
    @Comment("연간사용량")
    private String yUseQua;

    @Column(name = "uwater_souc_code", length = 5)
    @Comment("수원코드")
    private String uwaterSoucCode;

    @Column(name = "dph", length = 20)
    @Comment("심도")
    private String dph;

    @Column(name = "dig_diam", length = 20)
    @Comment("굴착구경")
    private String digDiam;

    @Column(name = "pump_hrp", length = 20)
    @Comment("펌프마력")
    private String pumpHrp;

    @Column(name = "rwt_cap", length = 20)
    @Comment("저수량")
    private String rwtCap;

    @Column(name = "pipe_diam", length = 20)
    @Comment("관경")
    private String pipeDiam;

    @Column(name = "nat_wtlv", length = 20)
    @Comment("자연수위")
    private String natWtlv;

    @Column(name = "stb_wtlv", length = 20)
    @Comment("안정수위")
    private String stbWtlv;

    @Column(name = "frw_pln_qua", length = 20)
    @Comment("양수계획량")
    private String frwPlnQua;

    @Column(name = "first_reg_dthr")
    @Comment("최초등록일시")
    private LocalDateTime firstRegDthr;

    @Column(name = "last_mod_dthr")
    @Comment("최종수정일시")
    private LocalDateTime lastModDthr;

    @Column(name = "uwater_dtl_srv_code", length = 5)
    @Comment("세부용도코드")
    private String uwaterDtlSrvCode;
}
