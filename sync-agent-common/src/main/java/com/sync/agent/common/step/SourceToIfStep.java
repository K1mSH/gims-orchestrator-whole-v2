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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Source → IF 테이블 동기화 Step (공통 copy 엔진)
 *
 * Source 테이블에서 데이터를 조회(Extract)하고 IF 테이블에 적재(Load)하는 통합 Step.
 * ETL에서 E+L을 하나의 Step으로 처리하며, IF 테이블에 UPSERT 방식으로 적재한다.
 *
 * ── 사용처 ──
 * RCV/SND/Internal RCV 파이프라인이 공통으로 사용하는 범용 데이터 복사 Step.
 * Loader(DefaultLoadStep/InternalLoadStep)만 별도 Step 클래스를 사용한다.
 *
 *   Agent 타입       | PipelineConfig           | extractType
 *   RCV (DMZ)        | RcvPipelineConfig        | jewon=SIMPLE_COPY, obsvdata=CUSTOM_STAGING(Link)
 *   SND (DMZ)        | SndPipelineConfig        | 전부 SIMPLE_COPY
 *   RCV (Internal)   | RcvPipelineConfig (int)  | 전부 SIMPLE_COPY
 *
 * 각 PipelineConfig에서 ExtractStepConfig에 YAML 설정값(source-table, target-table, PK 등)을
 * 주입하여 new SourceToIfStep(config, ...)으로 생성한다.
 * 동일 클래스를 config만 바꿔서 재사용하는 구조.
 *
 * ── 두 가지 모드 ──
 * 1. SIMPLE_COPY: 직접 SQL 조회 (fetchSimpleCopy)
 *    - 전체 복사 (fullCopy=true) 또는 link_status 기반 증분
 *    - 조건실행(conditions) / 시간범위(timeRange) 실행 시 강제 적용
 * 2. CUSTOM_STAGING: 커스텀 DataFetcher로 데이터 조회
 *    - Link 테이블(link_ngwis) 기반 증분 동기화 (RCV obsvdata에서 사용)
 *    - conditions/timeRange가 있으면 SIMPLE_COPY로 오버라이드됨
 *
 * ── 주요 기능 ──
 * - Source 데이터 조회 (위 두 모드 중 하나)
 * - IF 테이블에 배치 UPSERT (ON CONFLICT DO UPDATE, batchSize=1000)
 * - source_refs 추적 정보 자동 생성 (zone:dsDbId:tableId:pk 형식)
 * - link_status 관리 (PENDING/RESYNC/SUCCESS/FAILED)
 * - Source 테이블 link_status 업데이트 (성공/실패)
 * - SyncLog 요약 저장 (매핑별 성공/실패 건수)
 */
@Slf4j
public class SourceToIfStep implements StepExecutor {

    // IF 테이블 메타 컬럼 목록 (소스에서 제외해야 함)
    private static final List<String> IF_META_COLUMNS = List.of(
            "source_refs", "link_status", "extracted_at", "updated_at", "execution_id"
    );

    // 소스 auto-increment PK 컬럼 (IF 테이블에도 동일 PK가 있으므로 제외해야 함)
    // 소스의 PK 값은 source_refs에 이미 추적됨
    private static final List<String> AUTO_INCREMENT_PK_COLUMNS = List.of("id");

    private static final int DEFAULT_BATCH_SIZE = 1000;

    private final ExtractStepConfig config;
    private final DataSourceProvider dataSourceProvider;
    private final SyncLogRepository syncLogRepository;
    private final long stepDelayMs;
    private final int batchSize;
    private String mappingName;  // YAML table-mappings name

    public void setMappingName(String mappingName) {
        this.mappingName = mappingName;
    }

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

            // SELECT용 컬럼: IF 메타 컬럼만 제외 (id 포함 → source_refs PK 추출에 필요)
            List<String> selectColumns = allColumns.stream()
                    .filter(c -> IF_META_COLUMNS.stream().noneMatch(meta -> meta.equalsIgnoreCase(c)))
                    .toList();

            // INSERT용 컬럼: IF 메타 + auto-increment PK 추가 제외 (id 제외 → IF 테이블 PK 충돌 방지)
            List<String> columns = selectColumns.stream()
                    .filter(c -> AUTO_INCREMENT_PK_COLUMNS.stream().noneMatch(pk -> pk.equalsIgnoreCase(c)))
                    .toList();
            log.info("[{}] Columns: all={}, select={}, insert={} (auto-increment PK excluded from INSERT only)",
                    getStepId(), allColumns.size(), selectColumns.size(), columns.size());

            // conditions 실행 시 link 테이블 갱신 스킵 플래그 설정
            ExecutionOptions execOptions = context.getExecutionOptions();
            if (execOptions != null && execOptions.hasConditions()) {
                context.getSharedData().put("skipLinkUpdate", true);
                log.info("[{}] Condition execution detected — link table update will be skipped", getStepId());
            }

            // 1. 데이터 조회 (타입에 따라 다른 방식) - selectColumns 사용 (id 포함)
            List<Map<String, Object>> records;

