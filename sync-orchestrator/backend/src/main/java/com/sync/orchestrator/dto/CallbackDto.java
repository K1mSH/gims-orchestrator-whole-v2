package com.sync.orchestrator.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

public class CallbackDto {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StartedRequest {
        private String executionId;
        private String agentId;
        private LocalDateTime startedAt;
        private String triggeredBy;  // MANUAL, SCHEDULE, CHAIN
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FinishedRequest {
        private String executionId;
        private String agentId;
        private String status;
        private Integer totalReadCount;
        private Integer totalWriteCount;
        private Integer totalSkipCount;
        private Long durationMs;
        private String errorMessage;
        private LocalDateTime finishedAt;
        private List<StepResultItem> stepResults;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StepResultItem {
        private String stepId;
        private String status;       // SUCCESS, FAILED, SKIPPED
        private Integer readCount;
        private Integer writeCount;
        private Integer skipCount;
        private Long durationMs;
        private String errorMessage;
        private Integer stepOrder;
    }
}
