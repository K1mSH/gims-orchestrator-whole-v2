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
 * Internal Loader Step (내부망 GIMS 적재)
 *
 * IF_RSV → GIMS Target 적재:
 * - 제원(tm_gd970001)은 GIMS 서비스가 자체 관리하는 마스터 데이터
 *   → Loader는 READ ONLY (brnch_id 매핑용)
 *   → 외부 업체 제원은 IF_RSV에서 끊김
 * - 관측데이터: IF_RSV PENDING 읽기 → EAV 확장 (1→3행) → batch INSERT
 * - 시간설정 실행 시: 해당 구간 UPDATE
 * - Link 테이블: obsv_code별 MAX(date,time) UPSERT
 */
@Slf4j
public class InternalBojoLoadStep implements StepExecutor {

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

    public InternalBojoLoadStep(String stepId, String stepName,
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
            String targetDbType = dataSourceProvider.getDbType(targetDsId);
            GimsTargetRepository targetRepo = new GimsTargetRepository(targetJdbc, targetDbType);

            String executionId = context.getExecutionId();

            // 조건실행 판별
            ExecutionOptions options = context.getExecutionOptions();
            boolean isResyncExecution = options.hasConditions() || options.isTimeRangeExecution();

            // 통합 조건 빌드 (tableName 필터링: obsvdata 테이블 대상만)
            List<ExecutionCondition> mergedConditions = buildMergedConditions(options, ifObsvdataTable);

            if (isResyncExecution) {
                log.info("[{}] 재동기화 실행: {} 조건", getStepId(), mergedConditions.size());
                for (ExecutionCondition c : mergedConditions) {
                    log.info("[{}]   조건: {} {} {}", getStepId(), c.getColumn(), c.getOperator(), c.getValue());
                }
            }

            // ===== 1. 사전 매핑 로드 (Target DB에서 READ) =====
            log.info("[{}] 1단계: Target DB에서 brnch_id / rslt_id 매핑 로드", getStepId());

            Map<String, Long> brnchIdMap = targetRepo.loadSpotIdMap(targetJewonTable);
            Map<String, Long> rsltIdMap = targetRepo.loadResultIdMap(targetResultTable);

            log.info("[{}] brnch_id {} 건, rslt_id {} 건 매핑 로드 완료",
                    getStepId(), brnchIdMap.size(), rsltIdMap.size());

            if (brnchIdMap.isEmpty()) {
                log.warn("[{}] {}에서 brnch_id 매핑을 찾을 수 없음 - target 제원이 아직 등록되지 않았을 수 있음",
                        getStepId(), targetJewonTable);
            }

            // ===== 2. 관측데이터 처리 (EAV 1→3 확장) =====
            log.info("[{}] 2단계: 관측데이터 로드 (EAV 확장)", getStepId());

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
            log.info("[{}] IF_RSV에서 {} 건의 {} 관측데이터 조회", getStepId(), obsvReadCount,
                    isResyncExecution ? "재동기화 (조건)" : "대기중");

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
                        Long brnchId = brnchIdMap.get(obsvCode);
                        if (brnchId == null) {
                            log.warn("[{}] obsv_code={}에 대한 brnch_id 없음, 건너뜀", getStepId(), obsvCode);
                            obsvFailed++;
                            obsvFailedIds.add(row.get("id"));
                            obsvFailedKeys.add(obsvCode);
                            if (obsvFirstError == null) obsvFirstError = "obsv_code=" + obsvCode + "에 대한 brnch_id 없음";
                            skipCount++;
                            continue;
                        }

                        // obsrvn_dt 계산
                        Timestamp obsrvnDt = buildObsrvnDt(obsvDateObj, obsvTimeObj);

                        // EAV 확장: 1 레코드 → 3 행
                        Double gwdep = toDouble(row.get("gwdep"));
                        Double gwtemp = toDouble(row.get("gwtemp"));
                        Double ec = toDouble(row.get("ec"));

                        // rslt_id 확보 (없으면 자동 생성)
                        long rsltGwdep = targetRepo.ensureResultId(targetResultTable, rsltIdMap, brnchId, IEM_GWDEP);
                        long rsltGwtemp = targetRepo.ensureResultId(targetResultTable, rsltIdMap, brnchId, IEM_GWTEMP);
                        long rsltEc = targetRepo.ensureResultId(targetResultTable, rsltIdMap, brnchId, IEM_EC);

                        // source_refs: IF 레코드의 id 참조
                        Object ifId = row.get("id");
                        String sourceRef = String.format("[\"I:%s:%s:%s\"]", sourceDsId, ifObsvdataTable, ifId);

                        // 3행 생성 (값이 있는 항목만)
                        if (gwdep != null) {
                            expandedRows.add(new Object[]{rsltGwdep, gwdep, obsrvnDt, 1, executionId, sourceRef});
                        }
                        if (gwtemp != null) {
                            expandedRows.add(new Object[]{rsltGwtemp, gwtemp, obsrvnDt, 1, executionId, sourceRef});
                        }
                        if (ec != null) {
                            expandedRows.add(new Object[]{rsltEc, ec, obsrvnDt, 1, executionId, sourceRef});
                        }

                        obsvSuccessIds.add(row.get("id"));
                        obsvSuccess++;

                        // Link 갱신용 max(date, time) 추적
                        String dateStr = formatDateStr(obsvDateObj);
                        String timeStr = formatTimeStr(obsvTimeObj);
                        updateMaxDateTime(maxDateTimePerCode, obsvCode, dateStr, timeStr);

                    } catch (Exception e) {
                        log.error("[{}] 관측데이터 처리 실패: obsv_code={}", getStepId(), obsvCode, e);
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
                    log.info("[{}] EAV 관측데이터 {} 행 INSERT 완료 (IF 레코드 {} 건 기준)",
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
                    log.info("[{}] 3단계: Link 테이블 갱신 건너뜀 (재동기화 실행)", getStepId());
                } else {
                    log.info("[{}] 3단계: Link 테이블 배치 UPSERT ({} 건)", getStepId(), maxDateTimePerCode.size());

                    Timestamp now = Timestamp.valueOf(LocalDateTime.now());
                    List<Object[]> linkRows = new ArrayList<>();
                    for (Map.Entry<String, String[]> entry : maxDateTimePerCode.entrySet()) {
                        String obsvCode = entry.getKey();
                        String[] dateTime = entry.getValue();
                        Long brnchId = brnchIdMap.get(obsvCode);
                        if (brnchId == null) continue;

                        linkRows.add(new Object[]{
                                obsvCode, brnchId,
                                dateTime[0], dateTime[1],  // last_obsrvn_ymd, last_obsrvn_hr
                                now,                        // chg_dt
                                dateTime[0], dateTime[1]    // frst_obsrvn_ymd, frst_obsrvn_hr (신규 INSERT 시만 사용)
                        });
                    }
                    linkUpdated = targetRepo.batchUpsertLink(targetLinkTable, linkRows);
                    log.info("[{}] Link {} 건 UPSERT 완료", getStepId(), linkUpdated);
                }
            }

