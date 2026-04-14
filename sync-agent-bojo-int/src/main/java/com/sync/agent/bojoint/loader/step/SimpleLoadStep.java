package com.sync.agent.bojoint.loader.step;

import com.sync.agent.bojoint.config.DynamicEntityManagerService;
import com.sync.agent.common.controller.DataSourceProvider;
import com.sync.agent.common.entity.SyncLog;
import com.sync.agent.common.repository.SyncLogRepository;
import com.sync.agent.common.service.IfTableService;
import com.sync.agent.common.step.ConditionBuilder;
import com.sync.agent.common.step.StepContext;
import com.sync.agent.common.step.StepExecutor;
import com.sync.agent.common.step.StepResult;
import com.sync.agent.common.step.Status;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 범용 1:1 매핑 Loader Step
 *
 * IF_RSV → Target MERGE (변환 없이 컬럼 직접 복사)
 * merge-key 기준으로 MATCHED → UPDATE, NOT MATCHED → INSERT
 * SN은 Target에서 IDENTITY 자동 채번이므로 INSERT 시 제외
 * Target에 SOURCE_REFS, EXECUTION_ID 추가 (추적용)
 */
@Slf4j
public class SimpleLoadStep implements StepExecutor {

    private final String stepId;
    private final String stepName;
    private final String ifTable;
    private final String targetTable;
    private final String mergeKey;
    private final List<String> configSourceTables;
    private final List<String> configTargetTables;
    private final DataSourceProvider dataSourceProvider;
    private final SyncLogRepository syncLogRepository;
    private final IfTableService ifTableService;
    private final DynamicEntityManagerService dynamicEmService;

    /** IF 전용 메타 컬럼 — Target MERGE에서 제외 (SOURCE_REFS, EXECUTION_ID는 별도 처리) */
    private static final Set<String> IF_ONLY_META = Set.of(
            "ID", "LINK_STATUS", "EXTRACTED_AT", "UPDATED_AT", "SOURCE_REFS", "EXECUTION_ID"
    );

    public SimpleLoadStep(String stepId, String stepName,
                          String ifTable, String targetTable, String mergeKey,
                          List<String> configSourceTables, List<String> configTargetTables,
                          DataSourceProvider dataSourceProvider,
                          SyncLogRepository syncLogRepository,
                          IfTableService ifTableService,
                          DynamicEntityManagerService dynamicEmService) {
        this.stepId = stepId;
        this.stepName = stepName;
        this.ifTable = ifTable;
        this.targetTable = targetTable;
        this.mergeKey = mergeKey;
        this.configSourceTables = configSourceTables;
        this.configTargetTables = configTargetTables;
        this.dataSourceProvider = dataSourceProvider;
        this.syncLogRepository = syncLogRepository;
        this.ifTableService = ifTableService;
        this.dynamicEmService = dynamicEmService;
    }

    @Override
    public String getStepId() { return stepId; }

    @Override
    public String getStepName() { return stepName; }

