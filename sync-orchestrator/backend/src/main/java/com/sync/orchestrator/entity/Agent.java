package com.sync.orchestrator.entity;

import com.sync.orchestrator.entity.Schedule;

import javax.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "agent")
@org.hibernate.annotations.Table(appliesTo = "agent", comment = "동기화 에이전트")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Agent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    @Comment("에이전트 PK")
    private Long id;

    @Column(name = "agent_code", length = 50, nullable = false, unique = true)
    @Comment("에이전트 고유 코드")
    private String agentCode;

    @Column(name = "agent_name", length = 100, nullable = false)
    @Comment("에이전트 표시명")
    private String agentName;

    @Column(name = "endpoint_url", length = 255, nullable = false)
    @Comment("에이전트 REST 엔드포인트 URL")
    private String endpointUrl;

    @Column(name = "zone", length = 50, nullable = false)
    @Comment("네트워크 존 (DMZ/INTERNAL)")
    private String zone;

    @Column(name = "is_active")
    @Comment("활성화 여부")
    @Builder.Default
    private Boolean isActive = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "agent_type", length = 20, nullable = false)
    @Comment("에이전트 유형 (RCV/LOADER/SND/DB_CON_PROXY)")
    private AgentType agentType;

    @Column(name = "datasource_tag", length = 50)
    @Comment("데이터소스 태그")
    private String datasourceTag;

    @Column(name = "source_datasource_id", length = 50)
    @Comment("소스 데이터소스 ID")
    private String sourceDatasourceId;

    @Column(name = "target_datasource_id", length = 50)
    @Comment("타겟 데이터소스 ID")
    private String targetDatasourceId;

    @Column(name = "description", columnDefinition = "TEXT")
    @Comment("설명")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    @Comment("상태 (ONLINE/OFFLINE/ERROR)")
    @Builder.Default
    private AgentStatus status = AgentStatus.OFFLINE;

    @Column(name = "last_executed_at")
    @Comment("마지막 실행 시각")
    private LocalDateTime lastExecutedAt;

    @Column(name = "last_execution_status", length = 20)
    @Comment("마지막 실행 결과")
    private String lastExecutionStatus;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    @Comment("생성 시각")
    private LocalDateTime createdAt;

    // 연관 엔티티 cascade 삭제
    @OneToMany(mappedBy = "agent", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Schedule> schedules = new ArrayList<>();

    // Agent가 사용할 테이블 목록
    @OneToMany(mappedBy = "agent", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<AgentTable> agentTables = new ArrayList<>();

    @Column(name = "retention_config", columnDefinition = "TEXT")
    @Comment("데이터 보존(Retention) 설정 JSON")
    private String retentionConfig;
}
