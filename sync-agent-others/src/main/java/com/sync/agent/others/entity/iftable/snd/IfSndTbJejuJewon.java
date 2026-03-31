package com.sync.agent.others.entity.iftable.snd;

import lombok.*;
import org.hibernate.annotations.Comment;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * IF_SND 제주 보조관측망 관측점 마스터 엔티티.
 *
 * <p>SND 파이프라인에서 Target DB의 제주 제원 데이터를 IF_SND 테이블로 추출할 때 사용된다.
 * {@code source_refs}에 UK, {@code execution_id}에 인덱스가 설정되어 있다.</p>
 *
 * <p>테이블: {@code if_snd_tb_jeju_jewon}</p>
 *
 * @see com.infolink.collector.entity.JejuJewon
 */
@Entity
@Table(name = "if_snd_tb_jeju_jewon",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_if_snd_tb_jeju_jewon_source_refs",
           columnNames = {"source_refs"}
       ),
       indexes = @Index(name = "idx_if_snd_tb_jeju_jewon_exec_id", columnList = "execution_id"))
@org.hibernate.annotations.Table(appliesTo = "if_snd_tb_jeju_jewon", comment = "IF_SND 제주 보조관측망 관측점 마스터")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IfSndTbJejuJewon {

    @Id
    @Column(name = "obsrvt_id", length = 30)
    @Comment("관측점 ID (PK)")
    private String obsrvtId;

    // ========== 비즈니스 컬럼 ==========

    @Column(name = "obsrvt_nm", length = 100)
    @Comment("관측소명")
    private String obsrvtNm;

    @Column(name = "spot_nm", length = 100)
    @Comment("지점명")
    private String spotNm;

    @Column(name = "lo_value", length = 20)
    @Comment("경도")
    private String loValue;

    @Column(name = "la_value", length = 20)
    @Comment("위도")
    private String laValue;

    @Column(name = "tmx_value", precision = 20, scale = 10)
    @Comment("TM X좌표")
    private BigDecimal tmxValue;

    @Column(name = "tmy_value", precision = 20, scale = 10)
    @Comment("TM Y좌표")
    private BigDecimal tmyValue;

    @Column(name = "extn_csng_calbr", length = 5)
    @Comment("케이싱 구경")
    private String extnCsngCalbr;

    @Column(name = "bunji", length = 40)
    @Comment("번지")
    private String bunji;

    @Column(name = "sigun_nm", length = 40)
    @Comment("시군명")
    private String sigunNm;

    @Column(name = "emd_nm", length = 40)
    @Comment("읍면동명")
    private String emdNm;

    @Column(name = "ho", length = 40)
    @Comment("호")
    private String ho;

    @Column(name = "li_nm", length = 40)
    @Comment("리명")
    private String liNm;

    @Column(name = "drnk_at", length = 1)
    @Comment("음용여부")
    private String drnkAt;

    @Column(name = "use_at", length = 1)
    @Comment("사용여부")
    private String useAt;

    @Column(name = "al_value", length = 50)
    @Comment("표고값")
    private String alValue;

    @Column(name = "wal")
    @Comment("자연수위")
    private BigDecimal wal;

    @Column(name = "ugrwtr_prpos_code", length = 2)
    @Comment("용도코드")
    private String ugrwtrPrposCode;

    @Column(name = "legaldong_code", length = 10)
    @Comment("법정동코드")
    private String legaldongCode;

    // ========== IF 메타 컬럼 ==========

    @Column(name = "source_refs", columnDefinition = "TEXT")
    @Comment("원본 참조키 (UK)")
    private String sourceRefs;

    @Builder.Default
    @Column(name = "link_status", length = 20)
    @Comment("연계 상태 (PENDING/SUCCESS/FAILED)")
    private String linkStatus = "PENDING";

    @Column(name = "extracted_at")
    @Comment("추출 시각")
    private LocalDateTime extractedAt;

    @Column(name = "updated_at")
    @Comment("수정 시각")
    private LocalDateTime updatedAt;

    @Column(name = "execution_id")
    @Comment("처리 실행 ID")
    private String executionId;
}
