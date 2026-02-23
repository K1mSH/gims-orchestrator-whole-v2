package com.sync.agent.bojo.entity.iftable.snd;

import lombok.*;

import javax.persistence.*;
import java.sql.Date;
import java.time.LocalDateTime;

/**
 * IF_SND_SEC_JEWON - IF 송신용 제원 테이블 (Target DB)
 *
 * 사용: relay-dmz-snd-bojo (쓰기), 외부 시스템 (읽기)
 * 흐름: sec_jewon (Target DB) → if_snd_sec_jewon (Target DB) → External
 *
 * PK: Source의 ID를 그대로 사용 (auto-generated 아님)
 */
@Entity
@Table(name = "if_snd_sec_jewon",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_if_snd_sec_jewon_source_refs",
           columnNames = {"source_refs"}
       ))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IfSndSecJewon {

    /**
     * Source 테이블의 ID를 그대로 사용 (PK)
     * IF 테이블은 소문자 컬럼명 사용
     */
    @Id
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
