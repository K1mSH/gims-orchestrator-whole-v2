package com.sync.agent.common.pipeline;

import com.sync.agent.common.step.StepExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * StepFactory 레지스트리
 *
 * Spring이 모든 StepFactory 구현체를 자동 주입하여 factory-key → Factory 매핑을 구성한다.
 * PipelineAssembler가 YAML step config의 factory-key로 이 레지스트리를 조회하여 Step을 생성한다.
 */
@Slf4j
@Component
public class StepFactoryRegistry {

    private final Map<String, StepFactory> factories = new HashMap<>();

    public StepFactoryRegistry(List<StepFactory> factoryList) {
        for (StepFactory f : factoryList) {
            factories.put(f.getFactoryKey(), f);
            log.info("Registered StepFactory: factory-key={}, class={}",
                    f.getFactoryKey(), f.getClass().getSimpleName());
        }
        log.info("Total {} StepFactory registered: {}", factories.size(), factories.keySet());
    }

    /**
     * YAML step config로 StepExecutor 생성
     *
     * @param stepConfig YAML steps 배열의 개별 항목 (factory-key 필수)
     * @return 생성된 StepExecutor
     */
    public StepExecutor create(Map<String, Object> stepConfig) {
        String factoryKey = (String) stepConfig.get("factory-key");
        if (factoryKey == null) {
            throw new IllegalArgumentException("factory-key is required in step config: " + stepConfig);
        }

        StepFactory factory = factories.get(factoryKey);
        if (factory == null) {
            throw new IllegalArgumentException("Unknown factory-key: " + factoryKey
                    + ". Registered: " + factories.keySet());
        }

        return factory.create(stepConfig);
    }
}
