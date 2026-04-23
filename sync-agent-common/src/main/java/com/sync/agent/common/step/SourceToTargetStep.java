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
 * Source → Target 복사 Step — 모든 Agent의 기본 카피 클래스
 *
 * Source 테이블에서 데이터를 조회(Extract)하여 Target 테이블에 적재(Load)하는 공통 Step.
 * Agent 유형(RCV / SND / Internal RCV / Provide)과 무관하게
 * "외부 DB에서 데이터 가져와 자체 관리 테이블로 옮기기" 패턴은 모두 이 Step 하나로 처리한다.
 *
 * ── 사용처 ──
 *  - RCV: 외부 업체 DB → IF_RSV (bojo/others)
 *  - SND: Target → IF_SND (bojo)
 *  - Internal RCV: IF_SND → 내부 IF_RSV (bojo-int)
 *  - Provide: Oracle 원본 → PG 제공 테이블 (provide)
 *  - 새 Agent 추가 시에도 이 Step을 재사용하는 것이 원칙
 *
 * 커스텀 변환·PIVOT·JOIN 등 복잡한 로직이 필요한 경우만 별도 Step 작성
 * (예: JejuJewonLoadStep, LinkTableUpdateStep, DefaultLoadStep).
 * 단순 1:1 복사는 반드시 이 Step 재사용.
 *
 * ── Factory 매핑 ──
 *  - SourceToTargetStepFactory (factory-key: source-to-if) — SIMPLE_COPY 기본 생성
 *  - LinkSourceToIfStepFactory (bojo) — CUSTOM_STAGING 모드 + LinkTableObsvDataFetcher
 *  (※ factory-key="source-to-if"는 기존 YAML 호환을 위해 유지, 별도 이슈에서 "source-to-target"으로 마이그레이션 예정)
 *
 * ── 두 가지 모드 ──
 * 1. SIMPLE_COPY: 직접 SQL 조회 (fetchSimpleCopy)
 *    - 전체 복사 (fullCopy=true) 또는 link_status 기반 증분
 *    - 조건실행(conditions) / 시간범위(timeRange) 실행 시 강제 적용
 * 2. CUSTOM_STAGING: 커스텀 DataFetcher로 데이터 조회
 *    - Link 테이블(link_ngwis) 기반 증분 동기화 (RCV obsvdata)
 *    - conditions/timeRange 있으면 SIMPLE_COPY로 오버라이드됨
 *
 * ── 타겟 메타 컬럼 처리 ──
 * 타겟이 IF 테이블(bojo/bojo-int): source_refs, link_status, extracted_at, updated_at, execution_id (5종)
 * 타겟이 제공 테이블(provide): source_refs, execution_id, updated_at (3종)
 * ExtractStepConfig.targetMetaColumns 로 커스터마이징.
 *
 * ── 주요 기능 ──
 * - Source 데이터 조회 (위 두 모드 중 하나)
 * - Target 테이블에 배치 UPSERT (ON CONFLICT DO UPDATE / MERGE INTO, batchSize=1000)
 * - source_refs 추적 정보 자동 생성 (zone:dsDbId:tableId:pk 형식, PK는 JDBC metadata로 탐지)
 * - link_status 관리 (PENDING/RESYNC/SUCCESS/FAILED) — 타겟에 해당 컬럼 있을 때만
 * - Source 테이블 link_status 업데이트 (성공/실패)
 * - SyncLog 요약 저장 (매핑별 성공/실패 건수)
 */
@Slf4j
public class SourceToTargetStep implements StepExecutor {

    // 타겟 메타 컬럼 기본값 (IF 표준) — Config.targetMetaColumns 로 오버라이드 가능
    // 소스에서 추출한 비즈니스 컬럼에서 제외할 목록으로도 사용 (soure에 동일 컬럼명이 있어도 타겟 메타로 덮어쓰기)
    private static final List<String> TARGET_META_COLUMNS_DEFAULT = List.of(
            "source_refs", "link_status", "extracted_at", "updated_at", "execution_id"
    );

    // 제외 컬럼 기본값은 ExtractStepConfig.DEFAULT_EXCLUDE_INSERT_COLUMNS 사용 (["id", "sn"])
    // 실제 제외는 "기본값(or YAML 명시) ∩ 타겟에 실제로 있는 컬럼" 교집합만.
    // 이 로직은 SourceToTargetStep.resolveExcludeInsertColumns() 에서 처리.

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

