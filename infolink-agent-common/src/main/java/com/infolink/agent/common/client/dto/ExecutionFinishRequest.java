package com.infolink.agent.common.client.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class ExecutionFinishRequest {

    private String executionId;
    private String agentId;
    private String status;
    private int totalReadCount;
    private int totalWriteCount;
    private int totalSkipCount;
    private long durationMs;
    private String errorMessage;
    private LocalDateTime finishedAt;
    private List<StepResultDto> stepResults;

    @Getter
    @Builder
    public static class StepResultDto {
        private String stepId;
        private String status;
        private int readCount;
        private int writeCount;
        private int skipCount;
        private long durationMs;
        private String errorMessage;
        private int stepOrder;
    }
}
