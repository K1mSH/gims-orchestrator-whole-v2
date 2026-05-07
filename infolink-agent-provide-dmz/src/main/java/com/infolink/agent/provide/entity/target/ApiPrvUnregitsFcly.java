package com.infolink.agent.provide.entity.target;

import lombok.*;
import org.hibernate.annotations.Comment;

import javax.persistence.*;
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
@Table(name = "api_prv_unregits_fcly",
       uniqueConstraints = @UniqueConstraint(name = "uk_api_prv_unregits_fcly_source_refs", columnNames = {"source_refs"}))
@org.hibernate.annotations.Table(appliesTo = "api_prv_unregits_fcly", comment = "대전 미등록시설 요약 제공")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiPrvUnregitsFcly {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("일련번호")
    private Long sn;

    @Column(name = "ctpv_nm", length = 40)
    @Comment("시도")
    private String ctpvNm;

    @Column(name = "sgg_nm", length = 40)
    @Comment("시군구")
    private String sggNm;

    @Column(name = "total")
    @Comment("합계")
    private Integer total;

    @Column(name = "used")
    @Comment("사용중")
    private Integer used;

    @Column(name = "unused")
    @Comment("미사용")
    private Integer unused;

    @Column(name = "undefined")
    @Comment("미확인")
    private Integer undefined;

    @Column(name = "permission")
    @Comment("허가")
    private Integer permission;

    @Column(name = "register")
    @Comment("신고")
    private Integer register;

    @Column(name = "restore")
    @Comment("원상복구")
    private Integer restore;

    @Column(name = "none_cnt")
    @Comment("시설없음")
    private Integer noneCnt;

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
