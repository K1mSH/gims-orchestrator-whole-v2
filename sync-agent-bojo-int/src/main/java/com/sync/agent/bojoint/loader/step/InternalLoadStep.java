package com.sync.agent.bojoint.loader.step;

import com.sync.agent.bojoint.loader.repository.GimsTargetRepository;
import com.sync.agent.common.controller.DataSourceProvider;
import com.sync.agent.common.entity.SyncLog;
import com.sync.agent.common.repository.SyncLogRepository;
import com.sync.agent.common.service.IfTableService;
import com.sync.agent.common.step.ConditionBuilder;
import com.sync.agent.common.step.ExecutionCondition;
import com.sync.agent.common.step.ExecutionOptions;
import com.sync.agent.common.step.StepContext;
import com.sync.agent.common.step.StepExecutor;
import com.sync.agent.common.step.StepResult;
import com.sync.agent.common.step.Status;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 내부망 GIMS 적재 Step
 *
 * IF_RSV → GIMS Target 적재:
 * - 제원(tm_gd970001)은 GIMS 서비스가 자체 관리하는 마스터 데이터
 *   → Loader는 READ ONLY (spot_id 매핑용)
 *   → 외부 업체 제원은 IF_RSV에서 끊김
 * - 관측데이터: IF_RSV PENDING 읽기 → EAV 확장 (1→3행) → batch INSERT
 * - 시간설정 실행 시: 해당 구간 UPDATE
 * - Link 테이블: obsv_code별 MAX(date,time) UPSERT
 */
@Slf4j
public class InternalLoadStep implements StepExecutor {

    private final String stepId;
    private final String stepName;
    private final String ifObsvdataTable;
    private final String targetJewonTable;
    private final String targetObsvdataTable;
    private final String targetLinkTable;
    private final String targetResultTable;
    private final DataSourceProvider dataSourceProvider;
    private final SyncLogRepository syncLogRepository;
    private final IfTableService ifTableService;

    // EAV 항목 ID
    private static final int IEM_GWDEP = 5;       // 지하수위
    private static final int IEM_GWTEMP = 163;     // 지하수온도
    private static final int IEM_EC = 52;           // 전기전도도

