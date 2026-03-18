package com.sync.agent.bojo.config;

import com.sync.agent.bojo.loader.step.DefaultLoadStep;
import com.sync.agent.common.pipeline.PipelineRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.util.List;

/**
 * Loader 파이프라인 설정
 *
 * 실행 모드별 Step 구현체를 PipelineRunner로 등록
 * - default: DefaultLoadStep (증분/시간지정/전체재적재)
 * - (향후) 새 모드 추가 시 Step 구현체 주입 + 등록
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class LoaderPipelineConfig {

    private final AgentConfigLoader agentConfigLoader;
    private final PipelineRegistry pipelineRegistry;
    private final DefaultLoadStep defaultLoadStep;

    @PostConstruct
    public void registerLoaderPipelines() {
        List<AgentDefinition> loaderDefs = agentConfigLoader.getAgentsByType("LOADER");
        log.info("Registering {} Loader pipelines", loaderDefs.size());

        for (AgentDefinition def : loaderDefs) {
            try {
                // default 모드
                PipelineRunner defaultRunner = new PipelineRunner(def.getAgentCode(), List.of(defaultLoadStep));
                pipelineRegistry.register(def.getAgentCode(), "LOADER", "default", defaultRunner);

                // (향후) 새 모드 등록 예시:
                // PipelineRunner regionRunner = new PipelineRunner(def.getAgentCode(), List.of(regionLoadStep));
                // pipelineRegistry.register(def.getAgentCode(), "LOADER", "manual-region", regionRunner);

                log.info("Registered Loader pipeline: {}", def.getAgentCode());
            } catch (Exception e) {
                log.error("Failed to register Loader pipeline: {}", def.getAgentCode(), e);
            }
        }
    }
}
