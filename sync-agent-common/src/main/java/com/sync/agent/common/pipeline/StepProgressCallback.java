package com.sync.agent.common.pipeline;

import com.sync.agent.common.step.StepResult;

/**
 * Step 진행 상황 콜백 인터페이스
 * Agent마다 다른 Step 구성을 지원하기 위한 확장 포인트
 */
public interface StepProgressCallback {

    /**
     * Step 시작 시 호출
     * @param executionId 실행 ID
     * @param stepId Step ID
     * @param stepName Step 표시 이름
     * @param stepOrder 현재 Step 순서 (1-based)
     * @param totalSteps 전체 Step 수
     */
    void onStepStarted(String executionId, String stepId, String stepName, int stepOrder, int totalSteps);

    /**
     * Step 완료 시 호출
     * @param executionId 실행 ID
     * @param result Step 실행 결과
     * @param stepOrder 현재 Step 순서 (1-based)
     * @param totalSteps 전체 Step 수
     */
    void onStepFinished(String executionId, StepResult result, int stepOrder, int totalSteps);
}