    public InternalLoadStep(String stepId, String stepName,
                            String ifObsvdataTable,
                            String targetJewonTable, String targetObsvdataTable,
                            String targetLinkTable, String targetResultTable,
                            DataSourceProvider dataSourceProvider,
                            SyncLogRepository syncLogRepository,
                            IfTableService ifTableService) {
        this.stepId = stepId;
        this.stepName = stepName;
        this.ifObsvdataTable = ifObsvdataTable;
        this.targetJewonTable = targetJewonTable;
        this.targetObsvdataTable = targetObsvdataTable;
        this.targetLinkTable = targetLinkTable;
        this.targetResultTable = targetResultTable;
        this.dataSourceProvider = dataSourceProvider;
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

        int obsvSuccess = 0, obsvFailed = 0;
        int linkUpdated = 0;
        List<String> obsvFailedKeys = new ArrayList<>();
        String obsvFirstError = null;
        int obsvReadCount = 0;

        try {
            String sourceDsId = context.getSourceDatasourceId();
            String targetDsId = context.getTargetDatasourceId();

            JdbcTemplate sourceJdbc = dataSourceProvider.getJdbcTemplate(sourceDsId);
            JdbcTemplate targetJdbc = dataSourceProvider.getJdbcTemplate(targetDsId);
            GimsTargetRepository targetRepo = new GimsTargetRepository(targetJdbc);

            String executionId = context.getExecutionId();

            // 조건실행 판별
            ExecutionOptions options = context.getExecutionOptions();
            boolean isResyncExecution = options.hasConditions() || options.isTimeRangeExecution();

            // 통합 조건 빌드
            List<ExecutionCondition> mergedConditions = buildMergedConditions(options);

            if (isResyncExecution) {
                log.info("[{}] Resync execution: {} conditions", getStepId(), mergedConditions.size());
                for (ExecutionCondition c : mergedConditions) {
                    log.info("[{}]   condition: {} {} {}", getStepId(), c.getColumn(), c.getOperator(), c.getValue());
                }
            }

            // ===== 1. 사전 매핑 로드 (Target DB에서 READ) =====
            log.info("[{}] Phase 1: Loading spot_id / result_id mappings from Target DB", getStepId());

            Map<String, Long> spotIdMap = targetRepo.loadSpotIdMap(targetJewonTable);
            Map<String, Long> resultIdMap = targetRepo.loadResultIdMap(targetResultTable);

            log.info("[{}] Loaded {} spot_id, {} result_id mappings",
                    getStepId(), spotIdMap.size(), resultIdMap.size());

            if (spotIdMap.isEmpty()) {
                log.warn("[{}] No spot_id mappings found in {} - target jewon may not be populated yet",
                        getStepId(), targetJewonTable);
            }

            // ===== 2. 관측데이터 처리 (EAV 1→3 확장) =====
            log.info("[{}] Phase 2: Loading obsvdata (EAV expansion)", getStepId());

            List<Map<String, Object>> pendingObsv;
            String sourceDbType = dataSourceProvider.getDbType(sourceDsId);
            if (isResyncExecution) {
                // Resync 실행: 통합 조건으로 조회
                ConditionBuilder.WhereClause where = ConditionBuilder.build(mergedConditions, sourceDbType);
                String sql = "SELECT * FROM " + ifObsvdataTable + where.toWhereSql();
                pendingObsv = sourceJdbc.queryForList(sql, where.getParamsArray());
            } else {
                // 기본 실행: PENDING 또는 RESYNC
                pendingObsv = sourceJdbc.queryForList(
                        "SELECT * FROM " + ifObsvdataTable + " WHERE link_status IN ('PENDING', 'RESYNC')");
            }

            obsvReadCount = pendingObsv.size();
            readCount += obsvReadCount;
            log.info("[{}] Read {} {} obsvdata records from IF_RSV", getStepId(), obsvReadCount,
                    isResyncExecution ? "resync (conditions)" : "pending");

            if (!pendingObsv.isEmpty()) {
                List<Object[]> expandedRows = new ArrayList<>();
                List<Object> obsvSuccessIds = new ArrayList<>();
                List<Object> obsvFailedIds = new ArrayList<>();

                // obsv_code별 max(date, time) 추적 (Link 갱신용)
                Map<String, String[]> maxDateTimePerCode = new HashMap<>();

                for (Map<String, Object> row : pendingObsv) {
                    String obsvCode = (String) row.get("obsv_code");
                    Object obsvDateObj = row.get("obsv_date");
                    Object obsvTimeObj = row.get("obsv_time");

                    try {
                        Long spotId = spotIdMap.get(obsvCode);
                        if (spotId == null) {
                            log.warn("[{}] No spot_id for obsv_code={}, skipping", getStepId(), obsvCode);
                            obsvFailed++;
                            obsvFailedIds.add(row.get("id"));
                            obsvFailedKeys.add(obsvCode);
                            if (obsvFirstError == null) obsvFirstError = "No spot_id for obsv_code=" + obsvCode;
                            skipCount++;
                            continue;
                        }

                        // obsrvn_dt 계산
                        Timestamp obsrvnDt = buildObsrvnDt(obsvDateObj, obsvTimeObj);

                        // EAV 확장: 1 레코드 → 3 행
                        Double gwdep = toDouble(row.get("gwdep"));
                        Double gwtemp = toDouble(row.get("gwtemp"));
                        Double ec = toDouble(row.get("ec"));

                        // result_id 확보 (없으면 자동 생성)
                        long resultGwdep = targetRepo.ensureResultId(targetResultTable, resultIdMap, spotId, IEM_GWDEP);
                        long resultGwtemp = targetRepo.ensureResultId(targetResultTable, resultIdMap, spotId, IEM_GWTEMP);
                        long resultEc = targetRepo.ensureResultId(targetResultTable, resultIdMap, spotId, IEM_EC);

                        // source_refs: IF 레코드의 id 참조
                        Object ifId = row.get("id");
                        String sourceRef = String.format("[\"I:%s:%s:%s\"]", sourceDsId, ifObsvdataTable, ifId);

                        // 3행 생성 (값이 있는 항목만)
                        if (gwdep != null) {
                            expandedRows.add(new Object[]{resultGwdep, gwdep, obsrvnDt, 1, executionId, sourceRef});
                        }
                        if (gwtemp != null) {
                            expandedRows.add(new Object[]{resultGwtemp, gwtemp, obsrvnDt, 1, executionId, sourceRef});
                        }
                        if (ec != null) {
                            expandedRows.add(new Object[]{resultEc, ec, obsrvnDt, 1, executionId, sourceRef});
                        }

                        obsvSuccessIds.add(row.get("id"));
                        obsvSuccess++;

                        // Link 갱신용 max(date, time) 추적
                        String dateStr = formatDateStr(obsvDateObj);
                        String timeStr = formatTimeStr(obsvTimeObj);
                        updateMaxDateTime(maxDateTimePerCode, obsvCode, dateStr, timeStr);

                    } catch (Exception e) {
                        log.error("Failed to process obsvdata: obsv_code={}", obsvCode, e);
                        obsvFailed++;
                        obsvFailedIds.add(row.get("id"));
                        obsvFailedKeys.add(obsvCode);
                        if (obsvFirstError == null) obsvFirstError = e.getMessage();
                        skipCount++;
                    }
                }

                // batch INSERT
                if (!expandedRows.isEmpty()) {
                    int inserted = targetRepo.batchInsertObsvdata(targetObsvdataTable, expandedRows);
                    writeCount = obsvSuccess;  // 논리적 레코드 수 기준 (EAV 확장 행 수가 아닌 IF 처리 건수)
                    log.info("[{}] Inserted {} EAV obsvdata rows (from {} IF records)",
                            getStepId(), inserted, obsvSuccess);
                }

                // IF 상태 업데이트
                if (!obsvSuccessIds.isEmpty()) {
                    ifTableService.batchMarkAsProcessed(ifObsvdataTable, "id", obsvSuccessIds, "SUCCESS", executionId);
                }
                if (!obsvFailedIds.isEmpty()) {
                    ifTableService.batchMarkAsProcessed(ifObsvdataTable, "id", obsvFailedIds, "FAILED", executionId);
                }

                // ===== 3. Link 테이블 갱신 (batch UPSERT) =====
                // Resync 실행 시 Link 갱신 스킵 (과거 데이터로 덮어쓰기 방지)
                if (isResyncExecution) {
                    log.info("[{}] Phase 3: Link table update SKIPPED (resync execution)", getStepId());
                } else {
                    log.info("[{}] Phase 3: Batch upserting link table ({} codes)", getStepId(), maxDateTimePerCode.size());

                    Timestamp now = Timestamp.valueOf(LocalDateTime.now());
                    List<Object[]> linkRows = new ArrayList<>();
                    for (Map.Entry<String, String[]> entry : maxDateTimePerCode.entrySet()) {
                        String obsvCode = entry.getKey();
                        String[] dateTime = entry.getValue();
                        Long spotId = spotIdMap.get(obsvCode);
                        if (spotId == null) continue;

                        linkRows.add(new Object[]{
                                obsvCode, spotId,
                                dateTime[0], dateTime[1],  // last_obsrvn_de, last_obsrvn_time
                                now,                        // change_dt
                                dateTime[0], dateTime[1]    // frst_date, frst_time (신규 INSERT 시만 사용)
                        });
                    }
                    linkUpdated = targetRepo.batchUpsertLink(targetLinkTable, linkRows);
                    log.info("[{}] Upserted {} link records", getStepId(), linkUpdated);
                }
            }

            long durationMs = System.currentTimeMillis() - startTime;
            log.info("[{}] Completed: read={}, write={}, skip={}, duration={}ms",
                    getStepId(), readCount, writeCount, skipCount, durationMs);

            // ===== 4. SyncLog 요약 저장 (매핑 단위, link 제외) =====
            saveSyncLogMapping(executionId, "obsvdata",
                    List.of(ifObsvdataTable),
                    List.of(targetObsvdataTable),
                    (long) obsvReadCount, (long) obsvSuccess, (long) obsvFailed, 0L,
                    obsvFailedKeys.isEmpty() ? null : String.join(",", obsvFailedKeys),
                    obsvFirstError);

            return StepResult.builder()
                    .stepId(getStepId())
                    .status(Status.SUCCESS)
                    .readCount(readCount)
                    .writeCount(writeCount)
                    .skipCount(skipCount)
                    .durationMs(durationMs)
                    .build();

        } catch (Exception e) {
            log.error("Step execution failed", e);

            String errorMessage = e.getMessage();
            if (errorMessage != null && errorMessage.length() > 500) {
                errorMessage = errorMessage.substring(0, 500) + "...";
            }

            saveSyncLogMapping(context.getExecutionId(), "obsvdata",
                    List.of(ifObsvdataTable),
                    List.of(targetObsvdataTable),
                    (long) obsvReadCount, (long) obsvSuccess, (long) obsvFailed, 0L,
                    obsvFailedKeys.isEmpty() ? null : String.join(",", obsvFailedKeys),
                    errorMessage);

            return StepResult.failed(getStepId(), e.getMessage(), System.currentTimeMillis() - startTime);
        }
    }

