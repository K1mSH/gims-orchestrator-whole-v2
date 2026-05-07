package com.infolink.agent.others.config.pipeline;

import com.infolink.agent.common.pipeline.PipelineRunner;
import com.infolink.agent.common.step.StepDefinition;
import com.infolink.agent.common.step.StepDefinitionProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * PipelineRegistry - (agentCode, modeId) -> PipelineRunner 라우팅
 *
 * 12개 논리적 Agent를 하나의 물리적 앱에서 관리
 * 각 Agent는 고유한 agentCode와 실행 모드별 PipelineRunner를 가짐
 * StepDefinitionProvider 구현하여 Step 메타데이터 제공
 */
@Slf4j
@Component
public class PipelineRegistry implements StepDefinitionProvider {

    private static final String DEFAULT_MODE = "default";

    // agentCode -> (modeId -> PipelineRunner)
    private final Map<String, Map<String, PipelineRunner>> runners = new ConcurrentHashMap<>();
    private final Map<String, String> agentTypes = new ConcurrentHashMap<>();  // agentCode -> "RCV"/"LOADER"/"SND"
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
        log.info("Registered pipeline: agentCode={}, type={}, modeId={}, steps={}",
                agentCode, agentType, modeId, stepDefinitions != null ? stepDefinitions.size() : 0);
    }

    /**
     * agentCode + modeId로 PipelineRunner 조회 (modeId 없으면 default fallback)
     */
    public PipelineRunner getRunner(String agentCode, String modeId) {
        Map<String, PipelineRunner> modeRunners = runners.get(agentCode);
        if (modeRunners == null) {
            throw new IllegalArgumentException("Unknown agentCode: " + agentCode + ". Registered: " + runners.keySet());
        }

        PipelineRunner runner = modeRunners.get(modeId);
        if (runner == null) {
            // 요청한 modeId가 없으면 default fallback
            runner = modeRunners.get(DEFAULT_MODE);
            if (runner != null) {
                log.info("ModeId '{}' not found for agentCode={}, falling back to default", modeId, agentCode);
            }
        }
        if (runner == null) {
            throw new IllegalArgumentException("No pipeline found for agentCode=" + agentCode
                    + ", modeId=" + modeId + ". Available modes: " + modeRunners.keySet());
        }
        return runner;
    }

    /**
     * agentCode로 PipelineRunner 조회 (default 모드)
     */
    public PipelineRunner getRunner(String agentCode) {
        return getRunner(agentCode, DEFAULT_MODE);
    }

    /**
     * agentCode의 Agent 타입 조회
     */
    public String getAgentType(String agentCode) {
        String type = agentTypes.get(agentCode);
        if (type == null) {
            throw new IllegalArgumentException("Unknown agentCode: " + agentCode);
        }
        return type;
    }

    /**
     * 등록된 모든 agentCode 목록
     */
    @Override
    public Set<String> getRegisteredAgentCodes() {
        return Collections.unmodifiableSet(runners.keySet());
    }

    /**
     * 특정 Agent의 Step 정의 목록 반환
     */
    @Override
    public List<StepDefinition> getStepDefinitions(String agentCode) {
        return stepDefs.getOrDefault(agentCode, List.of());
    }

    /**
     * 특정 타입의 agentCode 목록
     */
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
