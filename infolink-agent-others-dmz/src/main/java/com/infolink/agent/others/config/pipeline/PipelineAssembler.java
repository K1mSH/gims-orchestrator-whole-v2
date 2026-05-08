package com.infolink.agent.others.config.pipeline;

import com.infolink.agent.common.model.RetentionCandidate;
import com.infolink.agent.common.pipeline.StepFactoryRegistry;
import com.infolink.agent.common.pipeline.PipelineRunner;
import com.infolink.agent.common.service.RetentionCandidatesProvider;
import com.infolink.agent.common.step.StepDefinition;
import com.infolink.agent.common.step.StepExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * YAML steps → StepFactory → PipelineRunner 조립
 *
 * 모든 Agent를 YAML의 factory-key 기반으로 Runner를 생성하여 PipelineRegistry에 등록한다.
 * 기존 RcvPipelineConfig, SndPipelineConfig, LoaderPipelineConfig를 대체.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class PipelineAssembler {

    private final AgentConfigLoader agentConfigLoader;
    private final PipelineRegistry pipelineRegistry;
    private final StepFactoryRegistry stepFactoryRegistry;

    @PostConstruct
    public void assembleAll() {
        List<AgentDefinition> definitions = agentConfigLoader.getAgentDefinitions();
        log.info("Assembling {} pipelines", definitions.size());

        for (AgentDefinition def : definitions) {
            try {
                List<StepExecutor> steps = new ArrayList<>();
                List<StepDefinition> stepDefs = new ArrayList<>();

                int order = 1;
                for (Map<String, Object> stepConfig : def.getSteps()) {
                    StepExecutor step = stepFactoryRegistry.create(stepConfig);
                    steps.add(step);

                    stepDefs.add(StepDefinition.builder()
                            .stepId((String) stepConfig.get("id"))
                            .stepName((String) stepConfig.get("name"))
                            .displayOrder(order++)
                            .enabledByDefault(true)
                            .build());
                }

                PipelineRunner runner = new PipelineRunner(def.getAgentCode(), steps);
                pipelineRegistry.register(def.getAgentCode(), def.getType(), runner, stepDefs);
                log.info("Assembled pipeline: {} (type={}, steps={})",
                        def.getAgentCode(), def.getType(), steps.size());

            } catch (Exception e) {
                log.error("Failed to assemble pipeline: {}", def.getAgentCode(), e);
            }
        }
    }

    /**
     * RetentionCandidatesProvider 빈 — Agent yml retention-candidates 단일 진실원.
     * dev_plan/2026_05/08/retention-candidates-safety.md §3-3 layer D
     */
    @Bean
    public RetentionCandidatesProvider retentionCandidatesProvider() {
        return agentCode -> {
            for (AgentDefinition def : agentConfigLoader.getAgentDefinitions()) {
                if (agentCode.equals(def.getAgentCode())) {
                    return def.getRetentionCandidates();
                }
            }
            return List.of();
        };
    }
}
