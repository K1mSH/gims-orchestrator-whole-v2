package com.sync.agent.bojoint.config;

import com.sync.agent.common.pipeline.PipelineRunner;
import com.sync.agent.common.step.StepDefinition;
import com.sync.agent.common.step.StepDefinitionProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * PipelineRegistry - agentCode -> PipelineRunner 라우팅
 * StepDefinitionProvider 구현하여 Step 메타데이터 제공
 */
@Slf4j
@Component
public class PipelineRegistry implements StepDefinitionProvider {

    private final Map<String, PipelineRunner> runners = new ConcurrentHashMap<>();
    private final Map<String, String> agentTypes = new ConcurrentHashMap<>();
    private final Map<String, List<StepDefinition>> stepDefs = new ConcurrentHashMap<>();

    public void register(String agentCode, String agentType, PipelineRunner runner) {
        register(agentCode, agentType, runner, List.of());
    }

    public void register(String agentCode, String agentType, PipelineRunner runner,
                          List<StepDefinition> stepDefinitions) {
        runners.put(agentCode, runner);
        agentTypes.put(agentCode, agentType);
        if (stepDefinitions != null && !stepDefinitions.isEmpty()) {
            stepDefs.put(agentCode, stepDefinitions);
        }
        log.info("Registered pipeline: agentCode={}, type={}, steps={}",
                agentCode, agentType, stepDefinitions != null ? stepDefinitions.size() : 0);
    }

    public PipelineRunner getRunner(String agentCode) {
        PipelineRunner runner = runners.get(agentCode);
        if (runner == null) {
            throw new IllegalArgumentException("Unknown agentCode: " + agentCode + ". Registered: " + runners.keySet());
        }
        return runner;
    }

    public String getAgentType(String agentCode) {
        String type = agentTypes.get(agentCode);
        if (type == null) {
            throw new IllegalArgumentException("Unknown agentCode: " + agentCode);
        }
        return type;
    }

    @Override
    public Set<String> getRegisteredAgentCodes() {
        return Collections.unmodifiableSet(runners.keySet());
    }

    @Override
    public List<StepDefinition> getStepDefinitions(String agentCode) {
        return stepDefs.getOrDefault(agentCode, List.of());
    }

    public Set<String> getAgentCodesByType(String type) {
        return agentTypes.entrySet().stream()
                .filter(e -> type.equals(e.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    public int size() {
        return runners.size();
    }
}
