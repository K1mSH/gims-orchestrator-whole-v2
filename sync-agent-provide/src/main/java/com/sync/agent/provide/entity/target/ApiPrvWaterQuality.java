package com.sync.agent.provide.entity.target;

import lombok.*;
import org.hibernate.annotations.Comment;

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
@org.hibernate.annotations.Table(appliesTo = "api_prv_water_quality", comment = "수질정보 제공")
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

    @Id
    @Column(name = "gwel_no")
    @Comment("관정번호")
    private Long gwelNo;

    @Id
    @Column(name = "wq_insp_sn")
    @Comment("수질검사일련번호")
    private Long wqInspSn;

    @Column(name = "brnch_nm", length = 100)
    @Comment("지점명")
    private String brnchNm;

    @Column(name = "addr", length = 500)
    @Comment("주소")
    private String addr;

    @Column(name = "lot", length = 20)
    @Comment("경도")
    private String lot;

    @Column(name = "lat", length = 20)
    @Comment("위도")
    private String lat;

    @Column(name = "dkpp_yn", length = 1)
    @Comment("음용여부")
    private String dkppYn;

    @Column(name = "ugwtr_usg_cd", length = 100)
    @Comment("지하수용도코드")
    private String ugwtrUsgCd;

    @Column(name = "wq_insp_ymd", length = 8)
    @Comment("수질검사일자")
    private String wqInspYmd;

    @Comment("최초등록일시")
    private LocalDateTime frst_reg_dt;

    @Comment("최종변경일시")
    private LocalDateTime last_chg_dt;

    @Column(name = "exmn_yr", length = 4)
    @Comment("조사연도")
    private String exmnYr;

    @Column(name = "cycl")
    @Comment("차수")
    private Integer cycl;

    @Column(name = "usr_nm", length = 100)
    @Comment("입력기관명")
    private String usrNm;

    // ── 동적 수질항목 (주요 항목) ──

    @Column(name = "c0001", length = 100)
    @Comment("수질항목 0001 - 일반세균수")
    private String c0001;

    @Column(name = "c0002", length = 100)
    @Comment("수질항목 0002 - 총대장균군")
    private String c0002;

    @Column(name = "c0005", length = 100)
    @Comment("수질항목 0005 - 납")
    private String c0005;

    @Column(name = "c0006", length = 100)
    @Comment("수질항목 0006 - 불소")
    private String c0006;

    @Column(name = "c0009", length = 100)
    @Comment("수질항목 0009 - 수소이온농도")
    private String c0009;

    @Column(name = "c0012", length = 100)
    @Comment("수질항목 0012 - 질산성질소")
    private String c0012;

    @Column(name = "c0033", length = 100)
    @Comment("수질항목 0033 - 경도")
    private String c0033;

    @Column(name = "c0046", length = 100)
    @Comment("수질항목 0046 - 탁도")
    private String c0046;

    @Column(name = "c0049", length = 100)
    @Comment("수질항목 0049 - 전기전도도")
    private String c0049;

    @Column(name = "c0042", length = 100)
    @Comment("수질항목 0042 - 염소이온")
    private String c0042;

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
