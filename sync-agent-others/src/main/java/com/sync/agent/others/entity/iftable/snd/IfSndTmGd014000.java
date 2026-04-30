package com.sync.agent.others.entity.iftable.snd;

import lombok.*;
import org.hibernate.annotations.Comment;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * IF_SND 나라장터 입찰공고 엔티티.
 *
 * <p>SND 파이프라인에서 api_collector DB의 나라장터 데이터를 IF_SND 테이블로 추출할 때 사용된다.</p>
 *
 * <p>테이블: {@code if_snd_tm_gd014000}</p>
 */
@Entity
@Table(name = "if_snd_tm_gd014000",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_if_snd_tm_gd014000_source_refs",
           columnNames = {"source_refs"}
       ),
       indexes = @Index(name = "idx_if_snd_tm_gd014000_exec_id", columnList = "execution_id"))
@org.hibernate.annotations.Table(appliesTo = "if_snd_tm_gd014000", comment = "IF_SND 나라장터 입찰공고")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IfSndTmGd014000 {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sn")
    @Comment("일련번호 (PK)")
    private Long sn;

    // ========== 비즈니스 컬럼 ==========

    @Column(name = "type", length = 100)
    @Comment("유형 (공사/용역/외자/물품)")
    private String type;

    @Column(name = "bid_pbanc_no", length = 100)
    @Comment("입찰공고번호")
    private String bidPbancNo;

    @Column(name = "bid_pbanc_nm", length = 1000)
    @Comment("입찰공고명")
    private String bidPbancNm;

    @Column(name = "dmd_inst_nm", length = 200)
    @Comment("수요기관명")
    private String dmdInstNm;

    @Column(name = "bid_ddln_dt", length = 100)
    @Comment("입찰마감일시")
    private String bidDdlnDt;

    @Column(name = "bid_pbanc_dtl_lnkg", length = 1000)
    @Comment("공고상세링크")
    private String bidPbancDtlLnkg;

    @Column(name = "use_yn", length = 1)
    @Comment("사용여부")
    private String useYn;

    @Column(name = "reg_ymd", length = 8)
    @Comment("등록일자")
    private String regYmd;

    // ========== IF 메타 컬럼 ==========

    @Column(name = "source_refs", columnDefinition = "TEXT")
    @Comment("원본 참조키 (UK)")
    private String sourceRefs;

    @Builder.Default
    @Column(name = "link_status", length = 20)
    @Comment("연계 상태 (PENDING/SUCCESS/FAILED)")
    private String linkStatus = "PENDING";

    @Column(name = "extracted_at")
    @Comment("추출 시각")
    private LocalDateTime extractedAt;

    @Column(name = "updated_at")
    @Comment("수정 시각")
    private LocalDateTime updatedAt;

    @Column(name = "execution_id")
    @Comment("처리 실행 ID")
    private String executionId;
}
