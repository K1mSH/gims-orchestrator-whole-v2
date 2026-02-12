package com.sync.agent.bojo.loader.step;

import com.sync.agent.common.entity.SyncLog;
import com.sync.agent.common.repository.SyncLogRepository;
import com.sync.agent.common.service.IfTableService;
import com.sync.agent.common.step.StepContext;
import com.sync.agent.common.step.StepExecutor;
import com.sync.agent.common.step.StepResult;
import com.sync.agent.common.step.Status;
import com.sync.agent.bojo.entity.iftable.rsv.IfRsvSecJewon;
import com.sync.agent.bojo.entity.iftable.rsv.IfRsvSecObsvdata;
import com.sync.agent.bojo.entity.target.SecJewon;
import com.sync.agent.bojo.entity.target.SecObsvdata;
import com.sync.agent.bojo.loader.repository.TargetRepositoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 대전 관측망 데이터 적재 Step (JPA 버전)
 * IF_RSV 테이블 → Target 테이블 (sec_jewon, sec_obsvdata, link_ngwis)
 *
 * 모든 테이블이 Target DB에 있으므로 DynamicEntityManagerService 사용
 */
@Slf4j
@Component
public class DaejeonLoadStep implements StepExecutor {

    private final TargetRepositoryService targetRepository;
    private final SyncLogRepository syncLogRepository;
    private final IfTableService ifTableService;

    // Step 설정
    @Value("${loader.step.id}")
    private String stepId;

    @Value("${loader.step.name}")
    private String stepName;

    // IF 테이블 (읽기)
    @Value("${loader.if-table.jewon}")
    private String ifJewonTable;

    @Value("${loader.if-table.obsvdata}")
    private String ifObsvdataTable;

    // Target 테이블 (쓰기)
    @Value("${loader.target-table.jewon}")
    private String targetJewonTable;

    @Value("${loader.target-table.obsvdata}")
    private String targetObsvdataTable;

    // 기타 설정
    @Value("${agent.debug.step-delay-ms}")
    private long stepDelayMs;

    @Value("${agent.batch.insert-batch-size}")
    private int batchSize;

