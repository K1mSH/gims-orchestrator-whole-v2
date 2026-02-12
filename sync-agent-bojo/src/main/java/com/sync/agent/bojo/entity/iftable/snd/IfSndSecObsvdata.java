package com.sync.agent.bojo.entity.iftable.snd;

import lombok.*;

import javax.persistence.*;
import java.sql.Date;
import java.sql.Time;
import java.time.LocalDateTime;

/**
 * IF_SND_SEC_OBSVDATA - IF 송신용 관측데이터 테이블 (Target DB)
 *
 * 사용: relay-dmz-snd-bojo (쓰기), 외부 시스템 (읽기)
 * 흐름: sec_obsvdata (Target DB) → if_snd_sec_obsvdata (Target DB) → External
 *
 * PK: Source의 ID를 그대로 사용 (auto-generated 아님)
 */
@Entity
@Table(name = "if_snd_sec_obsvdata")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IfSndSecObsvdata {

    /**
     * Source 테이블의 ID를 그대로 사용 (PK)
     * IF 테이블은 소문자 컬럼명 사용
     */
    @Id
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
