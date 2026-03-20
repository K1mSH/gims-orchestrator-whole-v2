package com.sync.agent.bojoint.pipeline;

import com.sync.agent.common.datasource.DataSourceInfo;
import com.sync.agent.common.service.ExecutionService;
import com.sync.agent.common.client.OrchestratorClient;
import com.sync.agent.common.pipeline.PipelineResult;
import com.sync.agent.common.pipeline.PipelineRunner;
import com.sync.agent.common.step.Status;
import com.sync.agent.bojoint.config.pipeline.PipelineRegistry;
import com.sync.agent.bojoint.config.SyncDataSourceService;
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
 * 내부망 파이프라인 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PipelineService {

    private final PipelineRegistry pipelineRegistry;
    private final ExecutionService executionService;
    private final SyncDataSourceService syncDataSourceService;

    @Value("${agent.orchestrator-url}")
    private String orchestratorUrl;

    @Value("${agent.zone}")
    private String agentZone;

    private final Map<String, PipelineResult> executionResults = new ConcurrentHashMap<>();

    @Async("pipelineExecutor")
    public void executeAsync(String executionId, Map<String, Object> params) {
        final String finalExecutionId = (executionId == null || executionId.isEmpty())
                ? UUID.randomUUID().toString()
                : executionId;

        String agentCode = (String) params.get("agentCode");
        if (agentCode == null) {
            if (finalExecutionId.contains("_")) {
                agentCode = finalExecutionId.substring(0, finalExecutionId.lastIndexOf('_'));
            }
        }

        if (agentCode == null) {
            log.error("agentCode not found in params or executionId: {}", finalExecutionId);
            return;
        }

        final String finalAgentCode = agentCode;

        try {
            log.info("[BojoInt] Starting pipeline: executionId={}, agentCode={}", finalExecutionId, finalAgentCode);

            PipelineRunner baseRunner = pipelineRegistry.getRunner(finalAgentCode);

            OrchestratorClient orchestratorClient = new OrchestratorClient(orchestratorUrl, finalAgentCode);
            CompositeStepCallback callback = new CompositeStepCallback(orchestratorClient);
            PipelineRunner runner = baseRunner.withProgressCallback(callback);

            executeWithRunner(runner, orchestratorClient, finalExecutionId, finalAgentCode, params);
        } catch (Exception e) {
            log.error("[BojoInt] Pipeline failed before runner: {} - {}", finalExecutionId, e.getMessage(), e);
            notifyFailure(finalAgentCode, finalExecutionId, e.getMessage());
        } catch (Error err) {
            log.error("[BojoInt] CRITICAL ERROR: {} - {}", finalExecutionId, err.getMessage(), err);
            notifyFailure(finalAgentCode, finalExecutionId, "[CRITICAL] " + err.getClass().getSimpleName() + ": " + err.getMessage());
        }
    }

    private void executeWithRunner(PipelineRunner runner, OrchestratorClient orchestratorClient,
                                    String executionId, String agentCode, Map<String, Object> params) {
        PipelineResult result = null;
        try {
            // Proxy 경유로 credentials 해석
            String sourceDatasourceId = (String) params.get("sourceDatasourceId");
            String targetDatasourceId = (String) params.get("targetDatasourceId");

            DataSourceInfo sourceInfo;
            DataSourceInfo targetInfo;

            if (sourceDatasourceId != null) {
                sourceInfo = syncDataSourceService.resolveFromProxy(sourceDatasourceId);
            } else {
                sourceInfo = buildDataSourceInfo(params, "source");
            }

            if (targetDatasourceId != null) {
                targetInfo = syncDataSourceService.resolveFromProxy(targetDatasourceId);
            } else {
                targetInfo = buildDataSourceInfo(params, "target");
            }

            syncDataSourceService.setCurrentDatasources(sourceInfo, targetInfo);
            log.info("[BojoInt] Pipeline datasource configured - source: {} ({}:{}), target: {} ({}:{})",
                    sourceInfo.getDatasourceId(), sourceInfo.getHost(), sourceInfo.getPort(),
                    targetInfo.getDatasourceId(), targetInfo.getHost(), targetInfo.getPort());

            // Connection 풀 상태 검사
            String sourcePoolIssue = syncDataSourceService.checkPoolHealth(sourceInfo.getDatasourceId());
            if (sourcePoolIssue != null) {
                throw new IllegalStateException(sourcePoolIssue);
            }
            String targetPoolIssue = syncDataSourceService.checkPoolHealth(targetInfo.getDatasourceId());
            if (targetPoolIssue != null) {
                throw new IllegalStateException(targetPoolIssue);
            }

            executionService.recordExecutionStart(executionId, agentCode, sourceInfo.getDatasourceId(), targetInfo.getDatasourceId());

            String triggeredBy = params.get("triggeredBy") != null ? params.get("triggeredBy").toString() : "MANUAL";
            orchestratorClient.notifyStarted(executionId, triggeredBy);

            Map<String, Object> enrichedParams = new HashMap<>(params);
            enrichedParams.put("agentZone", agentZone);

            result = runner.run(executionId, enrichedParams);
            executionResults.put(executionId, result);
            executionService.recordExecutionFinish(executionId, result);

            log.info("[BojoInt] Pipeline completed: {} with status {}", executionId, result.getStatus());
        } catch (Exception e) {
            log.error("[BojoInt] Pipeline failed: {} - {}", executionId, e.getMessage(), e);
            result = buildFailedResult(executionId, e.getMessage());
            executionResults.put(executionId, result);
            try { executionService.recordExecutionFinish(executionId, result); } catch (Exception ex) { /* ignore */ }
        } catch (Error err) {
            log.error("[BojoInt] CRITICAL ERROR: {} - {}", executionId, err.getMessage(), err);
            result = buildFailedResult(executionId, "[CRITICAL] " + err.getClass().getSimpleName() + ": " + err.getMessage());
            executionResults.put(executionId, result);
            try { executionService.recordExecutionFinish(executionId, result); } catch (Exception ex) { /* ignore */ }
        } finally {
            if (result != null) {
                try { orchestratorClient.notifyFinished(result); } catch (Exception e) {
                    log.error("[BojoInt] Failed to notify orchestrator: {}", e.getMessage());
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
        Integer port;
        if (portObj instanceof Integer) {
            port = (Integer) portObj;
        } else if (portObj instanceof Number) {
            port = ((Number) portObj).intValue();
        } else if (portObj != null) {
            port = Integer.parseInt(portObj.toString());
        } else {
            log.error("[BojoInt] {} port is null! Available params: {}", prefix, params.keySet());
            port = null;
        }
        String databaseName = (String) params.get(prefix + "DatabaseName");
        String username = (String) params.get(prefix + "Username");
        String password = (String) params.get(prefix + "Password");

        if (datasourceId == null || host == null || port == null) {
            throw new IllegalArgumentException(prefix + " datasource info is incomplete. " +
                    "datasourceId=" + datasourceId + ", host=" + host + ", port=" + port);
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

    private void notifyFailure(String agentCode, String executionId, String errorMessage) {
        try {
            OrchestratorClient client = new OrchestratorClient(orchestratorUrl, agentCode);
            client.notifyFinished(buildFailedResult(executionId, errorMessage));
        } catch (Exception e) {
            log.error("[BojoInt] Failed to notify orchestrator about failure: {}", e.getMessage());
        }
    }

    public PipelineResult getExecutionResult(String executionId) {
        return executionResults.get(executionId);
    }
}
