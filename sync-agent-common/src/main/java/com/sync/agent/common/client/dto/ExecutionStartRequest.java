package com.sync.agent.common.client.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ExecutionStartRequest {

    private String executionId;
    private String agentId;
    private LocalDateTime startedAt;
    private String triggeredBy;  // MANUAL, SCHEDULE, CHAIN
}
