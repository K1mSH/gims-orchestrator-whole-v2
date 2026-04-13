package com.sync.agent.bojoint.loader.step;

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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 제주 관측데이터 Loader Step (I2)
 *
 * IF_RSV_TB_JEJU → PM_GD970201 (단일심도) / PM_GD970202 (다심도)
 * 레거시 ObsvrdataDB.java + target.xml 로직 이식
 *
 * MSN 분기:
 *   S11      → PM_GD970201 (단일심도)
 *   S21~S2N  → PM_GD970202 (다심도, PSTN_SN = N)
 *
 * 항목별 3건 INSERT (GL/WTEMP/SCOND)
 */
@Slf4j
public class JejuObsvdataLoadStep implements StepExecutor {

    // EAV 항목 ID (I1과 동일)
    private static final int IEM_GL = 5;         // 수위
    private static final int IEM_WTEMP = 163;    // 수온
    private static final int IEM_EC = 52;        // 전기전도도
    private static final int QLT_DEFAULT = 1;    // 품질 기본값

    private final String stepId;
    private final String stepName;
    private final String ifTable;
    private final List<String> configSourceTables;
    private final List<String> configTargetTables;
    private final DataSourceProvider dataSourceProvider;
    private final SyncLogRepository syncLogRepository;
    private final IfTableService ifTableService;
    private final com.sync.agent.bojoint.config.DynamicEntityManagerService dynamicEmService;

    // OBSRVT_ID → BRNCH_ID 캐시
    private final Map<String, Long> brnchIdCache = new HashMap<>();
    // (BRNCH_ID, ARTCL_ID) → RSLT_ID 캐시
    private final Map<String, Long> rsltIdCache = new HashMap<>();

