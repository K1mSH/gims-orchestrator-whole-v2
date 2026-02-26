package com.sync.agent.common.entity;

import javax.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 실행별 테이블 처리 요약 로그
 * 레코드별이 아닌 실행+테이블별로 요약 저장
 */
@Entity
@Table(name = "sync_log", indexes = {
    @Index(name = "idx_sync_log_execution", columnList = "execution_id"),
    @Index(name = "idx_sync_log_table", columnList = "execution_id, table_name")
})
@org.hibernate.annotations.Table(appliesTo = "sync_log", comment = "동기화 처리 로그 (Step별 테이블 단위 처리 결과)")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    @Comment("자동 증가 PK")
    private Long id;

    @Column(name = "execution_id", length = 100, nullable = false)
    @Comment("실행 고유 ID (execution 참조)")
    private String executionId;

    @Column(name = "step_id", length = 50)
    @Comment("Step 식별자")
    private String stepId;

    @Column(name = "table_name", length = 100)
    @Comment("처리 대상 테이블명")
    private String tableName;

    @Column(name = "table_type", length = 20)
    @Comment("테이블 유형 (SOURCE/IF/TARGET)")
    private String tableType;

    @Column(name = "success_count")
    @Comment("성공 건수")
    @Builder.Default
    private Long successCount = 0L;

    @Column(name = "failed_count")
    @Comment("실패 건수")
    @Builder.Default
    private Long failedCount = 0L;

    @Column(name = "skip_count")
    @Comment("스킵 건수")
    @Builder.Default
    private Long skipCount = 0L;

    @Column(name = "failed_keys", columnDefinition = "TEXT")
    @Comment("실패 키 목록")
    private String failedKeys;

    @Column(name = "error_summary", columnDefinition = "TEXT")
    @Comment("오류 요약")
    private String errorSummary;

    @Column(name = "source_pk_column", length = 100)
    @Comment("소스 PK 컬럼명")
    private String sourcePkColumn;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    @Comment("로그 생성 시각")
    private LocalDateTime createdAt;

    // ========== Helper Methods ==========

    /** 총 처리 건수 (skip 제외: 실제 처리된 success + failed만) */
    public long getTotalCount() {
        return (successCount != null ? successCount : 0L)
             + (failedCount != null ? failedCount : 0L);
    }

    /** 성공 여부 (실패 건수가 0이면 성공) */
    public boolean isSuccess() {
        return failedCount == null || failedCount == 0L;
    }
}
