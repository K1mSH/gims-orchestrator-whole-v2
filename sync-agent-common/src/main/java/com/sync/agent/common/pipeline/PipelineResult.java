package com.sync.agent.common.pipeline;

import com.sync.agent.common.step.Status;
import com.sync.agent.common.step.StepResult;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class PipelineResult {

    private String executionId;
    private String pipelineId;
    private Status status;
    private List<StepResult> stepResults;
    private long totalDurationMs;
    private String errorMessage;

    public int getTotalReadCount() {
        return stepResults.stream()
                .mapToInt(StepResult::getReadCount)
                .sum();
    }

    public int getTotalWriteCount() {
        return stepResults.stream()
                .mapToInt(StepResult::getWriteCount)
                .sum();
    }

    public int getTotalSkipCount() {
        return stepResults.stream()
                .mapToInt(StepResult::getSkipCount)
                .sum();
    }
}
