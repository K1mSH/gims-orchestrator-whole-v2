package com.sync.orchestrator.domain.agent;

import com.sync.orchestrator.domain.chain.AgentChainMember;
import com.sync.orchestrator.domain.schedule.Schedule;

import javax.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "agent")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Agent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "agent_code", length = 50, nullable = false, unique = true)
    private String agentCode;

    @Column(name = "agent_name", length = 100, nullable = false)
    private String agentName;

    @Column(name = "endpoint_url", length = 255, nullable = false)
    private String endpointUrl;

    @Column(name = "zone", length = 50, nullable = false)
    private String zone;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "agent_type", length = 20, nullable = false)
    private AgentType agentType;

    @Column(name = "datasource_tag", length = 50)
    private String datasourceTag;

    @Column(name = "source_datasource_id", length = 50)
    private String sourceDatasourceId;

    @Column(name = "target_datasource_id", length = 50)
    private String targetDatasourceId;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    @Builder.Default
    private AgentStatus status = AgentStatus.OFFLINE;

    @Column(name = "last_executed_at")
    private LocalDateTime lastExecutedAt;

    @Column(name = "last_execution_status", length = 20)
    private String lastExecutionStatus;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // 연관 엔티티 cascade 삭제
    @OneToMany(mappedBy = "agent", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Schedule> schedules = new ArrayList<>();

    @OneToMany(mappedBy = "agent", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<AgentChainMember> chainMembers = new ArrayList<>();

    // Agent가 사용할 테이블 목록
    @OneToMany(mappedBy = "agent", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<AgentTable> agentTables = new ArrayList<>();

    // Agent 실행 파라미터 메타데이터
    @OneToMany(mappedBy = "agent", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<AgentExecutionParam> executionParams = new ArrayList<>();

    // Agent Step 메타데이터
    @OneToMany(mappedBy = "agent", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<AgentStepDefinition> stepDefinitions = new ArrayList<>();
}
