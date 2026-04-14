package com.sync.agent.others.entity.iftable.snd;

import lombok.*;
import org.hibernate.annotations.Comment;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * IF_SND 뉴스 엔티티.
 *
 * <p>SND 파이프라인에서 api_collector DB의 뉴스 데이터를 IF_SND 테이블로 추출할 때 사용된다.</p>
 *
 * <p>테이블: {@code if_snd_tm_gd014001}</p>
 */
@Entity
@Table(name = "if_snd_tm_gd014001",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_if_snd_tm_gd014001_source_refs",
           columnNames = {"source_refs"}
       ),
       indexes = @Index(name = "idx_if_snd_tm_gd014001_exec_id", columnList = "execution_id"))
@org.hibernate.annotations.Table(appliesTo = "if_snd_tm_gd014001", comment = "IF_SND 뉴스")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IfSndTmGd014001 {

    @Id
    @Column(name = "sn")
    @Comment("일련번호 (PK)")
    private Long sn;

    // ========== 비즈니스 컬럼 ==========

    @Column(name = "ttl", length = 500, nullable = false)
    @Comment("제목")
    private String ttl;

    @Column(name = "orgnl_url", length = 500, nullable = false)
    @Comment("원본URL")
    private String orgnlUrl;

    @Column(name = "link", length = 1000)
    @Comment("링크")
    private String link;

    @Column(name = "expln", length = 4000)
    @Comment("설명")
    private String expln;

    @Column(name = "pstg_ymd", length = 500)
    @Comment("게시일자")
    private String pstgYmd;

    @Column(name = "press_nm", length = 100)
    @Comment("언론사명")
    private String pressNm;

    @Column(name = "vstr_cnt")
    @Comment("방문자수")
    private Long vstrCnt;

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
