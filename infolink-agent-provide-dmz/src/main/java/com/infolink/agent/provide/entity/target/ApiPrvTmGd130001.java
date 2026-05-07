package com.infolink.agent.provide.entity.target;

import lombok.*;
import org.hibernate.annotations.Comment;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 영향조사보고서 제공용 테이블 (Type A — A6)
 *
 * 레거시: OPN info_yhjs_info (영향조사 상세)
 * 원본: Oracle TM_GD50001 (레거시) → TM_GD130001 (표준화)
 */
@Entity
@Table(name = "api_prv_tm_gd130001",
       uniqueConstraints = @UniqueConstraint(name = "uk_api_prv_tm_gd130001_source_refs", columnNames = {"source_refs"}))
@org.hibernate.annotations.Table(appliesTo = "api_prv_tm_gd130001", comment = "영향조사보고서 제공")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiPrvTmGd130001 {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("일련번호")
    private Long sn;

    @Column(name = "isvr_no", length = 10)
    @Comment("영향조사보고서번호")
    private String isvrNo;

    @Column(name = "lclgv_cd", length = 7)
    @Comment("자치단체코드")
    private String lclgvCd;

    @Column(name = "isvr_nm", length = 200)
    @Comment("영향조사보고서명")
    private String isvrNm;

    @Column(name = "prmtv_data_inst_nm", length = 100)
    @Comment("원시자료제공기관명")
    private String prmtvDataInstNm;

    @Column(name = "data_crtr_yr", length = 4)
    @Comment("자료기준연도")
    private String dataCrtrYr;

    @Column(name = "pblcn_mm", length = 2)
    @Comment("발행월")
    private String pblcnMm;

    @Column(name = "isvr_ccd", length = 1)
    @Comment("영향조사보고서분류코드")
    private String isvrCcd;

    @Column(name = "prlg_sn")
    @Comment("부록일련번호")
    private Long prlgSn;

    @Column(name = "isvr_data_frm_cd", length = 1)
    @Comment("영향조사보고서자료형태코드")
    private String isvrDataFrmCd;

    @Column(name = "clct_inst_nm", length = 100)
    @Comment("수집기관명")
    private String clctInstNm;

    @Column(name = "data_clct_ymd", length = 10)
    @Comment("자료수집일자")
    private String dataClctYmd;

    @Column(name = "data_inpt_ymd", length = 8)
    @Comment("자료입력일자")
    private String dataInptYmd;

    @Column(name = "data_rgtr_nm", length = 50)
    @Comment("자료등록자명")
    private String dataRgtrNm;

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
