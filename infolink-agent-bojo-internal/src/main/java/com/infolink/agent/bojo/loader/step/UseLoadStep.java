package com.infolink.agent.bojo.loader.step;

import com.infolink.agent.bojo.config.DynamicEntityManagerService;
import com.infolink.agent.bojo.entity.iftable.IfRsvUseLegacyData;
import com.infolink.agent.bojo.entity.iftable.IfRsvUseStatusData;
import com.infolink.agent.bojo.entity.target.TmGd111010;
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

import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 이용량 Loader Step (I5)
 *
 * 2개 IF 소스 → 4개 타겟:
 *   IF_RSV_USE_LEGACY_DATA → PM_GD111021(시간) + PM_GD111022(일집계) + TM_GD111024(수신현황)
 *   IF_RSV_USE_STATUS_DATA → TM_GD111025(관측데이터)
 *
 * 레거시 UseToIn.java + target.xml 로직 이식
 */
@Slf4j
public class UseLoadStep implements StepExecutor {

    private static final String IF_LEGACY = "IF_RSV_USE_LEGACY_DATA";
    private static final String IF_STATUS = "IF_RSV_USE_STATUS_DATA";

    // Target 테이블명 (per-target 처리건수 집계용)
    private static final String T_111021 = "PM_GD111021";  // 시간자료
    private static final String T_111022 = "PM_GD111022";  // 일집계
    private static final String T_111024 = "TM_GD111024";  // 최근수신현황
    private static final String T_111025 = "TM_GD111025";  // 관측데이터

    private final String stepId;
    private final String stepName;
    private final List<String> configSourceTables;
    private final List<String> configTargetTables;
    private final DataSourceProvider dataSourceProvider;
    private final SyncLogRepository syncLogRepository;
    private final IfTableService ifTableService;
    private final DynamicEntityManagerService dynamicEmService;

    // TELNO → BRNCH_ID 캐시
    private final Map<String, Long> brnchIdCache = new HashMap<>();

