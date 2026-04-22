package com.sync.agent.provide.loader.step;

import com.sync.agent.common.controller.DataSourceProvider;
import com.sync.agent.common.entity.SyncLog;
import com.sync.agent.common.repository.SyncLogRepository;
import com.sync.agent.common.step.StepContext;
import com.sync.agent.common.step.StepExecutor;
import com.sync.agent.common.step.StepResult;
import com.sync.agent.common.step.Status;
import com.sync.agent.common.util.SourceRefUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 범용 Oracle → PG 적재 Step (Provide Agent 전용)
 *
 * Oracle 소스 테이블에서 조회 → PG 제공 테이블에 INSERT ... ON CONFLICT UPSERT
 * 컬럼 목록은 DB 메타데이터에서 동적 추출 — YAML에는 테이블명과 merge-key만 지정
 *
 * 기존 Agent 패턴 준수:
 * - source_refs: "zone:dsId:tbId:pk" 형식 (SourceRefUtils 사용)
 * - execution_id: 타겟에만 기록 (소스에는 X)
 * - 소스 LINK_STATUS 갱신: link_status + updated_at만 (execution_id 미포함)
 */
@Slf4j
public class ProvideLoadStep implements StepExecutor {

    private final String stepId;
    private final String stepName;
    private final String sourceTable;
    private final String targetTable;
    private final String mergeKey;
    private final List<String> mergeKeys;
    private final List<String> configSourceTables;
    private final List<String> configTargetTables;
    private final DataSourceProvider dataSourceProvider;
    private final SyncLogRepository syncLogRepository;

    /** 비즈니스 컬럼에서 제외할 메타/추적 컬럼 */
    private static final Set<String> META_COLUMNS = Set.of(
            "link_status", "extracted_at", "updated_at",
            "execution_id", "source_refs"
    );

    /** 타겟 INSERT에서 제외할 자동채번 컬럼 */
    private static final Set<String> AUTO_PK_COLUMNS = Set.of("id", "sn");

    public ProvideLoadStep(String stepId, String stepName,
                           String sourceTable, String targetTable, String mergeKey,
                           List<String> configSourceTables, List<String> configTargetTables,
                           DataSourceProvider dataSourceProvider,
                           SyncLogRepository syncLogRepository) {
        this.stepId = stepId;
        this.stepName = stepName;
        this.sourceTable = sourceTable;
        this.targetTable = targetTable;
        this.mergeKey = mergeKey;
        this.mergeKeys = Arrays.stream(mergeKey.split(","))
                .map(String::trim).collect(Collectors.toList());
        this.configSourceTables = configSourceTables;
        this.configTargetTables = configTargetTables;
        this.dataSourceProvider = dataSourceProvider;
        this.syncLogRepository = syncLogRepository;
    }

    @Override
    public String getStepId() { return stepId; }

    @Override
    public String getStepName() { return stepName; }

