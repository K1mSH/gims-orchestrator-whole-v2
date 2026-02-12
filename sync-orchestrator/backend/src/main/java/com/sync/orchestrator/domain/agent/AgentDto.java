package com.sync.orchestrator.domain.agent;

import javax.validation.constraints.NotBlank;
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
        private String agentId;
        @NotBlank
        private String agentName;
        @NotBlank
        private String endpointUrl;
        @NotBlank
        private String zone;
        private Boolean isActive;
        private AgentType agentType;
        private String sourceDatasourceId;
        private String targetDatasourceId;
        private String description;
        // 선택된 테이블 ID 목록
        private List<Long> sourceTableIds;
        private List<Long> targetTableIds;
        // 실행 파라미터
        private List<ExecutionParamInput> executionParams;
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
        // 실행 파라미터
        private List<ExecutionParamInput> executionParams;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ExecutionParamInput {
        private String paramId;
        private String label;
        private String description;
        private String dataType;
        private String defaultValue;
        private Boolean isEnabled;
        private Integer displayOrder;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ExecutionParamResponse {
        private Long id;
        private String paramId;
        private String label;
        private String description;
        private String dataType;
        private String defaultValue;
        private Boolean isEnabled;
        private Integer displayOrder;

        public static ExecutionParamResponse from(AgentExecutionParam param) {
            return ExecutionParamResponse.builder()
                    .id(param.getId())
                    .paramId(param.getParamId())
                    .label(param.getLabel())
                    .description(param.getDescription())
                    .dataType(param.getDataType())
                    .defaultValue(param.getDefaultValue())
                    .isEnabled(param.getIsEnabled())
                    .displayOrder(param.getDisplayOrder())
                    .build();
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private String agentId;
        private String agentName;
        private String endpointUrl;
        private String zone;
        private Boolean isActive;
        private AgentType agentType;
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
        // 실행 파라미터
        private List<ExecutionParamResponse> executionParams;

        public static Response from(Agent agent) {
            List<Long> sourceIds = agent.getAgentTables().stream()
                    .filter(at -> at.getTableType() == AgentTable.TableType.SOURCE)
                    .map(AgentTable::getDatasourceTableId)
                    .toList();
            List<Long> targetIds = agent.getAgentTables().stream()
                    .filter(at -> at.getTableType() == AgentTable.TableType.TARGET)
                    .map(AgentTable::getDatasourceTableId)
                    .toList();
            List<ExecutionParamResponse> execParams = agent.getExecutionParams().stream()
                    .map(ExecutionParamResponse::from)
                    .toList();

            return Response.builder()
                    .agentId(agent.getAgentId())
                    .agentName(agent.getAgentName())
                    .endpointUrl(agent.getEndpointUrl())
                    .zone(agent.getZone())
                    .isActive(agent.getIsActive())
                    .agentType(agent.getAgentType())
                    .sourceDatasourceId(agent.getSourceDatasourceId())
                    .targetDatasourceId(agent.getTargetDatasourceId())
                    .description(agent.getDescription())
                    .status(agent.getStatus())
                    .lastExecutedAt(agent.getLastExecutedAt())
                    .lastExecutionStatus(agent.getLastExecutionStatus())
                    .createdAt(agent.getCreatedAt())
                    .sourceTableIds(sourceIds)
                    .targetTableIds(targetIds)
                    .executionParams(execParams)
                    .build();
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class HealthCheckResponse {
        private String agentId;
        private AgentStatus status;
        private String message;
    }
}
