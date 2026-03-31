package com.sync.agent.others.entity.iftable.snd;

import lombok.*;
import org.hibernate.annotations.Comment;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * IF_SND 이용량 상태 데이터 엔티티.
 *
 * <p>SND 파이프라인에서 Target DB의 이용량 상태 데이터를 IF_SND 테이블로 추출할 때 사용된다.
 * {@code source_refs}에 UK, {@code execution_id}에 인덱스가 설정되어 있다.</p>
 *
 * <p>테이블: {@code if_snd_use_status_data}</p>
 */
@Entity
@Table(name = "if_snd_use_status_data",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_if_snd_use_status_data_source_refs",
           columnNames = {"source_refs"}
       ),
       indexes = @Index(name = "idx_if_snd_use_status_data_exec_id", columnList = "execution_id"))
@org.hibernate.annotations.Table(appliesTo = "if_snd_use_status_data", comment = "IF_SND 이용량 상태 데이터")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IfSndUseStatusData {

    @Id
    @Column(name = "sn")
    @Comment("일련번호 (PK)")
    private Long sn;

    // ========== 비즈니스 컬럼 ==========

    @Column(name = "telno")
    @Comment("전화번호")
    private String telno;

    @Column(name = "obsr_dt")
    @Comment("관측일시")
    private LocalDateTime obsrDt;

    @Column(name = "last_change_dt")
    @Comment("최종변경일시")
    private LocalDateTime lastChangeDt;

    @Column(name = "stat")
    @Comment("상태")
    private String stat;

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