    public DaejeonLoadStep(TargetRepositoryService targetRepository,
                           SyncLogRepository syncLogRepository,
                           IfTableService ifTableService) {
        this.targetRepository = targetRepository;
        this.syncLogRepository = syncLogRepository;
        this.ifTableService = ifTableService;
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

        // 테이블별 처리 결과 추적
        int jewonSuccess = 0, jewonFailed = 0;
        int obsvSuccess = 0, obsvFailed = 0;
        List<String> jewonFailedKeys = new ArrayList<>();
        List<String> obsvFailedKeys = new ArrayList<>();
        String jewonFirstError = null;
        String obsvFirstError = null;

        try {
            // 시간지정실행 여부 확인
            LocalDateTime paramStartTime = context.getParam("startTime");
            LocalDateTime paramEndTime = context.getParam("endTime");
            boolean isTimeRangeExecution = (paramStartTime != null || paramEndTime != null);

            if (isTimeRangeExecution) {
                log.info("[{}] Time-range execution mode: {} ~ {} (will resync ALL data)",
                        getStepId(), paramStartTime, paramEndTime);
            }

            // obsv-code 파라미터 추출 (프리셋 방식: Agent가 직접 해석)
            String filterObsvCode = context.getExecutionOptions().getParamValue("obsv-code");
            if (filterObsvCode != null) {
                log.info("[{}] obsv_code filter: {}", getStepId(), filterObsvCode);
            }

            // 1. 제원 데이터 처리
            // 시간지정실행: 전체 데이터 재동기화 (link_status 무시)
            // 일반실행: PENDING만 처리
            List<IfRsvSecJewon> pendingJewon = isTimeRangeExecution
                    ? targetRepository.findAllIfRsvJewonForResync()
                    : targetRepository.findIfRsvJewonPending(context.getExecutionId());

            // obsv_code 필터 적용 (in-memory)
            if (filterObsvCode != null) {
                pendingJewon = applyObsvCodeFilter(pendingJewon, filterObsvCode, IfRsvSecJewon::getObsvCode);
                log.info("[{}] After obsv_code filter: {} jewon records", getStepId(), pendingJewon.size());
            }
            readCount += pendingJewon.size();
            log.info("[{}] Found {} {} jewon records", getStepId(), pendingJewon.size(),
                    isTimeRangeExecution ? "total (resync)" : "pending");

            if (!pendingJewon.isEmpty()) {
                // 1-1. 기존 제원 삭제 (JPA)
                List<String> obsvCodes = pendingJewon.stream()
                        .map(IfRsvSecJewon::getObsvCode)
                        .toList();
                targetRepository.deleteJewonByObsvCodes(obsvCodes);

                // 1-2. 제원 INSERT (JPA)
                String loaderExecutionId = context.getExecutionId();
                List<SecJewon> jewonsToSave = new ArrayList<>();
                for (IfRsvSecJewon ifJewon : pendingJewon) {
                    try {
                        SecJewon secJewon = TargetRepositoryService.convertToSecJewon(ifJewon, loaderExecutionId);
                        jewonsToSave.add(secJewon);
                        updateIfJewonStatus(ifJewon, "SUCCESS", loaderExecutionId);
                        jewonSuccess++;
                    } catch (Exception e) {
                        log.error("Failed to convert jewon: {}", ifJewon.getObsvCode(), e);
                        skipCount++;
                        jewonFailed++;
                        jewonFailedKeys.add(ifJewon.getSourceRefs() != null ? ifJewon.getSourceRefs() : ifJewon.getObsvCode());
                        if (jewonFirstError == null) jewonFirstError = e.getMessage();
                        updateIfJewonStatus(ifJewon, "FAILED", loaderExecutionId);
                    }
                }

                // 일괄 저장
                if (!jewonsToSave.isEmpty()) {
                    int saved = targetRepository.saveAllJewon(jewonsToSave);
                    writeCount += saved;
                    log.info("[{}] Saved {} jewon records", getStepId(), saved);
                }
            }

            // 2. 관측데이터 처리
            // 시간지정실행: 해당 시간 범위의 모든 데이터 재동기화 (link_status 무시)
            // 일반실행: PENDING만 처리
            List<IfRsvSecObsvdata> pendingObsvData = isTimeRangeExecution
                    ? targetRepository.findIfRsvObsvdataByTimeRange(paramStartTime, paramEndTime)
                    : targetRepository.findIfRsvObsvdataPending(context.getExecutionId());

            // obsv_code 필터 적용 (in-memory)
            if (filterObsvCode != null) {
                pendingObsvData = applyObsvCodeFilter(pendingObsvData, filterObsvCode, IfRsvSecObsvdata::getObsvCode);
                log.info("[{}] After obsv_code filter: {} obsvdata records", getStepId(), pendingObsvData.size());
            }
            readCount += pendingObsvData.size();
            log.info("[{}] Found {} {} obsvdata records", getStepId(), pendingObsvData.size(),
                    isTimeRangeExecution ? "in time range (resync)" : "pending");

            if (!pendingObsvData.isEmpty()) {
                // 배치 처리
                String loaderExecId = context.getExecutionId();
                List<SecObsvdata> batch = new ArrayList<>();
                int resyncCount = 0;
                for (int i = 0; i < pendingObsvData.size(); i++) {
                    IfRsvSecObsvdata ifData = pendingObsvData.get(i);

                    try {
                        SecObsvdata secData = TargetRepositoryService.convertToSecObsvdata(ifData, loaderExecId);

                        // RESYNC 처리: 기존 레코드가 있으면 id 설정하여 UPDATE
                        if ("RESYNC".equals(ifData.getLinkStatus())) {
                            Integer existingId = targetRepository.findSecObsvdataIdByUniqueKey(
                                    ifData.getObsvCode(), ifData.getObsvDate(), ifData.getObsvTime());
                            if (existingId != null) {
                                secData.setId(existingId);  // JPA merge()가 UPDATE로 동작
                                resyncCount++;
                            }
                        }

                        batch.add(secData);
                        updateIfObsvDataStatus(ifData, "SUCCESS", loaderExecId);
                        obsvSuccess++;
                    } catch (Exception e) {
                        log.error("Failed to convert obsvdata: {}", ifData.getObsvCode(), e);
                        skipCount++;
                        obsvFailed++;
                        obsvFailedKeys.add(ifData.getSourceRefs() != null ? ifData.getSourceRefs() : ifData.getObsvCode());
                        if (obsvFirstError == null) obsvFirstError = e.getMessage();
                        updateIfObsvDataStatus(ifData, "FAILED", loaderExecId);
                    }

                    // 배치 사이즈에 도달하면 저장
                    if (batch.size() >= batchSize || i == pendingObsvData.size() - 1) {
                        if (!batch.isEmpty()) {
                            int saved = targetRepository.saveAllObsvData(batch);
                            writeCount += saved;
                            batch.clear();

                            // 진행률 로그
                            int processed = Math.min(i + 1, pendingObsvData.size());
                            int percent = (int) ((processed * 100.0) / pendingObsvData.size());
                            log.info("[{}] Processed {}/{} obsvdata ({}%)", getStepId(), processed, pendingObsvData.size(), percent);
                        }
                    }
                }

                if (resyncCount > 0) {
                    log.info("[{}] RESYNC: Updated {} existing obsvdata records", getStepId(), resyncCount);
                }

                // 3. Link 테이블 업데이트 (마지막 동기화 시점 기록) - JPA
                updateLinkTable(context, pendingObsvData);
            }

            log.info("[{}] Loaded {} records ({} skipped)", getStepId(), writeCount, skipCount);

            // 4. SyncLog 요약 저장 (테이블별 1건씩)
            // IF 테이블 (읽기 - LOADER 입장에서 SOURCE)
            saveSyncLogSummary(context.getExecutionId(), ifJewonTable, "IF",
                    (long) pendingJewon.size(), 0L, 0L, null, null);

            saveSyncLogSummary(context.getExecutionId(), ifObsvdataTable, "IF",
                    (long) pendingObsvData.size(), 0L, 0L, null, null);

            // TARGET 테이블 (쓰기)
            saveSyncLogSummary(context.getExecutionId(), targetJewonTable, "TARGET",
                    (long) jewonSuccess, (long) jewonFailed, 0L,
                    jewonFailedKeys.isEmpty() ? null : String.join(",", jewonFailedKeys),
                    jewonFirstError);

            saveSyncLogSummary(context.getExecutionId(), targetObsvdataTable, "TARGET",
                    (long) obsvSuccess, (long) obsvFailed, 0L,
                    obsvFailedKeys.isEmpty() ? null : String.join(",", obsvFailedKeys),
                    obsvFirstError);

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

            // SyncLog에 에러 정보 저장
            String errorMessage = e.getMessage();
            if (errorMessage != null && errorMessage.length() > 500) {
                errorMessage = errorMessage.substring(0, 500) + "...";
            }

            // IF 테이블 (읽기 정보 - 에러 발생 전까지 읽은 건수)
            saveSyncLogSummary(context.getExecutionId(), ifJewonTable, "IF",
                    (long) readCount, 0L, 0L, null, null);

            saveSyncLogSummary(context.getExecutionId(), ifObsvdataTable, "IF",
                    0L, 0L, 0L, null, null);

            // TARGET 테이블에 에러 정보 저장
            saveSyncLogSummary(context.getExecutionId(), targetJewonTable, "TARGET",
                    (long) jewonSuccess, (long) jewonFailed, 0L,
                    jewonFailedKeys.isEmpty() ? null : String.join(",", jewonFailedKeys),
                    errorMessage);

            saveSyncLogSummary(context.getExecutionId(), targetObsvdataTable, "TARGET",
                    (long) obsvSuccess, (long) obsvFailed, 0L,
                    obsvFailedKeys.isEmpty() ? null : String.join(",", obsvFailedKeys),
                    errorMessage);

            return StepResult.failed(getStepId(), e.getMessage(), System.currentTimeMillis() - startTime);
        }
    }

