package com.infolink.agent.common.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infolink.agent.common.controller.DataSourceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * sync_log 한 행(매핑/step 단위) 저장의 단일 진입점.
 *
 * <p>적재 위치 = {@code dsp.getTargetDatasourceId()} (sync_log = agent.target 룰).
 * 동적 JdbcTemplate 으로 INSERT — JpaRepository 우회. PG/Oracle 공용 SQL.
 *
 * <p>여기저기 복붙돼 있던 {@code SyncLog.builder()} + target_tables JSON 손코딩을 통합한다.
 * 단순배열({@code ["a","b"]})과 per-target count 배열({@code [{"name":"a","count":3}, ...]}) 두 형식을 지원.
 * per-target count 형식이어야 프론트 처리현황 화면이 타겟별 실 적재건수를 분리 표시한다
 * ({@code ExecutionDataController#parseTargetCounts}).
 *
 * <p>저장 실패는 {@code log.warn} 후 삼킴 — 기존 호출처들의 동작과 동일(파이프라인 본 흐름을 막지 않음).
 */
public final class SyncLogWriter {

    private static final Logger log = LoggerFactory.getLogger(SyncLogWriter.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String INSERT_SQL =
            "INSERT INTO sync_log (" +
            "execution_id, step_id, mapping_name, " +
            "source_tables, target_tables, source_pk_column, " +
            "read_count, write_count, failed_count, skip_count, " +
            "failed_keys, error_summary, created_at" +
            ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)";

    private SyncLogWriter() {}

    // ==================== 단순배열 target_tables (write 직접 지정) ====================

    public static void save(DataSourceProvider dsp, String executionId, String stepId, String mappingName,
                            List<String> sourceTables, List<String> targetTables,
                            long readCount, long writeCount, long failedCount, long skipCount,
                            List<String> failedKeys, String errorSummary) {
        save(dsp, executionId, stepId, mappingName, sourceTables, targetTables,
             readCount, writeCount, failedCount, skipCount, failedKeys, errorSummary, null);
    }

    public static void save(DataSourceProvider dsp, String executionId, String stepId, String mappingName,
                            List<String> sourceTables, List<String> targetTables,
                            long readCount, long writeCount, long failedCount, long skipCount,
                            List<String> failedKeys, String errorSummary, String sourcePkColumn) {
        persist(dsp, executionId, stepId, mappingName,
                jsonNameArray(sourceTables), jsonNameArray(targetTables),
                readCount, writeCount, failedCount, skipCount, failedKeys, errorSummary, sourcePkColumn);
    }

    // ==================== per-target count target_tables (write = tracker.total()) ====================

    public static void save(DataSourceProvider dsp, String executionId, String stepId, String mappingName,
                            List<String> sourceTables, TableCountTracker targetTracker,
                            long readCount, long failedCount, long skipCount,
                            List<String> failedKeys, String errorSummary) {
        save(dsp, executionId, stepId, mappingName, sourceTables, targetTracker,
             readCount, failedCount, skipCount, failedKeys, errorSummary, null);
    }

    public static void save(DataSourceProvider dsp, String executionId, String stepId, String mappingName,
                            List<String> sourceTables, TableCountTracker targetTracker,
                            long readCount, long failedCount, long skipCount,
                            List<String> failedKeys, String errorSummary, String sourcePkColumn) {
        long writeCount = targetTracker != null ? targetTracker.total() : 0L;
        String targetJson = (targetTracker != null && !targetTracker.isEmpty())
                ? jsonCountArray(targetTracker.asMap())
                : "[]";
        persist(dsp, executionId, stepId, mappingName,
                jsonNameArray(sourceTables), targetJson,
                readCount, writeCount, failedCount, skipCount, failedKeys, errorSummary, sourcePkColumn);
    }

    // ==================== 내부 ====================

    private static void persist(DataSourceProvider dsp, String executionId, String stepId, String mappingName,
                                String sourceJson, String targetJson,
                                long readCount, long writeCount, long failedCount, long skipCount,
                                List<String> failedKeys, String errorSummary, String sourcePkColumn) {
        try {
            String targetDs = dsp.getTargetDatasourceId();
            JdbcTemplate jdbc = dsp.getJdbcTemplate(targetDs);
            String resolvedMappingName = mappingName != null ? mappingName : stepId;
            String failedKeysStr = (failedKeys == null || failedKeys.isEmpty())
                    ? null : String.join(",", failedKeys);
            jdbc.update(INSERT_SQL,
                    executionId, stepId, resolvedMappingName,
                    sourceJson, targetJson, sourcePkColumn,
                    readCount, writeCount, failedCount, skipCount,
                    failedKeysStr, errorSummary);
        } catch (Exception e) {
            log.warn("[{}] SyncLog 저장 실패 (매핑: {}): {}", stepId, mappingName, e.getMessage());
        }
    }

    /** {@code ["a","b"]} */
    private static String jsonNameArray(List<String> names) {
        try {
            return MAPPER.writeValueAsString(names != null ? names : List.of());
        } catch (Exception e) {
            return "[]";
        }
    }

    /** {@code [{"name":"a","count":3},{"name":"b","count":1}]} (순서 보존) */
    private static String jsonCountArray(Map<String, Long> counts) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (counts != null) {
            for (Map.Entry<String, Long> e : counts.entrySet()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("name", e.getKey());
                m.put("count", e.getValue() != null ? e.getValue() : 0L);
                list.add(m);
            }
        }
        try {
            return MAPPER.writeValueAsString(list);
        } catch (Exception e) {
            return "[]";
        }
    }
}
