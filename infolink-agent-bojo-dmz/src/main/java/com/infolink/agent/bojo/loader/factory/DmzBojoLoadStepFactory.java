package com.infolink.agent.bojo.loader.factory;

import com.infolink.agent.bojo.loader.step.DmzBojoLoadStep;
import com.infolink.agent.common.pipeline.StepFactory;
import com.infolink.agent.common.step.StepExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * dmz-bojo-load Factory (bojo 전용)
 *
 * DMZ Loader Step 생성. 세부 설정(@Value 등)은 DmzBojoLoadStep 내부에서 처리.
 */
@Component
@RequiredArgsConstructor
public class DmzBojoLoadStepFactory implements StepFactory {

    private final DmzBojoLoadStep dmzBojoLoadStep;

    @Override
    public String getFactoryKey() {
        return "dmz-bojo-load";
    }

    @Override
    public StepExecutor create(Map<String, Object> config) {
        return dmzBojoLoadStep;
    }
}
