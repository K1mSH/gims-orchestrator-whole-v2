package com.sync.agent.bojo.config;

import com.sync.agent.common.pipeline.PipelineRunner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PipelineRegistry - agentId -> PipelineRunner 라우팅
 *
 * 12개 논리적 Agent를 하나의 물리적 앱에서 관리
 * 각 Agent는 고유한 agentId와 PipelineRunner를 가짐
 */
@Slf4j
@Component
public class PipelineRegistry {

    private final Map<String, PipelineRunner> runners = new ConcurrentHashMap<>();
    private final Map<String, String> agentTypes = new ConcurrentHashMap<>();  // agentId -> "RCV"/"LOADER"/"SND"

    /**
     * PipelineRunner 등록
     */
    public void register(String agentId, String agentType, PipelineRunner runner) {
        runners.put(agentId, runner);
        agentTypes.put(agentId, agentType);
        log.info("Registered pipeline: agentId={}, type={}", agentId, agentType);
    }

    /**
     * agentId로 PipelineRunner 조회
     */
    public PipelineRunner getRunner(String agentId) {
        PipelineRunner runner = runners.get(agentId);
        if (runner == null) {
            throw new IllegalArgumentException("Unknown agentId: " + agentId + ". Registered: " + runners.keySet());
        }
        return runner;
    }

    /**
     * agentId의 Agent 타입 조회
     */
    public String getAgentType(String agentId) {
        String type = agentTypes.get(agentId);
        if (type == null) {
            throw new IllegalArgumentException("Unknown agentId: " + agentId);
        }
        return type;
    }

    /**
     * 등록된 모든 agentId 목록
     */
    public Set<String> getRegisteredAgentIds() {
        return Collections.unmodifiableSet(runners.keySet());
    }

    /**
     * 특정 타입의 agentId 목록
     */
    public Set<String> getAgentIdsByType(String type) {
        return agentTypes.entrySet().stream()
                .filter(e -> type.equals(e.getValue()))
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet());
    }

    public int size() {
        return runners.size();
    }
}
