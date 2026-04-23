package com.sync.agent.provide.entity.target;

import lombok.*;
import org.hibernate.annotations.Comment;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 대전 수질입력현황 제공용 테이블 (Type B — B18)
 *
 * 레거시: OPN gnlwtqltinfo_inputsittn
 * 원본: TM_GD10001 + TM_GD30301 + TM_GD30302 JOIN + 집계
 * 전처리: UNION ALL + GROUP BY + CASE WHEN 입력여부 판별
 * 복합 PK: ctpv_nm + sgg_nm + exmn_yr + cycl
 */
@Entity
@Table(name = "api_prv_wq_input_status_dj",
       uniqueConstraints = @UniqueConstraint(name = "uk_api_prv_wq_input_status_dj_source_refs", columnNames = {"source_refs"}))
@org.hibernate.annotations.Table(appliesTo = "api_prv_wq_input_status_dj", comment = "대전 수질입력현황 제공")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiPrvWqInputStatusDj {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("일련번호")
    private Long sn;

    @Column(name = "ctpv_nm", length = 40)
    @Comment("시도")
    private String ctpvNm;

    @Column(name = "sgg_nm", length = 40)
    @Comment("시군구")
    private String sggNm;

    @Column(name = "exmn_yr", length = 4)
    @Comment("연도")
    private String exmnYr;

    @Column(name = "cycl")
    @Comment("차수")
    private Integer cycl;

    @Column(name = "total")
    @Comment("전체건수")
    private Integer total;

    @Column(name = "complt")
    @Comment("완료건수")
    private Integer complt;

    @Column(name = "ncomplt")
    @Comment("미완료건수")
    private Integer ncomplt;

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
