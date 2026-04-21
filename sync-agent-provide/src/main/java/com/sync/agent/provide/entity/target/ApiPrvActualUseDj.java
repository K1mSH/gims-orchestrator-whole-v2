package com.sync.agent.provide.entity.target;

import lombok.*;
import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 대전 이용실태 제공용 테이블 (Type B — B16)
 *
 * 레거시: OPN actualUseDetailDJ
 * 원본: RGETNTGMS02 + TC_GD00100 + TM_GD20930 JOIN
 * 전처리: CTE + JOIN + ROW_NUMBER
 * PK: 자동채번 sn (IDENTITY)
 */
@Entity
@Table(name = "api_prv_actual_use_dj")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiPrvActualUseDj {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long sn;

    /** 시군구 — 레거시: BRTC_NM || SIGUN_NM */
    @Column(name = "sigungu", length = 100)
    private String sigungu;

    /** 기준연도 — 레거시: CRIT_YY */
    @Column(name = "year", length = 4)
    private String year;

    /** 담당부서 — 레거시: SF_TEAM_NM */
    @Column(name = "depart", length = 100)
    private String depart;

    /** 작업완료일 — 레거시: WRK_CMPT_YMD */
    @Column(name = "ymd", length = 8)
    private String ymd;

    /** 완료여부 — 레거시: DECODE 결과 (완료/미완료) */
    @Column(name = "yn", length = 10)
    private String yn;

    // ── 추적 컬럼 ──

    @Column(name = "execution_id", length = 100)
    private String executionId;

    @Column(name = "source_refs", length = 4000)
    private String sourceRefs;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
