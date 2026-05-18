package com.infolink.agent.provide.entity.target;

import lombok.*;
import org.hibernate.annotations.Comment;

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
@Table(name = "api_prv_general_105",
       uniqueConstraints = @UniqueConstraint(name = "uk_api_prv_general_105_source_refs", columnNames = {"source_refs"}))
@org.hibernate.annotations.Table(appliesTo = "api_prv_general_105", comment = "보조관측 제원 제공")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiPrvGeneral105 {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("일련번호")
    private Long sn;

    @Column(name = "gwel_no")
    @Comment("관정번호")
    private Long gwelNo;

    @Column(name = "brnch_nm", length = 100)
    @Comment("지점명")
    private String brnchNm;

    @Column(name = "obsvtr_id", length = 30)
    @Comment("관측소코드")
    private String obsvtrId;

    @Column(name = "prmsn_dclr_no", length = 30)
    @Comment("인허가번호")
    private String prmsnDclrNo;

    @Column(name = "addr", length = 500)
    @Comment("주소")
    private String addr;

    @Column(name = "altd_vl", length = 20)
    @Comment("표고값")
    private String altdVl;

    @Column(name = "mng_instt_nm", length = 100)
    @Comment("관리기관명")
    private String mngInsttNm;

    @Column(name = "instl_ymd", length = 8)
    @Comment("설치일자")
    private String instlYmd;

    @Column(name = "gwel_frm_cd", length = 1)
    @Comment("관측유형")
    private String gwelFrmCd;

    @Column(name = "well_stle_cd", length = 1)
    @Comment("관정형태코드")
    private String wellStleCd;

    @Column(name = "csng_hg", length = 20)
    @Comment("케이싱높이")
    private String csngHg;

    @Column(name = "instl_dph_vl", length = 20)
    @Comment("굴착심도")
    private String instlDphVl;

    @Column(name = "suprr_dgg_calbr", length = 20)
    @Comment("굴착구경")
    private String suprrDggCalbr;

    @Column(name = "obsr_cycle_ctnt", length = 100)
    @Comment("관측주기내용")
    private String obsrCycleCtnt;

    @Column(name = "obsr_iem_nm", length = 100)
    @Comment("관측항목명")
    private String obsrIemNm;

    @Column(name = "ugwtr_dtl_usg_cd", length = 2)
    @Comment("용도코드")
    private String ugwtrDtlUsgCd;

    @Column(name = "dkpp_yn", length = 1)
    @Comment("음용여부")
    private String dkppYn;

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
