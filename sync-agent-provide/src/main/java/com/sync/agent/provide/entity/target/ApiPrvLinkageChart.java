package com.sync.agent.provide.entity.target;

import lombok.*;
import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 관측소연계 그래프 데이터 제공용 테이블 (Type B — B9/B10)
 *
 * 레거시: OPN linkage_analy_chart_general + observationStationTimeService
 * 원본: PM_GD60201 + TM_GD60101 PIVOT 결과
 * 전처리: PIVOT (OBSR_IEM_ID IN 5,163,52) + 표고-수위 계산
 * 복합 PK: gwel_no + ymd
 */
@Entity
@Table(name = "api_prv_linkage_chart")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(ApiPrvLinkageChart.PK.class)
public class ApiPrvLinkageChart {

    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class PK implements Serializable {
        private Long gwelNo;
        private String ymd;
    }

    /** 관정번호 — 레거시: GENNUM → GWEL_NO */
    @Id
    @Column(name = "gwel_no")
    private Long gwelNo;

    /** 관측일자 — 레거시: YMD (YYYYMMDD) */
    @Id
    @Column(name = "ymd", length = 8)
    private String ymd;

    /** 수위(표고) — 레거시: ELEV ("5" PIVOT) */
    @Column(name = "elev", length = 20)
    private String elev;

    /** 수온 — 레거시: WTEMP ("163" PIVOT) */
    @Column(name = "wtemp", length = 20)
    private String wtemp;

    /** 지하수위(표고-수위) — 레거시: LEV (AL_VALUE - "5") */
    @Column(name = "lev", length = 20)
    private String lev;

    /** 전기전도도 — 레거시: EC ("52" PIVOT) */
    @Column(name = "ec", length = 20)
    private String ec;

    // ── 추적 컬럼 ──

    @Column(name = "execution_id", length = 100)
    private String executionId;

    @Column(name = "source_refs", length = 4000)
    private String sourceRefs;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