    public UseLoadStep(String stepId, String stepName,
                        List<String> configSourceTables, List<String> configTargetTables,
                        DataSourceProvider dataSourceProvider,
                        SyncLogRepository syncLogRepository,
                        IfTableService ifTableService,
                        DynamicEntityManagerService dynamicEmService) {
        this.stepId = stepId;
        this.stepName = stepName;
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
        int totalRead = 0;
        int totalWrite = 0;
        int totalSkip = 0;
        int totalFailed = 0;
        List<String> failedKeys = new ArrayList<>();
        String firstError = null;

        try {
            String sourceDsId = context.getSourceDatasourceId();
            String targetDsId = context.getTargetDatasourceId();
            JdbcTemplate sourceJdbc = dataSourceProvider.getJdbcTemplate(sourceDsId);
            JdbcTemplate targetJdbc = dataSourceProvider.getJdbcTemplate(targetDsId);
            String executionId = context.getExecutionId();
            String sourceDbType = dataSourceProvider.getDbType(sourceDsId);
            boolean isResync = ConditionBuilder.isResyncExecution(context.getExecutionOptions());

            // ===== Phase 1: Legacy Data → PM_GD111021 + PM_GD111022 + TM_GD111024 =====
            ConditionBuilder.WhereClause legacyWhere = ConditionBuilder.buildIfTableQuery(
                    context.getExecutionOptions(), IF_LEGACY, sourceDbType);
            String legacySql = "SELECT * FROM " + IF_LEGACY + legacyWhere.toWhereSql();
            EntityManager sourceEm = dynamicEmService.getSourceEntityManager();
            List<IfRsvUseLegacyData> legacyRows;
            try {
                Query legacyQuery = sourceEm.createNativeQuery(legacySql, IfRsvUseLegacyData.class);
                Object[] legacyParams = legacyWhere.getParamsArray();
                for (int i = 0; i < legacyParams.length; i++) {
                    legacyQuery.setParameter(i + 1, legacyParams[i]);
                }
                legacyRows = legacyQuery.getResultList();
            } finally {
                sourceEm.close();
            }
            totalRead += legacyRows.size();
            log.info("[{}] Legacy 데이터 {} 건 조회 ({})", stepId, legacyRows.size(),
                    isResync ? "재동기화" : "대기중");

            if (!legacyRows.isEmpty()) {
                LegacyResult lr = processLegacyData(legacyRows, targetJdbc,
                        context, executionId);
                totalWrite += lr.writeCount;
                totalSkip += lr.skipCount;
                totalFailed += lr.failedCount;
                failedKeys.addAll(lr.failedKeys);
                if (firstError == null && lr.firstError != null) firstError = lr.firstError;

                // IF 상태 업데이트
                if (!lr.successIds.isEmpty()) {
                    ifTableService.batchMarkAsProcessed(IF_LEGACY, "ID", lr.successIds, "SUCCESS", executionId);
                }
                if (!lr.failedIds.isEmpty()) {
                    ifTableService.batchMarkAsProcessed(IF_LEGACY, "ID", lr.failedIds, "FAILED", executionId);
                }

                // 후처리: PM_GD111022 일집계 + TM_GD111024 최근수신현황
                int dailyCount = 0;
                if (!lr.affectedBrnchDates.isEmpty()) {
                    dailyCount = updateDailyAggregation(targetJdbc, lr.affectedBrnchDates, executionId);
                    log.info("[{}] PM_GD111022 일집계 {} 건 처리", stepId, dailyCount);
                }
                int lastReceiveCount = 0;
                if (!lr.affectedBrnchIds.isEmpty()) {
                    lastReceiveCount = updateLastReceive(targetJdbc, executionId);
                    log.info("[{}] TM_GD111024 최근수신현황 {} 건 갱신", stepId, lastReceiveCount);
                }
                totalWrite += dailyCount + lastReceiveCount;

                // SyncLog: Legacy 매핑 (per-target 처리건수 포함)
                TableCountTracker legacyTargets = new TableCountTracker(T_111021, T_111022, T_111024);
                legacyTargets.add(T_111021, lr.writeCount);
                legacyTargets.add(T_111022, dailyCount);
                legacyTargets.add(T_111024, lastReceiveCount);
                SyncLogWriter.save(syncLogRepository, executionId, stepId, "use-legacy",
                        List.of(IF_LEGACY), legacyTargets,
                        legacyRows.size(), lr.failedCount, lr.skipCount, lr.failedKeys, lr.firstError);
            }

            // ===== Phase 2: Status Data → TM_GD111025 =====
            ConditionBuilder.WhereClause statusWhere = ConditionBuilder.buildIfTableQuery(
                    context.getExecutionOptions(), IF_STATUS, sourceDbType);
            String statusSql = "SELECT * FROM " + IF_STATUS + statusWhere.toWhereSql();
            List<IfRsvUseStatusData> statusRows;
            EntityManager sourceEm2 = dynamicEmService.getSourceEntityManager();
            try {
                Query statusQuery = sourceEm2.createNativeQuery(statusSql, IfRsvUseStatusData.class);
                Object[] statusParams = statusWhere.getParamsArray();
                for (int i = 0; i < statusParams.length; i++) {
                    statusQuery.setParameter(i + 1, statusParams[i]);
                }
                statusRows = statusQuery.getResultList();
            } finally {
                sourceEm2.close();
            }
            totalRead += statusRows.size();
            log.info("[{}] Status 데이터 {} 건 조회", stepId, statusRows.size());

            if (!statusRows.isEmpty()) {
                StatusResult sr = processStatusData(statusRows, targetJdbc, context, executionId);
                totalWrite += sr.writeCount;
                totalFailed += sr.failedCount;
                failedKeys.addAll(sr.failedKeys);
                if (firstError == null && sr.firstError != null) firstError = sr.firstError;

                if (!sr.successIds.isEmpty()) {
                    ifTableService.batchMarkAsProcessed(IF_STATUS, "ID", sr.successIds, "SUCCESS", executionId);
                }
                if (!sr.failedIds.isEmpty()) {
                    ifTableService.batchMarkAsProcessed(IF_STATUS, "ID", sr.failedIds, "FAILED", executionId);
                }

                // SyncLog: Status 매핑
                TableCountTracker statusTargets = new TableCountTracker(T_111025);
                statusTargets.add(T_111025, sr.writeCount);
                SyncLogWriter.save(syncLogRepository, executionId, stepId, "use-status",
                        List.of(IF_STATUS), statusTargets,
                        statusRows.size(), sr.failedCount, 0L, sr.failedKeys, sr.firstError);
            }

            long durationMs = System.currentTimeMillis() - startTime;
            log.info("[{}] 완료: read={}, write={}, skip={}, failed={}, duration={}ms",
                    stepId, totalRead, totalWrite, totalSkip, totalFailed, durationMs);

            return StepResult.builder()
                    .stepId(stepId)
                    .status(totalFailed > 0 && totalWrite == 0 ? Status.FAILED : Status.SUCCESS)
                    .readCount(totalRead)
                    .writeCount(totalWrite)
                    .skipCount(totalSkip)
                    .durationMs(durationMs)
                    .errorMessage(firstError)
                    .build();

        } catch (Exception e) {
            log.error("[{}] Step 실행 실패", stepId, e);
            SyncLogWriter.save(syncLogRepository, context.getExecutionId(), stepId, stepId,
                    List.of(IF_LEGACY, IF_STATUS), List.of(T_111021, T_111022, T_111024, T_111025),
                    totalRead, totalWrite, totalFailed, totalSkip, failedKeys, e.getMessage());
            return StepResult.failed(stepId, e.getMessage(), System.currentTimeMillis() - startTime);
        }
    }

