package com.infolink.agent.provide.entity.target;

import lombok.*;
import org.hibernate.annotations.Comment;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 관정 제공용 테이블 (Type A — A5)
 *
 * 레거시: OPN info_general (관측망 상세)
 * 원본: Oracle TM_GD10001 (레거시) → TM_GD120001 (표준화)
 */
@Entity
@Table(name = "api_prv_tm_gd120001",
       uniqueConstraints = @UniqueConstraint(name = "uk_api_prv_tm_gd120001_source_refs", columnNames = {"source_refs"}))
@org.hibernate.annotations.Table(appliesTo = "api_prv_tm_gd120001", comment = "관정 제공")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiPrvTmGd120001 {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("일련번호")
    private Long sn;

    @Column(name = "gwel_no")
    @Comment("관정번호")
    private Long gwelNo;

    @Column(name = "ugwtr_exmn_cd", length = 3)
    @Comment("지하수조사코드")
    private String ugwtrExmnCd;

    @Column(name = "brnch_nm", length = 100)
    @Comment("지점명")
    private String brnchNm;

    @Column(name = "ctpv_nm", length = 40)
    @Comment("시도명")
    private String ctpvNm;

    @Column(name = "sgg_nm", length = 40)
    @Comment("시군구명")
    private String sggNm;

    @Column(name = "emd_nm", length = 30)
    @Comment("읍면동명")
    private String emdNm;

    @Column(name = "li_nm", length = 40)
    @Comment("리명")
    private String liNm;

    @Column(name = "addr", length = 250)
    @Comment("주소")
    private String addr;

    @Column(name = "cdsstm_cn", length = 4000)
    @Comment("좌표계내용")
    private String cdsstmCn;

    @Column(name = "trgnpt_cd", length = 1)
    @Comment("원점코드")
    private String trgnptCd;

    @Column(name = "lot", length = 20)
    @Comment("경도")
    private String lot;

    @Column(name = "lat", length = 20)
    @Comment("위도")
    private String lat;

    @Column(name = "xcrd")
    @Comment("X좌표")
    private Long xcrd;

    @Column(name = "ycrd")
    @Comment("Y좌표")
    private Long ycrd;

    @Column(name = "altd_vl")
    @Comment("표고값")
    private Long altdVl;

    @Column(name = "dtl_pstn_cn", length = 1000)
    @Comment("상세위치내용")
    private String dtlPstnCn;

    @Column(name = "grnds_gwel_no", length = 50)
    @Comment("현장관정번호")
    private String grndsGwelNo;

    @Column(name = "user_nm", length = 100)
    @Comment("사용자명")
    private String userNm;

    @Column(name = "ownr_nm", length = 40)
    @Comment("소유자명")
    private String ownrNm;

    @Column(name = "use_yn", length = 1)
    @Comment("사용여부")
    private String useYn;

    @Column(name = "wtlv_data_yn", length = 1)
    @Comment("수위자료여부")
    private String wtlvDataYn;

    @Column(name = "elcrst_data_yn", length = 1)
    @Comment("전기탐사자료여부")
    private String elcrstDataYn;

    @Column(name = "drll_data_yn", length = 1)
    @Comment("시추자료여부")
    private String drllDataYn;

    @Column(name = "brng_data_yn", length = 1)
    @Comment("착정자료여부")
    private String brngDataYn;

    @Column(name = "wpmp_dta_yn", length = 1)
    @Comment("양수자료여부")
    private String wpmpDtaYn;

    @Column(name = "wq_data_yn", length = 1)
    @Comment("수질자료여부")
    private String wqDataYn;

    @Column(name = "prmtv_data_nm", length = 100)
    @Comment("원시자료명")
    private String prmtvDataNm;

    @Column(name = "prmtv_data_inst_nm", length = 100)
    @Comment("원시자료기관명")
    private String prmtvDataInstNm;

    @Column(name = "data_crtr_yr", length = 4)
    @Comment("자료기준연도")
    private String dataCrtrYr;

    @Column(name = "rmrk", length = 500)
    @Comment("비고")
    private String rmrk;

    @Column(name = "bsn_nm", length = 50)
    @Comment("유역명")
    private String bsnNm;

    @Column(name = "stdg_cd", length = 10)
    @Comment("법정동코드")
    private String stdgCd;

    @Column(name = "gwel_frm_cd", length = 1)
    @Comment("관정형태코드")
    private String gwelFrmCd;

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