    // ==================== 조건 빌드 ====================

    /**
     * ExecutionOptions에서 통합 조건 목록 생성
     */
    private List<ExecutionCondition> buildMergedConditions(ExecutionOptions options) {
        List<ExecutionCondition> merged = new ArrayList<>();

        // 1. 사용자 동적 조건
        if (options.hasConditions()) {
            merged.addAll(options.getConditions());
        }

        // 2. 시간 범위 → obsv_date 조건
        if (options.isTimeRangeExecution()) {
            LocalDateTime start = options.getTimeRange().getStartTime();
            LocalDateTime end = options.getTimeRange().getEndTime();
            if (start != null && end != null) {
                merged.add(ExecutionCondition.between("obsv_date",
                        start.toLocalDate().toString(), end.toLocalDate().toString()));
            } else if (start != null) {
                merged.add(ExecutionCondition.gte("obsv_date", start.toLocalDate().toString()));
            }
        }

        // 3. obsv-code 파라미터 → obsv_code 조건
        String filterObsvCode = options.getParamValue("obsv-code");
        if (filterObsvCode != null) {
            if (filterObsvCode.contains(",")) {
                merged.add(ExecutionCondition.in("obsv_code", filterObsvCode));
            } else {
                merged.add(ExecutionCondition.eq("obsv_code", filterObsvCode.trim()));
            }
        }

        return merged;
    }

