package com.sync.agent.common.step;

public interface StepExecutor {

    String getStepId();

    /**
     * Step 표시 이름 (UI에서 보여질 이름, 한글 가능)
     * 기본값은 stepId와 동일
     */
    default String getStepName() {
        return getStepId();
    }

    StepResult execute(StepContext context);
}