    @Override
    public StepResult execute(StepContext context) {
        long startTime = System.currentTimeMillis();
        int readCount = 0;
        int writeCount = 0;
        int failedCount = 0;
        List<String> failedKeys = new ArrayList<>();
        String firstError = null;

        try {
            String sourceDsId = context.getSourceDatasourceId();
            String targetDsId = context.getTargetDatasourceId();
            JdbcTemplate targetJdbc = dataSourceProvider.getJdbcTemplate(targetDsId);
            String executionId = context.getExecutionId();

            // 1. IF_RSV에서 PENDING 건 조회 (native query → Map)
            String sourceDbType = dataSourceProvider.getDbType(sourceDsId);
            boolean isResync = ConditionBuilder.isResyncExecution(context.getExecutionOptions());
            ConditionBuilder.WhereClause where = ConditionBuilder.buildIfTableQuery(
                    context.getExecutionOptions(), ifTable, sourceDbType);
            String sql = "SELECT * FROM " + ifTable + where.toWhereSql();

            List<Map<String, Object>> pendingRows;
            EntityManager sourceEm = dynamicEmService.getSourceEntityManager();
            try {
                Query query = sourceEm.createNativeQuery(sql);
                query.unwrap(org.hibernate.query.NativeQuery.class)
                        .setResultTransformer(org.hibernate.transform.AliasToEntityMapResultTransformer.INSTANCE);
                Object[] params = where.getParamsArray();
                for (int i = 0; i < params.length; i++) {
                    query.setParameter(i + 1, params[i]);
                }
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> result = query.getResultList();
                pendingRows = result;
            } finally {
                sourceEm.close();
            }

            readCount = pendingRows.size();
            log.info("[{}] IF_RSV에서 {} 건 조회 ({})", stepId, readCount,
                    isResync ? "재동기화" : "대기중");

            if (pendingRows.isEmpty()) {
                return StepResult.builder()
                        .stepId(stepId).status(Status.SUCCESS)
                        .readCount(0).writeCount(0).skipCount(0)
                        .durationMs(System.currentTimeMillis() - startTime)
                        .build();
            }

            // 2. 비즈니스 컬럼 추출 (IF 전용 메타 제외)
            List<String> bizColumns = pendingRows.get(0).keySet().stream()
                    .filter(c -> !IF_ONLY_META.contains(c.toUpperCase()))
                    .collect(Collectors.toList());

            // INSERT용 컬럼 (SN 제외 — IDENTITY 자동 채번)
            List<String> insertColumns = bizColumns.stream()
                    .filter(c -> !"SN".equalsIgnoreCase(c))
                    .collect(Collectors.toList());

            log.info("[{}] 비즈니스 컬럼: {}, INSERT 컬럼: {}", stepId, bizColumns, insertColumns);

            // MERGE SQL 생성 (SOURCE_REFS, EXECUTION_ID 포함)
            String mergeSql = buildMergeSql(targetTable, mergeKey, bizColumns, insertColumns);
            log.debug("[{}] MERGE SQL: {}", stepId, mergeSql);

            List<Object> successIds = new ArrayList<>();
            List<Object> failedIds = new ArrayList<>();

            // 3. 건별 MERGE
            for (Map<String, Object> row : pendingRows) {
                Object ifId = getVal(row, "ID");
                String keyValue = getStr(row, mergeKey);
                try {
                    String sourceRef = String.format("[\"I:%s:%s:%s\"]", sourceDsId, ifTable, ifId);
                    Object[] mergeParams = buildMergeParams(
                            row, bizColumns, insertColumns, mergeKey, sourceRef, executionId);
                    targetJdbc.update(mergeSql, mergeParams);
                    successIds.add(ifId);
                    writeCount++;
                } catch (Exception e) {
                    log.error("[{}] MERGE 실패: {}={}", stepId, mergeKey, keyValue, e);
                    failedCount++;
                    failedIds.add(ifId);
                    failedKeys.add(keyValue != null ? keyValue : "unknown");
                    if (firstError == null) firstError = e.getMessage();
                }
            }

            // 4. IF 상태 업데이트
            if (!successIds.isEmpty()) {
                ifTableService.batchMarkAsProcessed(ifTable, "ID", successIds, "SUCCESS", executionId);
            }
            if (!failedIds.isEmpty()) {
                ifTableService.batchMarkAsProcessed(ifTable, "ID", failedIds, "FAILED", executionId);
            }

            long durationMs = System.currentTimeMillis() - startTime;
            log.info("[{}] 완료: read={}, write={}, failed={}, duration={}ms",
                    stepId, readCount, writeCount, failedCount, durationMs);

            // 5. SyncLog
            saveSyncLog(executionId, readCount, writeCount, failedCount, failedKeys, firstError);

            return StepResult.builder()
                    .stepId(stepId)
                    .status(failedCount > 0 && writeCount == 0 ? Status.FAILED : Status.SUCCESS)
                    .readCount(readCount)
                    .writeCount(writeCount)
                    .skipCount(0)
                    .durationMs(durationMs)
                    .errorMessage(firstError)
                    .build();

        } catch (Exception e) {
            log.error("[{}] Step 실행 실패", stepId, e);
            saveSyncLog(context.getExecutionId(), readCount, writeCount, failedCount, failedKeys, e.getMessage());
            return StepResult.failed(stepId, e.getMessage(), System.currentTimeMillis() - startTime);
        }
    }

    // ==================== MERGE SQL ====================

