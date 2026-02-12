package com.sync.agent.bojo.pipeline;

import com.sync.agent.common.datasource.DataSourceInfo;
import com.sync.agent.common.service.ExecutionService;
import com.sync.agent.common.service.StepLogService;
import com.sync.agent.common.client.OrchestratorClient;
import com.sync.agent.common.pipeline.PipelineResult;
import com.sync.agent.common.pipeline.PipelineRunner;
import com.sync.agent.common.step.Status;
import com.sync.agent.bojo.config.PipelineRegistry;
import com.sync.agent.bojo.config.SyncDataSourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 통합 파이프라인 서비스
 *
 * agentId를 params에서 받아 PipelineRegistry에서 적절한 Runner를 선택
 * OrchestratorClient는 실행마다 agentId로 새로 생성 (stateless)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PipelineService {

    private final PipelineRegistry pipelineRegistry;
    private final StepLogService stepLogService;
    private final ExecutionService executionService;
    private final SyncDataSourceService syncDataSourceService;

    @Value("${agent.orchestrator-url}")
    private String orchestratorUrl;

    @Value("${agent.zone}")
    private String agentZone;

    private final Map<String, PipelineResult> executionResults = new ConcurrentHashMap<>();

    /**
     * 파이프라인 실행 (Orchestrator에서 호출)
     * agentId를 params에서 추출하여 적절한 Runner 선택
     */
    @Async("pipelineExecutor")
    public void executeAsync(String executionId, Map<String, Object> params) {
        final String finalExecutionId = (executionId == null || executionId.isEmpty())
                ? UUID.randomUUID().toString()
                : executionId;

        // agentId 추출
        String agentId = (String) params.get("agentId");
        if (agentId == null) {
            // executionId에서 추출 시도 (형식: {agentId}_{uuid})
            if (finalExecutionId.contains("_")) {
                agentId = finalExecutionId.substring(0, finalExecutionId.lastIndexOf('_'));
            }
        }

        if (agentId == null) {
            log.error("agentId not found in params or executionId: {}", finalExecutionId);
            return;
        }

        final String finalAgentId = agentId;

        try {
            log.info("[Bojo] Starting pipeline: executionId={}, agentId={}", finalExecutionId, finalAgentId);

            PipelineRunner baseRunner = pipelineRegistry.getRunner(finalAgentId);

            // OrchestratorClient 생성 (실행마다, 해당 agentId로)
            OrchestratorClient orchestratorClient = new OrchestratorClient(orchestratorUrl, finalAgentId);
            CompositeStepCallback callback = new CompositeStepCallback(stepLogService, orchestratorClient);
            PipelineRunner runner = baseRunner.withProgressCallback(callback);

            executeWithRunner(runner, orchestratorClient, finalExecutionId, finalAgentId, params);
        } catch (Exception e) {
            log.error("[Bojo] Pipeline failed before runner: {} - {}", finalExecutionId, e.getMessage(), e);
            notifyFailure(finalAgentId, finalExecutionId, e.getMessage());
        } catch (Error err) {
            log.error("[Bojo] CRITICAL ERROR: {} - {}", finalExecutionId, err.getMessage(), err);
            notifyFailure(finalAgentId, finalExecutionId, "[CRITICAL] " + err.getClass().getSimpleName() + ": " + err.getMessage());
        }
    }

    private void executeWithRunner(PipelineRunner runner, OrchestratorClient orchestratorClient,
                                    String executionId, String agentId, Map<String, Object> params) {
        PipelineResult result = null;
        try {
            // ThreadLocal에 현재 파이프라인의 datasource 연결 정보 설정
            DataSourceInfo sourceInfo = buildDataSourceInfo(params, "source");
            DataSourceInfo targetInfo = buildDataSourceInfo(params, "target");
            syncDataSourceService.setCurrentDatasources(sourceInfo, targetInfo);
            log.info("[Bojo] Pipeline datasource configured - source: {} ({}:{}), target: {} ({}:{})",
                    sourceInfo.getDatasourceId(), sourceInfo.getHost(), sourceInfo.getPort(),
                    targetInfo.getDatasourceId(), targetInfo.getHost(), targetInfo.getPort());

            // 로컬 DB에 실행 시작 기록
            executionService.startExecution(executionId, agentId, sourceInfo.getDatasourceId(), targetInfo.getDatasourceId());

            // Orchestrator에 시작 알림
            String triggeredBy = params.get("triggeredBy") != null ? params.get("triggeredBy").toString() : "MANUAL";
            orchestratorClient.notifyStarted(executionId, triggeredBy);

            // Zone 정보 추가
            Map<String, Object> enrichedParams = new HashMap<>(params);
            enrichedParams.put("agentZone", agentZone);

            // 파이프라인 실행
            result = runner.run(executionId, enrichedParams);
            executionResults.put(executionId, result);
            executionService.finishExecution(executionId, result);

            log.info("[Bojo] Pipeline completed: {} with status {}", executionId, result.getStatus());
        } catch (Exception e) {
            log.error("[Bojo] Pipeline failed: {} - {}", executionId, e.getMessage(), e);
            result = buildFailedResult(executionId, e.getMessage());
            executionResults.put(executionId, result);
            try { executionService.finishExecution(executionId, result); } catch (Exception ex) { /* ignore */ }
        } catch (Error err) {
            log.error("[Bojo] CRITICAL ERROR: {} - {}", executionId, err.getMessage(), err);
            result = buildFailedResult(executionId, "[CRITICAL] " + err.getClass().getSimpleName() + ": " + err.getMessage());
            executionResults.put(executionId, result);
            try { executionService.finishExecution(executionId, result); } catch (Exception ex) { /* ignore */ }
        } finally {
            if (result != null) {
                try { orchestratorClient.notifyFinished(result); } catch (Exception e) {
                    log.error("[Bojo] Failed to notify orchestrator: {}", e.getMessage());
                }
            }
            syncDataSourceService.clearCurrentDatasources();
        }
    }

    private DataSourceInfo buildDataSourceInfo(Map<String, Object> params, String prefix) {
        String datasourceId = (String) params.get(prefix + "DatasourceId");
        String dbType = (String) params.get(prefix + "DbType");
        String host = (String) params.get(prefix + "Host");
        Object portObj = params.get(prefix + "Port");
        Integer port = portObj instanceof Integer ? (Integer) portObj : Integer.parseInt(portObj.toString());
        String databaseName = (String) params.get(prefix + "DatabaseName");
        String username = (String) params.get(prefix + "Username");
        String password = (String) params.get(prefix + "Password");

        if (datasourceId == null || host == null || port == null) {
            throw new IllegalArgumentException(prefix + " datasource info is incomplete.");
        }

        return DataSourceInfo.builder()
                .datasourceId(datasourceId)
                .dbType(dbType)
                .host(host)
                .port(port)
                .databaseName(databaseName)
                .username(username)
                .password(password)
                .build();
    }

    private PipelineResult buildFailedResult(String executionId, String errorMessage) {
        return PipelineResult.builder()
                .executionId(executionId)
                .status(Status.FAILED)
                .errorMessage(errorMessage)
                .totalDurationMs(0L)
                .stepResults(Collections.emptyList())
                .build();
    }

    private void notifyFailure(String agentId, String executionId, String errorMessage) {
        try {
            OrchestratorClient client = new OrchestratorClient(orchestratorUrl, agentId);
            client.notifyFinished(buildFailedResult(executionId, errorMessage));
        } catch (Exception e) {
            log.error("[Bojo] Failed to notify orchestrator about failure: {}", e.getMessage());
        }
    }

    public PipelineResult getExecutionResult(String executionId) {
        return executionResults.get(executionId);
    }
}