            // conditions 실행 시 CUSTOM_STAGING이어도 SIMPLE_COPY 경로 사용
            // → Link 기반 증분 로직은 기본 스케줄 실행 전용, 조건 실행은 직접 조회
            boolean useSimpleCopy = execOptions != null && (execOptions.hasConditions() || execOptions.isTimeRangeExecution());

            if (config.isCustomStaging() && config.getCustomDataFetcher() != null && !useSimpleCopy) {
                log.info("[{}] Using custom DataFetcher for CUSTOM_STAGING", getStepId());
                records = config.getCustomDataFetcher().fetch(context);
            } else {
                if (useSimpleCopy && config.isCustomStaging()) {
                    log.info("[{}] Conditions/TimeRange present — bypassing CUSTOM_STAGING, using SIMPLE_COPY", getStepId());
                }
                records = fetchSimpleCopy(context, selectColumns);
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
                saveSyncLogMapping(context.getExecutionId(), 0L, 0L, 0L, null, null, config.getPrimaryKeyColumn());

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

            // 조건 실행 여부 확인 (conditions 또는 기존 시간범위)
            ExecutionOptions execOpts = context.getExecutionOptions();
            boolean isConditionExecution = execOpts != null && execOpts.hasConditions();
            LocalDateTime paramStartTime = context.getParam("startTime");
            LocalDateTime paramEndTime = context.getParam("endTime");
            boolean isTimeRangeExecution = (paramStartTime != null && paramEndTime != null);
            boolean isResyncExecution = isConditionExecution || isTimeRangeExecution;

            String targetDbType = dataSourceProvider.getDbType(targetDsId);
            boolean useUpsert = config.isFullCopy() || isResyncExecution;
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
                if (isResyncExecution) {
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

            // 4. SyncLog 요약 저장 (매핑 단위)
            String failedKeysJson = failedKeys.isEmpty() ? null : String.join(",", failedKeys);
            saveSyncLogMapping(context.getExecutionId(),
                    (long) readCount, (long) writeCount, (long) skipCount,
                    failedKeysJson, firstError, config.getPrimaryKeyColumn());

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

            long failedCountVal = (long) (readCount - writeCount - skipCount);
            String failedKeysJson = failedKeys.isEmpty() ? null : String.join(",", failedKeys);
            saveSyncLogMapping(context.getExecutionId(),
                    (long) readCount, (long) writeCount, (long) skipCount,
                    failedKeysJson, errorMessage, config.getPrimaryKeyColumn());

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
     * @param useUpsert true: ON CONFLICT DO UPDATE (fullCopy 또는 RESYNC) - 전체 컬럼 갱신
     *                  false: 메타 경량 UPDATE (증분 동기화) - updated_at, execution_id만 갱신
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

        sb.append("INSERT INTO ").append(actualTableName.toLowerCase());

        sb.append(" (").append(columnList).append(", source_refs, link_status, extracted_at, updated_at, execution_id)");

        String placeholders = columns.stream().map(c -> "?").reduce((a, b) -> a + ", " + b).orElse("");
        sb.append(" VALUES (").append(placeholders).append(", ?, ?, ?, ?, ?)");

        if (!conflictCols.isEmpty()) {
            String conflictColList = conflictCols.stream()
                    .map(String::toLowerCase)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");

            if (isMysql(dbType)) {
                sb.append(" ON DUPLICATE KEY UPDATE ");

                if (useUpsert) {
                    // MySQL full UPSERT: 모든 데이터 컬럼 갱신
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
                } else {
                    // MySQL 증분: 메타 컬럼만 경량 UPDATE (데이터 보존)
                    sb.append("updated_at = VALUES(updated_at), execution_id = VALUES(execution_id)");
                }
            } else {
                // PostgreSQL
                sb.append(" ON CONFLICT (").append(conflictColList).append(") DO UPDATE SET ");

                if (useUpsert) {
                    // PostgreSQL full UPSERT: 모든 데이터 컬럼 갱신
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
                    // PostgreSQL 증분: 메타 컬럼만 경량 UPDATE (데이터 보존)
                    sb.append("updated_at = EXCLUDED.updated_at, execution_id = EXCLUDED.execution_id");
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
     *
     * 디폴트 조건:
     * - fullCopy=true: 조건 없음 (전체 조회)
     * - fullCopy=false: link_status IN ('PENDING', 'RESYNC', 'FAILED') OR IS NULL
     *
     * 실행 시 conditions가 있으면 같은 컬럼은 대체, 다른 컬럼은 추가.
     */
    private List<Map<String, Object>> fetchSimpleCopy(StepContext context, List<String> columns) {
        String sourceDsId = getSourceDatasourceId(context);
        JdbcTemplate sourceJdbc = dataSourceProvider.getJdbcTemplate(sourceDsId);
        String sourceDbType = dataSourceProvider.getDbType(sourceDsId);

        String actualSourceTable = JdbcTableNameResolver.resolve(
                sourceJdbc.getDataSource(), sourceDsId, config.getSourceTable());

        ExecutionOptions execOptions = context.getExecutionOptions();

        // tableName 필터링: 이 Step의 source-table에 해당하는 조건만 추출
        List<ExecutionCondition> execConditions = null;
        if (execOptions != null && execOptions.getConditions() != null) {
            String sourceTable = config.getSourceTable();
            execConditions = execOptions.getConditions().stream()
                    .filter(c -> c.getTableName() == null || c.getTableName().isEmpty()
                            || c.getTableName().equalsIgnoreCase(sourceTable))
                    .toList();
        }
        // 필터링 후 이 Step 대상 조건이 있는지 판단 (tableName 필터링 결과 기준)
        boolean hasConditions = execConditions != null && !execConditions.isEmpty();

        // 디폴트 조건 결정
        Map<String, ExecutionCondition> defaults = new LinkedHashMap<>();
        if (!config.isFullCopy()) {
            // fullCopy가 아니면 PENDING/RESYNC/FAILED 필터가 디폴트
            // 단, 동적 conditions가 있으면 이 디폴트를 사용하지 않음 (재동기화 목적이므로)
            if (!hasConditions) {
                defaults.put("link_status", ExecutionCondition.in("link_status", "PENDING,RESYNC,FAILED"));
            }
        }

        // 하위호환: 기존 startTime/endTime도 conditions로 변환
        if (!hasConditions) {
            LocalDateTime paramStartTime = context.getParam("startTime");
            LocalDateTime paramEndTime = context.getParam("endTime");
            if (paramStartTime != null && paramEndTime != null) {
                String timeCol = resolveTimeColumn();
                if (timeCol != null) {
                    defaults.remove("link_status"); // 시간범위 실행 시 link_status 필터 제거
                    defaults.put(timeCol, ExecutionCondition.between(timeCol,
                            paramStartTime.toString(), paramEndTime.toString()));
                    log.info("[{}] Time-range execution (legacy): {} ~ {}", getStepId(), paramStartTime, paramEndTime);
                }
            }
        }

        // conditions merge + WHERE 빌드
        ConditionBuilder.WhereClause where = ConditionBuilder.buildMerged(defaults, hasConditions ? execConditions : null, sourceDbType);

        // SQL 생성
        String columnList = columns.stream()
                .map(c -> qi(c, sourceDbType))
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
        String quotedTable = qi(actualSourceTable, sourceDbType);

        // link_status가 디폴트에 있으면 SELECT에 포함 (Source link_status 업데이트용)
        String selectColumns = columnList;
        if (!config.isFullCopy() && !hasConditions && defaults.containsKey("link_status")) {
            selectColumns = columnList + ", " + qi("link_status", sourceDbType);
        }

        String sql = "SELECT " + selectColumns + " FROM " + quotedTable + where.toWhereSql();

        List<Map<String, Object>> records = sourceJdbc.queryForList(sql, where.getParamsArray());

        if (hasConditions) {
            log.info("[{}] Condition execution - Found {} records from '{}' with {} conditions",
                    getStepId(), records.size(), actualSourceTable,
                    execConditions != null ? execConditions.size() : 0);
        } else {
            log.info("[{}] Found {} records from source table '{}'",
                    getStepId(), records.size(), actualSourceTable);
        }

        return records;
    }

    /**
     * 시간 컬럼명 결정 (하위호환용)
     */
    private String resolveTimeColumn() {
        if (config.getTimeColumn() != null && !config.getTimeColumn().isBlank()) {
            return config.getTimeColumn();
        }
        if (config.getDateColumn() != null && !config.getDateColumn().isBlank()) {
            return config.getDateColumn();
        }
        return null;
    }

    private String buildPendingSelectSql(String actualTableName, List<String> columns, String dbType) {
        String columnList = columns.stream()
                .map(c -> qi(c, dbType))
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
        String quotedTable = qi(actualTableName, dbType);

        return String.format(
                "SELECT %s, %s FROM %s WHERE %s IS NULL OR %s IN ('PENDING', 'RESYNC', 'FAILED')",
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

    private void saveSyncLogMapping(String executionId,
                                     Long readCount, Long writeCount, Long skipCount,
                                     String failedKeys, String errorSummary, String sourcePkColumn) {
        String resolvedMappingName = mappingName != null ? mappingName : config.getStepId();
        String sourceJson = "[\"" + config.getSourceTable() + "\"]";
        String targetJson = "[\"" + config.getTargetIfTable() + "\"]";
        long failedCount = readCount != null && writeCount != null && skipCount != null
                ? Math.max(0, readCount - writeCount - skipCount) : 0L;

        SyncLog logEntry = SyncLog.builder()
                .executionId(executionId)
                .stepId(getStepId())
                .mappingName(resolvedMappingName)
                .sourceTables(sourceJson)
                .targetTables(targetJson)
                .readCount(readCount != null ? readCount : 0L)
                .writeCount(writeCount != null ? writeCount : 0L)
                .failedCount(failedCount)
                .skipCount(skipCount != null ? skipCount : 0L)
                .failedKeys(failedKeys)
                .errorSummary(errorSummary)
                .sourcePkColumn(sourcePkColumn)
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