    /**
     * Oracle MERGE SQL 생성
     *
     * 파라미터 순서:
     *   ON절: [merge-key]
     *   UPDATE: [bizCols(SN,merge-key 제외)] + [SOURCE_REFS, EXECUTION_ID]
     *   INSERT: [insertCols] + [SOURCE_REFS, EXECUTION_ID]
     */
    private String buildMergeSql(String table, String mk, List<String> bizCols, List<String> insertCols) {
        StringBuilder sb = new StringBuilder();
        sb.append("MERGE INTO ").append(table).append(" t ");
        sb.append("USING (SELECT ? AS ").append(mk).append(" FROM DUAL) s ");
        sb.append("ON (t.").append(mk).append(" = s.").append(mk).append(") ");

        // UPDATE: 비즈니스(SN, merge-key 제외) + SOURCE_REFS, EXECUTION_ID
        List<String> updateCols = bizCols.stream()
                .filter(c -> !c.equalsIgnoreCase(mk) && !c.equalsIgnoreCase("SN"))
                .collect(Collectors.toList());
        sb.append("WHEN MATCHED THEN UPDATE SET ");
        sb.append(updateCols.stream().map(c -> "t." + c + " = ?").collect(Collectors.joining(", ")));
        sb.append(", t.SOURCE_REFS = ?, t.EXECUTION_ID = ?");

        // INSERT: 비즈니스(SN 제외) + SOURCE_REFS, EXECUTION_ID
        sb.append(" WHEN NOT MATCHED THEN INSERT (");
        sb.append(String.join(", ", insertCols));
        sb.append(", SOURCE_REFS, EXECUTION_ID) VALUES (");
        sb.append(insertCols.stream().map(c -> "?").collect(Collectors.joining(", ")));
        sb.append(", ?, ?)");

        return sb.toString();
    }

    /**
     * MERGE 파라미터 바인딩
     *
     * 순서: [merge-key] + [UPDATE bizCols] + [sourceRef, executionId]
     *                    + [INSERT insertCols] + [sourceRef, executionId]
     */
    private Object[] buildMergeParams(Map<String, Object> row,
                                       List<String> bizCols, List<String> insertCols,
                                       String mk, String sourceRef, String executionId) {
        List<Object> params = new ArrayList<>();

        // ON: merge-key
        params.add(getVal(row, mk));

        // UPDATE: 비즈니스(SN, merge-key 제외)
        for (String col : bizCols) {
            if (!col.equalsIgnoreCase(mk) && !col.equalsIgnoreCase("SN")) {
                params.add(getVal(row, col));
            }
        }
        params.add(sourceRef);
        params.add(executionId);

        // INSERT: 비즈니스(SN 제외)
        for (String col : insertCols) {
            params.add(getVal(row, col));
        }
        params.add(sourceRef);
        params.add(executionId);

        return params.toArray();
    }

    // ==================== SyncLog ====================

    private void saveSyncLog(String executionId, int readCount, int writeCount,
                             int failedCount, List<String> failedKeys, String errorMessage) {
        try {
            String sourceJson = "[" + configSourceTables.stream()
                    .map(t -> "\"" + t + "\"").reduce((a, b) -> a + "," + b).orElse("") + "]";
            String targetJson = "[" + configTargetTables.stream()
                    .map(t -> "\"" + t + "\"").reduce((a, b) -> a + "," + b).orElse("") + "]";
            SyncLog syncLog = SyncLog.builder()
                    .executionId(executionId)
                    .stepId(stepId)
                    .mappingName(stepId)
                    .sourceTables(sourceJson)
                    .targetTables(targetJson)
                    .readCount((long) readCount)
                    .writeCount((long) writeCount)
                    .failedCount((long) failedCount)
                    .skipCount(0L)
                    .failedKeys(failedKeys.isEmpty() ? null : String.join(",", failedKeys))
                    .errorSummary(errorMessage)
                    .build();
            syncLogRepository.save(syncLog);
        } catch (Exception e) {
            log.error("[{}] SyncLog 저장 실패", stepId, e);
        }
    }

    // ==================== 유틸리티 ====================

    private Object getVal(Map<String, Object> row, String key) {
        Object val = row.get(key);
        if (val != null) return val;
        val = row.get(key.toUpperCase());
        if (val != null) return val;
        return row.get(key.toLowerCase());
    }

    private String getStr(Map<String, Object> row, String key) {
        Object val = getVal(row, key);
        return val != null ? val.toString() : null;
    }
}
