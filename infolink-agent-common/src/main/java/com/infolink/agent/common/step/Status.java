package com.infolink.agent.common.step;

/**
 * Step/Pipeline 실행 상태
 *
 * PipelineRunner에서 Step이 FAILED를 반환하면 파이프라인 전체가 중단 (first-failure-stop).
 * SKIPPED는 selectedStepIds에 포함되지 않은 Step에 부여.
 */
public enum Status {
    SUCCESS,
    FAILED,
    SKIPPED
}
