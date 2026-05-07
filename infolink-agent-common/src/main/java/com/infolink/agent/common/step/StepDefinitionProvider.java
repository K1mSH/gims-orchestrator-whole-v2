package com.infolink.agent.common.step;

import java.util.List;

/**
 * Agent가 구현하는 인터페이스
 * PipelineRegistry 등에서 Step 메타데이터를 제공
 */
public interface StepDefinitionProvider {

    /**
     * 특정 Agent의 Step 정의 목록 반환
     */
    List<StepDefinition> getStepDefinitions(String agentCode);

    /**
     * 전체 등록된 agentCode 목록 반환
     */
    java.util.Set<String> getRegisteredAgentCodes();
}
