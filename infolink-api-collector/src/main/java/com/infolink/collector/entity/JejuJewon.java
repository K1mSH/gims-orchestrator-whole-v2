package com.infolink.collector.entity;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.Comment;

import javax.persistence.*;
import java.math.BigDecimal;

/**
 * 제주도 보조지하수관측망 관측점 마스터
 * - 원본: NGWIS.TB_JEJU_JEWON (DMZ 보조망 Oracle)
 * - 레거시: InsetTb_jeju_jewon → selectObsv.json API 수집 결과
 * - 테이블 자동 생성 (ddl-auto=update) + 향후 읽기용
 * - 쓰기는 JejuJewonExecutor에서 JdbcTemplate으로 수행
 */
@Entity
@Table(name = "tb_jeju_jewon")
@org.hibernate.annotations.Table(appliesTo = "tb_jeju_jewon", comment = "제주 보조관측망 관측점 마스터")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JejuJewon {

    @Id
    @Column(name = "obsrvt_id", length = 30)
    @Comment("관측점 ID (= API siteCode)")
    private String obsrvtId;

    @Column(name = "obsrvt_nm", length = 100)
    @Comment("관측소명")
    private String obsrvtNm;

    @Column(name = "spot_nm", length = 100)
    @Comment("지점명 (= API siteName)")
    private String spotNm;

    @Column(name = "lo_value", length = 20)
    @Comment("경도 (EPSG:4326 변환 결과)")
    private String loValue;

    @Column(name = "la_value", length = 20)
    @Comment("위도 (EPSG:4326 변환 결과)")
    private String laValue;

    @Column(name = "tmx_value", precision = 20, scale = 10)
    @Comment("TM X좌표 (EPSG:5186 원본)")
    private BigDecimal tmxValue;

    @Column(name = "tmy_value", precision = 20, scale = 10)
    @Comment("TM Y좌표 (EPSG:5186 원본)")
    private BigDecimal tmyValue;

    @Column(name = "extn_csng_calbr", length = 5)
    @Comment("케이싱 구경 (API: wCsiDia)")
    private String extnCsngCalbr;

    @Column(name = "bunji", length = 40)
    @Comment("번지 (API: wDevlocBunji)")
    private String bunji;

    @Column(name = "sigun_nm", length = 40)
    @Comment("시군명 (API: wDevlocCi)")
    private String sigunNm;

    @Column(name = "emd_nm", length = 40)
    @Comment("읍면동명 (API: wDevlocDo)")
    private String emdNm;

    @Column(name = "ho", length = 40)
    @Comment("호 (API: wDevlocHo)")
    private String ho;

    @Column(name = "li_nm", length = 40)
    @Comment("리명 (API: wDevlocLi)")
    private String liNm;

    @Column(name = "drnk_at", length = 1)
    @Comment("음용여부 (비음용=0, 그외=1)")
    private String drnkAt;

    @Column(name = "use_at", length = 1)
    @Comment("사용여부 (고정값 1)")
    private String useAt;

    @Column(name = "al_value", length = 50)
    @Comment("표고값 (API: wElev)")
    private String alValue;

    @Column(name = "wal")
    @Comment("자연수위 (API: wNatWtlv)")
    private BigDecimal wal;

    @Column(name = "ugrwtr_prpos_code", length = 2)
    @Comment("용도코드 (상수도=18, 농업=19, 기타=40)")
    private String ugrwtrPrposCode;

    @Column(name = "legaldong_code", length = 10)
    @Comment("법정동코드 (제주시=6510000, 서귀포시=6520000)")
    private String legaldongCode;

    @Builder.Default
    @Column(name = "link_status", length = 20, columnDefinition = "VARCHAR(20) DEFAULT 'PENDING'")
    @Comment("SND 연계 상태 (PENDING/SUCCESS/FAILED)")
    private String linkStatus = "PENDING";
}
