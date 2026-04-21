package com.sync.agent.provide.config.pipeline;

import com.sync.agent.common.pipeline.PipelineRunner;
import com.sync.agent.common.step.StepDefinition;
import com.sync.agent.common.step.StepDefinitionProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * PipelineRegistry - (agentCode, modeId) -> PipelineRunner 라우팅
 * StepDefinitionProvider 구현하여 Step 메타데이터 제공
 */
@Slf4j
@Component
public class PipelineRegistry implements StepDefinitionProvider {

    private static final String DEFAULT_MODE = "default";

    // agentCode -> (modeId -> PipelineRunner)
    private final Map<String, Map<String, PipelineRunner>> runners = new ConcurrentHashMap<>();
    private final Map<String, String> agentTypes = new ConcurrentHashMap<>();
    private final Map<String, List<StepDefinition>> stepDefs = new ConcurrentHashMap<>();

    /**
     * PipelineRunner 등록 (default 모드, StepDefinition 없이)
     */
    public void register(String agentCode, String agentType, PipelineRunner runner) {
        register(agentCode, agentType, DEFAULT_MODE, runner, List.of());
    }

    /**
     * PipelineRunner 등록 (default 모드, StepDefinition 포함)
     */
    public void register(String agentCode, String agentType, PipelineRunner runner,
                          List<StepDefinition> stepDefinitions) {
        register(agentCode, agentType, DEFAULT_MODE, runner, stepDefinitions);
    }

    /**
     * PipelineRunner 등록 (모드 지정, StepDefinition 없이)
     */
    public void register(String agentCode, String agentType, String modeId, PipelineRunner runner) {
        register(agentCode, agentType, modeId, runner, List.of());
    }

    /**
     * PipelineRunner 등록 (모드 지정, StepDefinition 포함)
     */
    public void register(String agentCode, String agentType, String modeId, PipelineRunner runner,
                          List<StepDefinition> stepDefinitions) {
        runners.computeIfAbsent(agentCode, k -> new ConcurrentHashMap<>()).put(modeId, runner);
        agentTypes.put(agentCode, agentType);
        if (stepDefinitions != null && !stepDefinitions.isEmpty()) {
            stepDefs.put(agentCode, stepDefinitions);
        }
        log.info("[Provide] 파이프라인 등록: agentCode={}, type={}, modeId={}, steps={}",
                agentCode, agentType, modeId,
                stepDefinitions != null ? stepDefinitions.size() : 0);
    }

    /**
     * agentCode + modeId로 PipelineRunner 조회 (modeId 없으면 default fallback)
     */
    public PipelineRunner getRunner(String agentCode, String modeId) {
        Map<String, PipelineRunner> modeRunners = runners.get(agentCode);
        if (modeRunners == null) {
            throw new IllegalArgumentException("알 수 없는 agentCode: " + agentCode + ". 등록됨: " + runners.keySet());
        }

        PipelineRunner runner = modeRunners.get(modeId);
        if (runner == null) {
            runner = modeRunners.get(DEFAULT_MODE);
            if (runner != null) {
                log.info("[Provide] agentCode={}에서 modeId '{}' 미발견, default로 대체", agentCode, modeId);
            }
        }
        if (runner == null) {
            throw new IllegalArgumentException("파이프라인을 찾을 수 없습니다. agentCode=" + agentCode
                    + ", modeId=" + modeId + ". 사용 가능한 모드: " + modeRunners.keySet());
        }
        return runner;
    }

    /**
     * agentCode로 PipelineRunner 조회 (default 모드)
     */
    public PipelineRunner getRunner(String agentCode) {
        return getRunner(agentCode, DEFAULT_MODE);
    }

    public String getAgentType(String agentCode) {
        String type = agentTypes.get(agentCode);
        if (type == null) {
            throw new IllegalArgumentException("알 수 없는 agentCode: " + agentCode);
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
        return (int) runners.values().stream().mapToLong(Map::size).sum();
    }
}
