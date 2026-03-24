package com.sync.agent.bojo.pipeline;

import com.sync.agent.common.datasource.DataSourceInfo;
import com.sync.agent.common.service.ExecutionService;
import com.sync.agent.common.client.OrchestratorClient;
import com.sync.agent.common.pipeline.PipelineResult;
import com.sync.agent.common.pipeline.PipelineRunner;
import com.sync.agent.common.step.Status;
import com.sync.agent.bojo.config.pipeline.PipelineRegistry;
import com.sync.agent.bojo.config.SyncDataSourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 통합 파이프라인 서비스
 *
 * agentCode를 params에서 받아 PipelineRegistry에서 적절한 Runner를 선택
 * OrchestratorClient는 실행마다 agentCode로 새로 생성 (stateless)
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
    private final Set<String> runningAgentCodes = ConcurrentHashMap.newKeySet();

    /**
     * 파이프라인 실행 (Orchestrator에서 호출)
     * agentCode를 params에서 추출하여 적절한 Runner 선택
     */
    @Async("pipelineExecutor")
    public void executeAsync(String executionId, Map<String, Object> params) {
        final String finalExecutionId = (executionId == null || executionId.isEmpty())
                ? UUID.randomUUID().toString()
                : executionId;

        // agentCode 추출
        String agentCode = (String) params.get("agentCode");
        if (agentCode == null) {
            // executionId에서 추출 시도 (형식: {agentCode}_{uuid})
            if (finalExecutionId.contains("_")) {
                agentCode = finalExecutionId.substring(0, finalExecutionId.lastIndexOf('_'));
            }
        }

        if (agentCode == null) {
            log.error("[Bojo] params 또는 executionId에서 agentCode를 찾을 수 없습니다: {}", finalExecutionId);
            return;
        }

        final String finalAgentCode = agentCode;

        runningAgentCodes.add(finalAgentCode);
        try {
            log.info("[Bojo] 파이프라인 시작: executionId={}, agentCode={}", finalExecutionId, finalAgentCode);

            PipelineRunner baseRunner = pipelineRegistry.getRunner(finalAgentCode);

            // OrchestratorClient 생성 (실행마다, 해당 agentCode로)
            OrchestratorClient orchestratorClient = new OrchestratorClient(orchestratorUrl, finalAgentCode);
            CompositeStepCallback callback = new CompositeStepCallback(orchestratorClient);
            PipelineRunner runner = baseRunner.withProgressCallback(callback);

            executeWithRunner(runner, orchestratorClient, finalExecutionId, finalAgentCode, params);
        } catch (Exception e) {
            log.error("[Bojo] Runner 실행 전 파이프라인 실패: {} - {}", finalExecutionId, e.getMessage(), e);
            notifyFailure(finalAgentCode, finalExecutionId, e.getMessage());
        } catch (Error err) {
            log.error("[Bojo] 치명적 오류: {} - {}", finalExecutionId, err.getMessage(), err);
            notifyFailure(finalAgentCode, finalExecutionId, "[CRITICAL] " + err.getClass().getSimpleName() + ": " + err.getMessage());
        } finally {
            runningAgentCodes.remove(finalAgentCode);
        }
    }

    private void executeWithRunner(PipelineRunner runner, OrchestratorClient orchestratorClient,
                                    String executionId, String agentCode, Map<String, Object> params) {
        PipelineResult result = null;
        try {
            // Proxy 경유로 credentials 해석 (params에 credentials 없이 datasourceId만으로 동작)
            String sourceDatasourceId = (String) params.get("sourceDatasourceId");
            String targetDatasourceId = (String) params.get("targetDatasourceId");

            DataSourceInfo sourceInfo;
            DataSourceInfo targetInfo;

            // params에 credentials가 있으면 무시하고 Proxy에서 해석
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
            log.info("[Bojo] 파이프라인 데이터소스 설정 완료 - source: {} ({}:{}), target: {} ({}:{})",
                    sourceInfo.getDatasourceId(), sourceInfo.getHost(), sourceInfo.getPort(),
                    targetInfo.getDatasourceId(), targetInfo.getHost(), targetInfo.getPort());

            // Connection 풀 상태 검사 (기존 풀이 있는 경우)
            String sourcePoolIssue = syncDataSourceService.checkPoolHealth(sourceInfo.getDatasourceId());
            if (sourcePoolIssue != null) {
                throw new IllegalStateException(sourcePoolIssue);
            }
            String targetPoolIssue = syncDataSourceService.checkPoolHealth(targetInfo.getDatasourceId());
            if (targetPoolIssue != null) {
                throw new IllegalStateException(targetPoolIssue);
            }

            // 로컬 DB에 실행 시작 기록
            executionService.recordExecutionStart(executionId, agentCode, sourceInfo.getDatasourceId(), targetInfo.getDatasourceId());

            // Orchestrator에 시작 알림
            String triggeredBy = params.get("triggeredBy") != null ? params.get("triggeredBy").toString() : "MANUAL";
            orchestratorClient.notifyStarted(executionId, triggeredBy);

            // Zone 정보 추가
            Map<String, Object> enrichedParams = new HashMap<>(params);
            enrichedParams.put("agentZone", agentZone);

            // 파이프라인 실행
            result = runner.run(executionId, enrichedParams);
            executionResults.put(executionId, result);
            executionService.recordExecutionFinish(executionId, result);

            log.info("[Bojo] 파이프라인 완료: {} 상태={}", executionId, result.getStatus());
        } catch (Exception e) {
            log.error("[Bojo] 파이프라인 실패: {} - {}", executionId, e.getMessage(), e);
            result = buildFailedResult(executionId, e.getMessage());
            executionResults.put(executionId, result);
            try { executionService.recordExecutionFinish(executionId, result); } catch (Exception ex) { /* ignore */ }
        } catch (Error err) {
            log.error("[Bojo] 치명적 오류: {} - {}", executionId, err.getMessage(), err);
            result = buildFailedResult(executionId, "[CRITICAL] " + err.getClass().getSimpleName() + ": " + err.getMessage());
            executionResults.put(executionId, result);
            try { executionService.recordExecutionFinish(executionId, result); } catch (Exception ex) { /* ignore */ }
        } finally {
            if (result != null) {
                try { orchestratorClient.notifyFinished(result); } catch (Exception e) {
                    log.error("[Bojo] Orchestrator 알림 실패: {}", e.getMessage());
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
            log.error("[Bojo] {} 포트가 null입니다! 사용 가능한 params: {}", prefix, params.keySet());
            port = null;
        }
        String databaseName = (String) params.get(prefix + "DatabaseName");
        String username = (String) params.get(prefix + "Username");
        String password = (String) params.get(prefix + "Password");

        if (datasourceId == null || host == null || port == null) {
            throw new IllegalArgumentException(prefix + " 데이터소스 정보가 불완전합니다. " +
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
            log.error("[Bojo] Orchestrator 실패 알림 전송 실패: {}", e.getMessage());
        }
    }

    public PipelineResult getExecutionResult(String executionId) {
        return executionResults.get(executionId);
    }

    public Set<String> getRunningAgentCodes() {
        return Collections.unmodifiableSet(runningAgentCodes);
    }
}
