package com.sync.agent.common.service;

import com.sync.agent.common.entity.Execution;
import com.sync.agent.common.entity.StepLog;
import com.sync.agent.common.pipeline.PipelineResult;
import com.sync.agent.common.repository.ExecutionRepository;
import com.sync.agent.common.repository.StepLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutionService {

    private final ExecutionRepository executionRepository;
    private final StepLogRepository stepLogRepository;

    @Transactional
    public Execution startExecution(String executionId) {
        return startExecution(executionId, null, null, null);
    }

    @Transactional
    public Execution startExecution(String executionId, String agentId) {
        return startExecution(executionId, agentId, null, null);
    }

    @Transactional
    public Execution startExecution(String executionId, String agentId, String sourceDatasourceId, String targetDatasourceId) {
        Execution execution = Execution.builder()
                .executionId(executionId)
                .agentId(agentId)
                .status("RUNNING")
                .startedAt(LocalDateTime.now())
                .sourceDatasourceId(sourceDatasourceId)
                .targetDatasourceId(targetDatasourceId)
                .build();

        Execution saved = executionRepository.save(execution);
        log.info("Execution started: {} by agent {} (source: {}, target: {})", executionId, agentId, sourceDatasourceId, targetDatasourceId);
        return saved;
    }

    @Transactional
    public Execution finishExecution(String executionId, PipelineResult result) {
        Execution execution = executionRepository.findByExecutionId(executionId);
        if (execution == null) {
            execution = Execution.builder()
                    .executionId(executionId)
                    .startedAt(LocalDateTime.now())
                    .build();
        }

        // 집계
        int totalRead = result.getStepResults().stream()
                .mapToInt(s -> s.getReadCount())
                .sum();
        int totalWrite = result.getStepResults().stream()
                .mapToInt(s -> s.getWriteCount())
                .sum();
        int totalSkip = result.getStepResults().stream()
                .mapToInt(s -> s.getSkipCount())
                .sum();

        execution.setStatus(result.getStatus().name());
        execution.setTotalReadCount(totalRead);
        execution.setTotalWriteCount(totalWrite);
        execution.setTotalSkipCount(totalSkip);
        execution.setDurationMs(result.getTotalDurationMs());
        execution.setErrorMessage(result.getErrorMessage());
        execution.setFinishedAt(LocalDateTime.now());

        Execution saved = executionRepository.save(execution);
        log.info("Execution finished: {} with status {}", executionId, result.getStatus());
        return saved;
    }

    public Optional<Execution> getExecution(String executionId) {
        return Optional.ofNullable(executionRepository.findByExecutionId(executionId));
    }

    public List<Execution> getRecentExecutions() {
        return executionRepository.findTop10ByOrderByStartedAtDesc();
    }

    public List<Execution> getAllExecutions() {
        return executionRepository.findAllByOrderByStartedAtDesc();
    }

    public List<StepLog> getStepLogs(String executionId) {
        return stepLogRepository.findByExecutionIdOrderByStepOrderAsc(executionId);
    }
}
