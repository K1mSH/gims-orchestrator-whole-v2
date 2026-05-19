package com.infolink.agent.bojo.loader.step;

import com.infolink.agent.bojo.config.DynamicEntityManagerService;
import com.infolink.agent.bojo.entity.iftable.IfRsvSecObsvdata;
import com.infolink.agent.bojo.loader.repository.GimsTargetRepository;
import com.infolink.agent.common.controller.DataSourceProvider;
import com.infolink.agent.common.repository.SyncLogRepository;
import com.infolink.agent.common.service.IfTableService;
import com.infolink.agent.common.step.ConditionBuilder;
import com.infolink.agent.common.step.StepContext;
import com.infolink.agent.common.step.StepExecutor;
import com.infolink.agent.common.step.StepResult;
import com.infolink.agent.common.step.Status;
import com.infolink.agent.common.sync.SyncLogWriter;
import com.infolink.agent.common.sync.TableCountTracker;
import com.infolink.agent.common.util.SourceRefUtils;
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
    private final List<String> configSourceTables;
    private final List<String> configTargetTables;
    private final DataSourceProvider dataSourceProvider;
    private final SyncLogRepository syncLogRepository;
    private final IfTableService ifTableService;
    private final com.infolink.agent.bojo.config.DynamicEntityManagerService dynamicEmService;

    // EAV 항목 ID
    private static final int IEM_GWDEP = 5;       // 지하수위
    private static final int IEM_GWTEMP = 163;     // 지하수온도
    private static final int IEM_EC = 52;           // 전기전도도

    public InternalBojoLoadStep(String stepId, String stepName,
                            String ifObsvdataTable,
                            String targetJewonTable, String targetObsvdataTable,
                            String targetLinkTable, String targetResultTable,
                            List<String> configSourceTables, List<String> configTargetTables,
                            DataSourceProvider dataSourceProvider,
                            SyncLogRepository syncLogRepository,
                            IfTableService ifTableService,
                            com.infolink.agent.bojo.config.DynamicEntityManagerService dynamicEmService) {
        this.stepId = stepId;
        this.stepName = stepName;
        this.ifObsvdataTable = ifObsvdataTable;
        this.targetJewonTable = targetJewonTable;
        this.targetObsvdataTable = targetObsvdataTable;
        this.targetLinkTable = targetLinkTable;
        this.targetResultTable = targetResultTable;
        this.configSourceTables = configSourceTables;
        this.configTargetTables = configTargetTables;
        this.dataSourceProvider = dataSourceProvider;
        this.syncLogRepository = syncLogRepository;
        this.ifTableService = ifTableService;
        this.dynamicEmService = dynamicEmService;
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
        int inserted = 0;
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
            boolean isResyncExecution = ConditionBuilder.isResyncExecution(context.getExecutionOptions());

            // ===== 1. 사전 매핑 로드 (Target DB에서 READ) =====
            log.info("[{}] 1단계: Target DB에서 brnch_id / rslt_id 매핑 로드", getStepId());

            Map<String, Long> brnchIdMap = targetRepo.loadSpotIdMap(targetJewonTable);
            Map<String, Long> rsltIdMap = targetRepo.loadResultIdMap(targetResultTable);

            log.info("[{}] brnch_id {} 건, rslt_id {} 건 매핑 로드 완료",
                    getStepId(), brnchIdMap.size(), rsltIdMap.size());

            // TM_GD970101 신규 INSERT 추적용 row count (ensureResultId 가 신규 row 생성한 횟수 측정)
            int rsltCountBefore = targetJdbc.queryForObject(
                    "SELECT COUNT(*) FROM " + targetResultTable, Integer.class);

            if (brnchIdMap.isEmpty()) {
                log.warn("[{}] {}에서 brnch_id 매핑을 찾을 수 없음 - target 제원이 아직 등록되지 않았을 수 있음",
                        getStepId(), targetJewonTable);
            }

            // ===== 2. 관측데이터 처리 (EAV 1→3 확장) =====
            log.info("[{}] 2단계: 관측데이터 로드 (EAV 확장)", getStepId());

            String sourceDbType = dataSourceProvider.getDbType(sourceDsId);
            ConditionBuilder.WhereClause where = ConditionBuilder.buildIfTableQuery(
                    context.getExecutionOptions(), ifObsvdataTable, sourceDbType);
            String sql = "SELECT * FROM " + ifObsvdataTable + where.toWhereSql();
            List<Map<String, Object>> pendingObsv;
            javax.persistence.EntityManager sourceEm = dynamicEmService.getSourceEntityManager();
            try {
                javax.persistence.Query query = sourceEm.createNativeQuery(sql, IfRsvSecObsvdata.class);
                Object[] params = where.getParamsArray();
                for (int i = 0; i < params.length; i++) {
                    query.setParameter(i + 1, params[i]);
                }
                List<IfRsvSecObsvdata> entities = query.getResultList();
                pendingObsv = new ArrayList<>();
                for (IfRsvSecObsvdata e : entities) {
                    Map<String, Object> map = new HashMap<>();
                    java.lang.reflect.Field[] fields = e.getClass().getDeclaredFields();
                    for (java.lang.reflect.Field f : fields) {
                        f.setAccessible(true);
                        javax.persistence.Column col = f.getAnnotation(javax.persistence.Column.class);
                        String key = col != null ? col.name().toLowerCase() : f.getName();
                        try { map.put(key, f.get(e)); } catch (IllegalAccessException ex) { /* skip */ }
                    }
                    pendingObsv.add(map);
                }
            } finally {
                sourceEm.close();
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
                        long rsltGwdep = targetRepo.ensureResultId(targetResultTable, rsltIdMap, brnchId, IEM_GWDEP, executionId);
                        long rsltGwtemp = targetRepo.ensureResultId(targetResultTable, rsltIdMap, brnchId, IEM_GWTEMP, executionId);
                        long rsltEc = targetRepo.ensureResultId(targetResultTable, rsltIdMap, brnchId, IEM_EC, executionId);

                        // source_refs: IF 레코드의 id 참조 (표준 양식 zone:dsId:tbId:pk)
                        Object ifId = row.get("id");
                        String sourceRef = SourceRefUtils.buildJson(context, ifObsvdataTable, ifId);

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
                    inserted = targetRepo.batchInsertObsvdata(targetObsvdataTable, expandedRows);
                    writeCount += inserted;  // 5dfa203 패턴: 실 INSERT 행 (EAV 1:3 팽창 그대로)
                    log.info("[{}] EAV 관측데이터 {} 행 INSERT 완료 (IF 레코드 {} 건 기준, EAV 1:3 확장)",
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
                                dateTime[0], dateTime[1],   // frst_obsrvn_ymd, frst_obsrvn_hr (신규 INSERT 시만 사용)
                                executionId                 // execution_id (target 추적용)
                        });
                    }
                    linkUpdated = targetRepo.batchUpsertLink(targetLinkTable, linkRows);
                    log.info("[{}] Link {} 건 UPSERT 완료", getStepId(), linkUpdated);
                }
            }

            long durationMs = System.currentTimeMillis() - startTime;
            log.info("[{}] 완료: read={}, write={}, skip={}, duration={}ms",
                    getStepId(), readCount, writeCount, skipCount, durationMs);

            // TM_GD970101 신규 INSERT 횟수 = 처리 후 row count - 처리 전 row count
            int rsltCountAfter = targetJdbc.queryForObject(
                    "SELECT COUNT(*) FROM " + targetResultTable, Integer.class);
            int rsltInserted = rsltCountAfter - rsltCountBefore;

            // ===== 4. SyncLog 요약 저장 (Agent 정의 정합: 1 mapping, 3 target — per-target count 메타 포함) =====
            // write=inserted (실 INSERT 행, EAV 1:3 팽창 포함). Link 도 같은 mapping 의 target 으로 표시.
            // target_tables JSON 형식: [{"name":"...","count":N}] — 각 target 별 실 적재 카운트 분리.
            TableCountTracker targetCounts = new TableCountTracker(targetObsvdataTable, targetResultTable, targetLinkTable);
            targetCounts.add(targetObsvdataTable, inserted);     // PM_GD970201
            targetCounts.add(targetResultTable, rsltInserted);    // TM_GD970101
            targetCounts.add(targetLinkTable, linkUpdated);       // TM_GD980002

            SyncLogWriter.save(dataSourceProvider, executionId, getStepId(), "obsvdata",
                    configSourceTables != null ? configSourceTables : List.of(ifObsvdataTable),
                    targetCounts,
                    obsvReadCount, obsvFailed, 0L,
                    obsvFailedKeys.isEmpty() ? null : obsvFailedKeys,
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

            SyncLogWriter.save(dataSourceProvider, context.getExecutionId(), getStepId(), "obsvdata",
                    List.of(ifObsvdataTable), List.of(targetObsvdataTable),
                    obsvReadCount, inserted, obsvFailed, 0L,
                    obsvFailedKeys.isEmpty() ? null : obsvFailedKeys,
                    errorMessage);

            return StepResult.failed(getStepId(), e.getMessage(), System.currentTimeMillis() - startTime);
        }
    }

    // ==================== 헬퍼 메서드 ====================

    private Timestamp buildObsrvnDt(Object dateObj, Object timeObj) {
        LocalDate date;
        if (dateObj instanceof java.sql.Timestamp) {
            date = ((java.sql.Timestamp) dateObj).toLocalDateTime().toLocalDate();
        } else if (dateObj instanceof java.sql.Date) {
            date = ((java.sql.Date) dateObj).toLocalDate();
        } else if (dateObj instanceof LocalDateTime) {
            date = ((LocalDateTime) dateObj).toLocalDate();
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
        } else if (timeObj instanceof java.sql.Timestamp) {
            time = ((java.sql.Timestamp) timeObj).toLocalDateTime().toLocalTime();
        } else if (timeObj instanceof LocalDateTime) {
            time = ((LocalDateTime) timeObj).toLocalTime();
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
        } else if (dateObj instanceof LocalDateTime) {
            return ((LocalDateTime) dateObj).toLocalDate().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
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
        } else if (timeObj instanceof LocalDateTime) {
            return ((LocalDateTime) timeObj).toLocalTime().format(DateTimeFormatter.ofPattern("HHmmss"));
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

}
