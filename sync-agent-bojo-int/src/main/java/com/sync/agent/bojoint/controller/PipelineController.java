package com.sync.agent.bojoint.controller;

import com.sync.agent.bojoint.config.pipeline.AgentConfigLoader;
import com.sync.agent.bojoint.config.pipeline.AgentDefinition;
import com.sync.agent.bojoint.config.pipeline.PipelineRegistry;
import com.sync.agent.bojoint.pipeline.PipelineService;
import com.sync.agent.common.pipeline.PipelineDto;
import com.sync.agent.common.pipeline.PipelineDto.*;
import com.sync.agent.common.pipeline.PipelineResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 내부망 Agent 파이프라인 컨트롤러
 */
@Slf4j
@RestController
@RequestMapping("/api/pipeline")
@RequiredArgsConstructor
public class PipelineController {

    private final PipelineService pipelineService;
    private final PipelineRegistry pipelineRegistry;
    private final AgentConfigLoader agentConfigLoader;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    @PostMapping("/execute")
    public ResponseEntity<?> execute(@RequestBody Map<String, Object> request) {
        log.info("[BojoInt] 파이프라인 실행 요청: {}", request);

        try {
            String executionId = (String) request.get("executionId");
            String agentCode = (String) request.get("agentCode");

            if (agentCode == null && executionId != null && executionId.contains("_")) {
                agentCode = executionId.substring(0, executionId.lastIndexOf('_'));
            }

            if (agentCode == null) {
                return ResponseEntity.badRequest().body(ExecuteResponse.builder()
                        .accepted(false).message("agentCode는 필수입니다").build());
            }

            if (!pipelineRegistry.getRegisteredAgentCodes().contains(agentCode)) {
                return ResponseEntity.badRequest().body(ExecuteResponse.builder()
                        .accepted(false)
                        .message("알 수 없는 agentCode: " + agentCode + ". 등록됨: " + pipelineRegistry.getRegisteredAgentCodes())
                        .build());
            }

            Map<String, Object> params = new HashMap<>(request);
            params.put("agentCode", agentCode);

            if (request.get("startTime") != null && request.get("endTime") != null) {
                LocalDateTime startTime = LocalDateTime.parse((String) request.get("startTime"), FORMATTER);
                LocalDateTime endTime = LocalDateTime.parse((String) request.get("endTime"), FORMATTER);

                if (java.time.Duration.between(startTime, endTime).toHours() > 24) {
                    return ResponseEntity.badRequest().body(ErrorResponse.builder()
                            .error("시간 범위는 24시간을 초과할 수 없습니다").build());
                }

                params.put("startTime", startTime);
                params.put("endTime", endTime);
            }

            pipelineService.executeAsync(executionId, params);

            log.info("[BojoInt] 파이프라인 실행 수락: executionId={}, agentCode={}", executionId, agentCode);

            return ResponseEntity.accepted().body(ExecuteResponse.builder()
                    .accepted(true)
                    .executionId(executionId)
                    .agentCode(agentCode)
                    .agentType(pipelineRegistry.getAgentType(agentCode))
                    .message("파이프라인 실행 시작됨")
                    .build());

        } catch (Exception e) {
            log.error("[BojoInt] 파이프라인 실행 시작 실패", e);
            return ResponseEntity.internalServerError().body(ExecuteResponse.builder()
                    .accepted(false).message("파이프라인 시작 실패: " + e.getMessage()).build());
        }
    }

    @PostMapping("/resync")
    public ResponseEntity<?> resync(@RequestBody Map<String, Object> request) {
        String executionId = (String) request.get("executionId");
        String agentCode = (String) request.get("agentCode");

        String startDateStr = (String) request.get("resyncStartDate");
        String endDateStr = (String) request.get("resyncEndDate");
        if (startDateStr == null || endDateStr == null) {
            return ResponseEntity.badRequest().body(ErrorResponse.builder()
                    .error("resyncStartDate와 resyncEndDate는 필수입니다 (형식: yyyy-MM-dd)").build());
        }

        Map<String, Object> params = new HashMap<>(request);
        params.put("agentCode", agentCode);

        try {
            java.sql.Date resyncStartDate = java.sql.Date.valueOf(startDateStr);
            java.sql.Date resyncEndDate = java.sql.Date.valueOf(endDateStr);

            long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(
                    resyncStartDate.toLocalDate(), resyncEndDate.toLocalDate());
            if (daysBetween > 31) {
                return ResponseEntity.badRequest().body(ErrorResponse.builder()
                        .error("재동기화 기간은 31일을 초과할 수 없습니다").build());
            }

            params.put("resyncStartDate", resyncStartDate);
            params.put("resyncEndDate", resyncEndDate);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ErrorResponse.builder()
                    .error("잘못된 날짜 형식입니다. yyyy-MM-dd를 사용하세요").build());
        }

