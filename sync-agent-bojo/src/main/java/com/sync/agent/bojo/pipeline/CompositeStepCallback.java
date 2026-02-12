package com.sync.agent.bojo.pipeline;

import com.sync.agent.common.service.StepLogService;
import com.sync.agent.common.pipeline.StepProgressCallback;
import com.sync.agent.common.step.StepResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class CompositeStepCallback implements StepProgressCallback {

    private final StepLogService stepLogService;
    private final StepProgressCallback orchestratorCallback;

    @Override
    public void onStepStarted(String executionId, String stepId, String stepName, int stepOrder, int totalSteps) {
        try {
            stepLogService.startStep(executionId, stepId, stepName, stepOrder, totalSteps);
        } catch (Exception e) {
            log.warn("Failed to save step log locally: {}", e.getMessage());
        }
        if (orchestratorCallback != null) {
            try {
                orchestratorCallback.onStepStarted(executionId, stepId, stepName, stepOrder, totalSteps);
            } catch (Exception e) {
                log.warn("Failed to notify orchestrator step started: {}", e.getMessage());
            }
        }
    }

    @Override
    public void onStepFinished(String executionId, StepResult result, int stepOrder, int totalSteps) {
        try {
            stepLogService.finishStep(executionId, result.getStepId(), result.getStatus().name(),
                    result.getReadCount(), result.getWriteCount(), result.getSkipCount(),
                    result.getErrorMessage(), result.getSourceTable(), result.getTargetTable());
        } catch (Exception e) {
            log.warn("Failed to update step log locally: {}", e.getMessage());
        }
        if (orchestratorCallback != null) {
            try {
                orchestratorCallback.onStepFinished(executionId, result, stepOrder, totalSteps);
            } catch (Exception e) {
                log.warn("Failed to notify orchestrator step finished: {}", e.getMessage());
            }
        }
    }
}
