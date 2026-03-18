package com.sync.agent.bojo.loader.step;

import com.sync.agent.common.step.ConditionBuilder;
import com.sync.agent.common.step.ExecutionCondition;
import com.sync.agent.common.step.ExecutionOptions;
import com.sync.agent.common.step.StepContext;
import com.sync.agent.common.step.StepExecutor;
import com.sync.agent.common.step.StepResult;
import com.sync.agent.common.step.Status;
import com.sync.agent.bojo.entity.iftable.rsv.IfRsvSecJewon;
import com.sync.agent.bojo.entity.iftable.rsv.IfRsvSecObsvdata;
import com.sync.agent.bojo.loader.repository.TargetRepositoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 기본 Loader Step (default 모드)
 * IF_RSV 테이블 → Target 테이블 (sec_jewon, sec_obsvdata)
 *
 * 조회 전략:
 * - 증분: linkStatus IN ('PENDING', 'RESYNC')
 * - 시간지정: obsvDate 범위
 * - 전체재적재: 전체 조회
 */
@Slf4j
@Component
public class DefaultLoadStep implements StepExecutor {

    private final TargetRepositoryService targetRepository;
    private final LoaderStepHelper helper;

    @Value("${loader.step.id}")
    private String stepId;

    @Value("${loader.step.name}")
    private String stepName;

    @Value("${loader.if-table.jewon}")
    private String ifJewonTable;

    @Value("${loader.if-table.obsvdata}")
    private String ifObsvdataTable;

    @Value("${loader.target-table.jewon}")
    private String targetJewonTable;

    @Value("${loader.target-table.obsvdata}")
    private String targetObsvdataTable;

    public DefaultLoadStep(TargetRepositoryService targetRepository, LoaderStepHelper helper) {
        this.targetRepository = targetRepository;
        this.helper = helper;
    }

    @Override
    public String getStepId() {
        return stepId;
    }

    @Override
    public String getStepName() {
        return stepName;
    }

