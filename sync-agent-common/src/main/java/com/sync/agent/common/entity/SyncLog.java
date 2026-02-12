package com.sync.agent.common.entity;

import javax.persistence.*;
import lombok.*;
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
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "execution_id", length = 100, nullable = false)
    private String executionId;

    @Column(name = "step_id", length = 50)
    private String stepId;

    /** 테이블명 (예: if_sec_jewon_view, sec_jewon_view) */
    @Column(name = "table_name", length = 100)
    private String tableName;

    /** 테이블 타입: SOURCE, IF, TARGET */
    @Column(name = "table_type", length = 20)
    private String tableType;

    /** 성공 건수 */
    @Column(name = "success_count")
    @Builder.Default
    private Long successCount = 0L;

    /** 실패 건수 */
    @Column(name = "failed_count")
    @Builder.Default
    private Long failedCount = 0L;

    /** 스킵 건수 */
    @Column(name = "skip_count")
    @Builder.Default
    private Long skipCount = 0L;

    /** 실패한 레코드의 source_pk 목록 (JSON 배열 또는 콤마 구분) */
    @Column(name = "failed_keys", columnDefinition = "TEXT")
    private String failedKeys;

    /** 에러 요약 (첫 번째 에러 또는 대표 에러) */
    @Column(name = "error_summary", columnDefinition = "TEXT")
    private String errorSummary;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // ========== Helper Methods ==========

    /** 총 처리 건수 */
    public long getTotalCount() {
        return (successCount != null ? successCount : 0L)
             + (failedCount != null ? failedCount : 0L)
             + (skipCount != null ? skipCount : 0L);
    }

    /** 성공 여부 (실패 건수가 0이면 성공) */
    public boolean isSuccess() {
        return failedCount == null || failedCount == 0L;
    }
}
