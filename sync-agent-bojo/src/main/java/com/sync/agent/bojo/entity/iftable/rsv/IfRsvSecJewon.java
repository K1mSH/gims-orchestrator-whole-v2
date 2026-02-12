package com.sync.agent.bojo.entity.iftable.rsv;

import lombok.*;

import javax.persistence.*;
import java.sql.Date;
import java.time.LocalDateTime;

/**
 * IF_RSV_SEC_JEWON - IF 수신용 제원 테이블 (Target DB)
 *
 * 사용: relay-dmz-rsv-bojo (쓰기), loader-dmz (읽기)
 * 흐름: sec_jewon_view (External) → if_rsv_sec_jewon (Target DB) → sec_jewon (Target DB)
 *
 * PK: Auto-generated (외부 DB 10개가 1개 IF_RSV에 연결되므로 Source ID 충돌 가능)
 * UK: obsv_code - 관측소 코드는 전체적으로 unique
 */
@Entity
@Table(name = "if_rsv_sec_jewon",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_if_rsv_sec_jewon_obsv_code",
           columnNames = {"obsv_code"}
       ))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IfRsvSecJewon {

    /**
     * Auto-generated PK (외부 DB들의 ID가 겹칠 수 있으므로)
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    // ========== 비즈니스 컬럼 ==========

    @Column(name = "obsv_code")
    private String obsvCode;

    @Column(name = "obsv_name")
    private String obsvName;

    @Column(name = "well")
    private Integer well;

    @Column(name = "sido")
    private String sido;

    @Column(name = "sigungu")
    private String sigungu;

    @Column(name = "upmyundo")
    private String upmyundo;

    @Column(name = "bunji")
    private String bunji;

    @Column(name = "ri")
    private String ri;

    @Column(name = "x")
    private String x;

    @Column(name = "y")
    private String y;

    @Column(name = "pyogo")
    private Double pyogo;

    @Column(name = "insdate")
    private Date insdate;

    @Column(name = "guldep")
    private Double guldep;

    @Column(name = "guldia")
    private Double guldia;

    @Column(name = "regdate")
    private Date regdate;

    @Column(name = "casing_height")
    private Double casingHeight;

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
