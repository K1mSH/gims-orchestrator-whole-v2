package com.sync.agent.common.step;

import com.sync.agent.common.config.JdbcTableNameResolver;
import com.sync.agent.common.controller.DataSourceProvider;
import com.sync.agent.common.entity.SyncLog;
import com.sync.agent.common.repository.SyncLogRepository;
import com.sync.agent.common.util.SourceRefUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Source → IF 테이블 동기화 Step (공통)
 *
 * Source 테이블에서 데이터를 조회(Extract)하고 IF 테이블에 적재(Load)하는 통합 Step.
 * ETL에서 E+L을 하나의 Step으로 처리하며, IF 테이블에 UPSERT 방식으로 적재한다.
 *
 * 주요 기능:
 * - Source 데이터 조회 (SIMPLE_COPY / CUSTOM_STAGING 두 가지 모드)
 * - IF 테이블에 배치 UPSERT (ON CONFLICT DO UPDATE)
 * - source_refs 추적 정보 자동 생성
 * - link_status 관리 (PENDING/RESYNC/SUCCESS/FAILED)
 * - SyncLog 요약 저장 (테이블별 성공/실패 건수)
 *
 * 두 가지 모드:
 * 1. SIMPLE_COPY: Source 1개 → IF (1:1), 전체 추적 가능
 * 2. CUSTOM_STAGING: 커스텀 DataFetcher로 데이터 조회, IF→Target만 추적
 */
@Slf4j
public class SourceToIfStep implements StepExecutor {

    // IF 테이블 메타 컬럼 목록 (소스에서 제외해야 함)
    private static final List<String> IF_META_COLUMNS = List.of(
            "source_refs", "link_status", "extracted_at", "updated_at", "execution_id"
    );

    private static final int DEFAULT_BATCH_SIZE = 1000;

    private final ExtractStepConfig config;
    private final DataSourceProvider dataSourceProvider;
    private final SyncLogRepository syncLogRepository;
    private final long stepDelayMs;
    private final int batchSize;

    public SourceToIfStep(
            ExtractStepConfig config,
            DataSourceProvider dataSourceProvider,
            SyncLogRepository syncLogRepository) {
        this(config, dataSourceProvider, syncLogRepository, 0, DEFAULT_BATCH_SIZE);
    }

    public SourceToIfStep(
            ExtractStepConfig config,
            DataSourceProvider dataSourceProvider,
            SyncLogRepository syncLogRepository,
            long stepDelayMs) {
        this(config, dataSourceProvider, syncLogRepository, stepDelayMs, DEFAULT_BATCH_SIZE);
    }

    public SourceToIfStep(
            ExtractStepConfig config,
            DataSourceProvider dataSourceProvider,
            SyncLogRepository syncLogRepository,
            int batchSize) {
        this(config, dataSourceProvider, syncLogRepository, 0, batchSize);
    }

    public SourceToIfStep(
            ExtractStepConfig config,
            DataSourceProvider dataSourceProvider,
            SyncLogRepository syncLogRepository,
            long stepDelayMs,
            int batchSize) {
        this.config = config;
        this.dataSourceProvider = dataSourceProvider;
        this.syncLogRepository = syncLogRepository;
        this.stepDelayMs = stepDelayMs;
        this.batchSize = batchSize > 0 ? batchSize : DEFAULT_BATCH_SIZE;
    }

    // ==================== SQL 방언 헬퍼 ====================

    private static boolean isMysql(String dbType) {
        return "MYSQL".equalsIgnoreCase(dbType) || "MARIADB".equalsIgnoreCase(dbType);
    }

    private static String qi(String name, String dbType) {
        if (isMysql(dbType)) return "`" + name + "`";
        return "\"" + name + "\"";
    }

    @Override
    public String getStepId() {
        return config.getStepId();
    }

    @Override
    public String getStepName() {
        return config.getStepName();
    }

