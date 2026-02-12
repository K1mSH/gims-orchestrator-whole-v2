package com.sync.orchestrator.domain.callback;

import com.sync.orchestrator.domain.agent.Agent;
import com.sync.orchestrator.domain.agent.AgentRepository;
import com.sync.orchestrator.domain.agent.AgentStatus;
import com.sync.orchestrator.domain.execution.ExecutionHistory;
import com.sync.orchestrator.domain.execution.ExecutionHistoryRepository;
import com.sync.orchestrator.domain.execution.ExecutionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Agent 콜백 처리 서비스
 * - 실행 시작/완료 시 ExecutionHistory에 요약 정보 저장
 * - Agent 상태 관리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CallbackService {

    private final AgentRepository agentRepository;
    private final ExecutionHistoryRepository executionHistoryRepository;

    /**
     * 실행 시작 콜백 처리
     * - Agent 상태를 RUNNING으로 변경
     * - ExecutionHistory 생성 (RUNNING 상태)
     */
    @Transactional
    public void handleStarted(CallbackDto.StartedRequest request) {
        // executionId에서 실제 agentId 추출 (executionId = {agentId}_{uuid} 형식)
        String agentId = extractAgentIdFromExecutionId(request.getExecutionId());
        log.info("Received started callback: executionId={}, extractedAgentId={} (requestAgentId={})",
                request.getExecutionId(), agentId, request.getAgentId());

        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));

        // Agent 상태 업데이트
        agent.setStatus(AgentStatus.RUNNING);
        agent.setLastExecutedAt(request.getStartedAt());
        agentRepository.save(agent);

        // ExecutionHistory 생성 (RUNNING 상태)
        ExecutionHistory history = ExecutionHistory.builder()
                .executionId(request.getExecutionId())
                .agentId(agentId)
                .agentName(agent.getAgentName())
                .agentType(agent.getAgentType() != null ? agent.getAgentType().name() : null)
                .status(ExecutionStatus.RUNNING)
                .startedAt(request.getStartedAt())
                .triggeredBy(request.getTriggeredBy() != null ? request.getTriggeredBy() : "MANUAL")
                .build();
        executionHistoryRepository.save(history);

        log.info("Agent status updated to RUNNING, ExecutionHistory created: {}", agentId);
    }

    /**
     * 실행 완료 콜백 처리
     * - Agent 상태를 ONLINE으로 복원
     * - ExecutionHistory 업데이트 (완료 정보)
     */
    @Transactional
    public void handleFinished(CallbackDto.FinishedRequest request) {
        // executionId에서 실제 agentId 추출 (executionId = {agentId}_{uuid} 형식)
        String agentId = extractAgentIdFromExecutionId(request.getExecutionId());
        log.info("Received finished callback: executionId={}, extractedAgentId={}, status={}",
                request.getExecutionId(), agentId, request.getStatus());

        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));

        // Agent 상태 업데이트
        if (agent.getStatus() == AgentStatus.RUNNING) {
            agent.setStatus(AgentStatus.ONLINE);
        }
        agent.setLastExecutionStatus(request.getStatus());
        agentRepository.save(agent);

        // ExecutionHistory 업데이트
        ExecutionHistory history = executionHistoryRepository.findById(request.getExecutionId())
                .orElse(ExecutionHistory.builder()
                        .executionId(request.getExecutionId())
                        .agentId(agentId)
                        .agentName(agent.getAgentName())
                        .build());

        history.setStatus(ExecutionStatus.valueOf(request.getStatus()));
        history.setTotalReadCount(request.getTotalReadCount() != null ? request.getTotalReadCount().longValue() : null);
        history.setTotalWriteCount(request.getTotalWriteCount() != null ? request.getTotalWriteCount().longValue() : null);
        history.setTotalSkipCount(request.getTotalSkipCount() != null ? request.getTotalSkipCount().longValue() : null);
        history.setDurationMs(request.getDurationMs());
        history.setErrorMessage(request.getErrorMessage());
        history.setFinishedAt(request.getFinishedAt());
        executionHistoryRepository.save(history);

        log.info("Agent status updated to ONLINE, ExecutionHistory updated: {} -> {}",
                agentId, request.getStatus());
    }

    /**
     * executionId에서 agentId 추출
     * 형식: {agentId}_{uuid}
     */
    private String extractAgentIdFromExecutionId(String executionId) {
        if (executionId == null) {
            throw new IllegalArgumentException("executionId is null");
        }
        int lastUnderscoreIndex = executionId.lastIndexOf('_');
        if (lastUnderscoreIndex == -1) {
            throw new IllegalArgumentException("Invalid executionId format: " + executionId);
        }
        return executionId.substring(0, lastUnderscoreIndex);
    }

    /**
     * 실시간 Step 진행 상황 콜백 처리
     * - 로그만 기록 (실행 데이터는 Agent DB에 저장됨)
     * - 추후 WebSocket을 통한 실시간 UI 업데이트에 활용 가능
     */
    @Transactional
    public void handleStep(CallbackDto.StepCallbackRequest request) {
        log.info("Received step callback: executionId={}, stepId={}, status={}, order={}/{}",
                request.getExecutionId(), request.getStepId(), request.getStatus(),
                request.getStepOrder(), request.getTotalSteps());

        // Step 데이터는 Agent DB에 저장됨
        // 여기서는 로그만 기록하고, 추후 WebSocket으로 프론트엔드에 실시간 전송 가능
    }
}