    public SourceToTargetStep(
            ExtractStepConfig config,
            DataSourceProvider dataSourceProvider,
            SyncLogRepository syncLogRepository) {
        this(config, dataSourceProvider, syncLogRepository, 0, DEFAULT_BATCH_SIZE);
    }

    public SourceToTargetStep(
            ExtractStepConfig config,
            DataSourceProvider dataSourceProvider,
            SyncLogRepository syncLogRepository,
            long stepDelayMs) {
        this(config, dataSourceProvider, syncLogRepository, stepDelayMs, DEFAULT_BATCH_SIZE);
    }

    public SourceToTargetStep(
            ExtractStepConfig config,
            DataSourceProvider dataSourceProvider,
            SyncLogRepository syncLogRepository,
            int batchSize) {
        this(config, dataSourceProvider, syncLogRepository, 0, batchSize);
    }

    public SourceToTargetStep(
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

    private static boolean isOracle(String dbType) {
        return "ORACLE".equalsIgnoreCase(dbType) || "TIBERO".equalsIgnoreCase(dbType);
    }

    /**
     * source DB에서 읽은 값을 target DB 타입에 맞게 변환
     * - java.sql.Time → String (target이 VARCHAR일 때 epoch 변환 방지)
     * - java.sql.Clob → String
     */
    private static Object convertParamForTarget(Object value, String targetDbType) {
        if (value == null) return null;

        // Time → Oracle/Tibero는 문자열 변환 (VARCHAR2 호환), PG/MySQL은 그대로 유지
        if (value instanceof java.sql.Time) {
            if (isOracle(targetDbType)) {
                return value.toString();
            }
            return value;
        }

        // Clob → String
        if (value instanceof java.sql.Clob) {
            try {
                java.sql.Clob clob = (java.sql.Clob) value;
                return clob.getSubString(1, (int) clob.length());
            } catch (Exception e) {
                return value.toString();
            }
        }

        return value;
    }

    private static String castToText(String column, boolean isMysqlDb, boolean isOracleDb) {
        if (isMysqlDb) return "CAST(" + column + " AS CHAR)";
        if (isOracleDb) return "TO_CHAR(" + column + ")";
        return column + "::text";
    }

    private static String qi(String name, String dbType) {
        if (isMysql(dbType)) return "`" + name + "`";
        if (isOracle(dbType)) return name;  // Oracle: 인용 없이 (자동 대문자)
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

            // SELECT용 컬럼: 타겟 메타 컬럼 제외 (id 포함 → source_refs PK 추출에 필요)
            // 기본 제외: TARGET_META_COLUMNS_DEFAULT (IF 표준 5종) — 어떤 타겟이든 이 이름 컬럼은 메타로 취급
            List<String> selectColumns = allColumns.stream()
                    .filter(c -> TARGET_META_COLUMNS_DEFAULT.stream().noneMatch(meta -> meta.equalsIgnoreCase(c)))
                    .toList();

            // INSERT용 컬럼: 제외 후보 ∩ 타겟 실제 컬럼 교집합만 제외
            // (타겟에 없으면 애초에 INSERT에 들어가지도 않으므로 제외할 필요 없음;
            //  타겟에 있을 때만 auto-increment 충돌 우려 → 제외)
            List<String> excludeActual = resolveExcludeInsertColumns(context);
            List<String> columns = selectColumns.stream()
                    .filter(c -> excludeActual.stream().noneMatch(ex -> ex.equalsIgnoreCase(c)))
                    .toList();
            log.info("[{}] Columns: all={}, select={}, insert={} (excluded from INSERT: {})",
                    getStepId(), allColumns.size(), selectColumns.size(), columns.size(), excludeActual);

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

            // 제외된 컬럼에 비즈니스 값이 있으면 경고 (auto-increment 아닐 수 있음)
            warnIfExcludedColumnsHaveValues(records, excludeActual);

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

            // 2. Target 테이블에 배치 UPSERT 적재
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

            // 타겟 메타 컬럼 리스트 (Config 기반, 기본값=IF 표준 5종)
            List<String> metaColumnsLower = config.getTargetMetaColumnList().stream()
                    .map(String::toLowerCase)
                    .toList();

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
                    params.add(convertParamForTarget(record.get(column), targetDbType));
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

                // link_status 결정 (타겟에 없어도 변수만 계산 — 실제 INSERT 여부는 metaColumns 따라)
                Object sourceLinkStatus = getRecordValue(record, "link_status");
                String recordLinkStatus;
                if (isResyncExecution) {
                    recordLinkStatus = "RESYNC";
                } else if ("RESYNC".equals(sourceLinkStatus)) {
                    recordLinkStatus = "RESYNC";
                } else {
                    recordLinkStatus = "PENDING";
                }

                Timestamp now = Timestamp.valueOf(LocalDateTime.now());

                // 메타 컬럼 값을 Config 순서대로 추가 (buildUpsertSql 의 allColumns 순서와 일치)
                for (String meta : metaColumnsLower) {
                    switch (meta) {
                        case "source_refs":
                            params.add(sourceRefsJson);
                            break;
                        case "link_status":
                            params.add(recordLinkStatus);
                            break;
                        case "extracted_at":
                            params.add(now);
                            break;
                        case "updated_at":
                            params.add(now);
                            break;
                        case "execution_id":
                            params.add(context.getExecutionId());
                            break;
                        default:
                            log.warn("[{}] Unknown target meta column: {}", getStepId(), meta);
                            params.add(null);
                    }
                }

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

            log.info("[{}] Loaded {} records to target table '{}', {} skipped (batch size: {})",
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

    // ==================== INSERT 제외 컬럼 해석 ====================

    /** 타겟 컬럼 메타 조회 결과 캐시 (Step 인스턴스 life-cycle 동안 1회만 조회) */
    private volatile List<String> cachedTargetColumnsLower;

    /**
     * INSERT에서 제외할 컬럼 목록 결정
     *
     * 결정 로직:
     *  1. Config 의 excludeInsertColumns (기본값 ["id","sn"]) 을 후보로
     *  2. 타겟 테이블의 실제 컬럼 메타 조회
     *  3. 후보 ∩ 타겟 컬럼 교집합만 최종 제외
     *
     * → 타겟에 없는 이름은 제외 불필요 (애초에 INSERT에 안 들어감),
     *   타겟에 있을 때만 auto-increment 충돌 우려 → 제외 적용.
     *   메타 조회 실패 시 안전하게 빈 리스트 반환 (아무것도 제외 안 함).
     */
    private List<String> resolveExcludeInsertColumns(StepContext context) {
        List<String> candidates = config.getExcludeInsertColumnList();
        String source = config.isExcludeInsertColumnsExplicit() ? "yaml" : "default";

        if (candidates == null || candidates.isEmpty()) {
            log.info("[{}] exclude-insert-columns: candidates=[] (source={}) → no exclusion",
                    getStepId(), source);
            return List.of();
        }

        List<String> targetCols = getTargetColumnsLower(context);
        if (targetCols.isEmpty()) {
            log.warn("[{}] exclude-insert-columns: target metadata unavailable, candidates={} (source={}) → skip exclusion (safe)",
                    getStepId(), candidates, source);
            return List.of();
        }

        List<String> actual = candidates.stream()
                .filter(c -> targetCols.contains(c.toLowerCase()))
                .toList();
        log.info("[{}] exclude-insert-columns: candidates={} (source={}), target matches={} → final exclude={}",
                getStepId(), candidates, source, actual, actual);
        return actual;
    }

    /**
     * 제외 컬럼이 실제 소스 레코드에 비즈니스 값을 가지고 있는지 샘플 체크
     * (값이 있으면 auto-increment가 아닌 비즈니스 컬럼일 가능성 → 데이터 누락 의심)
     * 첫 번째 non-null 값만 WARN 1회.
     */
    private void warnIfExcludedColumnsHaveValues(List<Map<String, Object>> records, List<String> excludeActual) {
        if (records.isEmpty() || excludeActual.isEmpty()) return;
        for (String ex : excludeActual) {
            for (Map<String, Object> record : records) {
                Object val = getRecordValue(record, ex);
                if (val != null) {
                    log.warn("[{}] Excluded column '{}' has non-null source value (sample={}). " +
                             "If this is business data (not auto-increment), " +
                             "set exclude-insert-columns: [] in YAML to include it.",
                            getStepId(), ex, val);
                    break;  // 컬럼당 WARN 1회
                }
            }
        }
    }

    /** 타겟 테이블 컬럼명(lowercase) 목록 — Step 인스턴스 내부 캐시 */
    private List<String> getTargetColumnsLower(StepContext context) {
        if (cachedTargetColumnsLower != null) {
            return cachedTargetColumnsLower;
        }
        synchronized (this) {
            if (cachedTargetColumnsLower != null) {
                return cachedTargetColumnsLower;
            }
            try {
                String targetDsId = getTargetDatasourceId(context);
                List<String> cols = fetchColumnsFromMetadata(targetDsId, config.getTargetIfTable());
                cachedTargetColumnsLower = cols.stream()
                        .map(String::toLowerCase)
                        .toList();
            } catch (Exception e) {
                log.warn("[{}] Failed to fetch target columns for exclude check: {}",
                        getStepId(), e.getMessage());
                cachedTargetColumnsLower = List.of();
            }
            return cachedTargetColumnsLower;
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
     *
     * 타겟 메타 컬럼은 Config.getTargetMetaColumnList() 에서 결정.
     *  - 기본(IF): source_refs, link_status, extracted_at, updated_at, execution_id
     *  - provide: source_refs, execution_id, updated_at (link_status/extracted_at 없음)
     *
     * UPDATE SET 대상은 extracted_at 제외 (최초 추출 시간 보존).
     *
     * @param useUpsert true: ON CONFLICT DO UPDATE (fullCopy 또는 RESYNC) - 전체 컬럼 갱신
     *                  false: 메타 경량 UPDATE (증분 동기화) - updated_at, execution_id만 갱신
     */
    private String buildUpsertSql(String actualTableName, List<String> columns, String dbType, boolean useUpsert) {
        List<String> bizColumns = columns.stream()
                .map(String::toLowerCase)
                .toList();

        // 충돌 기준: conflictKey 설정 시 해당 컬럼 사용, 없으면 primaryKey 사용
        List<String> pkCols = config.getPrimaryKeyColumnList();
        List<String> conflictCols = (config.getConflictKey() != null && !config.getConflictKey().isEmpty())
                ? List.of(config.getConflictKey())
                : pkCols;

        // 타겟 메타 컬럼 (Config 기반, 기본값=IF 표준 5종)
        List<String> metaColumns = config.getTargetMetaColumnList().stream()
                .map(String::toLowerCase)
                .toList();

        // 전체 컬럼 (데이터 + 메타)
        List<String> allColumns = new java.util.ArrayList<>(bizColumns);
        for (String meta : metaColumns) {
            if (!allColumns.contains(meta)) {
                allColumns.add(meta);
            }
        }

        if (isOracle(dbType) && !conflictCols.isEmpty()) {
            return buildOracleMergeSql(actualTableName, bizColumns, allColumns, metaColumns, conflictCols, useUpsert);
        }

        // PG / MySQL: INSERT 기반
        StringBuilder sb = new StringBuilder();
        String columnList = String.join(", ", allColumns);

        sb.append("INSERT INTO ").append(actualTableName.toLowerCase());
        sb.append(" (").append(columnList).append(")");

        String placeholders = allColumns.stream().map(c -> "?").reduce((a, b) -> a + ", " + b).orElse("");
        sb.append(" VALUES (").append(placeholders).append(")");

        if (!conflictCols.isEmpty()) {
            String conflictColList = conflictCols.stream()
                    .map(String::toLowerCase)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");

            // UPDATE 대상 메타 컬럼 — extracted_at 제외 (최초 추출 시간 보존)
            List<String> metaUpdateCols = metaColumns.stream()
                    .filter(m -> !"extracted_at".equalsIgnoreCase(m))
                    .toList();

            if (isMysql(dbType)) {
                sb.append(" ON DUPLICATE KEY UPDATE ");

                if (useUpsert) {
                    List<String> updateCols = bizColumns.stream()
                            .filter(col -> conflictCols.stream().noneMatch(ck -> ck.equalsIgnoreCase(col)))
                            .toList();

                    List<String> updateParts = new java.util.ArrayList<>();
                    for (String col : updateCols) {
                        updateParts.add(col + " = VALUES(" + col + ")");
                    }
                    // 메타 업데이트 (conflict key 제외)
                    for (String meta : metaUpdateCols) {
                        if (conflictCols.stream().noneMatch(ck -> ck.equalsIgnoreCase(meta))) {
                            updateParts.add(meta + " = VALUES(" + meta + ")");
                        }
                    }

                    sb.append(String.join(", ", updateParts));
                } else {
                    // 증분 경량: updated_at, execution_id 만 갱신
                    List<String> lightParts = new java.util.ArrayList<>();
                    for (String meta : List.of("updated_at", "execution_id")) {
                        if (metaColumns.contains(meta)) {
                            lightParts.add(meta + " = VALUES(" + meta + ")");
                        }
                    }
                    sb.append(String.join(", ", lightParts));
                }
            } else {
                // PostgreSQL
                sb.append(" ON CONFLICT (").append(conflictColList).append(") DO UPDATE SET ");

                if (useUpsert) {
                    List<String> updateCols = bizColumns.stream()
                            .filter(col -> conflictCols.stream().noneMatch(ck -> ck.equalsIgnoreCase(col)))
                            .toList();

                    List<String> updateParts = new java.util.ArrayList<>();
                    for (String col : updateCols) {
                        updateParts.add(col + " = EXCLUDED." + col);
                    }
                    // 메타 업데이트 (conflict key 제외)
                    for (String meta : metaUpdateCols) {
                        if (conflictCols.stream().noneMatch(ck -> ck.equalsIgnoreCase(meta))) {
                            updateParts.add(meta + " = EXCLUDED." + meta);
                        }
                    }

                    sb.append(String.join(", ", updateParts));
                } else {
                    // 증분 경량: updated_at, execution_id 만 갱신
                    List<String> lightParts = new java.util.ArrayList<>();
                    for (String meta : List.of("updated_at", "execution_id")) {
                        if (metaColumns.contains(meta)) {
                            lightParts.add(meta + " = EXCLUDED." + meta);
                        }
                    }
                    sb.append(String.join(", ", lightParts));
                }
            }
        }

        return sb.toString();
    }

    /**
     * Oracle MERGE INTO SQL 생성
     *
     * MERGE INTO table t
     * USING (SELECT ? AS col1, ? AS col2, ... FROM DUAL) s
     * ON (t.conflict_col = s.conflict_col)
     * WHEN MATCHED THEN UPDATE SET t.col = s.col, ...
     * WHEN NOT MATCHED THEN INSERT (col1, ...) VALUES (s.col1, ...)
     *
     * 파라미터 바인딩은 USING SELECT에서 한 번만 → INSERT/UPDATE 양쪽에서 별칭(s.col)으로 참조
     */
    private String buildOracleMergeSql(String tableName, List<String> bizColumns,
                                        List<String> allColumns, List<String> metaColumns,
                                        List<String> conflictCols, boolean useUpsert) {
        StringBuilder sb = new StringBuilder();

        // MERGE INTO table t
        sb.append("MERGE INTO ").append(tableName).append(" t ");

        // USING (SELECT ? AS col1, ? AS col2, ... FROM DUAL) s
        sb.append("USING (SELECT ");
        sb.append(allColumns.stream()
                .map(c -> "? AS " + c)
                .reduce((a, b) -> a + ", " + b)
                .orElse(""));
        sb.append(" FROM DUAL) s ");

        // ON (t.conflict_col = s.conflict_col AND ...)
        sb.append("ON (");
        sb.append(conflictCols.stream()
                .map(c -> "t." + c.toLowerCase() + " = s." + c.toLowerCase())
                .reduce((a, b) -> a + " AND " + b)
                .orElse(""));
        sb.append(") ");

        // UPDATE 대상 메타 컬럼 — extracted_at 제외 (최초 추출 시간 보존)
        List<String> metaUpdateCols = metaColumns.stream()
                .filter(m -> !"extracted_at".equalsIgnoreCase(m))
                .toList();

        // WHEN MATCHED THEN UPDATE SET
        sb.append("WHEN MATCHED THEN UPDATE SET ");
        if (useUpsert) {
            // full UPSERT: 모든 데이터 컬럼 + 메타 컬럼 갱신
            List<String> updateParts = new java.util.ArrayList<>();
            for (String col : bizColumns) {
                if (conflictCols.stream().noneMatch(ck -> ck.equalsIgnoreCase(col))) {
                    updateParts.add("t." + col + " = s." + col);
                }
            }
            for (String meta : metaUpdateCols) {
                if (conflictCols.stream().noneMatch(ck -> ck.equalsIgnoreCase(meta))) {
                    updateParts.add("t." + meta + " = s." + meta);
                }
            }
            sb.append(String.join(", ", updateParts));
        } else {
            // 증분: updated_at, execution_id 만 경량 UPDATE
            List<String> lightParts = new java.util.ArrayList<>();
            for (String meta : List.of("updated_at", "execution_id")) {
                if (metaColumns.contains(meta)) {
                    lightParts.add("t." + meta + " = s." + meta);
                }
            }
            sb.append(String.join(", ", lightParts));
        }

        // WHEN NOT MATCHED THEN INSERT
        sb.append(" WHEN NOT MATCHED THEN INSERT (");
        sb.append(String.join(", ", allColumns));
        sb.append(") VALUES (");
        sb.append(allColumns.stream()
                .map(c -> "s." + c)
                .reduce((a, b) -> a + ", " + b)
                .orElse(""));
        sb.append(")");

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
            if (isOracle(dbType)) {
                // Oracle: DATE + VARCHAR2(HHmmss) → TIMESTAMP 조합
                return "TO_TIMESTAMP(TO_CHAR(" + qi(actualDateCol, dbType) + ", 'YYYYMMDD') || ' ' || "
                        + qi(actualTimeCol, dbType) + ", 'YYYYMMDD HH24MISS')";
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

        // "SCHEMA.TABLE" 지원 — schema 분리해서 metaData.getPrimaryKeys 에 전달
        JdbcTableNameResolver.TableRef ref = JdbcTableNameResolver.parse(tableName);

        try {
            Connection conn = jdbc.getDataSource().getConnection();
            try {
                DatabaseMetaData metaData = conn.getMetaData();
                String catalog = conn.getCatalog();
                String[] tableVariants = {ref.table, ref.table.toLowerCase(), ref.table.toUpperCase()};
                String[] schemaVariants = (ref.schema != null)
                        ? new String[]{ref.schema, ref.schema.toLowerCase(), ref.schema.toUpperCase()}
                        : new String[]{null};

                outer:
                for (String schemaVar : schemaVariants) {
                    for (String tableVar : tableVariants) {
                        try (ResultSet rs = metaData.getPrimaryKeys(catalog, schemaVar, tableVar)) {
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
                            log.info("[{}] Detected source PK from metadata: {} (schema={}, table={})",
                                    getStepId(), pkColumns, schemaVar, tableVar);
                            break outer;
                        }
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

        // "SCHEMA.TABLE" 지원
        JdbcTableNameResolver.TableRef ref = JdbcTableNameResolver.parse(tableName);

        try {
            Connection conn = jdbc.getDataSource().getConnection();
            try {
                DatabaseMetaData metaData = conn.getMetaData();
                String catalog = conn.getCatalog();

                String[] tableVariants = {ref.table, ref.table.toLowerCase(), ref.table.toUpperCase()};
                String[] schemaVariants = (ref.schema != null)
                        ? new String[]{ref.schema, ref.schema.toLowerCase(), ref.schema.toUpperCase()}
                        : new String[]{null};

                outer:
                for (String schemaVar : schemaVariants) {
                    for (String tableVar : tableVariants) {
                        try (ResultSet rs = metaData.getColumns(catalog, schemaVar, tableVar, null)) {
                            while (rs.next()) {
                                String columnName = rs.getString("COLUMN_NAME");
                                columns.add(columnName);
                            }
                        }
                        if (!columns.isEmpty()) {
                            log.info("[{}] Auto-detected {} columns from table '{}' (schema={}, table={}): {}",
                                    getStepId(), columns.size(), tableName, schemaVar, tableVar, columns);
                            break outer;
                        }
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
        boolean isMysqlDb = isMysql(dbType);
        boolean isOracleDb = isOracle(dbType);

        List<String> pkCols = List.of(pkColumn.split(","));
        boolean isCompositePk = pkCols.size() > 1;

        int updateBatchSize = 500;
        int totalUpdated = 0;

        if (!isCompositePk) {
            // 단일 PK: IN 절 사용 (컬럼을 text 캐스팅)
            String colExpr = castToText(pkColumn.trim().toLowerCase(), isMysqlDb, isOracleDb);

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
                    .map(c -> castToText(c.toLowerCase(), isMysqlDb, isOracleDb))
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