    @Override
    public StepResult execute(StepContext context) {
        long startTime = System.currentTimeMillis();
        int readCount = 0;
        int writeCount = 0;
        int skipCount = 0;
        List<String> failedKeys = new ArrayList<>();
        String firstError = null;

        try {
            // 0. 컬럼 목록 결정 (설정값 우선, 없으면 자동 감지)
            List<String> allColumns = resolveColumns(context);
            if (allColumns.isEmpty()) {
                throw new IllegalStateException("No columns found for table: " + config.getSourceTable());
            }

            // IF 메타 컬럼 제외 (source_refs, link_status 등이 이미 소스에 있으면 중복 방지)
            List<String> columns = allColumns.stream()
                    .filter(c -> IF_META_COLUMNS.stream().noneMatch(meta -> meta.equalsIgnoreCase(c)))
                    .toList();
            log.info("[{}] Columns after filtering IF meta columns: {} -> {} (removed: {})",
                    getStepId(), allColumns.size(), columns.size(), allColumns.size() - columns.size());

            // 1. 데이터 조회 (타입에 따라 다른 방식)
            List<Map<String, Object>> records;

            if (config.isCustomStaging() && config.getCustomDataFetcher() != null) {
                log.info("[{}] Using custom DataFetcher for CUSTOM_STAGING", getStepId());
                records = config.getCustomDataFetcher().fetch(context);
            } else {
                log.info("[{}] Using default fetch for SIMPLE_COPY", getStepId());
                records = fetchSimpleCopy(context, columns);
            }

            readCount = records.size();
            log.info("[{}] Fetched {} records", getStepId(), readCount);

            // PK 기준 중복 제거 (Source VIEW의 JOIN으로 인한 중복 방지)
            List<String> pkCols = config.getPrimaryKeyColumnList();
            if (!pkCols.isEmpty() && records.size() > 1) {
                java.util.LinkedHashMap<String, Map<String, Object>> uniqueMap = new java.util.LinkedHashMap<>();
                for (Map<String, Object> record : records) {
                    StringBuilder pkBuilder = new StringBuilder();
                    for (String pkCol : pkCols) {
                        Object val = getRecordValue(record, pkCol);
                        pkBuilder.append(val != null ? val.toString() : "").append("|");
                    }
                    uniqueMap.put(pkBuilder.toString(), record);
                }
                if (uniqueMap.size() < records.size()) {
                    log.warn("[{}] Removed {} duplicate records by PK ({}→{})",
                            getStepId(), records.size() - uniqueMap.size(), records.size(), uniqueMap.size());
                    records = new ArrayList<>(uniqueMap.values());
                }
            }

            if (records.isEmpty()) {
                saveSyncLogSummary(context.getExecutionId(), config.getSourceTable(), "SOURCE", 0L, 0L, 0L, null, null);
                saveSyncLogSummary(context.getExecutionId(), config.getTargetIfTable(), "IF", 0L, 0L, 0L, null, null);

                return StepResult.builder()
                        .stepId(getStepId())
                        .status(Status.SUCCESS)
                        .readCount(0)
                        .writeCount(0)
                        .skipCount(0)
                        .durationMs(System.currentTimeMillis() - startTime)
                        .sourceTable(config.getSourceTable())
                        .targetTable(config.getTargetIfTable())
                        .build();
            }

            // 2. Target IF 테이블에 배치 UPSERT 적재
            String targetDsId = getTargetDatasourceId(context);
            JdbcTemplate targetJdbc = dataSourceProvider.getJdbcTemplate(targetDsId);
            String actualTargetIfTable = JdbcTableNameResolver.resolve(
                    targetJdbc.getDataSource(), targetDsId, config.getTargetIfTable());

            // 기간 지정 실행 여부 확인 (RESYNC 상태 결정용)
            LocalDateTime paramStartTime = context.getParam("startTime");
            LocalDateTime paramEndTime = context.getParam("endTime");
            boolean isTimeRangeExecution = (paramStartTime != null && paramEndTime != null);

            String targetDbType = dataSourceProvider.getDbType(targetDsId);
            boolean useUpsert = config.isFullCopy() || isTimeRangeExecution;
            String insertSql = buildUpsertSql(actualTargetIfTable, columns, targetDbType, useUpsert);
            log.info("[{}] {} SQL: {}", getStepId(), useUpsert ? "UPSERT" : "INSERT (DO NOTHING)", insertSql);
            String conflictInfo = (config.getConflictKey() != null && !config.getConflictKey().isEmpty())
                    ? "conflictKey=" + config.getConflictKey() : "PK=" + config.getPrimaryKeyColumnList();
            log.info("[{}] Conflict: {}, PK: {}", getStepId(), conflictInfo, config.getPrimaryKeyColumnList());
            log.info("[{}] Batch size: {}", getStepId(), batchSize);

            // sourceRef용 실제 PK 컬럼 감지 (DB 메타데이터)
            String sourceDsIdForPk = getSourceDatasourceId(context);
            List<String> sourceRefPkCols = detectSourcePrimaryKey(sourceDsIdForPk, config.getSourceTable());
            log.info("[{}] Source PK columns (sourceRef): {}", getStepId(), sourceRefPkCols);

            // 성공/실패한 레코드 키 수집 (Source link_status 업데이트용)
            List<Object> successPkValues = new ArrayList<>();
            List<Object> failedPkValues = new ArrayList<>();

            // ===== Phase 1: 전체 레코드의 파라미터 사전 준비 =====
            List<Object[]> allParams = new ArrayList<>(records.size());
            List<String> allRecordKeys = new ArrayList<>(records.size());
            List<String> allSourceRefsJson = new ArrayList<>(records.size());

            for (Map<String, Object> record : records) {
                List<Object> params = new ArrayList<>();
                for (String column : columns) {
                    params.add(record.get(column));
                }

                // source_refs 생성: "zone:dsId:tbId:pk" 형식 JSON 배열
                String sourceRef;
                if (sourceRefPkCols.size() == 1) {
                    Object pkValue = getRecordValue(record, sourceRefPkCols.get(0));
                    sourceRef = SourceRefUtils.build(context, config.getSourceTable(), pkValue);
                } else {
                    Object[] pkValues = sourceRefPkCols.stream()
                            .map(col -> getRecordValue(record, col))
                            .toArray();
                    sourceRef = SourceRefUtils.buildComposite(context, config.getSourceTable(), pkValues);
                }
                String sourceRefsJson = SourceRefUtils.toJsonSingle(sourceRef);
                params.add(sourceRefsJson); // source_refs

                // link_status 결정
                Object sourceLinkStatus = getRecordValue(record, "link_status");
                String recordLinkStatus;
                if (isTimeRangeExecution) {
                    recordLinkStatus = "RESYNC";
                } else if ("RESYNC".equals(sourceLinkStatus)) {
                    recordLinkStatus = "RESYNC";
                } else {
                    recordLinkStatus = "PENDING";
                }
                params.add(recordLinkStatus); // link_status

                Timestamp now = Timestamp.valueOf(LocalDateTime.now());
                params.add(now); // extracted_at
                params.add(now); // updated_at
                params.add(context.getExecutionId()); // execution_id

                allParams.add(params.toArray());
                allRecordKeys.add(buildRecordKey(record));
                allSourceRefsJson.add(sourceRefsJson);
            }

            // ===== Phase 2: 배치 단위로 UPSERT 실행 =====
            int totalCount = allParams.size();
            for (int batchStart = 0; batchStart < totalCount; batchStart += batchSize) {
                int batchEnd = Math.min(batchStart + batchSize, totalCount);
                List<Object[]> batchParams = allParams.subList(batchStart, batchEnd);
                List<String> batchKeys = allRecordKeys.subList(batchStart, batchEnd);
                List<String> batchSourceRefs = allSourceRefsJson.subList(batchStart, batchEnd);
                int currentBatchSize = batchParams.size();

                log.info("[{}] Batch UPSERT {}-{}/{} ({} records)",
                        getStepId(), batchStart + 1, batchEnd, totalCount, currentBatchSize);

                try {
                    int[] results = targetJdbc.batchUpdate(insertSql, batchParams);

                    // 배치 결과 검증: 실제 영향받은 행 수 기반으로 카운트
                    int batchWriteCount = 0;
                    for (int j = 0; j < results.length; j++) {
                        // >= 0: 영향받은 행 수, -2(SUCCESS_NO_INFO): 성공이나 건수 불명
                        if (results[j] >= 0 || results[j] == java.sql.Statement.SUCCESS_NO_INFO) {
                            batchWriteCount++;
                            successPkValues.add(batchKeys.get(j));
                        } else {
                            // EXECUTE_FAILED (-3) 등 실패 케이스
                            skipCount++;
                            failedKeys.add(batchKeys.get(j));
                            failedPkValues.add(batchKeys.get(j));
                            log.warn("[{}] Batch item failed: index={}, key={}, result={}",
                                    getStepId(), j, batchKeys.get(j), results[j]);
                        }
                    }
                    writeCount += batchWriteCount;

                    if (batchWriteCount < currentBatchSize) {
                        log.warn("[{}] Batch partially succeeded: {}/{} records written",
                                getStepId(), batchWriteCount, currentBatchSize);
                    }
                } catch (Exception batchEx) {
                    // 배치 실패 → 개별 실행으로 fallback (실패 레코드 특정용)
                    log.warn("[{}] Batch failed, falling back to individual inserts: {}",
                            getStepId(), batchEx.getMessage());

                    for (int j = 0; j < currentBatchSize; j++) {
                        String recordKey = batchKeys.get(j);
                        try {
                            targetJdbc.update(insertSql, batchParams.get(j));
                            writeCount++;
                            successPkValues.add(recordKey);
                        } catch (Exception e) {
                            log.error("Failed to upsert record: {}", recordKey, e);
                            skipCount++;
                            failedKeys.add(recordKey);
                            failedPkValues.add(recordKey);
                            if (firstError == null) {
                                firstError = e.getMessage();
                            }
                        }
                    }
                }

                // 디버그용 지연 (배치 단위)
                if (stepDelayMs > 0) {
                    try { Thread.sleep(stepDelayMs); } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.warn("Step interrupted");
                        break;
                    }
                }
            }

            log.info("[{}] Loaded {} records to IF table '{}', {} skipped (batch size: {})",
                    getStepId(), writeCount, config.getTargetIfTable(), skipCount, batchSize);

            // 3. Source 테이블 link_status 업데이트
            if (!config.isSkipSourceStatusUpdate() && !config.isCustomStaging()) {
                String sourceDsId = getSourceDatasourceId(context);
                JdbcTemplate sourceJdbc = dataSourceProvider.getJdbcTemplate(sourceDsId);
                String sourceDbType = dataSourceProvider.getDbType(sourceDsId);
                String actualSourceTable = JdbcTableNameResolver.resolve(
                        sourceJdbc.getDataSource(), sourceDsId, config.getSourceTable());

                if (!successPkValues.isEmpty()) {
                    int updated = updateSourceLinkStatus(sourceJdbc, actualSourceTable,
                            config.getPrimaryKeyColumn(), successPkValues, "SUCCESS", sourceDbType);
                    log.info("[{}] Updated {} source records to SUCCESS", getStepId(), updated);
                }

                if (!failedPkValues.isEmpty()) {
                    int updated = updateSourceLinkStatus(sourceJdbc, actualSourceTable,
                            config.getPrimaryKeyColumn(), failedPkValues, "FAILED", sourceDbType);
                    log.info("[{}] Updated {} source records to FAILED", getStepId(), updated);
                }
            } else {
                log.info("[{}] Skipping source link_status update (skipSourceStatusUpdate={}, customStaging={})",
                        getStepId(), config.isSkipSourceStatusUpdate(), config.isCustomStaging());
            }

            // 4. SyncLog 요약 저장
            saveSyncLogSummary(context.getExecutionId(), config.getSourceTable(), "SOURCE",
                    (long) readCount, 0L, 0L, null, null);

            String failedKeysJson = failedKeys.isEmpty() ? null : String.join(",", failedKeys);
            saveSyncLogSummary(context.getExecutionId(), config.getTargetIfTable(), "IF",
                    (long) writeCount, (long) skipCount, 0L, failedKeysJson, firstError);

            return StepResult.builder()
                    .stepId(getStepId())
                    .status(Status.SUCCESS)
                    .readCount(readCount)
                    .writeCount(writeCount)
                    .skipCount(skipCount)
                    .durationMs(System.currentTimeMillis() - startTime)
                    .sourceTable(config.getSourceTable())
                    .targetTable(config.getTargetIfTable())
                    .build();

        } catch (Exception e) {
            log.error("Step execution failed", e);

            String errorMessage = e.getMessage();
            if (errorMessage != null && errorMessage.length() > 500) {
                errorMessage = errorMessage.substring(0, 500) + "...";
            }

            saveSyncLogSummary(context.getExecutionId(), config.getSourceTable(), "SOURCE",
                    (long) readCount, 0L, 0L, null, null);

            String failedKeysJson = failedKeys.isEmpty() ? null : String.join(",", failedKeys);
            saveSyncLogSummary(context.getExecutionId(), config.getTargetIfTable(), "IF",
                    (long) writeCount, (long) (readCount - writeCount), 0L, failedKeysJson, errorMessage);

            return StepResult.failed(getStepId(), e.getMessage(), System.currentTimeMillis() - startTime);
        }
    }

    // ==================== 컬럼 해석 ====================

    /**
     * 컬럼 목록 결정: config에 있으면 사용, 없으면 source 테이블에서 자동 감지
     */
    private List<String> resolveColumns(StepContext context) {
        List<String> configColumns = config.getColumns();
        if (configColumns != null && !configColumns.isEmpty()) {
            log.info("[{}] Using configured columns: {}", getStepId(), configColumns);
            return configColumns;
        }

        if (config.getSourceTable() != null && !config.getSourceTable().isBlank()) {
            String sourceDsId = getSourceDatasourceId(context);
            log.info("[{}] No columns configured, auto-detecting from source table '{}'",
                    getStepId(), config.getSourceTable());
            return fetchColumnsFromMetadata(sourceDsId, config.getSourceTable());
        }

        return List.of();
    }

    // ==================== SQL 빌더 ====================

    /**
     * UPSERT/INSERT SQL 생성
     * IF 테이블 메타 컬럼: source_refs, link_status, extracted_at, updated_at, execution_id
     *
     * @param useUpsert true: ON CONFLICT DO UPDATE (fullCopy 또는 RESYNC)
     *                  false: ON CONFLICT DO NOTHING (증분 동기화 - 중복 스킵)
     */
    private String buildUpsertSql(String actualTableName, List<String> columns, String dbType, boolean useUpsert) {
        StringBuilder sb = new StringBuilder();

        List<String> ifColumns = columns.stream()
                .map(String::toLowerCase)
                .toList();

        String columnList = String.join(", ", ifColumns);

        // 충돌 기준: conflictKey 설정 시 해당 컬럼 사용, 없으면 primaryKey 사용
        List<String> pkCols = config.getPrimaryKeyColumnList();
        List<String> conflictCols = (config.getConflictKey() != null && !config.getConflictKey().isEmpty())
                ? List.of(config.getConflictKey())
                : pkCols;

        if (isMysql(dbType) && !useUpsert && !pkCols.isEmpty()) {
            // MySQL: INSERT IGNORE (중복 PK 스킵)
            sb.append("INSERT IGNORE INTO ").append(actualTableName.toLowerCase());
        } else {
            sb.append("INSERT INTO ").append(actualTableName.toLowerCase());
        }

        sb.append(" (").append(columnList).append(", source_refs, link_status, extracted_at, updated_at, execution_id)");

        String placeholders = columns.stream().map(c -> "?").reduce((a, b) -> a + ", " + b).orElse("");
        sb.append(" VALUES (").append(placeholders).append(", ?, ?, ?, ?, ?)");

        if (!conflictCols.isEmpty()) {
            String conflictColList = conflictCols.stream()
                    .map(String::toLowerCase)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");

            if (isMysql(dbType)) {
                if (useUpsert) {
                    // MySQL: ON DUPLICATE KEY UPDATE
                    sb.append(" ON DUPLICATE KEY UPDATE ");

                    List<String> updateCols = ifColumns.stream()
                            .filter(col -> conflictCols.stream().noneMatch(ck -> ck.equalsIgnoreCase(col)))
                            .toList();

                    List<String> updateParts = new java.util.ArrayList<>();
                    for (String col : updateCols) {
                        updateParts.add(col + " = VALUES(" + col + ")");
                    }
                    // conflictKey가 source_refs가 아닌 경우에만 source_refs 갱신
                    if (conflictCols.stream().noneMatch(c -> c.equalsIgnoreCase("source_refs"))) {
                        updateParts.add("source_refs = VALUES(source_refs)");
                    }
                    updateParts.add("link_status = VALUES(link_status)");
                    updateParts.add("updated_at = VALUES(updated_at)");
                    updateParts.add("execution_id = VALUES(execution_id)");

                    sb.append(String.join(", ", updateParts));
                }
                // else: INSERT IGNORE already handles DO NOTHING
            } else {
                // PostgreSQL
                if (useUpsert) {
                    sb.append(" ON CONFLICT (").append(conflictColList).append(") DO UPDATE SET ");

                    List<String> updateCols = ifColumns.stream()
                            .filter(col -> conflictCols.stream().noneMatch(ck -> ck.equalsIgnoreCase(col)))
                            .toList();

                    List<String> updateParts = new java.util.ArrayList<>();
                    for (String col : updateCols) {
                        updateParts.add(col + " = EXCLUDED." + col);
                    }
                    // conflictKey가 source_refs가 아닌 경우에만 source_refs 갱신
                    if (conflictCols.stream().noneMatch(c -> c.equalsIgnoreCase("source_refs"))) {
                        updateParts.add("source_refs = EXCLUDED.source_refs");
                    }
                    updateParts.add("link_status = EXCLUDED.link_status");
                    updateParts.add("updated_at = EXCLUDED.updated_at");
                    updateParts.add("execution_id = EXCLUDED.execution_id");

                    sb.append(String.join(", ", updateParts));
                } else {
                    sb.append(" ON CONFLICT (").append(conflictColList).append(") DO NOTHING");
                }
            }
        }

        return sb.toString();
    }

    // ==================== DataSource ID 해석 ====================

    private String getSourceDatasourceId(StepContext context) {
        if (context.getSourceDatasourceId() != null) {
            return context.getSourceDatasourceId();
        }
        return dataSourceProvider.getSourceDatasourceId();
    }

    private String getTargetDatasourceId(StepContext context) {
        if (context.getTargetDatasourceId() != null) {
            return context.getTargetDatasourceId();
        }
        return dataSourceProvider.getTargetDatasourceId();
    }

    // ==================== SIMPLE_COPY 데이터 조회 ====================

    /**
     * SIMPLE_COPY 모드: 조건에 따라 데이터 조회
     * - fullCopy=true: 전체 조회 (RSV 제원 등 외부 DB용)
     * - 기간 지정 실행: startTime/endTime 범위
     * - 일반 실행: link_status = 'PENDING' 또는 NULL
     */
    private List<Map<String, Object>> fetchSimpleCopy(StepContext context, List<String> columns) {
        String sourceDsId = getSourceDatasourceId(context);
        JdbcTemplate sourceJdbc = dataSourceProvider.getJdbcTemplate(sourceDsId);
        String sourceDbType = dataSourceProvider.getDbType(sourceDsId);

        String actualSourceTable = JdbcTableNameResolver.resolve(
                sourceJdbc.getDataSource(), sourceDsId, config.getSourceTable());

        List<Map<String, Object>> records;

        if (config.isFullCopy()) {
            String selectSql = buildFullCopySelectSql(actualSourceTable, columns, sourceDbType);
            records = sourceJdbc.queryForList(selectSql);
            log.info("[{}] Full copy mode - Found {} records from source table '{}'",
                    getStepId(), records.size(), actualSourceTable);
        } else {
            LocalDateTime paramStartTime = context.getParam("startTime");
            LocalDateTime paramEndTime = context.getParam("endTime");

            if (paramStartTime != null && paramEndTime != null) {
                log.info("[{}] Time-range execution: {} ~ {}", getStepId(), paramStartTime, paramEndTime);

                String selectSql = buildSelectSql(actualSourceTable, columns, sourceDbType);
                records = sourceJdbc.queryForList(selectSql,
                        Timestamp.valueOf(paramStartTime), Timestamp.valueOf(paramEndTime));

                log.info("[{}] Found {} records from source table '{}' in range {} ~ {}",
                        getStepId(), records.size(), actualSourceTable, paramStartTime, paramEndTime);
            } else {
                log.info("[{}] Normal execution: querying PENDING records", getStepId());

                String selectSql = buildPendingSelectSql(actualSourceTable, columns, sourceDbType);
                records = sourceJdbc.queryForList(selectSql);

                log.info("[{}] Found {} PENDING records from source table '{}'",
                        getStepId(), records.size(), actualSourceTable);
            }
        }

        return records;
    }

    private String buildPendingSelectSql(String actualTableName, List<String> columns, String dbType) {
        String columnList = columns.stream()
                .map(c -> qi(c, dbType))
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
        String quotedTable = qi(actualTableName, dbType);

        return String.format(
                "SELECT %s, %s FROM %s WHERE %s IS NULL OR %s IN ('PENDING', 'RESYNC')",
                columnList, qi("link_status", dbType), quotedTable,
                qi("link_status", dbType), qi("link_status", dbType));
    }

    private String buildFullCopySelectSql(String actualTableName, List<String> columns, String dbType) {
        String columnList = columns.stream()
                .map(c -> qi(c, dbType))
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
        String quotedTable = qi(actualTableName, dbType);
        return String.format("SELECT %s FROM %s", columnList, quotedTable);
    }

    private String buildSelectSql(String actualTableName, List<String> columns, String dbType) {
        String columnList = columns.stream()
                .map(c -> qi(c, dbType))
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
        String quotedTable = qi(actualTableName, dbType);

        String timeExpr = buildTimeExpression(columns, dbType);

        return String.format(
                "SELECT %s FROM %s WHERE %s > ? AND %s <= ? ORDER BY %s",
                columnList, quotedTable,
                timeExpr, timeExpr, timeExpr
        );
    }

    private String buildTimeExpression(List<String> columns, String dbType) {
        if (config.getTimeExpression() != null && !config.getTimeExpression().isBlank()) {
            return config.getTimeExpression();
        }

        if (config.getDateColumn() != null && !config.getDateColumn().isBlank()
                && config.getTimeColumn() != null && !config.getTimeColumn().isBlank()) {
            String actualDateCol = resolveActualColumnName(columns, config.getDateColumn());
            String actualTimeCol = resolveActualColumnName(columns, config.getTimeColumn());
            if (isMysql(dbType)) {
                return "TIMESTAMP(" + qi(actualDateCol, dbType) + ", " + qi(actualTimeCol, dbType) + ")";
            }
            return "(" + qi(actualDateCol, dbType) + " + " + qi(actualTimeCol, dbType) + ")";
        }

        if (config.getTimeColumn() != null && !config.getTimeColumn().isBlank()) {
            String actualTimeCol = resolveActualColumnName(columns, config.getTimeColumn());
            return qi(actualTimeCol, dbType);
        }

        throw new IllegalStateException("No time column configured. Set timeColumn, dateColumn+timeColumn, or timeExpression.");
    }

    private String resolveActualColumnName(List<String> columns, String logicalColumnName) {
        return columns.stream()
                .filter(c -> c.equalsIgnoreCase(logicalColumnName))
                .findFirst()
                .orElse(logicalColumnName);
    }

    // ==================== SyncLog / 이력 ====================

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

    // ==================== 메타데이터 / PK 감지 ====================

    private List<String> detectSourcePrimaryKey(String datasourceId, String tableName) {
        List<String> pkColumns = new ArrayList<>();
        JdbcTemplate jdbc = dataSourceProvider.getJdbcTemplate(datasourceId);

        try {
            Connection conn = jdbc.getDataSource().getConnection();
            try {
                DatabaseMetaData metaData = conn.getMetaData();
                String catalog = conn.getCatalog();
                String[] variants = {tableName, tableName.toLowerCase(), tableName.toUpperCase()};

                for (String variant : variants) {
                    try (ResultSet rs = metaData.getPrimaryKeys(catalog, null, variant)) {
                        while (rs.next()) {
                            String colName = rs.getString("COLUMN_NAME");
                            int keySeq = rs.getInt("KEY_SEQ");
                            while (pkColumns.size() < keySeq) {
                                pkColumns.add(null);
                            }
                            pkColumns.set(keySeq - 1, colName);
                        }
                    }
                    if (!pkColumns.isEmpty()) {
                        pkColumns.removeIf(java.util.Objects::isNull);
                        log.info("[{}] Detected source PK from metadata: {} (table: {})",
                                getStepId(), pkColumns, variant);
                        break;
                    }
                }
            } finally {
                conn.close();
            }
        } catch (Exception e) {
            log.warn("[{}] Failed to detect source PK from metadata: {}", getStepId(), e.getMessage());
        }

        if (pkColumns.isEmpty()) {
            List<String> ukCols = config.getPrimaryKeyColumnList();
            if (!ukCols.isEmpty()) {
                pkColumns.add(ukCols.get(0));
                log.info("[{}] PK detection failed, fallback to first UK column: {}", getStepId(), ukCols.get(0));
            }
        }

        return pkColumns;
    }

    private List<String> fetchColumnsFromMetadata(String datasourceId, String tableName) {
        List<String> columns = new ArrayList<>();
        JdbcTemplate jdbc = dataSourceProvider.getJdbcTemplate(datasourceId);
        String dbType = dataSourceProvider.getDbType(datasourceId);

        try {
            Connection conn = jdbc.getDataSource().getConnection();
            try {
                DatabaseMetaData metaData = conn.getMetaData();
                String catalog = conn.getCatalog();

                String[] variants = {tableName, tableName.toLowerCase(), tableName.toUpperCase()};

                for (String variant : variants) {
                    try (ResultSet rs = metaData.getColumns(catalog, null, variant, null)) {
                        while (rs.next()) {
                            String columnName = rs.getString("COLUMN_NAME");
                            columns.add(columnName);
                        }
                    }
                    if (!columns.isEmpty()) {
                        log.info("[{}] Auto-detected {} columns from table '{}': {}",
                                getStepId(), columns.size(), tableName, columns);
                        break;
                    }
                }

                if (columns.isEmpty()) {
                    log.warn("[{}] No columns found for table '{}'. Using fallback query.", getStepId(), tableName);
                    columns = fetchColumnsFromQuery(jdbc, tableName, dbType);
                }
            } finally {
                conn.close();
            }
        } catch (Exception e) {
            log.error("[{}] Failed to fetch columns from metadata for table '{}': {}",
                    getStepId(), tableName, e.getMessage());
        }

        return columns;
    }

    private List<String> fetchColumnsFromQuery(JdbcTemplate jdbc, String tableName, String dbType) {
        List<String> columns = new ArrayList<>();
        String[] variants = {tableName, tableName.toLowerCase(), tableName.toUpperCase()};

        for (String variant : variants) {
            try {
                String sql = String.format("SELECT * FROM %s WHERE 1=0", qi(variant, dbType));
                jdbc.query(sql, rs -> {
                    var metaData = rs.getMetaData();
                    int columnCount = metaData.getColumnCount();
                    for (int i = 1; i <= columnCount; i++) {
                        columns.add(metaData.getColumnName(i));
                    }
                    return null;
                });
                if (!columns.isEmpty()) {
                    log.info("[{}] Fetched {} columns via query from table '{}': {}",
                            getStepId(), columns.size(), variant, columns);
                    break;
                }
            } catch (Exception e) {
                log.debug("[{}] Table variant '{}' not found, trying next...", getStepId(), variant);
            }
        }

        if (columns.isEmpty()) {
            log.error("[{}] Failed to fetch columns via query for table '{}' (all variants tried)",
                    getStepId(), tableName);
        }
        return columns;
    }

    // ==================== Source link_status 업데이트 ====================

    private int updateSourceLinkStatus(JdbcTemplate jdbc, String tableName,
                                        String pkColumn, List<Object> pkValues, String status, String dbType) {
        if (pkValues.isEmpty()) return 0;

        // PK 값이 String인데 컬럼이 숫자/날짜 타입일 수 있으므로, 컬럼을 text로 캐스팅하여 비교
        boolean isMysql = "mysql".equalsIgnoreCase(dbType);

        List<String> pkCols = List.of(pkColumn.split(","));
        boolean isCompositePk = pkCols.size() > 1;

        int updateBatchSize = 500;
        int totalUpdated = 0;

        if (!isCompositePk) {
            // 단일 PK: IN 절 사용 (컬럼을 text 캐스팅)
            String colExpr = isMysql
                    ? "CAST(" + pkColumn.trim().toLowerCase() + " AS CHAR)"
                    : pkColumn.trim().toLowerCase() + "::text";

            for (int i = 0; i < pkValues.size(); i += updateBatchSize) {
                List<Object> batch = pkValues.subList(i, Math.min(i + updateBatchSize, pkValues.size()));
                String placeholders = batch.stream().map(v -> "?").reduce((a, b) -> a + ", " + b).orElse("");

                String sql = String.format(
                        "UPDATE %s SET link_status = ? WHERE %s IN (%s)",
                        tableName.toLowerCase(), colExpr, placeholders);

                List<Object> params = new ArrayList<>();
                params.add(status);
                params.addAll(batch);

                try {
                    int updated = jdbc.update(sql, params.toArray());
                    totalUpdated += updated;
                } catch (Exception e) {
                    log.error("[{}] Failed to update source link_status: {}", getStepId(), e.getMessage(), e);
                }
            }
        } else {
            // 복합 PK: ROW값 비교 (col1::text, col2::text) IN ((?,?), (?,?))
            List<String> trimmedCols = pkCols.stream().map(String::trim).toList();
            String colList = trimmedCols.stream()
                    .map(c -> isMysql
                            ? "CAST(" + c.toLowerCase() + " AS CHAR)"
                            : c.toLowerCase() + "::text")
                    .reduce((a, b) -> a + ", " + b).orElse("");
            String singleTuple = "(" + trimmedCols.stream().map(c -> "?").reduce((a, b) -> a + ", " + b).orElse("") + ")";

            for (int i = 0; i < pkValues.size(); i += updateBatchSize) {
                List<Object> batch = pkValues.subList(i, Math.min(i + updateBatchSize, pkValues.size()));
                String tuples = batch.stream().map(v -> singleTuple).reduce((a, b) -> a + ", " + b).orElse("");

                String sql = String.format(
                        "UPDATE %s SET link_status = ? WHERE (%s) IN (%s)",
                        tableName.toLowerCase(), colList, tuples);

                List<Object> params = new ArrayList<>();
                params.add(status);
                for (Object pkVal : batch) {
                    String[] parts = pkVal.toString().split("\\|", -1);
                    for (String part : parts) {
                        params.add(part);
                    }
                }

                try {
                    int updated = jdbc.update(sql, params.toArray());
                    totalUpdated += updated;
                } catch (Exception e) {
                    log.error("[{}] Failed to update source link_status (composite PK): {}", getStepId(), e.getMessage(), e);
                }
            }
        }

        log.debug("[{}] Updated source link_status: table={}, count={}, status={}", getStepId(), tableName, totalUpdated, status);
        return totalUpdated;
    }

    // ==================== 유틸리티 ====================

    private String buildRecordKey(Map<String, Object> record) {
        List<String> pkCols = config.getPrimaryKeyColumnList();
        if (pkCols.isEmpty()) {
            return "unknown";
        }
        if (pkCols.size() == 1) {
            Object val = getRecordValue(record, pkCols.get(0));
            return val != null ? val.toString() : "unknown";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pkCols.size(); i++) {
            if (i > 0) sb.append("|");
            Object val = getRecordValue(record, pkCols.get(i));
            sb.append(val != null ? val.toString() : "");
        }
        return sb.toString();
    }

    private Object getRecordValue(Map<String, Object> record, String columnName) {
        if (record.containsKey(columnName)) {
            return record.get(columnName);
        }
        for (Map.Entry<String, Object> entry : record.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(columnName)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