    // ==================== Legacy 처리 ====================

    private LegacyResult processLegacyData(List<IfRsvUseLegacyData> rows,
                                            JdbcTemplate targetJdbc,
                                            StepContext context, String executionId) {
        LegacyResult result = new LegacyResult();

        for (IfRsvUseLegacyData row : rows) {
            Long ifId = row.getId();
            String telno = row.getTelno();

            try {
                Long brnchId = resolveBrnchId(telno);
                if (brnchId == null) {
                    log.warn("[{}] BRNCH_ID 미발견: telno={}, SKIP", stepId, telno);
                    result.skipCount++;
                    result.successIds.add(ifId);
                    continue;
                }

                String sourceRef = SourceRefUtils.buildJson(context, IF_LEGACY, ifId);

                // 음수 → 0 변환
                Number lastMeasure = row.getLastMeasureValue();
                Number usgqty = row.getUsgqty();
                if (usgqty != null && usgqty.doubleValue() < 0) {
                    usgqty = 0;
                }

                // PM_GD111021 MERGE (시간자료)
                targetJdbc.update(
                    "MERGE INTO PM_GD111021 t " +
                    "USING (SELECT ? AS BRNCH_ID, CAST(? AS DATE) AS OBSRVN_DT FROM DUAL) s " +
                    "ON (t.BRNCH_ID = s.BRNCH_ID AND t.OBSRVN_DT = s.OBSRVN_DT) " +
                    "WHEN MATCHED THEN UPDATE SET LAST_MSRMT_VL = ?, USE_QNT = ?, EXECUTION_ID = ?, SOURCE_REFS = ? " +
                    "WHEN NOT MATCHED THEN INSERT (BRNCH_ID, OBSRVN_DT, LAST_MSRMT_VL, USE_QNT, EXECUTION_ID, SOURCE_REFS) " +
                    "VALUES (?, ?, ?, ?, ?, ?)",
                    brnchId, row.getObsrDt(),
                    lastMeasure, usgqty, executionId, sourceRef,
                    brnchId, row.getObsrDt(), lastMeasure, usgqty, executionId, sourceRef
                );

                // 영향받은 (BRNCH_ID, 날짜) 기록 — 일집계용
                String ymd = toYmd(row.getObsrDt());
                if (ymd != null) {
                    result.affectedBrnchDates.add(brnchId + ":" + ymd);
                }
                result.affectedBrnchIds.add(brnchId);

                result.successIds.add(ifId);
                result.writeCount++;
            } catch (Exception e) {
                log.error("[{}] Legacy 처리 실패: telno={}", stepId, telno, e);
                result.failedCount++;
                result.failedIds.add(ifId);
                result.failedKeys.add(telno);
                if (result.firstError == null) result.firstError = e.getMessage();
            }
        }
        return result;
    }

    // ==================== 일집계 후처리 ====================

    private int updateDailyAggregation(JdbcTemplate jdbc, Set<String> brnchDates, String executionId) {
        int count = 0;
        for (String key : brnchDates) {
            String[] parts = key.split(":");
            long brnchId = Long.parseLong(parts[0]);
            String ymd = parts[1];

            try {
                jdbc.update(
                    "MERGE INTO PM_GD111022 t " +
                    "USING (" +
                    "  SELECT BRNCH_ID, TO_CHAR(OBSRVN_DT, 'YYYYMMDD') AS OBSRVN_YMD, " +
                    "         MAX(LAST_MSRMT_VL) AS LAST_MSRMT_VL, SUM(USE_QNT) AS USE_QNT " +
                    "  FROM PM_GD111021 " +
                    "  WHERE BRNCH_ID = ? AND TO_CHAR(OBSRVN_DT, 'YYYYMMDD') = ? " +
                    "  GROUP BY BRNCH_ID, TO_CHAR(OBSRVN_DT, 'YYYYMMDD')" +
                    ") s ON (t.BRNCH_ID = s.BRNCH_ID AND t.OBSRVN_YMD = s.OBSRVN_YMD) " +
                    "WHEN MATCHED THEN UPDATE SET LAST_MSRMT_VL = s.LAST_MSRMT_VL, USE_QNT = s.USE_QNT, EXECUTION_ID = ? " +
                    "WHEN NOT MATCHED THEN INSERT (BRNCH_ID, OBSRVN_YMD, LAST_MSRMT_VL, USE_QNT, EXECUTION_ID) " +
                    "VALUES (s.BRNCH_ID, s.OBSRVN_YMD, s.LAST_MSRMT_VL, s.USE_QNT, ?)",
                    brnchId, ymd, executionId, executionId
                );
                count++;
            } catch (Exception e) {
                log.warn("[{}] 일집계 실패: brnch_id={}, ymd={}", stepId, brnchId, ymd, e);
            }
        }
        return count;
    }

