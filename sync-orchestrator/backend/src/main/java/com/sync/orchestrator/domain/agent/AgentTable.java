package com.sync.orchestrator.domain.agent;

import javax.persistence.*;
import lombok.*;

/**
 * Agent가 동기화에 사용할 테이블 매핑
 * Datasource에 등록된 테이블 중 Agent가 실제 사용할 테이블을 선택
 */
@Entity
@Table(name = "agent_table",
        uniqueConstraints = @UniqueConstraint(columnNames = {"agent_id", "datasource_table_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentTable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @Column(name = "datasource_table_id", nullable = false)
    private Long datasourceTableId;

    /**
     * SOURCE: source datasource의 테이블
     * TARGET: target datasource의 테이블
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "table_type", length = 20, nullable = false)
    private TableType tableType;

    public enum TableType {
        SOURCE, TARGET
    }
}
