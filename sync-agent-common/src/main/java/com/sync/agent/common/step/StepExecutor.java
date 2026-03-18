package com.sync.agent.common.step;

/**
 * 파이프라인 Step 실행 인터페이스
 *
 * PipelineRunner가 순차 실행하는 Step의 공통 계약.
 * 구현체: SourceToIfStep(RCV/SND copy), DefaultLoadStep(DMZ Loader),
 *         InternalLoadStep(Internal Loader), LinkTableUpdateStep(Link 갱신)
 *
 * PipelineConfig에서 구현체를 생성하여 Runner의 steps 리스트에 담으면,
 * Runner는 이 인터페이스만 알고 step.execute(context)로 다형성 실행.
 */
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
