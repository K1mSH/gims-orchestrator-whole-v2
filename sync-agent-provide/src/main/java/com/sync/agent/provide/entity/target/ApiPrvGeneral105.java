package com.sync.agent.provide.entity.target;

import lombok.*;
import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 보조관측 제원 제공용 테이블 (Type B — B5)
 *
 * 레거시: OPN info_general_105 (TM_GD10001 + TM_GD60130 + TM_GD60001 + TM_GD60002 + TM_GD70002 JOIN)
 * 원본: 5테이블 JOIN 결과
 * 전처리: 다중 JOIN (보조지하수관측망/수동보조지하수관측망 필터)
 * PK: gwel_no (레거시: GENNUM)
 */
@Entity
@Table(name = "api_prv_general_105")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiPrvGeneral105 {

    /** 관정번호 (PK) — 레거시: GENNUM → GWEL_NO */
    @Id
    @Column(name = "gwel_no")
    private Long gwelNo;

    /** 지점명 — 레거시: OBSV_NAME(JIGUNAME) → BRNCH_NM */
    @Column(name = "brnch_nm", length = 100)
    private String brnchNm;

    /** 관측소코드 — 레거시: OBSV_CODE → OBSVTR_ID */
    @Column(name = "obsvtr_id", length = 30)
    private String obsvtrId;

    /** 인허가번호 — 레거시: INPERM_NO → PRMSN_DCLR_NO */
    @Column(name = "prmsn_dclr_no", length = 30)
    private String prmsnDclrNo;

    /** 주소 — 레거시: SIDO || SIGUNGU || UPMYUNDO || RI 결합 */
    @Column(name = "addr", length = 500)
    private String addr;

    /** 표고값 — 레거시: PYOGO → ALTD_VL */
    @Column(name = "altd_vl", length = 20)
    private String altdVl;

    /** 관리기관 — 레거시: MGR_ORG(SOUR_GOV) → MNG_INSTT_NM */
    @Column(name = "mng_instt_nm", length = 100)
    private String mngInsttNm;

    /** 설치일자 — 레거시: INSDATE → INSTL_YMD */
    @Column(name = "instl_ymd", length = 8)
    private String instlYmd;

    /** 관측유형 — 레거시: OBSV_TYPE → GWEL_FRM_CD */
    @Column(name = "gwel_frm_cd", length = 1)
    private String gwelFrmCd;

    /** 관정형태코드 — 레거시: WELL → GWEL_FRM_CD (중복, well_stle_code) */
    @Column(name = "well_stle_cd", length = 1)
    private String wellStleCd;

    /** 케이싱높이 — 레거시: CASING_HEIGHT → CSNG_HG */
    @Column(name = "csng_hg", length = 20)
    private String csngHg;

    /** 굴착심도 — 레거시: GULDEP → INSTL_DPH_VL */
    @Column(name = "instl_dph_vl", length = 20)
    private String instlDphVl;

    /** 굴착구경 — 레거시: GULDIA → SUPRR_DGG_CALBR */
    @Column(name = "suprr_dgg_calbr", length = 20)
    private String suprrDggCalbr;

    /** 관측주기내용 — 레거시: GIGWANMETHOD → OBSR_CYCLE_CTNT */
    @Column(name = "obsr_cycle_ctnt", length = 100)
    private String obsrCycleCtnt;

    /** 관측항목명 — 레거시: GIGWANITEM → OBSR_IEM_NM */
    @Column(name = "obsr_iem_nm", length = 100)
    private String obsrIemNm;

    /** 용도코드 — 레거시: YONGDO_CD(GROUNDUSE) → UGWTR_DTL_USG_CD */
    @Column(name = "ugwtr_dtl_usg_cd", length = 2)
    private String ugwtrDtlUsgCd;

    /** 음용여부 — 레거시: UMYONG(UWATER_POTA_YN) → DKPP_YN */
    @Column(name = "dkpp_yn", length = 1)
    private String dkppYn;

    // ── 추적 컬럼 ──

    @Column(name = "execution_id", length = 100)
    private String executionId;

    @Column(name = "source_refs", length = 4000)
    private String sourceRefs;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
