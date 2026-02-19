package com.sync.agent.bojo.entity.target;

import lombok.*;

import javax.persistence.*;
import java.sql.Date;

/**
 * SEC_JEWON - Target 제원 테이블 (Internal Zone)
 *
 * 사용: loader-dmz (쓰기)
 * 흐름: if_sec_jewon (DMZ) → sec_jewon (Internal)
 */
@Entity
@Table(name = "sec_jewon",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_sec_jewon_obsv_code",
           columnNames = {"obsv_code"}
       ))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SecJewon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

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