    /**
     * Link 테이블 업데이트 (마지막 동기화 시점) - JPA 사용
     */
    private void updateLinkTable(StepContext context, List<IfRsvSecObsvdata> processedData) {
        // 각 obsv_code별 마지막 데이터 찾기
        processedData.stream()
                .collect(Collectors.groupingBy(IfRsvSecObsvdata::getObsvCode))
                .forEach((obsvCode, dataList) -> {
                    // 가장 마지막 데이터 찾기
                    IfRsvSecObsvdata lastData = dataList.stream()
                            .max((a, b) -> {
                                int dateCompare = a.getObsvDate().compareTo(b.getObsvDate());
                                if (dateCompare != 0) return dateCompare;
                                return a.getObsvTime().compareTo(b.getObsvTime());
                            })
                            .orElse(null);

                    if (lastData != null) {
                        // JPA 방식으로 Link 업데이트
                        targetRepository.updateLinkLastSync(
                                obsvCode,
                                lastData.getObsvDate(),
                                lastData.getObsvTime()
                        );
                    }
                });
    }

    private void updateIfJewonStatus(IfRsvSecJewon record, String status, String sndExecutionId) {
        ifTableService.markAsProcessed(ifJewonTable, "id", record.getId(), status, sndExecutionId);
    }

    private void updateIfObsvDataStatus(IfRsvSecObsvdata record, String status, String sndExecutionId) {
        ifTableService.markAsProcessed(ifObsvdataTable, "id", record.getId(), status, sndExecutionId);
    }

    /**
     * obsv_code 필터 적용 (in-memory)
     * 콤마 구분이면 여러 값 매칭, 아니면 정확한 매칭
     */
    private <T> List<T> applyObsvCodeFilter(List<T> records, String filterValue,
                                             Function<T, String> obsvCodeExtractor) {
        if (filterValue.contains(",")) {
            List<String> inValues = List.of(filterValue.split(","));
            return records.stream().filter(record -> {
                String code = obsvCodeExtractor.apply(record);
                return code != null && inValues.stream().anyMatch(v -> v.trim().equals(code));
            }).collect(Collectors.toList());
        } else {
            String trimmed = filterValue.trim();
            return records.stream().filter(record -> {
                String code = obsvCodeExtractor.apply(record);
                return code != null && code.equals(trimmed);
            }).collect(Collectors.toList());
        }
    }

    /**
     * 테이블별 처리 요약 저장
     */
    private void saveSyncLogSummary(String executionId, String tableName, String tableType,
                                     Long successCount, Long failedCount, Long skipCount,
                                     String failedKeys, String errorSummary) {
        SyncLog logEntry = SyncLog.builder()
                .executionId(executionId)
                .stepId(getStepId())
                .tableName(tableName)
                .tableType(tableType)
                .successCount(successCount)
                .failedCount(failedCount)
                .skipCount(skipCount)
                .failedKeys(failedKeys)
                .errorSummary(errorSummary)
                .build();
        syncLogRepository.save(logEntry);
    }
}
