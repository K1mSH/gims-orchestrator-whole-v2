package com.sync.agent.common.step;

import lombok.Builder;
import lombok.Getter;

/**
 * 실행 방식(모드) 메타데이터
 *
 * Agent 소스코드에 미리 정의된 실행 모드를 표현.
 * Orchestrator가 GET /api/pipeline/{agentCode}/execution-modes 로 조회.
 */
@Getter
@Builder
public class ExecutionModeDefinition {
    private String modeId;        // "incremental", "full-reload"
    private String modeName;      // "증분 적재", "전체 재적재"
    private String description;
    private int displayOrder;
    private boolean isDefault;
}