    @Override
    public StepResult execute(StepContext context) {
        long startTime = System.currentTimeMillis();
        int readCount = 0, writeCount = 0, failedCount = 0;
        List<String> failedKeys = new ArrayList<>();
        String firstError = null;

        try {
            String sourceDsId = context.getSourceDatasourceId();
            String targetDsId = context.getTargetDatasourceId();
            String executionId = context.getExecutionId();

            JdbcTemplate sourceJdbc = dataSourceProvider.getJdbcTemplate(sourceDsId);
            JdbcTemplate targetJdbc = dataSourceProvider.getJdbcTemplate(targetDsId);

            // 1. 소스 테이블 컬럼 메타데이터 추출
            List<String> sourceColumns = getTableColumns(sourceJdbc, sourceTable);
            boolean hasLinkStatus = sourceColumns.stream()
                    .anyMatch(c -> c.equalsIgnoreCase("link_status"));

            // 비즈니스 컬럼 (메타 + 자동PK 제외, 단 merge-key는 유지)
            List<String> bizColumns = sourceColumns.stream()
                    .filter(c -> !META_COLUMNS.contains(c.toLowerCase()))
                    .filter(c -> !AUTO_PK_COLUMNS.contains(c.toLowerCase())
                            || mergeKeys.stream().anyMatch(k -> k.equalsIgnoreCase(c)))
                    .collect(Collectors.toList());

            log.info("[Provide] [{}] 소스 컬럼: {}개 (LINK_STATUS: {})", stepId, bizColumns.size(),
                    hasLinkStatus ? "있음" : "없음");

            // 2. 소스 데이터 조회
            String selectSql;
            if (hasLinkStatus) {
                selectSql = "SELECT * FROM " + sourceTable + " WHERE LINK_STATUS IN ('PENDING', 'FAILED')";
            } else {
                selectSql = "SELECT * FROM " + sourceTable;
            }

            List<Map<String, Object>> rows = sourceJdbc.queryForList(selectSql);
            readCount = rows.size();
            log.info("[Provide] [{}] 소스 조회: {}건 ({})", stepId, readCount, sourceTable);

            if (rows.isEmpty()) {
                saveSyncLog(executionId, 0, 0, 0, null, null);
                return StepResult.builder()
                        .stepId(stepId).status(Status.SUCCESS)
                        .readCount(0).writeCount(0).skipCount(0)
                        .durationMs(System.currentTimeMillis() - startTime)
                        .build();
            }

            // 3. UPSERT SQL 생성 (PG: INSERT ... ON CONFLICT)
            String upsertSql = buildPgUpsertSql(targetTable, bizColumns);
            log.debug("[Provide] [{}] UPSERT SQL: {}", stepId, upsertSql);

            List<Object> successIds = new ArrayList<>();
            List<Object> failedIdsList = new ArrayList<>();

            // 4. 건별 UPSERT
            for (Map<String, Object> row : rows) {
                Object rowId = getVal(row, "SN");
                if (rowId == null) rowId = getVal(row, "ID");
                String keyDisplay = mergeKeys.stream()
                        .map(k -> getStr(row, k))
                        .collect(Collectors.joining("|"));

                try {
                    // source_refs: 기존 패턴 (SourceRefUtils)
                    String sourceRef;
                    if (mergeKeys.size() > 1) {
                        Object[] pkValues = mergeKeys.stream()
                                .map(k -> getVal(row, k)).toArray();
                        sourceRef = SourceRefUtils.buildComposite(context, sourceTable, pkValues);
                    } else {
                        sourceRef = SourceRefUtils.build(context, sourceTable, getVal(row, mergeKeys.get(0)));
                    }
                    String sourceRefJson = SourceRefUtils.toJsonSingle(sourceRef);

                    Object[] params = buildUpsertParams(row, bizColumns, executionId, sourceRefJson);
                    targetJdbc.update(upsertSql, params);
                    writeCount++;

                    if (hasLinkStatus && rowId != null) {
                        successIds.add(rowId);
                    }
                } catch (Exception e) {
                    failedCount++;
                    failedKeys.add(keyDisplay != null ? keyDisplay : "unknown");
                    if (firstError == null) firstError = e.getMessage();
                    log.warn("[Provide] [{}] UPSERT 실패: {} — {}", stepId, keyDisplay, e.getMessage());

                    if (hasLinkStatus && rowId != null) {
                        failedIdsList.add(rowId);
                    }
                }
            }

            // 5. 소스 LINK_STATUS 갱신 (link_status + updated_at만, execution_id 미포함)
            if (hasLinkStatus) {
                batchUpdateLinkStatus(sourceJdbc, sourceTable, successIds, "SUCCESS");
                batchUpdateLinkStatus(sourceJdbc, sourceTable, failedIdsList, "FAILED");
            }

            long durationMs = System.currentTimeMillis() - startTime;
            log.info("[Provide] [{}] 완료: read={}, write={}, failed={}, {}ms",
                    stepId, readCount, writeCount, failedCount, durationMs);

            // 6. SyncLog
            saveSyncLog(executionId, readCount, writeCount, failedCount, failedKeys, firstError);

            return StepResult.builder()
                    .stepId(stepId)
                    .status(failedCount > 0 && writeCount == 0 ? Status.FAILED : Status.SUCCESS)
                    .readCount(readCount).writeCount(writeCount).skipCount(0)
                    .durationMs(durationMs)
                    .errorMessage(firstError)
                    .build();

        } catch (Exception e) {
            log.error("[Provide] [{}] Step 실행 실패", stepId, e);
            saveSyncLog(context.getExecutionId(), readCount, writeCount, failedCount,
                    failedKeys, e.getMessage());
            return StepResult.failed(stepId, e.getMessage(), System.currentTimeMillis() - startTime);
        }
    }

    // ==================== PG UPSERT SQL 동적 생성 ====================

    private String buildPgUpsertSql(String table, List<String> bizCols) {
        // INSERT 컬럼: 비즈니스 + 추적
        List<String> insertCols = new ArrayList<>(bizCols);
        insertCols.add("execution_id");
        insertCols.add("source_refs");
        insertCols.add("updated_at");

        String colList = insertCols.stream()
                .map(String::toLowerCase)
                .collect(Collectors.joining(", "));
        String placeholders = insertCols.stream()
                .map(c -> "updated_at".equalsIgnoreCase(c) ? "NOW()" : "?")
                .collect(Collectors.joining(", "));

        // ON CONFLICT 키
        String conflictKeys = mergeKeys.stream()
                .map(String::toLowerCase)
                .collect(Collectors.joining(", "));

        // UPDATE: merge-key 제외한 모든 컬럼
        List<String> updateCols = bizCols.stream()
                .filter(c -> mergeKeys.stream().noneMatch(k -> k.equalsIgnoreCase(c)))
                .collect(Collectors.toList());
        updateCols.add("execution_id");
        updateCols.add("source_refs");
        updateCols.add("updated_at");

        String updateSet = updateCols.stream()
                .map(c -> "updated_at".equalsIgnoreCase(c)
                        ? "updated_at = NOW()"
                        : c.toLowerCase() + " = EXCLUDED." + c.toLowerCase())
                .collect(Collectors.joining(", "));

        return "INSERT INTO " + table.toLowerCase()
                + " (" + colList + ")"
                + " VALUES (" + placeholders + ")"
                + " ON CONFLICT (" + conflictKeys + ") DO UPDATE SET "
                + updateSet;
    }

