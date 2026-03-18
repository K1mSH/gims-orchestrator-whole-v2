package com.sync.agent.common.step;

import lombok.Builder;
import lombok.Getter;

/**
 * 개별 Step 실행 결과
 *
 * StepExecutor.execute()가 반환하며, PipelineRunner가 stepResults 리스트에 수집.
 * 팩토리 메서드: success(), failed(), skipped()로 생성.
 */
@Getter
@Builder
public class StepResult {

    private String stepId;
    private Status status;
    private int readCount;
    private int writeCount;
    private int skipCount;
    private long durationMs;
    private String errorMessage;
    private String sourceTable;
    private String targetTable;

    public static StepResult success(String stepId, int readCount, int writeCount, long durationMs) {
        return StepResult.builder()
                .stepId(stepId)
                .status(Status.SUCCESS)
                .readCount(readCount)
                .writeCount(writeCount)
                .skipCount(0)
                .durationMs(durationMs)
                .build();
    }

    public static StepResult failed(String stepId, String errorMessage, long durationMs) {
        return StepResult.builder()
                .stepId(stepId)
                .status(Status.FAILED)
                .errorMessage(errorMessage)
                .durationMs(durationMs)
                .build();
    }

    public static StepResult skipped(String stepId, String reason) {
        return StepResult.builder()
                .stepId(stepId)
                .status(Status.SKIPPED)
                .errorMessage(reason)
                .durationMs(0)
                .build();
    }
}
