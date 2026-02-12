package com.sync.orchestrator.domain.callback;

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
        private List<StepResultDto> stepResults;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StepResultDto {
        private String stepId;
        private String status;
        private Integer readCount;
        private Integer writeCount;
        private Integer skipCount;
        private Long durationMs;
        private String errorMessage;
        private Integer stepOrder;
    }

    /**
     * 실시간 Step 진행 상황 콜백 DTO
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StepCallbackRequest {
        private String executionId;
        private String agentId;
        private String stepId;
        private String stepName;    // 표시용 이름 (한글 등)
        private Integer stepOrder;
        private Integer totalSteps;
        private String status;      // RUNNING, SUCCESS, FAILED
        private Integer readCount;
        private Integer writeCount;
        private Integer skipCount;
        private Long durationMs;
        private String errorMessage;
        private LocalDateTime timestamp;
    }
}
