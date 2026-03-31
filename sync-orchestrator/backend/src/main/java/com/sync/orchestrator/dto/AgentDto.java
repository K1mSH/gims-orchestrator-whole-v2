package com.sync.orchestrator.dto;

import com.sync.orchestrator.entity.Agent;
import com.sync.orchestrator.entity.AgentStatus;
import com.sync.orchestrator.entity.AgentTable;
import com.sync.orchestrator.entity.AgentType;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

public class AgentDto {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateRequest {
        @NotBlank
        private String agentCode;
        @NotBlank
        private String agentName;
        @NotBlank
        private String endpointUrl;
        @NotBlank
        private String zone;
        @NotNull
        private AgentType agentType;
        private String datasourceTag;
        private Boolean isActive;
        private String sourceDatasourceId;
        private String targetDatasourceId;
        private String description;
        // 선택된 테이블 ID 목록
        private List<Long> sourceTableIds;
        private List<Long> targetTableIds;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UpdateRequest {
        private String agentName;
        private String endpointUrl;
        private String zone;
        private Boolean isActive;
        private AgentType agentType;
        private String datasourceTag;
        private String sourceDatasourceId;
        private String targetDatasourceId;
        private String description;
        private AgentStatus status;
        // 선택된 테이블 ID 목록
        private List<Long> sourceTableIds;
        private List<Long> targetTableIds;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private Long id;
        private String agentCode;
        private String agentName;
        private String endpointUrl;
        private String zone;
        private Boolean isActive;
        private AgentType agentType;
        private String datasourceTag;
        private String sourceDatasourceId;
        private String targetDatasourceId;
        private String description;
        private AgentStatus status;
        private LocalDateTime lastExecutedAt;
        private String lastExecutionStatus;
        private LocalDateTime createdAt;
        // 선택된 테이블 ID 목록
        private List<Long> sourceTableIds;
        private List<Long> targetTableIds;
        // Retention 설정 JSON
        private String retentionConfig;

        public static Response from(Agent agent) {
            List<Long> sourceIds = agent.getAgentTables().stream()
                    .filter(at -> at.getTableType() == AgentTable.TableType.SOURCE)
                    .map(AgentTable::getDatasourceTableId)
                    .toList();
            List<Long> targetIds = agent.getAgentTables().stream()
                    .filter(at -> at.getTableType() == AgentTable.TableType.TARGET)
                    .map(AgentTable::getDatasourceTableId)
                    .toList();

            return Response.builder()
                    .id(agent.getId())
                    .agentCode(agent.getAgentCode())
                    .agentName(agent.getAgentName())
                    .endpointUrl(agent.getEndpointUrl())
                    .zone(agent.getZone())
                    .isActive(agent.getIsActive())
                    .agentType(agent.getAgentType())
                    .datasourceTag(agent.getDatasourceTag())
                    .sourceDatasourceId(agent.getSourceDatasourceId())
                    .targetDatasourceId(agent.getTargetDatasourceId())
                    .description(agent.getDescription())
                    .status(agent.getStatus())
                    .lastExecutedAt(agent.getLastExecutedAt())
                    .lastExecutionStatus(agent.getLastExecutionStatus())
                    .createdAt(agent.getCreatedAt())
                    .sourceTableIds(sourceIds)
                    .targetTableIds(targetIds)
                    .retentionConfig(agent.getRetentionConfig())
                    .build();
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class HealthCheckResponse {
        private Long id;
        private String agentCode;
        private AgentStatus status;
        private String message;
    }
}
