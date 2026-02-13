package com.sync.orchestrator.domain.callback;

import lombok.*;

import java.time.LocalDateTime;

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
    }
}
