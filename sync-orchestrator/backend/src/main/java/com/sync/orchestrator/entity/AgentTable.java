package com.sync.orchestrator.entity;

import javax.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;

@Entity
@Table(name = "agent_table",
        uniqueConstraints = @UniqueConstraint(columnNames = {"agent_id", "datasource_table_id"}))
@org.hibernate.annotations.Table(appliesTo = "agent_table", comment = "에이전트-테이블 매핑")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentTable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("PK")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @Column(name = "datasource_table_id", nullable = false)
    @Comment("데이터소스 테이블 FK")
    private Long datasourceTableId;

    @Enumerated(EnumType.STRING)
    @Column(name = "table_type", length = 20, nullable = false)
    @Comment("테이블 유형 (SOURCE/TARGET)")
    private TableType tableType;

    public enum TableType {
        SOURCE, TARGET
    }
}
