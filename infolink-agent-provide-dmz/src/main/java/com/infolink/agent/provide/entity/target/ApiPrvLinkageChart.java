package com.infolink.agent.provide.entity.target;

import lombok.*;
import org.hibernate.annotations.Comment;

import javax.persistence.*;
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
@Table(name = "api_prv_linkage_chart",
       uniqueConstraints = @UniqueConstraint(name = "uk_api_prv_linkage_chart_source_refs", columnNames = {"source_refs"}))
@org.hibernate.annotations.Table(appliesTo = "api_prv_linkage_chart", comment = "관측소연계 그래프 데이터 제공")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiPrvLinkageChart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("일련번호")
    private Long sn;

    @Column(name = "gwel_no")
    @Comment("관정번호")
    private Long gwelNo;

    @Column(name = "ymd", length = 8)
    @Comment("관측일자 (YYYYMMDD)")
    private String ymd;

    @Column(name = "elev", length = 20)
    @Comment("수위(표고)")
    private String elev;

    @Column(name = "wtemp", length = 20)
    @Comment("수온")
    private String wtemp;

    @Column(name = "lev", length = 20)
    @Comment("지하수위(표고-수위)")
    private String lev;

    @Column(name = "ec", length = 20)
    @Comment("전기전도도")
    private String ec;

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
