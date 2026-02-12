package com.sync.agent.common.step;

import com.sync.agent.common.config.JdbcTableNameResolver;
import com.sync.agent.common.controller.DataSourceProvider;
import com.sync.agent.common.entity.SyncLog;
import com.sync.agent.common.repository.SyncLogRepository;
import com.sync.agent.common.service.SyncRecordHistoryService;
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
 * Source 테이블 → IF 테이블 추출 Step (공통)
 *
 * 두 가지 모드 지원:
 * 1. SIMPLE_COPY: Source 1개 → IF (1:1), 전체 추적 가능
 * 2. CUSTOM_STAGING: 커스텀 DataFetcher로 데이터 조회, IF→Target만 추적
 */
@Slf4j
public class SourceToIfExtractStep implements StepExecutor {

    // IF 테이블 메타 컬럼 목록 (소스에서 제외해야 함)
    private static final List<String> IF_META_COLUMNS = List.of(
            "source_refs", "link_status", "extracted_at", "updated_at", "execution_id"
    );

    private final ExtractStepConfig config;
    private final DataSourceProvider dataSourceProvider;
    private final SyncLogRepository syncLogRepository;
    private final SyncRecordHistoryService historyService;
    private final long stepDelayMs;

    public SourceToIfExtractStep(
            ExtractStepConfig config,
            DataSourceProvider dataSourceProvider,
            SyncLogRepository syncLogRepository) {
        this(config, dataSourceProvider, syncLogRepository, null, 0);
    }

    public SourceToIfExtractStep(
            ExtractStepConfig config,
            DataSourceProvider dataSourceProvider,
            SyncLogRepository syncLogRepository,
            SyncRecordHistoryService historyService) {
        this(config, dataSourceProvider, syncLogRepository, historyService, 0);
    }

    public SourceToIfExtractStep(
            ExtractStepConfig config,
            DataSourceProvider dataSourceProvider,
            SyncLogRepository syncLogRepository,
            long stepDelayMs) {
        this(config, dataSourceProvider, syncLogRepository, null, stepDelayMs);
    }

