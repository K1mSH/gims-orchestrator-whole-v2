package com.sync.agent.provide.entity.target;

import lombok.*;
import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 수질정보 제공용 테이블 (Type B — B11/B12)
 *
 * 레거시: OPN waterQualityInfo + waterQualityInfoDJ
 * 원본: TM_GD10001 + TM_GD30301 + TM_GD30302 JOIN + 동적 PIVOT
 * 전처리: 동적 수질항목 PIVOT (iterate) + 주소 결합 + 용도코드 변환
 * 복합 PK: gwel_no + wq_insp_sn
 */
@Entity
@Table(name = "api_prv_water_quality")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(ApiPrvWaterQuality.PK.class)
public class ApiPrvWaterQuality {

    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class PK implements Serializable {
        private Long gwelNo;
        private Long wqInspSn;
    }

    /** 관정번호 — 레거시: GENNUM → GWEL_NO */
    @Id
    @Column(name = "gwel_no")
    private Long gwelNo;

    /** 수질검사일련번호 — 레거시: QLTWTR_INSPCT_SN → WQ_INSP_SN */
    @Id
    @Column(name = "wq_insp_sn")
    private Long wqInspSn;

    /** 지점명 — 레거시: SPOT_NM → BRNCH_NM */
    @Column(name = "brnch_nm", length = 100)
    private String brnchNm;

    /** 주소 — 레거시: BRTC_NM || SIGUN_NM || EMD_NM || LI_NM || ADDR 결합 */
    @Column(name = "addr", length = 500)
    private String addr;

    /** 경도 — 레거시: LO_VALUE → LOT */
    @Column(name = "lot", length = 20)
    private String lot;

    /** 위도 — 레거시: LA_VALUE → LAT */
    @Column(name = "lat", length = 20)
    private String lat;

    /** 음용여부 — 레거시: DRNK_AT → DKPP_YN */
    @Column(name = "dkpp_yn", length = 1)
    private String dkppYn;

    /** 지하수용도코드 — 레거시: UGRWTR_PRPOS_CODE → UGWTR_USG_CD */
    @Column(name = "ugwtr_usg_cd", length = 100)
    private String ugwtrUsgCd;

    /** 수질검사일자 — 레거시: QLTWTR_INSPCT_DE → WQ_INSP_YMD */
    @Column(name = "wq_insp_ymd", length = 8)
    private String wqInspYmd;

    /** 최초등록일시 — 레거시: FRST_REGIST_DT → FRST_REG_DT */
    private LocalDateTime frst_reg_dt;

    /** 최종변경일시 — 레거시: LAST_CHANGE_DT → LAST_CHG_DT */
    private LocalDateTime last_chg_dt;

    /** 조사연도 — 레거시: INVSTG_YEAR → EXMN_YR */
    @Column(name = "exmn_yr", length = 4)
    private String exmnYr;

    /** 차수 — 레거시: ODR → CYCL */
    @Column(name = "cycl")
    private Integer cycl;

    /** 입력기관명 — 레거시: usrNM */
    @Column(name = "usr_nm", length = 100)
    private String usrNm;

    // ── 동적 수질항목 (주요 항목만 — 추후 확장) ──

    /** 수질항목 0001 — 일반세균수 */
    @Column(name = "c0001", length = 100)
    private String c0001;

    /** 수질항목 0002 — 총대장균군 */
    @Column(name = "c0002", length = 100)
    private String c0002;

    /** 수질항목 0005 — 납 */
    @Column(name = "c0005", length = 100)
    private String c0005;

    /** 수질항목 0006 — 불소 */
    @Column(name = "c0006", length = 100)
    private String c0006;

    /** 수질항목 0009 — 수소이온농도 */
    @Column(name = "c0009", length = 100)
    private String c0009;

    /** 수질항목 0012 — 질산성질소 */
    @Column(name = "c0012", length = 100)
    private String c0012;

    /** 수질항목 0033 — 경도 */
    @Column(name = "c0033", length = 100)
    private String c0033;

    /** 수질항목 0046 — 탁도 */
    @Column(name = "c0046", length = 100)
    private String c0046;

    /** 수질항목 0049 — 전기전도도 */
    @Column(name = "c0049", length = 100)
    private String c0049;

    /** 수질항목 0042 — 염소이온 */
    @Column(name = "c0042", length = 100)
    private String c0042;

    // TODO: 추가 수질항목 컬럼 — iterate 기반 동적 PIVOT이므로 요청 항목에 따라 확장

    // ── 추적 컬럼 ──

    @Column(name = "execution_id", length = 100)
    private String executionId;

    @Column(name = "source_refs", length = 4000)
    private String sourceRefs;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