    // ==================== 최근수신현황 후처리 ====================

    private int updateLastReceive(JdbcTemplate jdbc, String executionId) {
        return jdbc.update(
            "MERGE INTO TM_GD111024 t " +
            "USING (SELECT BRNCH_ID, MAX(OBSRVN_DT) AS OBSRVN_DT FROM PM_GD111021 GROUP BY BRNCH_ID) s " +
            "ON (t.BRNCH_ID = s.BRNCH_ID) " +
            "WHEN MATCHED THEN UPDATE SET OBSRVN_DT = s.OBSRVN_DT, EXECUTION_ID = ? " +
            "WHEN NOT MATCHED THEN INSERT (BRNCH_ID, OBSRVN_DT, EXECUTION_ID) VALUES (s.BRNCH_ID, s.OBSRVN_DT, ?)",
            executionId, executionId
        );
    }

    // ==================== Status 처리 ====================

    private StatusResult processStatusData(List<IfRsvUseStatusData> rows,
                                            JdbcTemplate targetJdbc,
                                            StepContext context, String executionId) {
        StatusResult result = new StatusResult();

        for (IfRsvUseStatusData row : rows) {
            Long ifId = row.getId();
            Long sn = row.getSn();

            try {
                String sourceRef = SourceRefUtils.buildJson(context, IF_STATUS, ifId);

                targetJdbc.update(
                    "INSERT INTO TM_GD111025 (SN, TELNO, OBSRVN_DT, LAST_CHG_DT, EXECUTION_ID, SOURCE_REFS) " +
                    "VALUES (?, ?, ?, ?, ?, ?)",
                    sn, row.getTelno(), row.getObsrDt(), row.getLastChangeDt(), executionId, sourceRef
                );

                result.successIds.add(ifId);
                result.writeCount++;
            } catch (Exception e) {
                log.error("[{}] Status 처리 실패: sn={}", stepId, sn, e);
                result.failedCount++;
                result.failedIds.add(ifId);
                result.failedKeys.add(String.valueOf(sn));
                if (result.firstError == null) result.firstError = e.getMessage();
            }
        }
        return result;
    }

    // ==================== 조회 + 캐시 ====================

    private Long resolveBrnchId(String telno) {
        return brnchIdCache.computeIfAbsent(telno, t -> {
            EntityManager em = dynamicEmService.getTargetEntityManager();
            try {
                List<TmGd111010> results = em.createQuery(
                        "SELECT e FROM TmGd111010 e WHERE e.telno = :telno", TmGd111010.class)
                        .setParameter("telno", t)
                        .getResultList();
                return results.isEmpty() ? null : results.get(0).getBrnchId();
            } catch (Exception e) {
                return null;
            } finally {
                em.close();
            }
        });
    }

    // ==================== 헬퍼 ====================

    private String toYmd(Object val) {
        if (val == null) return null;
        String str = val.toString().replace("-", "").replace("/", "");
        return str.length() >= 8 ? str.substring(0, 8) : null;
    }

    // ==================== 결과 클래스 ====================

    private static class LegacyResult {
        int writeCount = 0;
        int skipCount = 0;
        int failedCount = 0;
        List<Object> successIds = new ArrayList<>();
        List<Object> failedIds = new ArrayList<>();
        List<String> failedKeys = new ArrayList<>();
        String firstError = null;
        Set<String> affectedBrnchDates = new HashSet<>();
        Set<Long> affectedBrnchIds = new HashSet<>();
    }

    private static class StatusResult {
        int writeCount = 0;
        int failedCount = 0;
        List<Object> successIds = new ArrayList<>();
        List<Object> failedIds = new ArrayList<>();
        List<String> failedKeys = new ArrayList<>();
        String firstError = null;
    }
}
