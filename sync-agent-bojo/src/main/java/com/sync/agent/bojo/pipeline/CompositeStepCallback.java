package com.sync.agent.bojo.pipeline;

import com.sync.agent.common.pipeline.StepProgressCallback;
import com.sync.agent.common.step.StepResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class CompositeStepCallback implements StepProgressCallback {

    private final StepProgressCallback orchestratorCallback;

    @Override
    public void onStepStarted(String executionId, String stepId, String stepName, int stepOrder, int totalSteps) {
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
        if (orchestratorCallback != null) {
            try {
                orchestratorCallback.onStepFinished(executionId, result, stepOrder, totalSteps);
            } catch (Exception e) {
                log.warn("Failed to notify orchestrator step finished: {}", e.getMessage());
            }
        }
    }
}
