package com.sync.agent.bojoint.controller;

import com.sync.agent.bojoint.config.AgentConfigLoader;
import com.sync.agent.bojoint.config.AgentDefinition;
import com.sync.agent.bojoint.config.PipelineRegistry;
import com.sync.agent.bojoint.pipeline.PipelineService;
import com.sync.agent.common.pipeline.PipelineResult;
import com.sync.agent.common.step.ExecutionModeDefinition;
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
    public ResponseEntity<Map<String, Object>> execute(@RequestBody Map<String, Object> request) {
        log.info("[BojoInt] Pipeline execute request: {}", request);

        Map<String, Object> response = new HashMap<>();

        try {
            String executionId = (String) request.get("executionId");
            String agentCode = (String) request.get("agentCode");

            if (agentCode == null && executionId != null && executionId.contains("_")) {
                agentCode = executionId.substring(0, executionId.lastIndexOf('_'));
            }

            if (agentCode == null) {
                response.put("accepted", false);
                response.put("message", "agentCode is required");
                return ResponseEntity.badRequest().body(response);
            }

            if (!pipelineRegistry.getRegisteredAgentCodes().contains(agentCode)) {
                response.put("accepted", false);
                response.put("message", "Unknown agentCode: " + agentCode + ". Registered: " + pipelineRegistry.getRegisteredAgentCodes());
                return ResponseEntity.badRequest().body(response);
            }

            Map<String, Object> params = new HashMap<>(request);
            params.put("agentCode", agentCode);

            // executionModeId 전달
            String executionModeId = (String) request.get("executionModeId");
            if (executionModeId != null) {
                params.put("executionModeId", executionModeId);
                log.info("[BojoInt] Execution mode: {}", executionModeId);
            }

            if (request.get("startTime") != null && request.get("endTime") != null) {
                LocalDateTime startTime = LocalDateTime.parse((String) request.get("startTime"), FORMATTER);
                LocalDateTime endTime = LocalDateTime.parse((String) request.get("endTime"), FORMATTER);

                if (java.time.Duration.between(startTime, endTime).toHours() > 24) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "error", "Time range cannot exceed 24 hours"
                    ));
                }

                params.put("startTime", startTime);
                params.put("endTime", endTime);
            }

            pipelineService.executeAsync(executionId, params);

            response.put("accepted", true);
            response.put("executionId", executionId);
            response.put("agentCode", agentCode);
            response.put("agentType", pipelineRegistry.getAgentType(agentCode));
            response.put("message", "Pipeline execution started");

            log.info("[BojoInt] Pipeline execution accepted: executionId={}, agentCode={}", executionId, agentCode);

            return ResponseEntity.accepted().body(response);

        } catch (Exception e) {
            log.error("[BojoInt] Pipeline execution failed to start", e);
            response.put("accepted", false);
            response.put("message", "Failed to start pipeline: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping("/resync")
    public ResponseEntity<Map<String, Object>> resync(@RequestBody Map<String, Object> request) {
        String executionId = (String) request.get("executionId");
        String agentCode = (String) request.get("agentCode");

        String startDateStr = (String) request.get("resyncStartDate");
        String endDateStr = (String) request.get("resyncEndDate");
        if (startDateStr == null || endDateStr == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "resyncStartDate and resyncEndDate are required (format: yyyy-MM-dd)"
            ));
        }

        Map<String, Object> params = new HashMap<>(request);
        params.put("agentCode", agentCode);

        try {
            java.sql.Date resyncStartDate = java.sql.Date.valueOf(startDateStr);
            java.sql.Date resyncEndDate = java.sql.Date.valueOf(endDateStr);

            long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(
                    resyncStartDate.toLocalDate(), resyncEndDate.toLocalDate());
            if (daysBetween > 31) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Resync period cannot exceed 31 days"
                ));
            }

            params.put("resyncStartDate", resyncStartDate);
            params.put("resyncEndDate", resyncEndDate);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid date format. Use yyyy-MM-dd"
            ));
        }

        pipelineService.executeAsync(executionId, params);
        return ResponseEntity.ok(Map.of(
                "message", "Resync pipeline started",
                "executionId", executionId != null ? executionId : "auto-generated",
                "agentCode", agentCode != null ? agentCode : "unknown",
                "resyncPeriod", startDateStr + " ~ " + endDateStr
        ));
    }

    @GetMapping("/status/{executionId}")
    public ResponseEntity<?> getStatus(@PathVariable String executionId) {
        PipelineResult result = pipelineService.getExecutionResult(executionId);
        if (result == null) {
            return ResponseEntity.ok(Map.of("status", "RUNNING"));
        }
        return ResponseEntity.ok(Map.of(
                "status", result.getStatus().name(),
                "totalReadCount", result.getTotalReadCount(),
                "totalWriteCount", result.getTotalWriteCount(),
                "totalSkipCount", result.getTotalSkipCount(),
                "durationMs", result.getTotalDurationMs()
        ));
    }

    @GetMapping("/execution/{executionId}")
    public ResponseEntity<Map<String, Object>> getExecutionResult(@PathVariable String executionId) {
        PipelineResult result = pipelineService.getExecutionResult(executionId);

        if (result == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> response = new HashMap<>();
        response.put("executionId", executionId);
        response.put("status", result.getStatus().name());
        response.put("readCount", result.getTotalReadCount());
        response.put("writeCount", result.getTotalWriteCount());
        response.put("skipCount", result.getTotalSkipCount());
        response.put("durationMs", result.getTotalDurationMs());

        if (result.getErrorMessage() != null) {
            response.put("errorMessage", result.getErrorMessage());
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{agentCode}/execution-modes")
    public ResponseEntity<List<Map<String, Object>>> getExecutionModes(@PathVariable String agentCode) {
        if (!pipelineRegistry.getRegisteredAgentCodes().contains(agentCode)) {
            return ResponseEntity.badRequest().build();
        }

        List<ExecutionModeDefinition> modes = pipelineRegistry.getExecutionModes(agentCode);
        List<Map<String, Object>> result = new ArrayList<>();
        for (ExecutionModeDefinition mode : modes) {
            Map<String, Object> m = new HashMap<>();
            m.put("modeId", mode.getModeId());
            m.put("modeName", mode.getModeName());
            m.put("description", mode.getDescription());
            m.put("displayOrder", mode.getDisplayOrder());
            m.put("isDefault", mode.isDefault());
            result.add(m);
        }

        log.info("Execution modes for {}: {}", agentCode, result.size());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{agentCode}/tables")
    public ResponseEntity<Map<String, Object>> getPipelineTables(@PathVariable String agentCode) {
        if (!pipelineRegistry.getRegisteredAgentCodes().contains(agentCode)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Unknown agentCode: " + agentCode));
        }

        List<Map<String, String>> tables = new ArrayList<>();

        for (AgentDefinition def : agentConfigLoader.getAgentDefinitions()) {
            if (!agentCode.equals(def.getAgentCode())) continue;

            if (def.getJewon() != null) {
                if (def.getJewon().getSourceTable() != null) {
                    tables.add(Map.of("tableName", def.getJewon().getSourceTable(), "type", "SOURCE"));
                }
                if (def.getJewon().getTargetTable() != null) {
                    tables.add(Map.of("tableName", def.getJewon().getTargetTable(), "type", "TARGET"));
                }
            }
            if (def.getObsvdata() != null) {
                if (def.getObsvdata().getSourceTable() != null) {
                    tables.add(Map.of("tableName", def.getObsvdata().getSourceTable(), "type", "SOURCE"));
                }
                if (def.getObsvdata().getTargetTable() != null) {
                    tables.add(Map.of("tableName", def.getObsvdata().getTargetTable(), "type", "TARGET"));
                }
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("agentCode", agentCode);
        response.put("tables", tables);

        log.info("Pipeline tables for {}: {}", agentCode, tables);
        return ResponseEntity.ok(response);
    }
}
