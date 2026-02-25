package com.sync.agent.common.client;

import com.sync.agent.common.client.dto.ExecutionFinishRequest;
import com.sync.agent.common.client.dto.ExecutionStartRequest;
import com.sync.agent.common.pipeline.PipelineResult;
import com.sync.agent.common.step.StepResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
public class OrchestratorClient implements com.sync.agent.common.pipeline.StepProgressCallback {

    private final String orchestratorUrl;
    private final String agentId;
    private final RestTemplate restTemplate;

    public OrchestratorClient(String orchestratorUrl, String agentId) {
        this.orchestratorUrl = orchestratorUrl;
        this.agentId = agentId;
        this.restTemplate = new RestTemplate();
    }

    public OrchestratorClient(String orchestratorUrl, String agentId, RestTemplate restTemplate) {
        this.orchestratorUrl = orchestratorUrl;
        this.agentId = agentId;
        this.restTemplate = restTemplate;
    }

    /**
     * Step 시작 알림 (로그만 출력, 실시간 콜백 제거 - finished에서 일괄 전송)
     */
    @Override
    public void onStepStarted(String executionId, String stepId, String stepName, int stepOrder, int totalSteps) {
        log.debug("Step started: executionId={}, step={} ({}/{})", executionId, stepId, stepOrder, totalSteps);
    }

    /**
     * Step 완료 알림 (로그만 출력, 실시간 콜백 제거 - finished에서 일괄 전송)
     */
    @Override
    public void onStepFinished(String executionId, StepResult result, int stepOrder, int totalSteps) {
        log.debug("Step finished: executionId={}, step={}, status={}", executionId, result.getStepId(), result.getStatus());
    }

    public void notifyStarted(String executionId, String triggeredBy) {
        try {
            ExecutionStartRequest request = ExecutionStartRequest.builder()
                    .executionId(executionId)
                    .agentId(agentId)
                    .startedAt(LocalDateTime.now())
                    .triggeredBy(triggeredBy != null ? triggeredBy : "MANUAL")
                    .build();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<ExecutionStartRequest> entity = new HttpEntity<>(request, headers);

            String url = orchestratorUrl + "/api/callback/started";
            restTemplate.postForEntity(url, entity, Void.class);

            log.info("Notified orchestrator: execution started. executionId={}", executionId);
        } catch (Exception e) {
            log.error("Failed to notify orchestrator about execution start: {}", e.getMessage());
        }
    }

    public void notifyFinished(PipelineResult result) {
        try {
            // Step 결과를 DTO로 변환
            AtomicInteger order = new AtomicInteger(1);
            List<ExecutionFinishRequest.StepResultDto> stepDtos = result.getStepResults().stream()
                    .map(step -> ExecutionFinishRequest.StepResultDto.builder()
                            .stepId(step.getStepId())
                            .status(step.getStatus().name())
                            .readCount(step.getReadCount())
                            .writeCount(step.getWriteCount())
                            .skipCount(step.getSkipCount())
                            .durationMs(step.getDurationMs())
                            .errorMessage(step.getErrorMessage())
                            .stepOrder(order.getAndIncrement())
                            .build())
                    .collect(Collectors.toList());

            ExecutionFinishRequest request = ExecutionFinishRequest.builder()
                    .executionId(result.getExecutionId())
                    .agentId(agentId)
                    .status(result.getStatus().name())
                    .totalReadCount(result.getTotalReadCount())
                    .totalWriteCount(result.getTotalWriteCount())
                    .totalSkipCount(result.getTotalSkipCount())
                    .durationMs(result.getTotalDurationMs())
                    .errorMessage(result.getErrorMessage())
                    .finishedAt(LocalDateTime.now())
                    .stepResults(stepDtos)
                    .build();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<ExecutionFinishRequest> entity = new HttpEntity<>(request, headers);

            String url = orchestratorUrl + "/api/callback/finished";
            restTemplate.postForEntity(url, entity, Void.class);

            log.info("Notified orchestrator: execution finished. executionId={}, status={}, steps={}",
                    result.getExecutionId(), result.getStatus(), stepDtos.size());
        } catch (Exception e) {
            log.error("Failed to notify orchestrator about execution finish: {}", e.getMessage());
        }
    }
}