    private Object[] buildUpsertParams(Map<String, Object> row,
                                        List<String> bizCols,
                                        String executionId, String sourceRefJson) {
        List<Object> params = new ArrayList<>();
        for (String col : bizCols) {
            params.add(getVal(row, col));
        }
        params.add(executionId);
        params.add(sourceRefJson);
        // updated_at는 NOW()이므로 파라미터 불필요
        return params.toArray();
    }

    // ==================== 소스 LINK_STATUS 갱신 (기존 패턴) ====================

    /**
     * 소스 테이블 LINK_STATUS 배치 갱신
     * 기존 IfTableService.batchMarkAsProcessed 패턴:
     * - link_status + updated_at만 갱신
     * - execution_id는 건드리지 않음 (타겟에만 기록)
     */
    private void batchUpdateLinkStatus(JdbcTemplate jdbc, String table,
                                        List<Object> ids, String status) {
        if (ids == null || ids.isEmpty()) return;

        // PK 컬럼 판별 (SN > ID 순서)
        List<String> columns = getTableColumns(jdbc, table);
        String pkColumn = "SN";
        if (columns.stream().noneMatch(c -> c.equalsIgnoreCase("SN"))) {
            if (columns.stream().anyMatch(c -> c.equalsIgnoreCase("ID"))) {
                pkColumn = "ID";
            } else {
                // PK 컬럼 없으면 첫 번째 merge-key 사용
                pkColumn = mergeKeys.get(0);
            }
        }

        try {
            int chunkSize = 1000;
            int totalUpdated = 0;
            for (int i = 0; i < ids.size(); i += chunkSize) {
                List<Object> chunk = ids.subList(i, Math.min(i + chunkSize, ids.size()));
                String placeholders = chunk.stream().map(v -> "?").collect(Collectors.joining(", "));

                List<Object> params = new ArrayList<>();
                params.add(status);
                params.add(Timestamp.valueOf(LocalDateTime.now()));
                params.addAll(chunk);

                String sql = String.format(
                        "UPDATE %s SET LINK_STATUS = ?, UPDATED_AT = ? WHERE %s IN (%s)",
                        table, pkColumn, placeholders);

                totalUpdated += jdbc.update(sql, params.toArray());
            }
            log.debug("[Provide] [{}] LINK_STATUS → {}: {}건", stepId, status, totalUpdated);
        } catch (Exception e) {
            log.warn("[Provide] [{}] LINK_STATUS 갱신 실패: {}", stepId, e.getMessage());
        }
    }

    // ==================== DB 메타데이터 ====================

    private List<String> getTableColumns(JdbcTemplate jdbc, String tableName) {
        List<String> columns = new ArrayList<>();
        try {
            jdbc.execute((Connection con) -> {
                DatabaseMetaData meta = con.getMetaData();
                String schema = null;
                String tbl = tableName.toUpperCase();
                if (tableName.contains(".")) {
                    String[] parts = tableName.split("\\.");
                    schema = parts[0].toUpperCase();
                    tbl = parts[1].toUpperCase();
                }
                try (ResultSet rs = meta.getColumns(null, schema, tbl, null)) {
                    while (rs.next()) {
                        columns.add(rs.getString("COLUMN_NAME"));
                    }
                }
                if (columns.isEmpty()) {
                    try (ResultSet rs = meta.getColumns(null, null, tableName.toLowerCase(), null)) {
                        while (rs.next()) {
                            columns.add(rs.getString("COLUMN_NAME"));
                        }
                    }
                }
                return null;
            });
        } catch (Exception e) {
            log.warn("[Provide] [{}] 컬럼 메타 조회 실패: {} — {}", stepId, tableName, e.getMessage());
        }
        return columns;
    }

    // ==================== SyncLog ====================

    private void saveSyncLog(String executionId, int readCount, int writeCount,
                             int failedCount, List<String> failedKeys, String errorMessage) {
        try {
            String sourceJson = "[" + configSourceTables.stream()
                    .map(t -> "\"" + t + "\"").collect(Collectors.joining(",")) + "]";
            String targetJson = "[" + configTargetTables.stream()
                    .map(t -> "\"" + t + "\"").collect(Collectors.joining(",")) + "]";

            syncLogRepository.save(SyncLog.builder()
                    .executionId(executionId)
                    .stepId(stepId)
                    .mappingName(stepId)
                    .sourceTables(sourceJson)
                    .targetTables(targetJson)
                    .sourcePkColumn(mergeKey)
                    .readCount((long) readCount)
                    .writeCount((long) writeCount)
                    .failedCount((long) failedCount)
                    .skipCount(0L)
                    .failedKeys(failedKeys != null && !failedKeys.isEmpty()
                            ? String.join(",", failedKeys) : null)
                    .errorSummary(errorMessage)
                    .build());
        } catch (Exception e) {
            log.error("[Provide] [{}] SyncLog 저장 실패", stepId, e);
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
