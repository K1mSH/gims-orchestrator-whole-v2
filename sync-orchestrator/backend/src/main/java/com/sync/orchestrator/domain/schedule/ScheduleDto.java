package com.sync.orchestrator.domain.schedule;

import javax.validation.constraints.NotBlank;
import lombok.*;

import java.time.LocalDateTime;

public class ScheduleDto {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateRequest {
        @NotBlank
        private String agentId;
        @NotBlank
        private String cronExpression;
        private Boolean isEnabled;
        private String executionOptions;  // JSON: {"filters":[...]}
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UpdateRequest {
        private String cronExpression;
        private Boolean isEnabled;
        private String executionOptions;  // JSON: {"filters":[...]}
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private Long scheduleId;
        private String agentId;
        private String agentName;
        private String cronExpression;
        private Boolean isEnabled;
        private String executionOptions;
        private LocalDateTime createdAt;

        public static Response from(Schedule schedule) {
            return Response.builder()
                    .scheduleId(schedule.getScheduleId())
                    .agentId(schedule.getAgent().getAgentId())
                    .agentName(schedule.getAgent().getAgentName())
                    .cronExpression(schedule.getCronExpression())
                    .isEnabled(schedule.getIsEnabled())
                    .executionOptions(schedule.getExecutionOptions())
                    .createdAt(schedule.getCreatedAt())
                    .build();
        }
    }
}
