package com.sync.agent.common.client.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class StepCallbackRequest {
    private String executionId;
    private String agentId;
    private String stepId;
    private String stepName;  // 표시용 이름 (한글 등)
    private int stepOrder;
    private int totalSteps;
    private String status;    // RUNNING, SUCCESS, FAILED
    private Integer readCount;
    private Integer writeCount;
    private Integer skipCount;
    private Long durationMs;
    private String errorMessage;
    private LocalDateTime timestamp;
}