    // ==================== 헬퍼 메서드 ====================

    private Timestamp buildObsrvnDt(Object dateObj, Object timeObj) {
        LocalDate date;
        if (dateObj instanceof java.sql.Date) {
            date = ((java.sql.Date) dateObj).toLocalDate();
        } else if (dateObj instanceof LocalDate) {
            date = (LocalDate) dateObj;
        } else if (dateObj instanceof String) {
            date = LocalDate.parse((String) dateObj);
        } else {
            throw new IllegalArgumentException("Unsupported date type: " + (dateObj != null ? dateObj.getClass() : "null"));
        }

        LocalTime time = LocalTime.MIDNIGHT;
        if (timeObj instanceof java.sql.Time) {
            time = ((java.sql.Time) timeObj).toLocalTime();
        } else if (timeObj instanceof LocalTime) {
            time = (LocalTime) timeObj;
        } else if (timeObj instanceof String) {
            String ts = (String) timeObj;
            if (ts.length() == 6) {
                time = LocalTime.parse(ts, DateTimeFormatter.ofPattern("HHmmss"));
            } else {
                time = LocalTime.parse(ts);
            }
        }

        return Timestamp.valueOf(LocalDateTime.of(date, time));
    }

    private String formatDateStr(Object dateObj) {
        if (dateObj instanceof java.sql.Date) {
            return ((java.sql.Date) dateObj).toLocalDate().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        } else if (dateObj instanceof LocalDate) {
            return ((LocalDate) dateObj).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        } else if (dateObj instanceof String) {
            return ((String) dateObj).replace("-", "");
        }
        return null;
    }

    private String formatTimeStr(Object timeObj) {
        if (timeObj instanceof java.sql.Time) {
            return ((java.sql.Time) timeObj).toLocalTime().format(DateTimeFormatter.ofPattern("HHmmss"));
        } else if (timeObj instanceof LocalTime) {
            return ((LocalTime) timeObj).format(DateTimeFormatter.ofPattern("HHmmss"));
        } else if (timeObj instanceof String) {
            return ((String) timeObj).replace(":", "");
        }
        return null;
    }

    private void updateMaxDateTime(Map<String, String[]> map, String obsvCode,
                                    String dateStr, String timeStr) {
        String[] existing = map.get(obsvCode);
        if (existing == null) {
            map.put(obsvCode, new String[]{dateStr, timeStr});
            return;
        }

        String existingKey = (existing[0] != null ? existing[0] : "") + (existing[1] != null ? existing[1] : "");
        String newKey = (dateStr != null ? dateStr : "") + (timeStr != null ? timeStr : "");
        if (newKey.compareTo(existingKey) > 0) {
            map.put(obsvCode, new String[]{dateStr, timeStr});
        }
    }

    private Double toDouble(Object val) {
        if (val == null) return null;
        if (val instanceof Number) return ((Number) val).doubleValue();
        try {
            return Double.parseDouble(val.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void saveSyncLogMapping(String executionId, String mappingName,
                                     List<String> sourceTables, List<String> targetTables,
                                     Long readCount, Long writeCount, Long failedCount, Long skipCount,
                                     String failedKeys, String errorSummary) {
        try {
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
        } catch (Exception e) {
            log.warn("Failed to save SyncLog for mapping {}: {}", mappingName, e.getMessage());
        }
    }
}
