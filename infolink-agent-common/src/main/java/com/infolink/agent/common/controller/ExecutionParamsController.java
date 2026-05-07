package com.infolink.agent.common.controller;

import com.infolink.agent.common.config.ExecutionParamsProperties;
import com.infolink.agent.common.step.ExecutionParamDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Agent의 실행 파라미터 메타데이터 조회 API
 * Orchestrator가 Agent 등록 시 호출하여 파라미터 목록을 가져감
 */
@RestController
@RequestMapping("/api/pipeline")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "common.controller.execution-params.enabled", havingValue = "true")
public class ExecutionParamsController {

    private final ExecutionParamsProperties executionParamsProperties;

    /**
     * Agent가 지원하는 실행 파라미터 목록 조회
     * 정의가 없으면 빈 목록 반환
     */
    @GetMapping("/execution-params")
    public ResponseEntity<List<ExecutionParamDefinition>> getExecutionParams() {
        return ResponseEntity.ok(executionParamsProperties.getExecutionParams());
    }
}
