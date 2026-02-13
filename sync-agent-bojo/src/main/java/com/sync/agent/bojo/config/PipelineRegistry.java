package com.sync.agent.bojo.config;

import com.sync.agent.common.pipeline.PipelineRunner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PipelineRegistry - agentCode -> PipelineRunner 라우팅
 *
 * 12개 논리적 Agent를 하나의 물리적 앱에서 관리
 * 각 Agent는 고유한 agentCode와 PipelineRunner를 가짐
 */
@Slf4j
@Component
public class PipelineRegistry {

    private final Map<String, PipelineRunner> runners = new ConcurrentHashMap<>();
    private final Map<String, String> agentTypes = new ConcurrentHashMap<>();  // agentCode -> "RCV"/"LOADER"/"SND"

    /**
     * PipelineRunner 등록
     */
    public void register(String agentCode, String agentType, PipelineRunner runner) {
        runners.put(agentCode, runner);
        agentTypes.put(agentCode, agentType);
        log.info("Registered pipeline: agentCode={}, type={}", agentCode, agentType);
    }

    /**
     * agentCode로 PipelineRunner 조회
     */
    public PipelineRunner getRunner(String agentCode) {
        PipelineRunner runner = runners.get(agentCode);
        if (runner == null) {
            throw new IllegalArgumentException("Unknown agentCode: " + agentCode + ". Registered: " + runners.keySet());
        }
        return runner;
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
    public Set<String> getRegisteredAgentCodes() {
        return Collections.unmodifiableSet(runners.keySet());
    }

    /**
     * 특정 타입의 agentCode 목록
     */
    public Set<String> getAgentCodesByType(String type) {
        return agentTypes.entrySet().stream()
                .filter(e -> type.equals(e.getValue()))
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());
    }

    public int size() {
        return runners.size();
    }
}
