package com.sync.agent.provide.entity.target;

import lombok.*;
import org.hibernate.annotations.Comment;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 식약처 정기수질검사 제공용 테이블 (Type B — B13)
 *
 * 레거시: OPN waterQualityMfdsInfo (TM_GD70201 + TM_GD70202 JOIN + 동적 PIVOT)
 * 원본: Oracle TM_GD70201 (레거시) → TM_GD30350 (표준화)
 *       Oracle TM_GD70202 (레거시) → TM_GD30351 (표준화)
 * 전처리: JOIN + 동적 수질항목 PIVOT + 차수 계산
 * PK: wq_insp_sn
 */
@Entity
@Table(name = "api_prv_water_quality_mfds",
       uniqueConstraints = @UniqueConstraint(name = "uk_api_prv_water_quality_mfds_source_refs", columnNames = {"source_refs"}))
@org.hibernate.annotations.Table(appliesTo = "api_prv_water_quality_mfds", comment = "식약처 정기수질검사 제공")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiPrvWaterQualityMfds {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("일련번호")
    private Long sn;

    @Column(name = "wq_insp_sn")
    @Comment("수질검사일련번호")
    private Long wqInspSn;

    @Column(name = "orgwt_clsf_cd", length = 1)
    @Comment("원수분류코드")
    private String orgwtClsfCd;

    @Column(name = "conm_nm", length = 200)
    @Comment("상호명")
    private String conmNm;

    @Column(name = "ctpv_nm", length = 40)
    @Comment("시도명")
    private String ctpvNm;

    @Column(name = "sgg_nm", length = 30)
    @Comment("시군구명")
    private String sggNm;

    @Column(name = "emd_nm", length = 30)
    @Comment("읍면동명")
    private String emdNm;

    @Column(name = "li_nm", length = 40)
    @Comment("리명")
    private String liNm;

    @Column(name = "gwel_addr", length = 250)
    @Comment("관정주소")
    private String gwelAddr;

    @Column(name = "ugwtr_usg_cd", length = 2)
    @Comment("지하수용도코드")
    private String ugwtrUsgCd;

    @Column(name = "exmn_yr", length = 4)
    @Comment("조사연도")
    private String exmnYr;

    @Column(name = "cycl")
    @Comment("차수")
    private Integer cycl;

    @Column(name = "rslt_ntfctn_ymd", length = 8)
    @Comment("결과통보일자")
    private String rsltNtfctnYmd;

    @Column(name = "wq_insp_ymd", length = 8)
    @Comment("수질검사일자")
    private String wqInspYmd;

    @Column(name = "dmnd_ymd", length = 8)
    @Comment("요청일자")
    private String dmndYmd;

    @Column(name = "wq_crtr_usg_cd", length = 2)
    @Comment("수질기준용도코드")
    private String wqCrtrUsgCd;

    @Column(name = "wq_insp_rslt_cd", length = 1)
    @Comment("수질검사결과코드")
    private String wqInspRsltCd;

    @Column(name = "wq_insp_etc_cn", length = 100)
    @Comment("수질검사기타내용")
    private String wqInspEtcCn;

    @Column(name = "wq_insp_prps_cn", length = 100)
    @Comment("수질검사목적내용")
    private String wqInspPrpsCn;

    @Column(name = "prmsn_dclr_no", length = 30)
    @Comment("허가신고번호")
    private String prmsnDclrNo;

    @Column(name = "usr_nm", length = 100)
    @Comment("사용자명")
    private String usrNm;

    // ── 동적 수질항목 (주요 항목) ──

    @Column(name = "c0001", length = 100)
    @Comment("수질항목 0001")
    private String c0001;

    @Column(name = "c0002", length = 100)
    @Comment("수질항목 0002")
    private String c0002;

    @Column(name = "c0005", length = 100)
    @Comment("수질항목 0005")
    private String c0005;

    @Column(name = "c0006", length = 100)
    @Comment("수질항목 0006")
    private String c0006;

    @Column(name = "c0009", length = 100)
    @Comment("수질항목 0009")
    private String c0009;

    @Column(name = "c0012", length = 100)
    @Comment("수질항목 0012")
    private String c0012;

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
