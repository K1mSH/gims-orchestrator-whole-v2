package com.sync.agent.common.entity;

import javax.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * UPSERT 이력 관리
 * 레코드 단위 처리 이력을 기록하여, execution_id/source_refs가 덮어씌워져도
 * 이전 실행 이력을 추적할 수 있도록 함.
 */
@Entity
@Table(name = "sync_record_history", indexes = {
    @Index(name = "idx_srh_execution", columnList = "execution_id"),
    @Index(name = "idx_srh_record", columnList = "table_name, record_key")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncRecordHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** 이 작업을 수행한 실행 ID */
    @Column(name = "execution_id", length = 100, nullable = false)
    private String executionId;

    /** Step ID */
    @Column(name = "step_id", length = 50)
    private String stepId;

    /** 대상 테이블 (if_rsv_sec_jewon, sec_jewon 등) */
    @Column(name = "table_name", length = 100)
    private String tableName;

    /** 레코드 비즈니스 키 (obsv_code 또는 복합키 obsv_code|date|time) */
    @Column(name = "record_key", length = 500)
    private String recordKey;

    /** 처리 액션: INSERT, UPDATE, UPSERT */
    @Column(name = "action", length = 20)
    private String action;

    /** 이 시점의 source_refs 값 */
    @Column(name = "source_refs", columnDefinition = "TEXT")
    private String sourceRefs;

    /** 처리 시각 */
    @Column(name = "processed_at")
    private LocalDateTime processedAt;
}