    @Override
    public StepResult execute(StepContext context) {
        long startTime = System.currentTimeMillis();
        int readCount = 0;
        int writeCount = 0;
        int skipCount = 0;

        LoaderStepHelper.ProcessResult jewonResult = new LoaderStepHelper.ProcessResult();
        LoaderStepHelper.ProcessResult obsvResult = new LoaderStepHelper.ProcessResult();

        try {
            ExecutionOptions options = context.getExecutionOptions();
            boolean isResyncExecution = options.hasConditions() || options.isTimeRangeExecution();

            // conditions + 시간범위 + obsv-code 파라미터를 통합 조건으로 merge
            List<ExecutionCondition> mergedConditions = buildMergedConditions(options);

            if (isResyncExecution) {
                log.info("[{}] Resync execution mode: {} conditions", getStepId(), mergedConditions.size());
                for (ExecutionCondition c : mergedConditions) {
                    log.info("[{}]   condition: {} {} {}", getStepId(), c.getColumn(), c.getOperator(), c.getValue());
                }
                // Link 테이블 갱신 스킵 (과거 데이터로 덮어쓰기 방지)
                context.getSharedData().put("skipLinkUpdate", true);
            }

            String executionId = context.getExecutionId();

            // ===== 1. 제원 데이터 처리 =====
            List<IfRsvSecJewon> pendingJewon = isResyncExecution
                    ? targetRepository.findIfRsvJewonForResync(mergedConditions)
                    : targetRepository.findIfRsvJewonPending(executionId);

            readCount += pendingJewon.size();
            log.info("[{}] Found {} {} jewon records", getStepId(), pendingJewon.size(),
                    isResyncExecution ? "matching conditions (resync)" : "pending");

            jewonResult = helper.processJewon(pendingJewon, executionId, getStepId(), ifJewonTable);
            writeCount += jewonResult.getWriteCount();
            skipCount += jewonResult.getFailedCount();

            // ===== 2. 관측데이터 처리 =====
            List<IfRsvSecObsvdata> pendingObsvData = isResyncExecution
                    ? targetRepository.findIfRsvObsvdataForResync(mergedConditions)
                    : targetRepository.findIfRsvObsvdataPending(executionId);

            readCount += pendingObsvData.size();
            log.info("[{}] Found {} {} obsvdata records", getStepId(), pendingObsvData.size(),
                    isResyncExecution ? "matching conditions (resync)" : "pending");

            obsvResult = helper.processObsvdata(pendingObsvData, executionId, getStepId(), ifObsvdataTable);
            writeCount += obsvResult.getWriteCount();
            skipCount += obsvResult.getFailedCount();

            log.info("[{}] Loaded {} records ({} skipped) in {}ms",
                    getStepId(), writeCount, skipCount, System.currentTimeMillis() - startTime);

            // SyncLog 저장
            helper.saveSyncLog(executionId, getStepId(), "jewon",
                    List.of(ifJewonTable), List.of(targetJewonTable),
                    pendingJewon.size(), jewonResult.getSuccessCount(), jewonResult.getFailedCount(), 0L,
                    jewonResult.getFailedKeys(), jewonResult.getFirstError());

            helper.saveSyncLog(executionId, getStepId(), "obsvdata",
                    List.of(ifObsvdataTable), List.of(targetObsvdataTable),
                    pendingObsvData.size(), obsvResult.getSuccessCount(), obsvResult.getFailedCount(), 0L,
                    obsvResult.getFailedKeys(), obsvResult.getFirstError());

            return StepResult.builder()
                    .stepId(getStepId())
                    .status(Status.SUCCESS)
                    .readCount(readCount)
                    .writeCount(writeCount)
                    .skipCount(skipCount)
                    .durationMs(System.currentTimeMillis() - startTime)
                    .build();

        } catch (Exception e) {
            log.error("Step execution failed", e);

            String errorMessage = e.getMessage();
            if (errorMessage != null && errorMessage.length() > 500) {
                errorMessage = errorMessage.substring(0, 500) + "...";
            }

            helper.saveSyncLog(context.getExecutionId(), getStepId(), "jewon",
                    List.of(ifJewonTable), List.of(targetJewonTable),
                    readCount, jewonResult.getSuccessCount(), jewonResult.getFailedCount(), 0L,
                    jewonResult.getFailedKeys(), errorMessage);
            helper.saveSyncLog(context.getExecutionId(), getStepId(), "obsvdata",
                    List.of(ifObsvdataTable), List.of(targetObsvdataTable),
                    0L, obsvResult.getSuccessCount(), obsvResult.getFailedCount(), 0L,
                    obsvResult.getFailedKeys(), errorMessage);

            return StepResult.failed(getStepId(), e.getMessage(), System.currentTimeMillis() - startTime);
        }
    }

    /**
     * ExecutionOptions에서 통합 조건 목록 생성
     * - conditions (동적 WHERE 조건)
     * - timeRange → obsv_date BETWEEN 조건으로 변환
     * - obsv-code 파라미터 → obsv_code EQ/IN 조건으로 변환
     */
    private List<ExecutionCondition> buildMergedConditions(ExecutionOptions options) {
        List<ExecutionCondition> merged = new ArrayList<>();

        // 1. 사용자 동적 조건
        if (options.hasConditions()) {
            merged.addAll(options.getConditions());
        }

        // 2. 시간 범위 → obsv_date BETWEEN 조건
        if (options.isTimeRangeExecution()) {
            LocalDateTime start = options.getTimeRange().getStartTime();
            LocalDateTime end = options.getTimeRange().getEndTime();
            if (start != null && end != null) {
                merged.add(ExecutionCondition.between("obsv_date",
                        start.toLocalDate().toString(), end.toLocalDate().toString()));
            } else if (start != null) {
                merged.add(ExecutionCondition.gte("obsv_date", start.toLocalDate().toString()));
            }
        }

        // 3. obsv-code 파라미터 → obsv_code 조건
        String filterObsvCode = options.getParamValue("obsv-code");
        if (filterObsvCode != null) {
            if (filterObsvCode.contains(",")) {
                merged.add(ExecutionCondition.in("obsv_code", filterObsvCode));
            } else {
                merged.add(ExecutionCondition.eq("obsv_code", filterObsvCode.trim()));
            }
        }

        return merged;
    }
}
