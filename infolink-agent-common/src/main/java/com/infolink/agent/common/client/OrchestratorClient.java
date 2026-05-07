package com.infolink.agent.common.client;

import com.infolink.agent.common.client.dto.ExecutionFinishRequest;
import com.infolink.agent.common.client.dto.ExecutionStartRequest;
import com.infolink.agent.common.pipeline.PipelineResult;
import com.infolink.agent.common.step.StepResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Agent → Orchestrator 실행 콜백 클라이언트
 *
 * 파이프라인 실행 시작/완료를 Orchestrator에 HTTP POST로 알린다.
 * PipelineService에서 실행마다 new OrchestratorClient(url, agentCode)로 생성 (stateless).
 *
 * ── 콜백 엔드포인트 ──
 * - POST {orchestratorUrl}/api/callback/started  — 실행 시작 알림
 * - POST {orchestratorUrl}/api/callback/finished — 실행 완료 알림 (Step 결과 일괄 전송)
 *
 * ── 재시도 ──
 * sendWithRetry(): 최대 3회, 지연 = 2초 × 시도번호 (2s, 4s, 6s)
 * 전부 실패해도 파이프라인 실행 자체에는 영향 없음 (finally 블록에서 호출)
 *
 * ── Step 콜백 ──
 * StepProgressCallback을 구현하지만, Step별 실시간 콜백은 로그만 출력.
 * Step 결과는 notifyFinished()에서 PipelineResult로 일괄 전송.
 *
 * ── 사용처 ──
 * - infolink-agent-bojo-dmz: PipelineService.executeWithRunner()
 * - infolink-agent-bojo-internal: PipelineService.executeWithRunner()
 */
@Slf4j
public class OrchestratorClient implements com.infolink.agent.common.pipeline.StepProgressCallback {

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

    private static final int MAX_RETRY = 3;
    private static final long RETRY_DELAY_MS = 2000;

    public void notifyStarted(String executionId, String triggeredBy) {
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
        sendWithRetry(url, entity, "started", executionId);
    }

    public void notifyFinished(PipelineResult result) {
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
        sendWithRetry(url, entity, "finished", result.getExecutionId());
    }

    private void sendWithRetry(String url, HttpEntity<?> entity, String callbackType, String executionId) {
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            try {
                restTemplate.postForEntity(url, entity, Void.class);
                log.info("Notified orchestrator: execution {}. executionId={}", callbackType, executionId);
                return;
            } catch (Exception e) {
                log.warn("Callback {} 전송 실패 (시도 {}/{}): executionId={}, error={}",
                        callbackType, attempt, MAX_RETRY, executionId, e.getMessage());
                if (attempt < MAX_RETRY) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        log.error("Callback {} 전송 최종 실패: executionId={} ({}회 재시도 모두 실패)", callbackType, executionId, MAX_RETRY);
    }
}
