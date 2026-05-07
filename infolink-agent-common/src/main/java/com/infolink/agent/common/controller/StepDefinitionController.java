package com.infolink.agent.common.controller;

import com.infolink.agent.common.step.StepDefinition;
import com.infolink.agent.common.step.StepDefinitionProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

/**
 * Agent Step 메타데이터 조회 API
 * Orchestrator가 Agent 등록 시 호출하여 Step 목록을 가져감
 */
@RestController
@RequestMapping("/api/pipeline")
@RequiredArgsConstructor
@ConditionalOnBean(StepDefinitionProvider.class)
public class StepDefinitionController {

    private final StepDefinitionProvider stepDefinitionProvider;

    /**
     * 전체 Agent의 Step 정의 조회
     * 반환: { agentCode -> [StepDefinition] }
     */
    @GetMapping("/step-definitions")
    public ResponseEntity<Map<String, List<StepDefinition>>> getAllStepDefinitions() {
        Map<String, List<StepDefinition>> result = new LinkedHashMap<>();
        for (String agentCode : stepDefinitionProvider.getRegisteredAgentCodes()) {
            List<StepDefinition> defs = stepDefinitionProvider.getStepDefinitions(agentCode);
            if (defs != null && !defs.isEmpty()) {
                result.put(agentCode, defs);
            }
        }
        return ResponseEntity.ok(result);
    }

    /**
     * 특정 Agent의 Step 정의 조회
     */
    @GetMapping("/{agentCode}/step-definitions")
    public ResponseEntity<List<StepDefinition>> getStepDefinitions(@PathVariable String agentCode) {
        List<StepDefinition> defs = stepDefinitionProvider.getStepDefinitions(agentCode);
        return ResponseEntity.ok(defs != null ? defs : List.of());
    }
}
