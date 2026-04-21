package com.sync.agent.provide.entity.target;

import lombok.*;
import javax.persistence.*;
import java.io.Serializable;
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
@Table(name = "api_prv_wq_input_status_dj")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(ApiPrvWqInputStatusDj.PK.class)
public class ApiPrvWqInputStatusDj {

    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class PK implements Serializable {
        private String ctpvNm;
        private String sggNm;
        private String exmnYr;
        private Integer cycl;
    }

    /** 시도 — 레거시: sido → CTPV_NM */
    @Id
    @Column(name = "ctpv_nm", length = 40)
    private String ctpvNm;

    /** 시군구 — 레거시: sigungu → SGG_NM */
    @Id
    @Column(name = "sgg_nm", length = 40)
    private String sggNm;

    /** 연도 — 레거시: year → EXMN_YR */
    @Id
    @Column(name = "exmn_yr", length = 4)
    private String exmnYr;

    /** 차수 — 레거시: odr → CYCL */
    @Id
    @Column(name = "cycl")
    private Integer cycl;

    /** 전체건수 — 레거시: total */
    @Column(name = "total")
    private Integer total;

    /** 완료건수 — 레거시: complt */
    @Column(name = "complt")
    private Integer complt;

    /** 미완료건수 — 레거시: ncomplt */
    @Column(name = "ncomplt")
    private Integer ncomplt;

    // ── 추적 컬럼 ──

    @Column(name = "execution_id", length = 100)
    private String executionId;

    @Column(name = "source_refs", length = 4000)
    private String sourceRefs;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