    public SourceToIfExtractStep(
            ExtractStepConfig config,
            DataSourceProvider dataSourceProvider,
            SyncLogRepository syncLogRepository,
            SyncRecordHistoryService historyService,
            long stepDelayMs) {
        this.config = config;
        this.dataSourceProvider = dataSourceProvider;
        this.syncLogRepository = syncLogRepository;
        this.historyService = historyService;
        this.stepDelayMs = stepDelayMs;
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
                // CUSTOM_STAGING: 커스텀 DataFetcher 사용
                log.info("[{}] Using custom DataFetcher for CUSTOM_STAGING", getStepId());
                records = config.getCustomDataFetcher().fetch(context);
            } else {
                // SIMPLE_COPY: 기본 시간 범위 조회 (동적 컬럼 사용)
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
                // 빈 결과도 로그 기록
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

            // 2. Target IF 테이블에 적재
            String targetDsId = getTargetDatasourceId(context);
            JdbcTemplate targetJdbc = dataSourceProvider.getJdbcTemplate(targetDsId);
            // 실제 테이블명 조회 (대소문자 처리) - JdbcTableNameResolver 사용
            String actualTargetIfTable = JdbcTableNameResolver.resolve(
                    targetJdbc.getDataSource(), targetDsId, config.getTargetIfTable());

            // 기간 지정 실행 여부 확인 (RESYNC 상태 결정용)
            LocalDateTime paramStartTime = context.getParam("startTime");
            LocalDateTime paramEndTime = context.getParam("endTime");
            boolean isTimeRangeExecution = (paramStartTime != null && paramEndTime != null);

            // 항상 UPSERT 사용 (ON CONFLICT DO UPDATE)
            String insertSql = buildUpsertSql(actualTargetIfTable, columns);
            log.info("[{}] UPSERT SQL: {}", getStepId(), insertSql);
            log.info("[{}] UK columns (UPSERT): {}", getStepId(), config.getPrimaryKeyColumnList());

            // sourceRef용 실제 PK 컬럼 감지 (DB 메타데이터)
            String sourceDsIdForPk = getSourceDatasourceId(context);
            List<String> sourceRefPkCols = detectSourcePrimaryKey(sourceDsIdForPk, config.getSourceTable());
            log.info("[{}] Source PK columns (sourceRef): {}", getStepId(), sourceRefPkCols);

            int totalCount = records.size();
            int currentIndex = 0;

            // 성공/실패한 레코드 키 수집 (Source link_status 업데이트용)
            List<Object> successPkValues = new ArrayList<>();
            List<Object> failedPkValues = new ArrayList<>();

            // 이력 기록용 엔트리 수집
            List<SyncRecordHistoryService.HistoryEntry> historyEntries = new ArrayList<>();

            for (Map<String, Object> record : records) {
                String recordKey = buildRecordKey(record);
                currentIndex++;

                // 진행률 로그 (10% 단위)
                int progressPercent = (int) ((currentIndex * 100.0) / totalCount);
                if (currentIndex == 1 || currentIndex == totalCount || progressPercent % 10 == 0) {
                    log.info("[{}] Processing {}/{} ({}%) - {}",
                            getStepId(), currentIndex, totalCount, progressPercent, recordKey);
                }

                try {
                    // 파라미터 준비: 컬럼 값들 + source_refs + link_status + extracted_at + updated_at + execution_id
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

                    // link_status: Source가 RESYNC면 전파, 기간 지정이면 RESYNC, 그외 PENDING
                    Object sourceLinkStatus = getRecordValue(record, "link_status");
                    String recordLinkStatus;
                    if (isTimeRangeExecution) {
                        recordLinkStatus = "RESYNC";
                    } else if ("RESYNC".equals(sourceLinkStatus)) {
                        recordLinkStatus = "RESYNC";  // Source RESYNC 전파
                    } else {
                        recordLinkStatus = "PENDING";
                    }
                    params.add(recordLinkStatus); // link_status

                    Timestamp now = Timestamp.valueOf(LocalDateTime.now());
                    params.add(now); // extracted_at
                    params.add(now); // updated_at
                    params.add(context.getExecutionId()); // execution_id

                    targetJdbc.update(insertSql, params.toArray());
                    writeCount++;
                    successPkValues.add(recordKey);

                    // 이력 엔트리 수집
                    historyEntries.add(SyncRecordHistoryService.HistoryEntry.builder()
                            .recordKey(buildRecordKey(record))
                            .action("UPSERT")
                            .sourceRefs(sourceRefsJson)
                            .build());

                    // 디버그용 지연
                    if (stepDelayMs > 0) {
                        Thread.sleep(stepDelayMs);
                    }

                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("Step interrupted");
                    break;
                } catch (Exception e) {
                    log.error("Failed to extract record: {}", recordKey, e);
                    skipCount++;
                    failedKeys.add(recordKey);
                    failedPkValues.add(recordKey);
                    if (firstError == null) {
                        firstError = e.getMessage();
                    }
                }
            }

            log.info("[{}] Loaded {} records to IF table '{}', {} skipped",
                    getStepId(), writeCount, config.getTargetIfTable(), skipCount);

            // 2-1. 이력 배치 저장
            if (historyService != null && !historyEntries.isEmpty()) {
                historyService.saveBatch(context.getExecutionId(), getStepId(),
                        config.getTargetIfTable(), historyEntries);
            }

            // 3. Source 테이블 link_status 업데이트
            // - skipSourceStatusUpdate=true: 외부 DB (RSV 등 VIEW라서 업데이트 불가)
            // - CUSTOM_STAGING: 커스텀 로직 사용 (RSV obsvdata)
            if (!config.isSkipSourceStatusUpdate() && !config.isCustomStaging()) {
                String sourceDsId = getSourceDatasourceId(context);
                JdbcTemplate sourceJdbc = dataSourceProvider.getJdbcTemplate(sourceDsId);
                String actualSourceTable = JdbcTableNameResolver.resolve(
                        sourceJdbc.getDataSource(), sourceDsId, config.getSourceTable());

                // 성공한 레코드: link_status = 'SUCCESS'
                if (!successPkValues.isEmpty()) {
                    int updated = updateSourceLinkStatus(sourceJdbc, actualSourceTable,
                            config.getPrimaryKeyColumn(), successPkValues, "SUCCESS");
                    log.info("[{}] Updated {} source records to SUCCESS", getStepId(), updated);
                }

                // 실패한 레코드: link_status = 'FAILED'
                if (!failedPkValues.isEmpty()) {
                    int updated = updateSourceLinkStatus(sourceJdbc, actualSourceTable,
                            config.getPrimaryKeyColumn(), failedPkValues, "FAILED");
                    log.info("[{}] Updated {} source records to FAILED", getStepId(), updated);
                }
            } else {
                log.info("[{}] Skipping source link_status update (skipSourceStatusUpdate={}, customStaging={})",
                        getStepId(), config.isSkipSourceStatusUpdate(), config.isCustomStaging());
            }

            // 4. SyncLog 요약 저장 (테이블별 1건씩)
            // Source 테이블: 읽은 건수 기록
            saveSyncLogSummary(context.getExecutionId(), config.getSourceTable(), "SOURCE",
                    (long) readCount, 0L, 0L, null, null);

            // IF 테이블: 쓴 건수 기록 (성공/실패)
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

            // SyncLog에 에러 정보 저장
            String errorMessage = e.getMessage();
            if (errorMessage != null && errorMessage.length() > 500) {
                errorMessage = errorMessage.substring(0, 500) + "...";
            }

            // Source 테이블: 읽은 건수 기록 (에러 발생 전까지 읽은 수)
            saveSyncLogSummary(context.getExecutionId(), config.getSourceTable(), "SOURCE",
                    (long) readCount, 0L, 0L, null, null);

            // IF 테이블: 에러 정보 저장 (성공/실패/에러 메시지)
            String failedKeysJson = failedKeys.isEmpty() ? null : String.join(",", failedKeys);
            saveSyncLogSummary(context.getExecutionId(), config.getTargetIfTable(), "IF",
                    (long) writeCount, (long) (readCount - writeCount), 0L, failedKeysJson, errorMessage);

            return StepResult.failed(getStepId(), e.getMessage(), System.currentTimeMillis() - startTime);
        }
    }

    /**
     * 컬럼 목록 결정: config에 있으면 사용, 없으면 source 테이블에서 자동 감지
     */
    private List<String> resolveColumns(StepContext context) {
        List<String> configColumns = config.getColumns();
        if (configColumns != null && !configColumns.isEmpty()) {
            log.info("[{}] Using configured columns: {}", getStepId(), configColumns);
            return configColumns;
        }

        // sourceTable이 설정되어 있으면 자동 감지 (SIMPLE_COPY, CUSTOM_STAGING 모두 지원)
        if (config.getSourceTable() != null && !config.getSourceTable().isBlank()) {
            String sourceDsId = getSourceDatasourceId(context);
            log.info("[{}] No columns configured, auto-detecting from source table '{}'",
                    getStepId(), config.getSourceTable());
            return fetchColumnsFromMetadata(sourceDsId, config.getSourceTable());
        }

        return List.of();
    }

    /**
     * UPSERT SQL 생성 (항상 ON CONFLICT DO UPDATE)
     * IF 테이블 메타 컬럼: source_refs, link_status, extracted_at, updated_at, execution_id
     *
     * 모든 실행에서 UPSERT 사용:
     * - 새 데이터: INSERT
     * - 기존 데이터: UPDATE (변경사항 반영)
     *
     * link_status는 레코드별로 다를 수 있어 placeholder 사용:
     * - PENDING: 일반 동기화
     * - RESYNC: 재동기화 (기간 지정 실행에서 설정)
     *
     * IF 테이블은 소문자 컬럼명 사용 (Source는 대문자일 수 있음)
     */
    private String buildUpsertSql(String actualTableName, List<String> columns) {
        StringBuilder sb = new StringBuilder();

        // IF 테이블 컬럼명은 소문자로 통일
        List<String> ifColumns = columns.stream()
                .map(String::toLowerCase)
                .toList();

        String columnList = String.join(", ", ifColumns);

        // INSERT 부분 (IF 테이블은 따옴표 없이 소문자)
        sb.append("INSERT INTO ").append(actualTableName.toLowerCase());
        sb.append(" (").append(columnList).append(", source_refs, link_status, extracted_at, updated_at, execution_id)");

        // link_status도 placeholder로 (레코드별로 다를 수 있음)
        String placeholders = columns.stream().map(c -> "?").reduce((a, b) -> a + ", " + b).orElse("");
        sb.append(" VALUES (").append(placeholders).append(", ?, ?, ?, ?, ?)");

        List<String> pkCols = config.getPrimaryKeyColumnList();
        if (!pkCols.isEmpty()) {
            String pkColList = pkCols.stream()
                    .map(String::toLowerCase)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");

            // 항상 UPSERT (ON CONFLICT DO UPDATE)
            sb.append(" ON CONFLICT (").append(pkColList).append(") DO UPDATE SET ");

            // PK가 아닌 컬럼들만 UPDATE
            List<String> updateCols = ifColumns.stream()
                    .filter(col -> pkCols.stream().noneMatch(pk -> pk.equalsIgnoreCase(col)))
                    .toList();

            List<String> updateParts = new java.util.ArrayList<>();
            for (String col : updateCols) {
                updateParts.add(col + " = EXCLUDED." + col);
            }
            // 메타 컬럼도 업데이트 (link_status는 EXCLUDED 값 사용)
            updateParts.add("source_refs = EXCLUDED.source_refs");
            updateParts.add("link_status = EXCLUDED.link_status");
            updateParts.add("updated_at = EXCLUDED.updated_at");
            updateParts.add("execution_id = EXCLUDED.execution_id");

            sb.append(String.join(", ", updateParts));
        }

        return sb.toString();
    }

    /**
     * Context에서 Source DataSource ID 조회 (context 우선, fallback to provider)
     */
    private String getSourceDatasourceId(StepContext context) {
        if (context.getSourceDatasourceId() != null) {
            return context.getSourceDatasourceId();
        }
        return dataSourceProvider.getSourceDatasourceId();
    }

    /**
     * Context에서 Target DataSource ID 조회 (context 우선, fallback to provider)
     */
    private String getTargetDatasourceId(StepContext context) {
        if (context.getTargetDatasourceId() != null) {
            return context.getTargetDatasourceId();
        }
        return dataSourceProvider.getTargetDatasourceId();
    }

    /**
     * SIMPLE_COPY 모드: 조건에 따라 데이터 조회
     *
     * - fullCopy=true: 전체 조회 (RSV 제원 등 외부 DB용)
     * - 기간 지정 실행: startTime/endTime 범위의 데이터 조회
     * - 일반 실행: link_status = 'PENDING' 또는 NULL인 데이터 조회
     *
     */
    private List<Map<String, Object>> fetchSimpleCopy(StepContext context, List<String> columns) {
        String sourceDsId = getSourceDatasourceId(context);
        JdbcTemplate sourceJdbc = dataSourceProvider.getJdbcTemplate(sourceDsId);

        // 실제 테이블명 조회 (대소문자 처리) - JdbcTableNameResolver 사용
        String actualSourceTable = JdbcTableNameResolver.resolve(
                sourceJdbc.getDataSource(), sourceDsId, config.getSourceTable());

        List<Map<String, Object>> records;

        // fullCopy 모드: 전체 조회 (시간 조건 없음)
        if (config.isFullCopy()) {
            String selectSql = buildFullCopySelectSql(actualSourceTable, columns);
            records = sourceJdbc.queryForList(selectSql);
            log.info("[{}] Full copy mode - Found {} records from source table '{}'",
                    getStepId(), records.size(), actualSourceTable);
        } else {
            LocalDateTime paramStartTime = context.getParam("startTime");
            LocalDateTime paramEndTime = context.getParam("endTime");

            if (paramStartTime != null && paramEndTime != null) {
                // 기간 지정 실행: 시간 범위로 조회
                log.info("[{}] Time-range execution: {} ~ {}", getStepId(), paramStartTime, paramEndTime);

                String selectSql = buildSelectSql(actualSourceTable, columns);
                records = sourceJdbc.queryForList(selectSql,
                        Timestamp.valueOf(paramStartTime), Timestamp.valueOf(paramEndTime));

                log.info("[{}] Found {} records from source table '{}' in range {} ~ {}",
                        getStepId(), records.size(), actualSourceTable, paramStartTime, paramEndTime);
            } else {
                // 일반 실행: link_status = 'PENDING' 또는 NULL 조회
                log.info("[{}] Normal execution: querying PENDING records", getStepId());

                String selectSql = buildPendingSelectSql(actualSourceTable, columns);
                records = sourceJdbc.queryForList(selectSql);

                log.info("[{}] Found {} PENDING records from source table '{}'",
                        getStepId(), records.size(), actualSourceTable);
            }
        }

        return records;
    }

    /**
     * PENDING/RESYNC 상태 조회용 SELECT SQL 생성
     * link_status = 'PENDING', 'RESYNC', 또는 NULL인 레코드 조회
     *
     * - PENDING: 일반 동기화 (새 데이터)
     * - RESYNC: 재동기화 필요 (기간 지정 실행으로 들어온 데이터, UPSERT 필요)
     */
    private String buildPendingSelectSql(String actualTableName, List<String> columns) {
        String columnList = columns.stream()
                .map(c -> "\"" + c + "\"")
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
        String quotedTable = "\"" + actualTableName + "\"";

        return String.format(
                "SELECT %s, \"link_status\" FROM %s WHERE \"link_status\" IS NULL OR \"link_status\" IN ('PENDING', 'RESYNC')",
                columnList, quotedTable);
    }

    /**
     * 전체 복사용 SELECT SQL (시간 조건 없음)
     */
    private String buildFullCopySelectSql(String actualTableName, List<String> columns) {
        String columnList = columns.stream()
                .map(c -> "\"" + c + "\"")
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
        String quotedTable = "\"" + actualTableName + "\"";
        return String.format("SELECT %s FROM %s", columnList, quotedTable);
    }

    /**
     * 동적 SELECT SQL 생성
     * PostgreSQL 대소문자 이슈 처리: 테이블명/컬럼명에 따옴표 추가
     * DATE + TIME 조합 지원: config.getTimeExpressionSql() 사용
     */
    private String buildSelectSql(String actualTableName, List<String> columns) {
        String columnList = columns.stream()
                .map(c -> "\"" + c + "\"")
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
        String quotedTable = "\"" + actualTableName + "\"";

        // 시간 표현식 생성 (dateColumn + timeColumn 또는 timeColumn만)
        String timeExpr = buildTimeExpression(columns);

        return String.format(
                "SELECT %s FROM %s WHERE %s > ? AND %s <= ? ORDER BY %s",
                columnList, quotedTable,
                timeExpr, timeExpr, timeExpr
        );
    }

    /**
     * 시간 표현식 생성 (DATE + TIME 조합 지원)
     */
    private String buildTimeExpression(List<String> columns) {
        // 1. 커스텀 표현식이 있으면 그대로 사용
        if (config.getTimeExpression() != null && !config.getTimeExpression().isBlank()) {
            return config.getTimeExpression();
        }

        // 2. dateColumn + timeColumn 조합
        if (config.getDateColumn() != null && !config.getDateColumn().isBlank()
                && config.getTimeColumn() != null && !config.getTimeColumn().isBlank()) {
            String actualDateCol = resolveActualColumnName(columns, config.getDateColumn());
            String actualTimeCol = resolveActualColumnName(columns, config.getTimeColumn());
            // PostgreSQL: DATE + TIME = TIMESTAMP
            return "(\"" + actualDateCol + "\" + \"" + actualTimeCol + "\")";
        }

        // 3. timeColumn만 사용
        if (config.getTimeColumn() != null && !config.getTimeColumn().isBlank()) {
            String actualTimeCol = resolveActualColumnName(columns, config.getTimeColumn());
            return "\"" + actualTimeCol + "\"";
        }

        throw new IllegalStateException("No time column configured. Set timeColumn, dateColumn+timeColumn, or timeExpression.");
    }

    /**
     * 컬럼 목록에서 실제 컬럼명 찾기 (대소문자 무시)
     */
    private String resolveActualColumnName(List<String> columns, String logicalColumnName) {
        return columns.stream()
                .filter(c -> c.equalsIgnoreCase(logicalColumnName))
                .findFirst()
                .orElse(logicalColumnName);  // 못 찾으면 원본 반환
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

    /**
     * Source 테이블의 실제 PK 컬럼 감지 (DatabaseMetaData.getPrimaryKeys)
     * sourceRef 생성에 사용 (UPSERT용 UK와 별개)
     *
     * @return PK 컬럼명 리스트 (감지 실패 시 config UK의 첫 번째 컬럼으로 fallback)
     */
    private List<String> detectSourcePrimaryKey(String datasourceId, String tableName) {
        List<String> pkColumns = new ArrayList<>();
        JdbcTemplate jdbc = dataSourceProvider.getJdbcTemplate(datasourceId);

        try {
            Connection conn = jdbc.getDataSource().getConnection();
            try {
                DatabaseMetaData metaData = conn.getMetaData();
                String[] variants = {tableName, tableName.toLowerCase(), tableName.toUpperCase()};

                for (String variant : variants) {
                    try (ResultSet rs = metaData.getPrimaryKeys(null, null, variant)) {
                        while (rs.next()) {
                            String colName = rs.getString("COLUMN_NAME");
                            int keySeq = rs.getInt("KEY_SEQ");
                            // KEY_SEQ 순서대로 정렬을 위해 위치에 맞게 삽입
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

        // 감지 실패 시 config UK의 첫 번째 컬럼으로 fallback
        if (pkColumns.isEmpty()) {
            List<String> ukCols = config.getPrimaryKeyColumnList();
            if (!ukCols.isEmpty()) {
                pkColumns.add(ukCols.get(0));
                log.info("[{}] PK detection failed, fallback to first UK column: {}", getStepId(), ukCols.get(0));
            }
        }

        return pkColumns;
    }

    /**
     * Source 테이블에서 컬럼 목록 자동 조회 (SIMPLE_COPY용)
     * columns 설정이 없을 때 메타데이터에서 가져옴
     */
    private List<String> fetchColumnsFromMetadata(String datasourceId, String tableName) {
        List<String> columns = new ArrayList<>();
        JdbcTemplate jdbc = dataSourceProvider.getJdbcTemplate(datasourceId);

        try {
            Connection conn = jdbc.getDataSource().getConnection();
            try {
                DatabaseMetaData metaData = conn.getMetaData();

                // 테이블명 변형 시도 (대소문자)
                String[] variants = {tableName, tableName.toLowerCase(), tableName.toUpperCase()};

                for (String variant : variants) {
                    try (ResultSet rs = metaData.getColumns(null, null, variant, null)) {
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
                    // Fallback: 실제 쿼리로 컬럼 가져오기
                    columns = fetchColumnsFromQuery(jdbc, tableName);
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

    /**
     * 쿼리 실행으로 컬럼 목록 가져오기 (메타데이터 실패 시 fallback)
     * PostgreSQL 대소문자 이슈 처리: 대소문자 변형 시도
     */
    private List<String> fetchColumnsFromQuery(JdbcTemplate jdbc, String tableName) {
        List<String> columns = new ArrayList<>();
        String[] variants = {tableName, tableName.toLowerCase(), tableName.toUpperCase()};

        for (String variant : variants) {
            try {
                String sql = String.format("SELECT * FROM \"%s\" WHERE 1=0", variant);
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

    /**
     * Source 테이블의 link_status 업데이트 (배치)
     *
     * @param jdbc JdbcTemplate
     * @param tableName 테이블명 (실제 테이블명, 대소문자 유지)
     * @param pkColumn PK 컬럼명
     * @param pkValues PK 값 목록
     * @param status 설정할 상태 (SUCCESS, FAILED)
     * @return 업데이트된 행 수
     */
    private int updateSourceLinkStatus(JdbcTemplate jdbc, String tableName,
                                        String pkColumn, List<Object> pkValues, String status) {
        if (pkValues.isEmpty()) return 0;

        // 배치 크기 제한 (너무 큰 IN 절 방지)
        int batchSize = 500;
        int totalUpdated = 0;

        for (int i = 0; i < pkValues.size(); i += batchSize) {
            List<Object> batch = pkValues.subList(i, Math.min(i + batchSize, pkValues.size()));

            String placeholders = batch.stream().map(v -> "?").reduce((a, b) -> a + ", " + b).orElse("");

            // PostgreSQL: 테이블명/컬럼명은 소문자로 저장됨 (따옴표 없이 사용 시)
            // Source 테이블은 link_status만 업데이트 (updated_at 컬럼이 없을 수 있음)
            String sql = String.format(
                    "UPDATE %s SET link_status = ? WHERE %s IN (%s)",
                    tableName.toLowerCase(),
                    pkColumn.toLowerCase(),
                    placeholders);

            log.debug("[{}] Update SQL: {}", getStepId(), sql);

            List<Object> params = new ArrayList<>();
            params.add(status);
            params.addAll(batch);

            try {
                int updated = jdbc.update(sql, params.toArray());
                totalUpdated += updated;
                log.debug("[{}] Batch updated {} records", getStepId(), updated);
            } catch (Exception e) {
                log.error("[{}] Failed to update source link_status: {}", getStepId(), e.getMessage(), e);
            }
        }

        return totalUpdated;
    }

    /**
     * 레코드의 비즈니스 키 생성 (PK 컬럼 값 기반)
     * 단일 PK: "GPM-3050-001"
     * 복합 PK: "GPM-3050-001|2026-01-15|093000"
     */
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

    /**
     * Record에서 값 조회 (대소문자 무시)
     */
    private Object getRecordValue(Map<String, Object> record, String columnName) {
        // 정확한 키로 먼저 시도
        if (record.containsKey(columnName)) {
            return record.get(columnName);
        }
        // 대소문자 무시하고 찾기
        for (Map.Entry<String, Object> entry : record.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(columnName)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