            long durationMs = System.currentTimeMillis() - startTime;
            log.info("[{}] 완료: read={}, write={}, skip={}, duration={}ms",
                    getStepId(), readCount, writeCount, skipCount, durationMs);

            // ===== 4. SyncLog 요약 저장 (매핑 단위, link 제외) =====
            saveSyncLogMapping(executionId, "obsvdata",
                    List.of(ifObsvdataTable),
                    List.of(targetObsvdataTable),
                    (long) obsvReadCount, (long) obsvSuccess, (long) obsvFailed, 0L,
                    obsvFailedKeys.isEmpty() ? null : String.join(",", obsvFailedKeys),
                    obsvFirstError);

            // read>0인데 write=0이면 실패 처리 (제원 매칭 실패 등)
            if (readCount > 0 && writeCount == 0) {
                String failMessage = String.format("적재 실패: read=%d건 중 write=0건 (제원 매칭 실패 등)", readCount);
                log.error("[{}] {}", getStepId(), failMessage);
                return StepResult.builder()
                        .stepId(getStepId())
                        .status(Status.FAILED)
                        .readCount(readCount)
                        .writeCount(writeCount)
                        .skipCount(skipCount)
                        .durationMs(durationMs)
                        .errorMessage(failMessage)
                        .build();
            }

            return StepResult.builder()
                    .stepId(getStepId())
                    .status(Status.SUCCESS)
                    .readCount(readCount)
                    .writeCount(writeCount)
                    .skipCount(skipCount)
                    .durationMs(durationMs)
                    .build();

        } catch (Exception e) {
            log.error("[{}] Step 실행 실패", getStepId(), e);

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
     * ExecutionOptions에서 통합 조건 목록 생성 (tableName 필터링 포함)
     */
    private List<ExecutionCondition> buildMergedConditions(ExecutionOptions options, String targetTable) {
        List<ExecutionCondition> merged = new ArrayList<>();

        // 1. 사용자 동적 조건 (tableName 필터링)
        if (options.hasConditions()) {
            options.getConditions().stream()
                    .filter(c -> c.getTableName() == null || c.getTableName().isEmpty()
                            || c.getTableName().equalsIgnoreCase(targetTable))
                    .forEach(merged::add);
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
        if (dateObj instanceof java.sql.Timestamp) {
            date = ((java.sql.Timestamp) dateObj).toLocalDateTime().toLocalDate();
        } else if (dateObj instanceof java.sql.Date) {
            date = ((java.sql.Date) dateObj).toLocalDate();
        } else if (dateObj instanceof LocalDate) {
            date = (LocalDate) dateObj;
        } else if (dateObj instanceof String) {
            date = LocalDate.parse((String) dateObj);
        } else {
            throw new IllegalArgumentException("지원하지 않는 날짜 타입: " + (dateObj != null ? dateObj.getClass() : "null"));
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
        if (dateObj instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) dateObj).toLocalDateTime().toLocalDate().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        } else if (dateObj instanceof java.sql.Date) {
            return ((java.sql.Date) dateObj).toLocalDate().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        } else if (dateObj instanceof LocalDate) {
            return ((LocalDate) dateObj).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        } else if (dateObj instanceof String) {
            return ((String) dateObj).replace("-", "");
        }
        return null;
    }

    private String formatTimeStr(Object timeObj) {
        if (timeObj instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) timeObj).toLocalDateTime().toLocalTime().format(DateTimeFormatter.ofPattern("HHmmss"));
        } else if (timeObj instanceof java.sql.Time) {
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
            log.warn("[{}] SyncLog 저장 실패 (매핑: {}): {}", getStepId(), mappingName, e.getMessage());
        }
    }
}
