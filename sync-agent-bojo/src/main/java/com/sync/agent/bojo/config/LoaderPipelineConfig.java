package com.sync.agent.bojo.config;

import com.sync.agent.bojo.loader.step.DaejeonLoadStep;
import com.sync.agent.common.pipeline.PipelineRunner;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.util.List;

/**
 * Loader 파이프라인 설정
 *
 * IF_RSV → Target 적재 (JPA 기반)
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class LoaderPipelineConfig {

    private final AgentConfigLoader agentConfigLoader;
    private final PipelineRegistry pipelineRegistry;
    private final DaejeonLoadStep daejeonLoadStep;

    @PostConstruct
    public void registerLoaderPipelines() {
        List<AgentDefinition> loaderDefs = agentConfigLoader.getAgentsByType("LOADER");
        log.info("Registering {} Loader pipelines", loaderDefs.size());

        for (AgentDefinition def : loaderDefs) {
            try {
                PipelineRunner runner = new PipelineRunner(def.getAgentCode(), List.of(daejeonLoadStep));
                pipelineRegistry.register(def.getAgentCode(), "LOADER", runner);
                log.info("Registered Loader pipeline: {}", def.getAgentCode());
            } catch (Exception e) {
                log.error("Failed to register Loader pipeline: {}", def.getAgentCode(), e);
            }
        }
    }
}
