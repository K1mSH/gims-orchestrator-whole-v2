package com.infolink.agent.common.entity;

import javax.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 실행별 매핑 단위 처리 요약 로그
 * per-table이 아닌 per-mapping 단위로 저장 (YAML table-mappings 기반)
 */
@Entity
@Table(name = "sync_log", indexes = {
    @Index(name = "idx_sync_log_execution", columnList = "execution_id"),
    @Index(name = "idx_sync_log_mapping", columnList = "execution_id, mapping_name")
})
@org.hibernate.annotations.Table(appliesTo = "sync_log", comment = "동기화 처리 로그 (매핑 단위 처리 결과)")
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

    @Column(name = "mapping_name", length = 100)
    @Comment("매핑 이름 (YAML table-mappings의 name)")
    private String mappingName;

    @Column(name = "source_tables", length = 4000)
    @Comment("source 테이블 목록 (JSON 배열)")
    private String sourceTables;

    @Column(name = "target_tables", length = 4000)
    @Comment("target 테이블 목록 (JSON 배열)")
    private String targetTables;

    @Column(name = "read_count")
    @Comment("읽기 건수 (source에서 읽은 수)")
    @Builder.Default
    private Long readCount = 0L;

    @Column(name = "write_count")
    @Comment("쓰기 건수 (target에 쓴 수)")
    @Builder.Default
    private Long writeCount = 0L;

    @Column(name = "failed_count")
    @Comment("실패 건수")
    @Builder.Default
    private Long failedCount = 0L;

    @Column(name = "skip_count")
    @Comment("스킵 건수")
    @Builder.Default
    private Long skipCount = 0L;

    @Column(name = "failed_keys", length = 4000)
    @Comment("실패 키 목록")
    private String failedKeys;

    @Column(name = "error_summary", length = 4000)
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

    /** 총 처리 건수 (write + failed) */
    public long getTotalCount() {
        return (writeCount != null ? writeCount : 0L)
             + (failedCount != null ? failedCount : 0L);
    }

    /** 성공 여부 (실패 건수가 0이면 성공) */
    public boolean isSuccess() {
        return failedCount == null || failedCount == 0L;
    }

    /** source_tables JSON 문자열에 특정 테이블명이 포함되어 있는지 */
    public boolean containsSourceTable(String tableName) {
        return sourceTables != null && sourceTables.toLowerCase().contains("\"" + tableName.toLowerCase() + "\"");
    }

    /** target_tables JSON 문자열에 특정 테이블명이 포함되어 있는지 */
    public boolean containsTargetTable(String tableName) {
        return targetTables != null && targetTables.toLowerCase().contains("\"" + tableName.toLowerCase() + "\"");
    }
}
