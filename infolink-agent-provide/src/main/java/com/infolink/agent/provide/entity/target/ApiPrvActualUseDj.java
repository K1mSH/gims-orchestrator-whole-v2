package com.infolink.agent.provide.entity.target;

import lombok.*;
import org.hibernate.annotations.Comment;

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
@Table(name = "api_prv_actual_use_dj",
       uniqueConstraints = @UniqueConstraint(name = "uk_api_prv_actual_use_dj_source_refs", columnNames = {"source_refs"}))
@org.hibernate.annotations.Table(appliesTo = "api_prv_actual_use_dj", comment = "대전 이용실태 제공")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiPrvActualUseDj {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("일련번호")
    private Long sn;

    @Column(name = "sigungu", length = 100)
    @Comment("시군구")
    private String sigungu;

    @Column(name = "year", length = 4)
    @Comment("기준연도")
    private String year;

    @Column(name = "depart", length = 100)
    @Comment("담당부서")
    private String depart;

    @Column(name = "ymd", length = 8)
    @Comment("작업완료일")
    private String ymd;

    @Column(name = "yn", length = 10)
    @Comment("완료여부 (완료/미완료)")
    private String yn;

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
