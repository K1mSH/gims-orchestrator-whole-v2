package com.sync.agent.common.pipeline;

import com.sync.agent.common.step.ConditionOperator;
import com.sync.agent.common.step.ExecutionCondition;
import com.sync.agent.common.step.ExecutionOptions;
import com.sync.agent.common.step.Status;
import com.sync.agent.common.step.StepContext;
import com.sync.agent.common.step.StepExecutor;
import com.sync.agent.common.step.StepResult;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
public class PipelineRunner {

    private final String pipelineId;
    private final List<StepExecutor> steps;
    private StepProgressCallback progressCallback;

    public PipelineRunner(String pipelineId, List<StepExecutor> steps) {
        this.pipelineId = pipelineId;
        this.steps = steps;
    }

    /**
     * Step 진행 상황 콜백 설정
     */
    public PipelineRunner withProgressCallback(StepProgressCallback callback) {
        this.progressCallback = callback;
        return this;
    }

    public PipelineResult run() {
        return run(Map.of());
    }

    public PipelineResult run(Map<String, Object> params) {
        String executionId = UUID.randomUUID().toString();
        return run(executionId, params);
    }

    public PipelineResult run(String executionId, Map<String, Object> params) {
        long startTime = System.currentTimeMillis();
        List<StepResult> stepResults = new ArrayList<>();
        Status finalStatus = Status.SUCCESS;
        String errorMessage = null;
        int totalSteps = steps.size();

        // Context 생성
        StepContext context = StepContext.builder()
                .executionId(executionId)
                .pipelineId(pipelineId)
                .params(params)
                .build();

        // params에서 datasource ID 추출하여 context에 설정
        if (params.containsKey("sourceDatasourceId")) {
            context.setSourceDatasourceId((String) params.get("sourceDatasourceId"));
        }
        if (params.containsKey("targetDatasourceId")) {
            context.setTargetDatasourceId((String) params.get("targetDatasourceId"));
        }
        // params에서 zone 정보 추출하여 context에 설정
        if (params.containsKey("sourceZone")) {
            context.setSourceZone((String) params.get("sourceZone"));
        }
        if (params.containsKey("agentZone")) {
            context.setAgentZone((String) params.get("agentZone"));
        }
        // sourceRef용 추가 정보
        if (params.containsKey("sourceZoneShortCode")) {
            context.setSourceZoneShortCode((String) params.get("sourceZoneShortCode"));
        }
        if (params.containsKey("sourceDatasourceDbId")) {
            Object dbId = params.get("sourceDatasourceDbId");
            if (dbId instanceof Long) {
                context.setSourceDatasourceDbId((Long) dbId);
            } else if (dbId instanceof Integer) {
                context.setSourceDatasourceDbId(((Integer) dbId).longValue());
            } else if (dbId != null) {
                context.setSourceDatasourceDbId(Long.parseLong(dbId.toString()));
            }
        }
        if (params.containsKey("sourceTableIds")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> rawTableIds = (Map<String, Object>) params.get("sourceTableIds");
            if (rawTableIds != null) {
                Map<String, Long> tableIds = new java.util.HashMap<>();
                rawTableIds.forEach((k, v) -> {
                    if (v instanceof Long) {
                        tableIds.put(k, (Long) v);
                    } else if (v instanceof Integer) {
                        tableIds.put(k, ((Integer) v).longValue());
                    } else if (v != null) {
                        tableIds.put(k, Long.parseLong(v.toString()));
                    }
                });
                context.setSourceTableIds(tableIds);
            }
        }

        // 시간 범위 파라미터 변환 (String → LocalDateTime)
        // Orchestrator에서 시간 지정 실행 시 전달됨
        if (params.containsKey("startTime")) {
            Object startTimeObj = params.get("startTime");
            LocalDateTime parsedStartTime = parseLocalDateTime(startTimeObj);
            if (parsedStartTime != null) {
                context.getParams().put("startTime", parsedStartTime);
                log.info("Pipeline time range - startTime: {}", parsedStartTime);
            }
        }
        if (params.containsKey("endTime")) {
            Object endTimeObj = params.get("endTime");
            LocalDateTime parsedEndTime = parseLocalDateTime(endTimeObj);
            if (parsedEndTime != null) {
                context.getParams().put("endTime", parsedEndTime);
                log.info("Pipeline time range - endTime: {}", parsedEndTime);
            }
        }

        // ExecutionOptions 생성 (조건실행 옵션)
        ExecutionOptions executionOptions = buildExecutionOptions(context.getParams(), params);
        context.setExecutionOptions(executionOptions);

        if (executionOptions.isTimeRangeExecution()) {
            log.info("Pipeline ExecutionOptions - timeRange: {} ~ {}",
                    executionOptions.getTimeRange().getStartTime(),
                    executionOptions.getTimeRange().getEndTime());
        }
        if (executionOptions.hasParams()) {
            log.info("Pipeline ExecutionOptions - {} params", executionOptions.getParams().size());
            for (ExecutionOptions.ExecutionParam param : executionOptions.getParams()) {
                log.info("  Param: {} = {}", param.getParamId(), param.getValue());
            }
        }

        // selectedStepIds: 사용자가 선택한 Step만 실행 (없으면 전체 실행)
        @SuppressWarnings("unchecked")
        List<String> selectedStepIds = params.get("selectedStepIds") instanceof List
                ? (List<String>) params.get("selectedStepIds") : null;

        if (selectedStepIds != null && !selectedStepIds.isEmpty()) {
            log.info("Pipeline [{}] selective execution: selectedStepIds={}", pipelineId, selectedStepIds);
        }

        log.info("Pipeline [{}] started. executionId={}, totalSteps={}", pipelineId, executionId, totalSteps);

        int stepOrder = 0;
        for (StepExecutor step : steps) {
            stepOrder++;

            // selectedStepIds가 있으면: 목록에 포함된 것만 실행
            if (selectedStepIds != null && !selectedStepIds.isEmpty()
                    && !selectedStepIds.contains(step.getStepId())) {
                log.info("Step [{}] skipped (not in selectedStepIds). ({}/{})", step.getStepId(), stepOrder, totalSteps);
                StepResult skippedResult = StepResult.skipped(step.getStepId(), "선택되지 않은 Step");
                stepResults.add(skippedResult);

                // Skip 콜백 호출
                if (progressCallback != null) {
                    try {
                        progressCallback.onStepStarted(executionId, step.getStepId(), step.getStepName(), stepOrder, totalSteps);
                        progressCallback.onStepFinished(executionId, skippedResult, stepOrder, totalSteps);
                    } catch (Exception e) {
                        log.warn("Failed to notify step skipped: {}", e.getMessage());
                    }
                }
                continue;
            }

            log.info("Step [{}] started. ({}/{})", step.getStepId(), stepOrder, totalSteps);

            // Step 시작 콜백 호출
            if (progressCallback != null) {
                try {
                    progressCallback.onStepStarted(executionId, step.getStepId(), step.getStepName(), stepOrder, totalSteps);
                } catch (Exception e) {
                    log.warn("Failed to notify step started: {}", e.getMessage());
                }
            }

            try {
                StepResult result = step.execute(context);
                stepResults.add(result);

                log.info("Step [{}] completed. status={}, read={}, write={}, duration={}ms ({}/{})",
                        step.getStepId(), result.getStatus(),
                        result.getReadCount(), result.getWriteCount(), result.getDurationMs(),
                        stepOrder, totalSteps);

                // Step 완료 콜백 호출
                if (progressCallback != null) {
                    try {
                        progressCallback.onStepFinished(executionId, result, stepOrder, totalSteps);
                    } catch (Exception e) {
                        log.warn("Failed to notify step finished: {}", e.getMessage());
                    }
                }

                if (result.getStatus() == Status.FAILED) {
                    finalStatus = Status.FAILED;
                    errorMessage = result.getErrorMessage();
                    log.error("Step [{}] failed: {}", step.getStepId(), errorMessage);
                    break;
                }
            } catch (Exception e) {
                log.error("Step [{}] threw exception: {}", step.getStepId(), e.getMessage(), e);
                StepResult failedResult = StepResult.failed(
                        step.getStepId(),
                        e.getMessage(),
                        System.currentTimeMillis() - startTime
                );
                stepResults.add(failedResult);

                // 실패 시에도 콜백 호출
                if (progressCallback != null) {
                    try {
                        progressCallback.onStepFinished(executionId, failedResult, stepOrder, totalSteps);
                    } catch (Exception ex) {
                        log.warn("Failed to notify step finished: {}", ex.getMessage());
                    }
                }

                finalStatus = Status.FAILED;
                errorMessage = e.getMessage();
                break;
            }
        }

        long totalDuration = System.currentTimeMillis() - startTime;
        log.info("Pipeline [{}] finished. status={}, totalDuration={}ms",
                pipelineId, finalStatus, totalDuration);

        return PipelineResult.builder()
                .executionId(executionId)
                .pipelineId(pipelineId)
                .status(finalStatus)
                .stepResults(stepResults)
                .totalDurationMs(totalDuration)
                .errorMessage(errorMessage)
                .build();
    }

