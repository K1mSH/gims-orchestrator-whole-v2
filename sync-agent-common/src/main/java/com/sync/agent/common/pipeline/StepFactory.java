package com.sync.agent.common.pipeline;

import com.sync.agent.common.step.StepExecutor;

import java.util.Map;

/**
 * Step 생성 팩토리 인터페이스
 *
 * YAML의 factory-key 값과 매칭되어, 해당 step config로 StepExecutor를 생성한다.
 * 각 모듈(common, bojo, bojo-int)에서 구현체를 @Component로 등록하면
 * StepFactoryRegistry가 자동 수집한다.
 */
public interface StepFactory {

    /**
     * 이 Factory의 매핑 키 — YAML의 factory-key 값과 일치해야 함
     */
    String getFactoryKey();

    /**
     * YAML step config(Map)로 StepExecutor 생성
     *
     * @param stepConfig YAML steps 배열의 개별 항목 (id, name, factory-key, 기타 파라미터)
     * @return 생성된 StepExecutor
     */
    StepExecutor create(Map<String, Object> stepConfig);
}
