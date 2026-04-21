package com.sync.agent.provide.pipeline;

import com.sync.agent.common.pipeline.StepProgressCallback;
import com.sync.agent.common.step.StepResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Provide Agent용 OrchestratorClient 콜백 래퍼.
 *
 * <p>파이프라인 실행 중 각 Step의 시작/종료 이벤트를 Orchestrator에 전달한다.
 * 콜백 호출 실패 시 예외를 삼키고 경고 로그만 남겨, 파이프라인 실행에
 * 영향을 주지 않도록 한다.</p>
 *
 * @see com.sync.agent.common.pipeline.StepProgressCallback
 */
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
                log.warn("[Provide] Orchestrator Step 시작 알림 실패: {}", e.getMessage());
            }
        }
    }

    @Override
    public void onStepFinished(String executionId, StepResult result, int stepOrder, int totalSteps) {
        if (orchestratorCallback != null) {
            try {
                orchestratorCallback.onStepFinished(executionId, result, stepOrder, totalSteps);
            } catch (Exception e) {
                log.warn("[Provide] Orchestrator Step 완료 알림 실패: {}", e.getMessage());
            }
        }
    }
}
