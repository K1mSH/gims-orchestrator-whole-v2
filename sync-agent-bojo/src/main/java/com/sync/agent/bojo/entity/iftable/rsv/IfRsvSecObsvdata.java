package com.sync.agent.bojo.entity.iftable.rsv;

import lombok.*;
import org.hibernate.annotations.Comment;

import javax.persistence.*;
import java.sql.Date;
import java.sql.Time;
import java.time.LocalDateTime;

/**
 * IF_RSV_SEC_OBSVDATA - IF 수신용 관측데이터 테이블 (Target DB)
 *
 * 사용: relay-dmz-rsv-bojo (쓰기), loader-dmz (읽기)
 * 흐름: sec_obsvdata_view (External) → if_rsv_sec_obsvdata (Target DB) → sec_obsvdata (Target DB)
 *
 * PK: Auto-generated (외부 DB 10개가 1개 IF_RSV에 연결되므로 Source ID 충돌 가능)
 * UK: source_refs - 소스 레코드 고유 식별자 (zone:dsId:tbId:pk 형식)
 */
@Entity
@Table(name = "if_rsv_sec_obsvdata",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_if_rsv_sec_obsvdata_source_refs",
           columnNames = {"source_refs"}
       ))
@org.hibernate.annotations.Table(appliesTo = "if_rsv_sec_obsvdata", comment = "IF_RSV 보조지하수관측망 관측데이터 (RCV 적재 → Loader가 EAV 확장하여 Target 적재)")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IfRsvSecObsvdata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    @Comment("자동 증가 PK")
    private Integer id;

    // ========== 비즈니스 컬럼 (복합 키) ==========

    @Column(name = "obsv_code")
    @Comment("관측소 코드")
    private String obsvCode;

    @Column(name = "obsv_date")
    @Comment("관측 일자")
    private Date obsvDate;

    @Column(name = "obsv_time")
    @Comment("관측 시각")
    private Time obsvTime;

    // ========== 비즈니스 컬럼 (데이터) ==========

    @Column(name = "gwdep")
    @Comment("지하수위 (m)")
    private Double gwdep;

    @Column(name = "gwtemp")
    @Comment("지하수온도 (°C)")
    private Double gwtemp;

    @Column(name = "ec")
    @Comment("전기전도도 (μS/cm)")
    private Double ec;

    @Column(name = "remark")
    @Comment("비고")
    private String remark;

    // ========== IF 메타 컬럼 ==========

    @Column(name = "source_refs", columnDefinition = "TEXT")
    @Comment("원본 참조키 (UK, 외부 DB PK 조합)")
    private String sourceRefs;

    @Builder.Default
    @Column(name = "link_status", length = 20)
    @Comment("처리 상태 (PENDING/SUCCESS/FAILED)")
    private String linkStatus = "PENDING";

    @Column(name = "extracted_at")
    @Comment("RCV 추출 시각")
    private LocalDateTime extractedAt;

    @Column(name = "updated_at")
    @Comment("최종 수정 시각")
    private LocalDateTime updatedAt;

    @Column(name = "execution_id")
    @Comment("처리한 실행 ID")
    private String executionId;
}
