package com.infolink.agent.bojo.loader.step;

import com.infolink.agent.common.entity.SyncLog;
import com.infolink.agent.common.repository.SyncLogRepository;
import com.infolink.agent.common.service.IfTableService;
import com.infolink.agent.common.step.StepContext;
import com.infolink.agent.common.util.SourceRefUtils;
import com.infolink.agent.bojo.entity.iftable.rsv.IfRsvSecJewon;
import com.infolink.agent.bojo.entity.iftable.rsv.IfRsvSecObsvdata;
import com.infolink.agent.bojo.entity.target.SecJewon;
import com.infolink.agent.bojo.entity.target.SecObsvdata;
import com.infolink.agent.bojo.loader.repository.TargetRepositoryService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Loader Step 공통 처리 로직
 *
 * 각 모드별 Step 구현체가 조회 로직만 자체 구현하고,
 * 변환/UPSERT/IF상태/SyncLog 처리는 이 헬퍼를 통해 수행
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoaderStepHelper {

    private final TargetRepositoryService targetRepository;
    private final SyncLogRepository syncLogRepository;
    private final IfTableService ifTableService;

    /**
     * 제원 데이터 변환 + batch UPSERT + IF 상태 업데이트
     *
     * @return 처리 결과 (성공/실패 건수, 에러 정보)
     */
    public ProcessResult processJewon(List<IfRsvSecJewon> records, String executionId,
                                       String stepId, String ifTableName) {
        return processJewon(records, executionId, stepId, ifTableName, null);
    }

    /**
     * 제원 데이터 변환 + batch UPSERT + IF 상태 업데이트
     * context가 있으면 source_refs를 IF 테이블 기준으로 새로 생성 (Loader 추적용)
     */
    public ProcessResult processJewon(List<IfRsvSecJewon> records, String executionId,
                                       String stepId, String ifTableName, StepContext context) {
        ProcessResult result = new ProcessResult();

        if (records.isEmpty()) return result;

        List<SecJewon> toSave = new ArrayList<>();
        List<Object> successIds = new ArrayList<>();
        List<Object> failedIds = new ArrayList<>();

        for (IfRsvSecJewon ifJewon : records) {
            try {
                SecJewon secJewon = TargetRepositoryService.convertToSecJewon(ifJewon, executionId);
                // Loader: source_refs를 IF 테이블 기준으로 새로 생성 (역추적용)
                if (context != null) {
                    String sourceRef = SourceRefUtils.build(context, ifTableName, ifJewon.getId());
                    secJewon.setSourceRefs(SourceRefUtils.toJsonSingle(sourceRef));
                }
                toSave.add(secJewon);
                successIds.add(ifJewon.getId());
                result.successCount++;
            } catch (Exception e) {
                log.error("[{}] Failed to convert jewon: {}", stepId, ifJewon.getObsvCode(), e);
                result.failedCount++;
                failedIds.add(ifJewon.getId());
                result.failedKeys.add(ifJewon.getSourceRefs() != null ? ifJewon.getSourceRefs() : ifJewon.getObsvCode());
                if (result.firstError == null) result.firstError = e.getMessage();
            }
        }

        if (!toSave.isEmpty()) {
            int saved = targetRepository.batchUpsertJewon(toSave);
            result.writeCount = saved;
            log.info("[{}] Batch UPSERT {} jewon records", stepId, saved);
        }

        if (!successIds.isEmpty()) {
            ifTableService.batchMarkAsProcessed(ifTableName, "id", successIds, "SUCCESS", executionId);
        }
        if (!failedIds.isEmpty()) {
            ifTableService.batchMarkAsProcessed(ifTableName, "id", failedIds, "FAILED", executionId);
        }

        return result;
    }

    /**
     * 관측데이터 변환 + batch UPSERT + IF 상태 업데이트
     *
     * @return 처리 결과 (성공/실패 건수, 에러 정보)
     */
    public ProcessResult processObsvdata(List<IfRsvSecObsvdata> records, String executionId,
                                          String stepId, String ifTableName) {
        return processObsvdata(records, executionId, stepId, ifTableName, null);
    }

    /**
     * 관측데이터 변환 + batch UPSERT + IF 상태 업데이트
     * context가 있으면 source_refs를 IF 테이블 기준으로 새로 생성 (Loader 추적용)
     */
    public ProcessResult processObsvdata(List<IfRsvSecObsvdata> records, String executionId,
                                          String stepId, String ifTableName, StepContext context) {
        ProcessResult result = new ProcessResult();

        if (records.isEmpty()) return result;

        List<SecObsvdata> toSave = new ArrayList<>();
        List<Object> successIds = new ArrayList<>();
        List<Object> failedIds = new ArrayList<>();

        for (IfRsvSecObsvdata ifData : records) {
            try {
                SecObsvdata secData = TargetRepositoryService.convertToSecObsvdata(ifData, executionId);
                // Loader: source_refs를 IF 테이블 기준으로 새로 생성 (역추적용)
                if (context != null) {
                    String sourceRef = SourceRefUtils.build(context, ifTableName, ifData.getId());
                    secData.setSourceRefs(SourceRefUtils.toJsonSingle(sourceRef));
                }
                toSave.add(secData);
                successIds.add(ifData.getId());
                result.successCount++;
            } catch (Exception e) {
                log.error("[{}] Failed to convert obsvdata: {}", stepId, ifData.getObsvCode(), e);
                result.failedCount++;
                failedIds.add(ifData.getId());
                result.failedKeys.add(ifData.getSourceRefs() != null ? ifData.getSourceRefs() : ifData.getObsvCode());
                if (result.firstError == null) result.firstError = e.getMessage();
            }
        }

        if (!toSave.isEmpty()) {
            int saved = targetRepository.batchUpsertObsvdata(toSave);
            result.writeCount = saved;
            log.info("[{}] Batch UPSERT {} obsvdata records", stepId, saved);
        }

        if (!successIds.isEmpty()) {
            ifTableService.batchMarkAsProcessed(ifTableName, "id", successIds, "SUCCESS", executionId);
        }
        if (!failedIds.isEmpty()) {
            ifTableService.batchMarkAsProcessed(ifTableName, "id", failedIds, "FAILED", executionId);
        }

        return result;
    }

    /**
     * SyncLog 매핑 단위 저장
     */
    public void saveSyncLog(String executionId, String stepId, String mappingName,
                             List<String> sourceTables, List<String> targetTables,
                             long readCount, long writeCount, long failedCount, long skipCount,
                             List<String> failedKeys, String errorSummary) {
        String sourceJson = "[" + sourceTables.stream().map(t -> "\"" + t + "\"").reduce((a, b) -> a + "," + b).orElse("") + "]";
        String targetJson = "[" + targetTables.stream().map(t -> "\"" + t + "\"").reduce((a, b) -> a + "," + b).orElse("") + "]";

        SyncLog logEntry = SyncLog.builder()
                .executionId(executionId)
                .stepId(stepId)
                .mappingName(mappingName)
                .sourceTables(sourceJson)
                .targetTables(targetJson)
                .readCount(readCount)
                .writeCount(writeCount)
                .failedCount(failedCount)
                .skipCount(skipCount)
                .failedKeys(failedKeys != null && !failedKeys.isEmpty() ? String.join(",", failedKeys) : null)
                .errorSummary(errorSummary)
                .build();
        syncLogRepository.save(logEntry);
    }

    /**
     * obsv_code 필터 적용 (in-memory)
     */
    public <T> List<T> applyObsvCodeFilter(List<T> records, String filterValue,
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
     * 처리 결과 (Step에서 집계용으로 사용)
     */
    @Getter
    public static class ProcessResult {
        int successCount = 0;
        int failedCount = 0;
        int writeCount = 0;
        List<String> failedKeys = new ArrayList<>();
        String firstError = null;
    }
}
