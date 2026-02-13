package com.sync.agent.bojo.controller;

import com.sync.agent.bojo.config.PipelineRegistry;
import com.sync.agent.bojo.pipeline.PipelineService;
import com.sync.agent.common.pipeline.PipelineResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 통합 Agent 파이프라인 컨트롤러
 *
 * Orchestrator로부터 실행 트리거를 받아 파이프라인 실행
 * agentCode를 통해 RCV/Loader/SND 파이프라인을 라우팅
 */
@Slf4j
@RestController
@RequestMapping("/api/pipeline")
@RequiredArgsConstructor
public class PipelineController {

    private final PipelineService pipelineService;
    private final PipelineRegistry pipelineRegistry;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /**
     * 파이프라인 실행 트리거 (비동기)
     * Orchestrator가 호출하여 RCV/Loader/SND 파이프라인 실행
     */
    @PostMapping("/execute")
    public ResponseEntity<Map<String, Object>> execute(@RequestBody Map<String, Object> request) {
        log.info("[Bojo] Pipeline execute request: {}", request);

        Map<String, Object> response = new HashMap<>();

        try {
            String executionId = (String) request.get("executionId");
            String agentCode = (String) request.get("agentCode");

            // agentCode 검증
            if (agentCode == null && executionId != null && executionId.contains("_")) {
                agentCode = executionId.substring(0, executionId.lastIndexOf('_'));
            }

            if (agentCode == null) {
                response.put("accepted", false);
                response.put("message", "agentCode is required");
                return ResponseEntity.badRequest().body(response);
            }

            // 등록된 agentCode인지 확인
            if (!pipelineRegistry.getRegisteredAgentCodes().contains(agentCode)) {
                response.put("accepted", false);
                response.put("message", "Unknown agentCode: " + agentCode + ". Registered: " + pipelineRegistry.getRegisteredAgentCodes());
                return ResponseEntity.badRequest().body(response);
            }

            // params 구성
            Map<String, Object> params = new HashMap<>(request);
            params.put("agentCode", agentCode);

            // 시간 범위 설정
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

            // 비동기 실행
            pipelineService.executeAsync(executionId, params);

            response.put("accepted", true);
            response.put("executionId", executionId);
            response.put("agentCode", agentCode);
            response.put("agentType", pipelineRegistry.getAgentType(agentCode));
            response.put("message", "Pipeline execution started");

            log.info("[Bojo] Pipeline execution accepted: executionId={}, agentCode={}", executionId, agentCode);

            return ResponseEntity.accepted().body(response);

        } catch (Exception e) {
            log.error("[Bojo] Pipeline execution failed to start", e);
            response.put("accepted", false);
            response.put("message", "Failed to start pipeline: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    /**
     * 재동기화 (관리자가 기간 지정)
     */
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

    /**
     * 실행 상태 조회
     */
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

    /**
     * 실행 결과 상세 조회
     */
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
}
