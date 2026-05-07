package com.infolink.orchestrator.dto;

import com.infolink.orchestrator.entity.Schedule;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;

public class ScheduleDto {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateRequest {
        @NotNull
        private Long agentId;
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
        private Long agentId;
        private String agentCode;
        private String agentName;
        private String cronExpression;
        private Boolean isEnabled;
        private String executionOptions;
        private LocalDateTime createdAt;

        public static Response from(Schedule schedule) {
            return Response.builder()
                    .scheduleId(schedule.getScheduleId())
                    .agentId(schedule.getAgent().getId())
                    .agentCode(schedule.getAgent().getAgentCode())
                    .agentName(schedule.getAgent().getAgentName())
                    .cronExpression(schedule.getCronExpression())
                    .isEnabled(schedule.getIsEnabled())
                    .executionOptions(schedule.getExecutionOptions())
                    .createdAt(schedule.getCreatedAt())
                    .build();
        }
    }
}
