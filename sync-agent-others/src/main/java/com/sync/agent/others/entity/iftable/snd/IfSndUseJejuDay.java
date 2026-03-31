package com.sync.agent.others.entity.iftable.snd;

import lombok.*;
import org.hibernate.annotations.Comment;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * IF_SND 제주 일일 이용량 데이터 엔티티.
 *
 * <p>SND 파이프라인에서 Target DB의 제주 일일 이용량 데이터를 IF_SND 테이블로 추출할 때 사용된다.
 * 복합PK(obsrvt_id + obsr_de) 대신 자동증분 PK를 사용하고, source_refs에 복합키를 저장한다.
 * {@code source_refs}에 UK, {@code execution_id}에 인덱스가 설정되어 있다.</p>
 *
 * <p>테이블: {@code if_snd_use_jeju_day}</p>
 */
@Entity
@Table(name = "if_snd_use_jeju_day",
       uniqueConstraints = @UniqueConstraint(
           name = "uk_if_snd_use_jeju_day_source_refs",
           columnNames = {"source_refs"}
       ),
       indexes = @Index(name = "idx_if_snd_use_jeju_day_exec_id", columnList = "execution_id"))
@org.hibernate.annotations.Table(appliesTo = "if_snd_use_jeju_day", comment = "IF_SND 제주 일일 이용량 데이터")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IfSndUseJejuDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    @Comment("자동증분 PK")
    private Long id;

    // ========== 비즈니스 컬럼 ==========

    @Column(name = "obsrvt_id", length = 30)
    @Comment("관측점 ID")
    private String obsrvtId;

    @Column(name = "obsr_de", length = 8)
    @Comment("관측일 (yyyyMMdd)")
    private String obsrDe;

    @Column(name = "usgqty")
    @Comment("사용량")
    private Double usgqty;

    @Column(name = "last_mesure_value")
    @Comment("최종계측값")
    private Double lastMesureValue;

    @Column(name = "frst_mesure_value")
    @Comment("최초계측값")
    private Double frstMesureValue;

    @Column(name = "dta_sttus_code", length = 2)
    @Comment("데이터상태코드")
    private String dtaSttusCode;

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