    /**
     * params에서 ExecutionOptions 생성
     *
     * filters 형식 (Orchestrator에서 전달):
     * [{"paramId":"sido","value":"경기도"}, ...]
     *
     * @param contextParams context.getParams() (시간이 이미 파싱된 Map)
     * @param rawParams 원본 params Map (filters 키 포함)
     */
    @SuppressWarnings("unchecked")
    private ExecutionOptions buildExecutionOptions(Map<String, Object> contextParams, Map<String, Object> rawParams) {
        ExecutionOptions.ExecutionOptionsBuilder builder = ExecutionOptions.builder();

        // 1. TimeRange
        LocalDateTime startTime = contextParams.get("startTime") instanceof LocalDateTime
                ? (LocalDateTime) contextParams.get("startTime") : null;
        LocalDateTime endTime = contextParams.get("endTime") instanceof LocalDateTime
                ? (LocalDateTime) contextParams.get("endTime") : null;

        if (startTime != null) {
            builder.timeRange(ExecutionOptions.TimeRange.builder()
                    .startTime(startTime)
                    .endTime(endTime)
                    .build());
        }

        // 2. Params (Orchestrator에서 전달)
        List<ExecutionOptions.ExecutionParam> execParams = new ArrayList<>();
        Object filtersObj = rawParams.get("filters");
        if (filtersObj instanceof List) {
            List<Map<String, Object>> filterMaps = (List<Map<String, Object>>) filtersObj;
            for (Map<String, Object> fm : filterMaps) {
                execParams.add(ExecutionOptions.ExecutionParam.builder()
                        .paramId((String) fm.get("paramId"))
                        .value(fm.get("value"))
                        .build());
            }
        }
        builder.params(execParams);

        // 3. Conditions (동적 WHERE 조건)
        List<ExecutionCondition> conditions = new ArrayList<>();
        Object conditionsObj = rawParams.get("conditions");
        if (conditionsObj instanceof List) {
            List<Map<String, Object>> condMaps = (List<Map<String, Object>>) conditionsObj;
            for (Map<String, Object> cm : condMaps) {
                String column = (String) cm.get("column");
                String operatorStr = (String) cm.get("operator");
                String value = cm.get("value") != null ? String.valueOf(cm.get("value")) : null;
                String value2 = cm.get("value2") != null ? String.valueOf(cm.get("value2")) : null;

                if (column != null && operatorStr != null) {
                    try {
                        ConditionOperator operator = ConditionOperator.valueOf(operatorStr.toUpperCase());
                        conditions.add(ExecutionCondition.builder()
                                .column(column)
                                .operator(operator)
                                .value(value)
                                .value2(value2)
                                .build());
                    } catch (IllegalArgumentException e) {
                        log.warn("Unknown condition operator: {}, skipping", operatorStr);
                    }
                }
            }
        }
        builder.conditions(conditions);

        return builder.build();
    }

    /**
     * Object를 LocalDateTime으로 변환
     * - LocalDateTime: 그대로 반환
     * - String: ISO 형식으로 파싱 (yyyy-MM-ddTHH:mm:ss)
     */
    private LocalDateTime parseLocalDateTime(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof LocalDateTime) {
            return (LocalDateTime) obj;
        }
        if (obj instanceof String) {
            String str = (String) obj;
            if (!str.isBlank()) {
                try {
                    return LocalDateTime.parse(str);
                } catch (Exception e) {
                    log.warn("Failed to parse LocalDateTime from string: {}", str);
                }
            }
        }
        return null;
    }
}
