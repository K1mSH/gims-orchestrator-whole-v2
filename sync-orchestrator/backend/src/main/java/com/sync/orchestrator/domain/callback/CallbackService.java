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
        // executionId에서 agentCode 추출 (executionId = {agentCode}_{uuid} 형식)
        String agentCode = extractAgentCodeFromExecutionId(request.getExecutionId());
        log.info("Received started callback: executionId={}, extractedAgentCode={} (requestAgentId={})",
                request.getExecutionId(), agentCode, request.getAgentId());

        Agent agent = agentRepository.findByAgentCode(agentCode)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentCode));

        // Agent 상태 업데이트
        agent.setStatus(AgentStatus.RUNNING);
        agent.setLastExecutedAt(request.getStartedAt());
        agentRepository.save(agent);

        // ExecutionHistory 생성 (RUNNING 상태)
        ExecutionHistory history = ExecutionHistory.builder()
                .executionId(request.getExecutionId())
                .agentCode(agentCode)
                .agentName(agent.getAgentName())
                .agentType(agent.getAgentType() != null ? agent.getAgentType().name() : null)
                .status(ExecutionStatus.RUNNING)
                .startedAt(request.getStartedAt())
                .triggeredBy(request.getTriggeredBy() != null ? request.getTriggeredBy() : "MANUAL")
                .build();
        executionHistoryRepository.save(history);

        log.info("Agent status updated to RUNNING, ExecutionHistory created: {}", agentCode);
    }

    /**
     * 실행 완료 콜백 처리
     * - Agent 상태를 ONLINE으로 복원
     * - ExecutionHistory 업데이트 (완료 정보)
     */
    @Transactional
    public void handleFinished(CallbackDto.FinishedRequest request) {
        // executionId에서 agentCode 추출 (executionId = {agentCode}_{uuid} 형식)
        String agentCode = extractAgentCodeFromExecutionId(request.getExecutionId());
        log.info("Received finished callback: executionId={}, extractedAgentCode={}, status={}",
                request.getExecutionId(), agentCode, request.getStatus());

        Agent agent = agentRepository.findByAgentCode(agentCode)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentCode));

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
                        .agentCode(agentCode)
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
                agentCode, request.getStatus());
    }

    /**
     * executionId에서 agentCode 추출
     * 형식: {agentCode}_{uuid}
     */
    private String extractAgentCodeFromExecutionId(String executionId) {
        if (executionId == null) {
            throw new IllegalArgumentException("executionId is null");
        }
        int lastUnderscoreIndex = executionId.lastIndexOf('_');
        if (lastUnderscoreIndex == -1) {
            throw new IllegalArgumentException("Invalid executionId format: " + executionId);
        }
        return executionId.substring(0, lastUnderscoreIndex);
    }

}