        pipelineService.executeAsync(executionId, params);
        return ResponseEntity.ok(ResyncResponse.builder()
                .message("재동기화 파이프라인 시작됨")
                .executionId(executionId != null ? executionId : "auto-generated")
                .agentCode(agentCode != null ? agentCode : "unknown")
                .resyncPeriod(startDateStr + " ~ " + endDateStr)
                .build());
    }

    @GetMapping("/status/{executionId}")
    public ResponseEntity<StatusResponse> getStatus(@PathVariable String executionId) {
        PipelineResult result = pipelineService.getExecutionResult(executionId);
        if (result == null) {
            return ResponseEntity.ok(StatusResponse.builder().status("RUNNING").build());
        }
        return ResponseEntity.ok(StatusResponse.builder()
                .status(result.getStatus().name())
                .totalReadCount(result.getTotalReadCount())
                .totalWriteCount(result.getTotalWriteCount())
                .totalSkipCount(result.getTotalSkipCount())
                .durationMs(result.getTotalDurationMs())
                .build());
    }

    @GetMapping("/execution/{executionId}")
    public ResponseEntity<ExecutionResultResponse> getExecutionResult(@PathVariable String executionId) {
        PipelineResult result = pipelineService.getExecutionResult(executionId);
        if (result == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ExecutionResultResponse.builder()
                .executionId(executionId)
                .status(result.getStatus().name())
                .readCount(result.getTotalReadCount())
                .writeCount(result.getTotalWriteCount())
                .skipCount(result.getTotalSkipCount())
                .durationMs(result.getTotalDurationMs())
                .errorMessage(result.getErrorMessage())
                .build());
    }

    @GetMapping("/{agentCode}/select-tables")
    public ResponseEntity<List<String>> getSelectTables(@PathVariable String agentCode) {
        if (!pipelineRegistry.getRegisteredAgentCodes().contains(agentCode)) {
            return ResponseEntity.badRequest().build();
        }

        for (AgentDefinition def : agentConfigLoader.getAgentDefinitions()) {
            if (agentCode.equals(def.getAgentCode())) {
                return ResponseEntity.ok(def.getSelectTables());
            }
        }

        return ResponseEntity.ok(List.of());
    }

    @GetMapping("/{agentCode}/tables")
    public ResponseEntity<?> getPipelineTables(@PathVariable String agentCode) {
        if (!pipelineRegistry.getRegisteredAgentCodes().contains(agentCode)) {
            return ResponseEntity.badRequest().body(ErrorResponse.builder()
                    .error("알 수 없는 agentCode: " + agentCode).build());
        }

        List<TableInfo> tables = new ArrayList<>();

        for (AgentDefinition def : agentConfigLoader.getAgentDefinitions()) {
            if (!agentCode.equals(def.getAgentCode())) continue;

            for (Map<String, Object> stepConfig : def.getSteps()) {
                String sourceTable = (String) stepConfig.get("source-table");
                String targetTable = (String) stepConfig.get("target-table");
                if (sourceTable != null) {
                    tables.add(TableInfo.builder().tableName(sourceTable).type("SOURCE").build());
                }
                if (targetTable != null) {
                    tables.add(TableInfo.builder().tableName(targetTable).type("TARGET").build());
                }
            }
        }

        log.info("[BojoInt] 파이프라인 테이블 목록 {}: {}", agentCode, tables);
        return ResponseEntity.ok(TablesResponse.builder()
                .agentCode(agentCode).tables(tables).build());
    }
}
