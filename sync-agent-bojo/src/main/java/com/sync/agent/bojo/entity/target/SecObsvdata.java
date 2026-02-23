package com.sync.agent.bojo.entity.target;

import lombok.*;

import javax.persistence.*;
import java.sql.Date;
import java.sql.Time;

/**
 * SEC_OBSVDATA - Target 관측데이터 테이블 (Internal Zone)
 *
 * 사용: loader-dmz (쓰기)
 * 흐름: if_sec_obsvdata (DMZ) → sec_obsvdata (Internal)
 */
@Entity
@Table(name = "sec_obsvdata",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_sec_obsvdata_source_refs",
           columnNames = {"source_refs"}
       ))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SecObsvdata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    // ========== 비즈니스 컬럼 ==========

    @Column(name = "obsv_code")
    private String obsvCode;

    @Column(name = "obsv_date")
    private Date obsvDate;

    @Column(name = "obsv_time")
    private Time obsvTime;

    @Column(name = "gwdep")
    private Double gwdep;

    @Column(name = "gwtemp")
    private Double gwtemp;

    @Column(name = "ec")
    private Double ec;

    @Column(name = "remark")
    private String remark;

    // ========== 추적 컬럼 ==========

    /** 원본 데이터 참조 정보 */
    @Column(name = "source_refs", columnDefinition = "TEXT")
    private String sourceRefs;

    /** 연계 상태 (SYNCED, PENDING, ERROR 등) */
    @Column(name = "link_status", length = 20)
    private String linkStatus;

    /** 이 데이터를 쓴 실행 ID */
    @Column(name = "execution_id", length = 100)
    private String executionId;
}