    public JejuObsvdataLoadStep(String stepId, String stepName,
                                 String ifTable,
                                 List<String> configSourceTables, List<String> configTargetTables,
                                 DataSourceProvider dataSourceProvider,
                                 SyncLogRepository syncLogRepository,
                                 IfTableService ifTableService,
                                 com.sync.agent.bojoint.config.DynamicEntityManagerService dynamicEmService) {
        this.stepId = stepId;
        this.stepName = stepName;
        this.ifTable = ifTable;
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
            JdbcTemplate sourceJdbc = dataSourceProvider.getJdbcTemplate(sourceDsId);
            JdbcTemplate targetJdbc = dataSourceProvider.getJdbcTemplate(targetDsId);
            String executionId = context.getExecutionId();

            // 1. IF 테이블 조회
            String sourceDbType = dataSourceProvider.getDbType(sourceDsId);
            boolean isResync = ConditionBuilder.isResyncExecution(context.getExecutionOptions());
            ConditionBuilder.WhereClause where = ConditionBuilder.buildIfTableQuery(
                    context.getExecutionOptions(), ifTable, sourceDbType);
            String sql = "SELECT * FROM " + ifTable + where.toWhereSql();
            List<Map<String, Object>> pendingRows = sourceJdbc.queryForList(sql, where.getParamsArray());
            readCount = pendingRows.size();
            log.info("[{}] IF_RSV에서 {} 건의 {} 관측데이터 조회", stepId, readCount,
                    isResync ? "재동기화 (조건)" : "대기중");

            if (pendingRows.isEmpty()) {
                return StepResult.builder()
                        .stepId(stepId).status(Status.SUCCESS)
                        .readCount(0).writeCount(0).skipCount(0)
                        .durationMs(System.currentTimeMillis() - startTime)
                        .build();
            }

            List<Object> successIds = new ArrayList<>();
            List<Object> failedIds = new ArrayList<>();

            // 2. 건별 처리
            for (Map<String, Object> row : pendingRows) {
                Object ifId = row.get("ID");
                String obsvtrId = getString(row, "OBSRVT_ID");
                String msn = getString(row, "MSN");

                try {
                    String sourceRef = String.format("[\"I:%s:%s:%s\"]", sourceDsId, ifTable, ifId);

                    // BRNCH_ID 조회 (캐시)
                    Long brnchId = resolveBrnchId(targetJdbc, obsvtrId);
                    if (brnchId == null) {
                        log.warn("[{}] BRNCH_ID 미발견 (제원 미등록): obsrvt_id={}, SKIP", stepId, obsvtrId);
                        failedCount++;
                        failedIds.add(ifId);
                        failedKeys.add(obsvtrId + "(제원미등록)");
                        if (firstError == null) firstError = "BRNCH_ID 미발견: " + obsvtrId;
                        continue;
                    }

                    // 날짜 조합
                    String ymd = getString(row, "YMD");
                    String dataTime = getString(row, "DATA_TIME");
                    String dtStr = ymd + nvl(dataTime);

                    // MSN 분기
                    boolean isMultiDepth = msn != null && msn.startsWith("S2");
                    Integer pstnSn = null;
                    if (isMultiDepth && msn.length() >= 3) {
                        try {
                            pstnSn = Integer.parseInt(msn.substring(2));
                        } catch (NumberFormatException e) {
                            pstnSn = 1;
                        }
                    }

                    // 항목별 INSERT (빈 값은 건너뜀)
                    int inserted = 0;
                    inserted += insertIfNotEmpty(targetJdbc, brnchId, IEM_GL, getString(row, "GL"),
                            dtStr, isMultiDepth, pstnSn, sourceRef, executionId);
                    inserted += insertIfNotEmpty(targetJdbc, brnchId, IEM_WTEMP, getString(row, "WTEMP"),
                            dtStr, isMultiDepth, pstnSn, sourceRef, executionId);
                    inserted += insertIfNotEmpty(targetJdbc, brnchId, IEM_EC, getString(row, "SCOND"),
                            dtStr, isMultiDepth, pstnSn, sourceRef, executionId);

                    successIds.add(ifId);
                    writeCount += inserted;
                } catch (Exception e) {
                    log.error("[{}] 관측데이터 처리 실패: obsrvt_id={}, msn={}", stepId, obsvtrId, msn, e);
                    failedCount++;
                    failedIds.add(ifId);
                    failedKeys.add(obsvtrId);
                    if (firstError == null) firstError = e.getMessage();
                }
            }

            // 3. IF 상태 업데이트
            if (!successIds.isEmpty()) {
                ifTableService.batchMarkAsProcessed(ifTable, "ID", successIds, "SUCCESS", executionId);
            }
            if (!failedIds.isEmpty()) {
                ifTableService.batchMarkAsProcessed(ifTable, "ID", failedIds, "FAILED", executionId);
            }

            long durationMs = System.currentTimeMillis() - startTime;
            log.info("[{}] 완료: read={}, write={}, failed={}, duration={}ms",
                    stepId, readCount, writeCount, failedCount, durationMs);

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

    // ==================== INSERT 메서드 ====================

    /**
     * 값이 유효하면 INSERT, 건수 반환 (0 또는 1)
     */
    private int insertIfNotEmpty(JdbcTemplate jdbc, long brnchId, int artclId,
                                  String value, String dtStr,
                                  boolean isMultiDepth, Integer pstnSn,
                                  String sourceRef, String executionId) {
        if (value == null || value.trim().isEmpty()) return 0;

        Long rsltId = resolveRsltId(jdbc, brnchId, artclId);
        if (rsltId == null) {
            log.warn("[{}] RSLT_ID 미발견: brnch_id={}, artcl_id={}, SKIP", stepId, brnchId, artclId);
            return 0;
        }

        if (isMultiDepth) {
            jdbc.update(
                "INSERT INTO PM_GD970202 (RSLT_ID, PSTN_SN, OBSRVN_DATA_VL, OBSRVN_DT, QLT_ID, EXECUTION_ID, SOURCE_REFS) " +
                "VALUES (?, ?, ?, TO_DATE(?, 'YYYYMMDDHH24MISS'), ?, ?, ?)",
                rsltId, pstnSn, value, dtStr, QLT_DEFAULT, executionId, sourceRef);
        } else {
            jdbc.update(
                "INSERT INTO PM_GD970201 (RSLT_ID, OBSRVN_DATA_VL, OBSRVN_DT, QLT_ID, EXECUTION_ID, SOURCE_REFS) " +
                "VALUES (?, ?, TO_DATE(?, 'YYYYMMDDHH24MISS'), ?, ?, ?)",
                rsltId, value, dtStr, QLT_DEFAULT, executionId, sourceRef);
        }
        return 1;
    }

    // ==================== 조회 + 캐시 ====================

    private Long resolveBrnchId(JdbcTemplate jdbc, String obsvtrId) {
        return brnchIdCache.computeIfAbsent(obsvtrId, id -> {
            try {
                return jdbc.queryForObject(
                        "SELECT BRNCH_ID FROM TM_GD970001 WHERE OBSVTR_ID = ?", Long.class, id);
            } catch (Exception e) {
                return null;
            }
        });
    }

    private Long resolveRsltId(JdbcTemplate jdbc, long brnchId, int artclId) {
        String cacheKey = brnchId + ":" + artclId;
        return rsltIdCache.computeIfAbsent(cacheKey, k -> {
            try {
                return jdbc.queryForObject(
                        "SELECT RSLT_ID FROM TM_GD970101 WHERE BRNCH_ID = ? AND OBSRVN_ARTCL_ID = ?",
                        Long.class, brnchId, artclId);
            } catch (Exception e) {
                return null;
            }
        });
    }

    // ==================== 헬퍼 ====================

    private String getString(Map<String, Object> row, String key) {
        Object val = row.get(key);
        return val != null ? val.toString() : null;
    }

    private String nvl(String val) {
        return val != null ? val : "";
    }

    private void saveSyncLog(String executionId, int readCount, int writeCount,
                              int failedCount, List<String> failedKeys, String errorSummary) {
        try {
            String sourceJson = "[" + configSourceTables.stream().map(t -> "\"" + t + "\"").reduce((a, b) -> a + "," + b).orElse("") + "]";
            String targetJson = "[" + configTargetTables.stream().map(t -> "\"" + t + "\"").reduce((a, b) -> a + "," + b).orElse("") + "]";
            SyncLog logEntry = SyncLog.builder()
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
                    .errorSummary(errorSummary)
                    .build();
            syncLogRepository.save(logEntry);
        } catch (Exception e) {
            log.warn("[{}] SyncLog 저장 실패: {}", stepId, e.getMessage());
        }
    }
}
