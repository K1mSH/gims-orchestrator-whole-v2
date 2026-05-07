package com.infolink.agent.common.pipeline;

import com.infolink.agent.common.step.Status;
import com.infolink.agent.common.step.StepResult;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * PipelineRunner.run() 실행 결과
 *
 * 전체 파이프라인의 상태(SUCCESS/FAILED), Step별 결과 목록, 총 소요 시간을 담는다.
 * OrchestratorClient.notifyFinished()에서 이 객체를 Orchestrator에 콜백 전송.
 * ExecutionService.recordExecutionFinish()에서 이 객체의 통계를 Agent 로컬 DB에 기록.
 */
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
