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
 * 대전 관측망 데이터 적재 Step (JDBC batch UPSERT 버전)
 * IF_RSV 테이블 → Target 테이블 (sec_jewon, sec_obsvdata)
 *
 * 성능 최적화:
 * - JPA merge() → JDBC batch UPSERT (ON CONFLICT)
 * - 레코드별 RESYNC SELECT 제거 → ON CONFLICT가 자동 처리
 * - 레코드별 IF 상태 UPDATE → 배치 UPDATE (batchMarkAsProcessed)
 * - 레코드별 Link UPDATE → 배치 UPSERT (batchUpsertLinks)
 * - ~31,000 DB round-trip → ~15 DB round-trip
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

            String loaderExecutionId = context.getExecutionId();

            // ===== 1. 제원 데이터 처리 (JDBC batch UPSERT) =====
            List<IfRsvSecJewon> pendingJewon = isTimeRangeExecution
                    ? targetRepository.findAllIfRsvJewonForResync()
                    : targetRepository.findIfRsvJewonPending(context.getExecutionId());

            if (filterObsvCode != null) {
                pendingJewon = applyObsvCodeFilter(pendingJewon, filterObsvCode, IfRsvSecJewon::getObsvCode);
                log.info("[{}] After obsv_code filter: {} jewon records", getStepId(), pendingJewon.size());
            }
            readCount += pendingJewon.size();
            log.info("[{}] Found {} {} jewon records", getStepId(), pendingJewon.size(),
                    isTimeRangeExecution ? "total (resync)" : "pending");

            if (!pendingJewon.isEmpty()) {
                // Phase 1: 변환 (in-memory) + 성공/실패 ID 수집
                List<SecJewon> jewonsToSave = new ArrayList<>();
                List<Object> jewonSuccessIds = new ArrayList<>();
                List<Object> jewonFailedIds = new ArrayList<>();

                for (IfRsvSecJewon ifJewon : pendingJewon) {
                    try {
                        SecJewon secJewon = TargetRepositoryService.convertToSecJewon(ifJewon, loaderExecutionId);
                        jewonsToSave.add(secJewon);
                        jewonSuccessIds.add(ifJewon.getId());
                        jewonSuccess++;
                    } catch (Exception e) {
                        log.error("Failed to convert jewon: {}", ifJewon.getObsvCode(), e);
                        skipCount++;
                        jewonFailed++;
                        jewonFailedIds.add(ifJewon.getId());
                        jewonFailedKeys.add(ifJewon.getSourceRefs() != null ? ifJewon.getSourceRefs() : ifJewon.getObsvCode());
                        if (jewonFirstError == null) jewonFirstError = e.getMessage();
                    }
                }

                // Phase 2: JDBC batch UPSERT (DELETE 불필요 - ON CONFLICT가 처리)
                if (!jewonsToSave.isEmpty()) {
                    int saved = targetRepository.batchUpsertJewon(jewonsToSave);
                    writeCount += saved;
                    log.info("[{}] Batch UPSERT {} jewon records", getStepId(), saved);
                }

                // Phase 3: IF 상태 배치 UPDATE
                if (!jewonSuccessIds.isEmpty()) {
                    ifTableService.batchMarkAsProcessed(ifJewonTable, "id", jewonSuccessIds, "SUCCESS", loaderExecutionId);
                }
                if (!jewonFailedIds.isEmpty()) {
                    ifTableService.batchMarkAsProcessed(ifJewonTable, "id", jewonFailedIds, "FAILED", loaderExecutionId);
                }
            }

            // ===== 2. 관측데이터 처리 (JDBC batch UPSERT - RESYNC SELECT 제거) =====
            List<IfRsvSecObsvdata> pendingObsvData = isTimeRangeExecution
                    ? targetRepository.findIfRsvObsvdataByTimeRange(paramStartTime, paramEndTime)
                    : targetRepository.findIfRsvObsvdataPending(context.getExecutionId());

            if (filterObsvCode != null) {
                pendingObsvData = applyObsvCodeFilter(pendingObsvData, filterObsvCode, IfRsvSecObsvdata::getObsvCode);
                log.info("[{}] After obsv_code filter: {} obsvdata records", getStepId(), pendingObsvData.size());
            }
            readCount += pendingObsvData.size();
            log.info("[{}] Found {} {} obsvdata records", getStepId(), pendingObsvData.size(),
                    isTimeRangeExecution ? "in time range (resync)" : "pending");

            if (!pendingObsvData.isEmpty()) {
                // Phase 1: 변환 (in-memory) + 성공/실패 ID 수집
                // RESYNC SELECT 완전 제거 - ON CONFLICT (obsv_code, obsv_date, obsv_time) 가 자동 처리
                List<SecObsvdata> obsvToSave = new ArrayList<>();
                List<Object> obsvSuccessIds = new ArrayList<>();
                List<Object> obsvFailedIds = new ArrayList<>();

                for (IfRsvSecObsvdata ifData : pendingObsvData) {
                    try {
                        SecObsvdata secData = TargetRepositoryService.convertToSecObsvdata(ifData, loaderExecutionId);
                        obsvToSave.add(secData);
                        obsvSuccessIds.add(ifData.getId());
                        obsvSuccess++;
                    } catch (Exception e) {
                        log.error("Failed to convert obsvdata: {}", ifData.getObsvCode(), e);
                        skipCount++;
                        obsvFailed++;
                        obsvFailedIds.add(ifData.getId());
                        obsvFailedKeys.add(ifData.getSourceRefs() != null ? ifData.getSourceRefs() : ifData.getObsvCode());
                        if (obsvFirstError == null) obsvFirstError = e.getMessage();
                    }
                }

                // Phase 2: JDBC batch UPSERT (ON CONFLICT가 INSERT/UPDATE 자동 처리)
                if (!obsvToSave.isEmpty()) {
                    int saved = targetRepository.batchUpsertObsvdata(obsvToSave);
                    writeCount += saved;
                    log.info("[{}] Batch UPSERT {} obsvdata records", getStepId(), saved);
                }

                // Phase 3: IF 상태 배치 UPDATE
                if (!obsvSuccessIds.isEmpty()) {
                    ifTableService.batchMarkAsProcessed(ifObsvdataTable, "id", obsvSuccessIds, "SUCCESS", loaderExecutionId);
                }
                if (!obsvFailedIds.isEmpty()) {
                    ifTableService.batchMarkAsProcessed(ifObsvdataTable, "id", obsvFailedIds, "FAILED", loaderExecutionId);
                }

                // link_ngwis는 RCV에서 관리 (Loader는 link_status만 처리)
            }

            log.info("[{}] Loaded {} records ({} skipped) in {}ms",
                    getStepId(), writeCount, skipCount, System.currentTimeMillis() - startTime);

            // 4. SyncLog 요약 저장 (매핑 단위)
            saveSyncLogMapping(context.getExecutionId(), "jewon",
                    List.of(ifJewonTable), List.of(targetJewonTable),
                    (long) pendingJewon.size(), (long) jewonSuccess, (long) jewonFailed, 0L,
                    jewonFailedKeys.isEmpty() ? null : String.join(",", jewonFailedKeys),
                    jewonFirstError);

            saveSyncLogMapping(context.getExecutionId(), "obsvdata",
                    List.of(ifObsvdataTable), List.of(targetObsvdataTable),
                    (long) pendingObsvData.size(), (long) obsvSuccess, (long) obsvFailed, 0L,
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

            String errorMessage = e.getMessage();
            if (errorMessage != null && errorMessage.length() > 500) {
                errorMessage = errorMessage.substring(0, 500) + "...";
            }

            saveSyncLogMapping(context.getExecutionId(), "jewon",
                    List.of(ifJewonTable), List.of(targetJewonTable),
                    (long) readCount, (long) jewonSuccess, (long) jewonFailed, 0L,
                    jewonFailedKeys.isEmpty() ? null : String.join(",", jewonFailedKeys),
                    errorMessage);
            saveSyncLogMapping(context.getExecutionId(), "obsvdata",
                    List.of(ifObsvdataTable), List.of(targetObsvdataTable),
                    0L, (long) obsvSuccess, (long) obsvFailed, 0L,
                    obsvFailedKeys.isEmpty() ? null : String.join(",", obsvFailedKeys),
                    errorMessage);

            return StepResult.failed(getStepId(), e.getMessage(), System.currentTimeMillis() - startTime);
        }
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
     * 매핑 단위 처리 요약 저장
     */
    private void saveSyncLogMapping(String executionId, String mappingName,
                                     List<String> sourceTables, List<String> targetTables,
                                     Long readCount, Long writeCount, Long failedCount, Long skipCount,
                                     String failedKeys, String errorSummary) {
        String sourceJson = "[" + sourceTables.stream().map(t -> "\"" + t + "\"").reduce((a, b) -> a + "," + b).orElse("") + "]";
        String targetJson = "[" + targetTables.stream().map(t -> "\"" + t + "\"").reduce((a, b) -> a + "," + b).orElse("") + "]";

        SyncLog logEntry = SyncLog.builder()
                .executionId(executionId)
                .stepId(getStepId())
                .mappingName(mappingName)
                .sourceTables(sourceJson)
                .targetTables(targetJson)
                .readCount(readCount != null ? readCount : 0L)
                .writeCount(writeCount != null ? writeCount : 0L)
                .failedCount(failedCount != null ? failedCount : 0L)
                .skipCount(skipCount != null ? skipCount : 0L)
                .failedKeys(failedKeys)
                .errorSummary(errorSummary)
                .build();
        syncLogRepository.save(logEntry);
    }
}
