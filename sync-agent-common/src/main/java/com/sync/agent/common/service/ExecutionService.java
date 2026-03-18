package com.sync.agent.common.service;

import com.sync.agent.common.entity.Execution;
import com.sync.agent.common.pipeline.PipelineResult;
import com.sync.agent.common.repository.ExecutionRepository;
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

    /**
     * 실행 시작 이력 기록 (Agent 로컬 DB)
     * 실제 파이프라인 실행이 아닌, 실행 현황을 DB에 INSERT하는 메서드.
     * 실제 실행은 PipelineService의 runner.run()에서 이루어진다.
     */
    @Transactional
    public Execution recordExecutionStart(String executionId) {
        return recordExecutionStart(executionId, null, null, null);
    }

    @Transactional
    public Execution recordExecutionStart(String executionId, String agentId) {
        return recordExecutionStart(executionId, agentId, null, null);
    }

    @Transactional
    public Execution recordExecutionStart(String executionId, String agentId, String sourceDatasourceId, String targetDatasourceId) {
        Execution execution = Execution.builder()
                .executionId(executionId)
                .agentId(agentId)
                .status("RUNNING")
                .startedAt(LocalDateTime.now())
                .sourceDatasourceId(sourceDatasourceId)
                .targetDatasourceId(targetDatasourceId)
                .build();

        Execution saved = executionRepository.save(execution);
        log.info("Execution recorded (start): {} by agent {} (source: {}, target: {})", executionId, agentId, sourceDatasourceId, targetDatasourceId);
        return saved;
    }

    /**
     * 실행 완료 이력 기록 (Agent 로컬 DB)
     * PipelineResult의 통계를 집계하여 기존 실행 이력을 UPDATE한다.
     */
    @Transactional
    public Execution recordExecutionFinish(String executionId, PipelineResult result) {
        Execution execution = executionRepository.findByExecutionId(executionId);
        if (execution == null) {
            execution = Execution.builder()
                    .executionId(executionId)
                    .startedAt(LocalDateTime.now())
                    .build();
        }

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
        log.info("Execution recorded (finish): {} with status {}", executionId, result.getStatus());
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
}
