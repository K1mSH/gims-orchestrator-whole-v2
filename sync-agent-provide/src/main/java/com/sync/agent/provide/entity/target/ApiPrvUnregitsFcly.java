package com.sync.agent.provide.entity.target;

import lombok.*;
import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 대전 미등록시설 요약 제공용 테이블 (Type B — B17)
 *
 * 레거시: OPN unRegitsFclySmrize
 * 원본: Oracle TM_GD00301 (레거시) → TM_GD83001 (표준화)
 * 전처리: UNION ALL + 집계 COUNT(USE_STTUS_CTNT/PROCESS_CTNT)
 * 복합 PK: ctpv_nm + sgg_nm
 */
@Entity
@Table(name = "api_prv_unregits_fcly")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(ApiPrvUnregitsFcly.PK.class)
public class ApiPrvUnregitsFcly {

    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class PK implements Serializable {
        private String ctpvNm;
        private String sggNm;
    }

    /** 시도 — 레거시: SIDO → CTPV_NM */
    @Id
    @Column(name = "ctpv_nm", length = 40)
    private String ctpvNm;

    /** 시군구 — 레거시: SIGUNGU → SGG_NM */
    @Id
    @Column(name = "sgg_nm", length = 40)
    private String sggNm;

    /** 합계 — 레거시: TOTAL */
    @Column(name = "total")
    private Integer total;

    /** 사용중 — 레거시: USED */
    @Column(name = "used")
    private Integer used;

    /** 미사용 — 레거시: UNUSED */
    @Column(name = "unused")
    private Integer unused;

    /** 미확인 — 레거시: UNDEFINED */
    @Column(name = "undefined")
    private Integer undefined;

    /** 허가 — 레거시: PERMISSION */
    @Column(name = "permission")
    private Integer permission;

    /** 신고 — 레거시: REGISTER */
    @Column(name = "register")
    private Integer register;

    /** 원상복구 — 레거시: RESTORE */
    @Column(name = "restore")
    private Integer restore;

    /** 시설없음 — 레거시: NONE */
    @Column(name = "none_cnt")
    private Integer noneCnt;

    // ── 추적 컬럼 ──

    @Column(name = "execution_id", length = 100)
    private String executionId;

    @Column(name = "source_refs", length = 4000)
    private String sourceRefs;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
