package com.infolink.orchestrator.dto;

import com.infolink.orchestrator.entity.Agent;
import com.infolink.orchestrator.entity.AgentStatus;
import com.infolink.orchestrator.entity.AgentTable;
import com.infolink.orchestrator.entity.AgentType;
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
        private Boolean isActive;
        private String sourceDatasourceId;
        private String targetDatasourceId;
        // agent /health 의 historyDatasourceId — sync_log + execution 적재 위치. discover 응답에서 전달받음 (readonly).
        private String historyDatasourceId;
        private String description;
        // 선택된 테이블 ID 목록 (기존 방식, 하위호환)
        private List<Long> sourceTableIds;
        private List<Long> targetTableIds;
        // 테이블명 기반 자동 연결 (auto-discover 방식)
        private List<String> sourceTableNames;
        private List<String> targetTableNames;
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
        private String sourceDatasourceId;
        private String targetDatasourceId;
        private String historyDatasourceId;    // sync_log + execution 적재 위치 (readonly UI 표시)
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
                    .sourceDatasourceId(agent.getSourceDatasourceId())
                    .targetDatasourceId(agent.getTargetDatasourceId())
                    .historyDatasourceId(agent.getHistoryDatasourceId())
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
