package com.sync.orchestrator.domain.agent;

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
        private String datasourceTag;
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
    public static class StepDefinitionResponse {
        private Long id;
        private String stepId;
        private String stepName;
        private String description;
        private Integer displayOrder;
        private Boolean enabledByDefault;

        public static StepDefinitionResponse from(AgentStepDefinition def) {
            return StepDefinitionResponse.builder()
                    .id(def.getId())
                    .stepId(def.getStepId())
                    .stepName(def.getStepName())
                    .description(def.getDescription())
                    .displayOrder(def.getDisplayOrder())
                    .enabledByDefault(def.getEnabledByDefault())
                    .build();
        }
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
        // 실행 파라미터
        private List<ExecutionParamResponse> executionParams;
        // Step 정의
        private List<StepDefinitionResponse> stepDefinitions;

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
            List<StepDefinitionResponse> stepDefs = agent.getStepDefinitions().stream()
                    .map(StepDefinitionResponse::from)
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
                    .executionParams(execParams)
                    .stepDefinitions(stepDefs)
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
