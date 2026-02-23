package com.sync.agent.bojo.entity.iftable.rsv;

import lombok.*;

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
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IfRsvSecObsvdata {

    /**
     * Auto-generated PK (외부 DB들의 ID가 겹칠 수 있으므로)
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    // ========== 비즈니스 컬럼 (복합 키) ==========

    @Column(name = "obsv_code")
    private String obsvCode;

    @Column(name = "obsv_date")
    private Date obsvDate;

    @Column(name = "obsv_time")
    private Time obsvTime;

    // ========== 비즈니스 컬럼 (데이터) ==========

    @Column(name = "gwdep")
    private Double gwdep;

    @Column(name = "gwtemp")
    private Double gwtemp;

    @Column(name = "ec")
    private Double ec;

    @Column(name = "remark")
    private String remark;

    // ========== IF 메타 컬럼 ==========

    @Column(name = "source_refs", columnDefinition = "TEXT")
    private String sourceRefs;

    /** 연계 상태 (PENDING, SUCCESS, FAILED) */
    @Builder.Default
    @Column(name = "link_status", length = 20)
    private String linkStatus = "PENDING";

    @Column(name = "extracted_at")
    private LocalDateTime extractedAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** 이 IF 테이블에 데이터를 쓴 Agent의 execution ID */
    @Column(name = "execution_id")
    private String executionId;
}
