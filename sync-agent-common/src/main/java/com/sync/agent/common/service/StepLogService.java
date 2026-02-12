package com.sync.agent.common.service;

import com.sync.agent.common.entity.StepLog;
import com.sync.agent.common.repository.StepLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StepLogService {

    private final StepLogRepository stepLogRepository;

    @Transactional
    public StepLog startStep(String executionId, String stepId, String stepName, int stepOrder, int totalSteps) {
        StepLog stepLog = StepLog.builder()
                .executionId(executionId)
                .stepId(stepId)
                .stepName(stepName)
                .stepOrder(stepOrder)
                .totalSteps(totalSteps)
                .status("RUNNING")
                .startedAt(LocalDateTime.now())
                .build();

        StepLog saved = stepLogRepository.save(stepLog);
        log.info("Step started: executionId={}, stepId={}, order={}/{}",
                executionId, stepId, stepOrder, totalSteps);
        return saved;
    }

    @Transactional
    public StepLog finishStep(String executionId, String stepId, String status,
                              Integer readCount, Integer writeCount, Integer skipCount,
                              String errorMessage) {
        return finishStep(executionId, stepId, status, readCount, writeCount, skipCount,
                errorMessage, null, null);
    }

    @Transactional
    public StepLog finishStep(String executionId, String stepId, String status,
                              Integer readCount, Integer writeCount, Integer skipCount,
                              String errorMessage, String sourceTable, String targetTable) {
        StepLog stepLog = stepLogRepository.findByExecutionIdAndStepId(executionId, stepId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "StepLog not found: executionId=" + executionId + ", stepId=" + stepId));

        stepLog.setStatus(status);
        stepLog.setReadCount(readCount);
        stepLog.setWriteCount(writeCount);
        stepLog.setSkipCount(skipCount);
        stepLog.setErrorMessage(errorMessage);
        stepLog.setSourceTable(sourceTable);
        stepLog.setTargetTable(targetTable);
        stepLog.setFinishedAt(LocalDateTime.now());

        StepLog saved = stepLogRepository.save(stepLog);
        log.info("Step finished: executionId={}, stepId={}, status={}, read={}, write={}, skip={}, source={}, target={}",
                executionId, stepId, status, readCount, writeCount, skipCount, sourceTable, targetTable);
        return saved;
    }

    public List<StepLog> getStepLogs(String executionId) {
        return stepLogRepository.findByExecutionIdOrderByStepOrderAsc(executionId);
    }
}
