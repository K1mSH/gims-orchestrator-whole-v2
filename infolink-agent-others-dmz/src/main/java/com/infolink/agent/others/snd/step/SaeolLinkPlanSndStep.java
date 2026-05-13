package com.infolink.agent.others.snd.step;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infolink.agent.common.controller.DataSourceProvider;
import com.infolink.agent.common.entity.SyncLog;
import com.infolink.agent.common.repository.SyncLogRepository;
import com.infolink.agent.common.step.StepContext;
import com.infolink.agent.common.step.StepExecutor;
import com.infolink.agent.common.step.StepResult;
import com.infolink.agent.common.util.SourceRefUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 새올 LINK_PLAN 기반 SND Step
 *
 * LINK_PLAN(변경 로그) 조회 → deduplicate → 테이블별 배치 SELECT → IF_SND MERGE
 * SourceToTargetStep을 수정하지 않고, Oracle 메타데이터 기반으로 MERGE SQL을 동적 생성한다.
 */
@Slf4j
public class SaeolLinkPlanSndStep implements StepExecutor {

    private static final int BATCH_SIZE = 1000;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // IF_SND 메타 컬럼 (소스에 없는 우리 추가 컬럼)
    private static final Set<String> META_COLUMNS = Set.of(
            "ID", "SOURCE_REFS", "LINK_STATUS", "EXTRACTED_AT", "UPDATED_AT", "EXECUTION_ID"
    );

    @Getter
    private final String stepId;
    private final String stepName;
    private final List<SaeolTableMapping> tableMappings;
    private final DataSourceProvider dataSourceProvider;
    private final SyncLogRepository syncLogRepository;

    public SaeolLinkPlanSndStep(String stepId,
                                 String stepName,
                                 List<SaeolTableMapping> tableMappings,
                                 DataSourceProvider dataSourceProvider,
                                 SyncLogRepository syncLogRepository) {
        this.stepId = stepId;
        this.stepName = stepName != null ? stepName : stepId;
        this.tableMappings = tableMappings;
        this.dataSourceProvider = dataSourceProvider;
        this.syncLogRepository = syncLogRepository;
    }

    @Override
    public String getStepName() {
        return stepName;
    }

    @Override
    public StepResult execute(StepContext context) {
        long startTime = System.currentTimeMillis();
        String executionId = context.getExecutionId();

        try {
            String sourceDsId = context.getSourceDatasourceId();
            String targetDsId = context.getTargetDatasourceId();

            // source = target = 같은 Oracle (29005)
            JdbcTemplate jdbc = dataSourceProvider.getJdbcTemplate(sourceDsId);

            // ① LINK_PLAN_CURSOR에서 마지막 link_idx 읽기
            long lastLinkIdx = getLastLinkIdx(jdbc, context.getPipelineId());
            log.info("[새올SND] 커서 위치: lastLinkIdx={}", lastLinkIdx);

            // ② LINK_PLAN 조회 (미처리분)
            List<Map<String, Object>> linkPlanRows = jdbc.queryForList(
                    "SELECT * FROM LINK_PLAN WHERE LINK_IDX > ? ORDER BY LINK_IDX",
                    lastLinkIdx
            );

            if (linkPlanRows.isEmpty()) {
                log.info("[새올SND] 처리할 LINK_PLAN 이벤트 없음");
                return StepResult.skipped(stepId, "LINK_PLAN 이벤트 없음");
            }
            log.info("[새올SND] LINK_PLAN 이벤트 {}건 조회", linkPlanRows.size());

            // ③ deduplicate: TABLE_NAME + PK 조합으로 최신 link_idx만 유지
            Map<String, Map<String, Map<String, Object>>> groupedByTable = deduplicateAndGroup(linkPlanRows);

            int totalRead = 0;
            int totalWrite = 0;
            long maxLinkIdx = lastLinkIdx;

            // ④ 테이블별 배치 처리 (전체 매핑 순회 — 이벤트 없는 테이블도 SyncLog 0건 기록)
            for (SaeolTableMapping mapping : tableMappings) {
                String sourceTable = mapping.getSourceTable();
                String targetTable = mapping.getTargetTable();

                Map<String, Map<String, Object>> pkRows = groupedByTable.get(sourceTable);
                if (pkRows == null || pkRows.isEmpty()) {
                    saveSyncLog(executionId, sourceTable, targetTable, 0, 0);
                    continue;
                }

                log.info("[새올SND] {} → {} ({}건)", sourceTable, targetTable, pkRows.size());

                // WHERE 조건 생성 (LINK_PLAN 키 → 소스 PK 매핑)
                List<Map<String, Object>> sourceRows = fetchSourceRows(
                        jdbc, sourceTable, mapping, pkRows.values());

                if (sourceRows.isEmpty()) {
                    log.warn("[새올SND] {} 소스 데이터 0건", sourceTable);
                    saveSyncLog(executionId, sourceTable, targetTable, 0, 0);
                    continue;
                }

                // IF_SND 컬럼 목록 조회 (Oracle 메타데이터)
                List<String> ifColumns = getTableColumns(jdbc, targetTable);
                List<String> dataColumns = ifColumns.stream()
                        .filter(c -> !META_COLUMNS.contains(c))
                        .collect(Collectors.toList());

                // MERGE SQL 동적 생성
                String mergeSql = buildOracleMergeSql(targetTable, dataColumns, ifColumns);

                // 배치 MERGE 실행
                int writeCount = executeBatchMerge(jdbc, mergeSql, sourceRows, dataColumns,
                        mapping, context, executionId);

                totalRead += sourceRows.size();
                totalWrite += writeCount;

                // SyncLog 저장
                saveSyncLog(executionId, sourceTable, targetTable, sourceRows.size(), writeCount);
            }

            // ⑤ maxLinkIdx 계산
            for (Map<String, Object> row : linkPlanRows) {
                long idx = ((Number) row.get("LINK_IDX")).longValue();
                if (idx > maxLinkIdx) maxLinkIdx = idx;
            }

            // ⑥ LINK_PLAN_CURSOR 갱신 (전체 완료 후 1회)
            updateLastLinkIdx(jdbc, context.getPipelineId(), maxLinkIdx);
            log.info("[새올SND] 커서 갱신: {} → {}", lastLinkIdx, maxLinkIdx);

            long duration = System.currentTimeMillis() - startTime;
            log.info("[새올SND] 완료: read={}, write={}, duration={}ms", totalRead, totalWrite, duration);

            return StepResult.success(stepId, totalRead, totalWrite, duration);

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[새올SND] 실패: {}", e.getMessage(), e);
            return StepResult.failed(stepId, e.getMessage(), duration);
        }
    }

    /**
     * LINK_PLAN 이벤트를 TABLE_NAME별로 그룹핑하고, 같은 PK는 최신 link_idx만 유지
     */
    private Map<String, Map<String, Map<String, Object>>> deduplicateAndGroup(
            List<Map<String, Object>> linkPlanRows) {

        // TABLE_NAME → { pkKey → row(최신) }
        Map<String, Map<String, Map<String, Object>>> result = new LinkedHashMap<>();

        for (Map<String, Object> row : linkPlanRows) {
            String tableName = ((String) row.get("TABLE_NAME")).trim();
            String flag = row.get("FLAG") != null ? ((String) row.get("FLAG")).trim() : "U";

            // D(DELETE)는 현재 skip
            if ("D".equals(flag)) {
                continue;
            }

            // 해당 테이블의 매핑 찾기
            SaeolTableMapping mapping = findMapping(tableName);
            if (mapping == null) {
                log.debug("[새올SND] 매핑 없는 테이블 skip: {}", tableName);
                continue;
            }

            // LINK_PLAN 컬럼에서 PK 키 생성
            String pkKey = buildPkKey(row, mapping);

            result.computeIfAbsent(tableName, k -> new LinkedHashMap<>())
                    .put(pkKey, row); // 같은 키면 나중 것(더 큰 link_idx)이 덮어씀
        }

        return result;
    }

    /**
     * LINK_PLAN 행에서 PK 키 문자열 생성 (deduplicate용)
     */
    private String buildPkKey(Map<String, Object> linkPlanRow, SaeolTableMapping mapping) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : mapping.getLinkPlanKeys().entrySet()) {
            String lpCol = entry.getKey().toUpperCase();
            Object val = linkPlanRow.get(lpCol);
            if (sb.length() > 0) sb.append("|");
            sb.append(val != null ? val.toString().trim() : "");
        }
        return sb.toString();
    }

    private SaeolTableMapping findMapping(String tableName) {
        return tableMappings.stream()
                .filter(m -> m.getSourceTable().equalsIgnoreCase(tableName))
                .findFirst()
                .orElse(null);
    }

    /**
     * 소스 테이블에서 LINK_PLAN PK에 해당하는 행들을 배치 SELECT
     */
    private List<Map<String, Object>> fetchSourceRows(
            JdbcTemplate jdbc, String sourceTable,
            SaeolTableMapping mapping, Collection<Map<String, Object>> linkPlanRows) {

        // LINK_PLAN 키 → 소스 WHERE 컬럼 매핑
        Map<String, String> keyMapping = mapping.getLinkPlanKeys();
        List<String> sourceWhereColumns = new ArrayList<>(keyMapping.values());

        // WHERE 조건: (col1, col2) IN ((?,?), (?,?), ...)
        // 또는 개별 OR: (col1=? AND col2=?) OR (col1=? AND col2=?)
        List<Object[]> pkValueSets = new ArrayList<>();
        for (Map<String, Object> lpRow : linkPlanRows) {
            Object[] vals = new Object[sourceWhereColumns.size()];
            int i = 0;
            for (Map.Entry<String, String> entry : keyMapping.entrySet()) {
                String lpCol = entry.getKey().toUpperCase();
                Object val = lpRow.get(lpCol);
                vals[i++] = val != null ? val.toString().trim() : null;
            }
            pkValueSets.add(vals);
        }

        if (pkValueSets.isEmpty()) {
            return Collections.emptyList();
        }

        // Oracle에서는 IN 절로 배치 조회
        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(sourceTable).append(" WHERE ");
        List<Object> params = new ArrayList<>();

        if (sourceWhereColumns.size() == 1) {
            // 단일 컬럼 IN
            String col = sourceWhereColumns.get(0);
            sql.append(col).append(" IN (");
            for (int i = 0; i < pkValueSets.size(); i++) {
                if (i > 0) sql.append(",");
                sql.append("?");
                params.add(pkValueSets.get(i)[0]);
            }
            sql.append(")");
        } else {
            // 복합 컬럼: OR 조건
            for (int i = 0; i < pkValueSets.size(); i++) {
                if (i > 0) sql.append(" OR ");
                sql.append("(");
                Object[] vals = pkValueSets.get(i);
                for (int j = 0; j < sourceWhereColumns.size(); j++) {
                    if (j > 0) sql.append(" AND ");
                    if (vals[j] == null) {
                        sql.append(sourceWhereColumns.get(j)).append(" IS NULL");
                    } else {
                        sql.append(sourceWhereColumns.get(j)).append(" = ?");
                        params.add(vals[j]);
                    }
                }
                sql.append(")");
            }
        }

        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    /**
     * Oracle USER_TAB_COLUMNS에서 컬럼 목록 조회 (ID 제외)
     */
    private List<String> getTableColumns(JdbcTemplate jdbc, String tableName) {
        return jdbc.queryForList(
                "SELECT COLUMN_NAME FROM USER_TAB_COLUMNS WHERE TABLE_NAME = ? ORDER BY COLUMN_ID",
                String.class, tableName.toUpperCase()
        ).stream()
                .filter(c -> !"ID".equals(c))
                .collect(Collectors.toList());
    }

    /**
     * Oracle MERGE INTO SQL 동적 생성
     */
    private String buildOracleMergeSql(String targetTable, List<String> dataColumns, List<String> allColumns) {
        // allColumns = dataColumns + META(SOURCE_REFS, LINK_STATUS, EXTRACTED_AT, UPDATED_AT, EXECUTION_ID) - ID 제외
        List<String> insertColumns = allColumns; // ID 제외된 상태

        StringBuilder sb = new StringBuilder();
        sb.append("MERGE INTO ").append(targetTable).append(" t ");

        // USING
        sb.append("USING (SELECT ");
        for (int i = 0; i < insertColumns.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("? AS ").append(insertColumns.get(i));
        }
        sb.append(" FROM DUAL) s ");

        // ON (SOURCE_REFS 기준)
        sb.append("ON (t.SOURCE_REFS = s.SOURCE_REFS) ");

        // WHEN MATCHED: 데이터 + 메타 갱신
        sb.append("WHEN MATCHED THEN UPDATE SET ");
        List<String> updateParts = new ArrayList<>();
        for (String col : dataColumns) {
            updateParts.add("t." + col + " = s." + col);
        }
        updateParts.add("t.LINK_STATUS = s.LINK_STATUS");
        updateParts.add("t.UPDATED_AT = s.UPDATED_AT");
        updateParts.add("t.EXECUTION_ID = s.EXECUTION_ID");
        sb.append(String.join(", ", updateParts));

        // WHEN NOT MATCHED
        sb.append(" WHEN NOT MATCHED THEN INSERT (");
        sb.append(String.join(", ", insertColumns));
        sb.append(") VALUES (");
        sb.append(insertColumns.stream().map(c -> "s." + c).collect(Collectors.joining(", ")));
        sb.append(")");

        return sb.toString();
    }

    /**
     * 배치 MERGE 실행
     */
    private int executeBatchMerge(JdbcTemplate jdbc, String mergeSql,
                                   List<Map<String, Object>> sourceRows,
                                   List<String> dataColumns,
                                   SaeolTableMapping mapping,
                                   StepContext context,
                                   String executionId) {
        int totalWrite = 0;

        // PK 컬럼 목록 (source_refs 생성용)
        String[] pkCols = mapping.getPrimaryKey().split(",");

        for (int i = 0; i < sourceRows.size(); i += BATCH_SIZE) {
            List<Map<String, Object>> batch = sourceRows.subList(
                    i, Math.min(i + BATCH_SIZE, sourceRows.size()));

            List<Object[]> paramsList = new ArrayList<>();
            for (Map<String, Object> row : batch) {
                List<Object> params = new ArrayList<>();

                // 데이터 컬럼 값
                for (String col : dataColumns) {
                    params.add(row.get(col));
                }

                // source_refs 생성
                Object[] pkValues = new Object[pkCols.length];
                for (int p = 0; p < pkCols.length; p++) {
                    Object val = row.get(pkCols[p].trim());
                    pkValues[p] = val != null ? val.toString().trim() : "";
                }
                String sourceRefsJson = SourceRefUtils.buildJson(context, mapping.getSourceTable(), pkValues);
                params.add(sourceRefsJson);

                // link_status
                params.add("PENDING");
                // extracted_at
                params.add(Timestamp.valueOf(LocalDateTime.now()));
                // updated_at
                params.add(Timestamp.valueOf(LocalDateTime.now()));
                // execution_id
                params.add(executionId);

                paramsList.add(params.toArray());
            }

            int[] results = jdbc.batchUpdate(mergeSql, paramsList);
            totalWrite += results.length;
        }

        return totalWrite;
    }

    private long getLastLinkIdx(JdbcTemplate jdbc, String agentCode) {
        try {
            Long val = jdbc.queryForObject(
                    "SELECT LAST_LINK_IDX FROM LINK_PLAN_CURSOR WHERE AGENT_CODE = ?",
                    Long.class, agentCode);
            return val != null ? val : 0L;
        } catch (Exception e) {
            // 첫 실행: 커서 행 없음 → 0부터 시작
            return 0L;
        }
    }

    private void updateLastLinkIdx(JdbcTemplate jdbc, String agentCode, long linkIdx) {
        int updated = jdbc.update(
                "UPDATE LINK_PLAN_CURSOR SET LAST_LINK_IDX = ?, UPDATED_AT = SYSTIMESTAMP WHERE AGENT_CODE = ?",
                linkIdx, agentCode);
        if (updated == 0) {
            // 첫 실행: INSERT
            jdbc.update(
                    "INSERT INTO LINK_PLAN_CURSOR (AGENT_CODE, LAST_LINK_IDX, UPDATED_AT) VALUES (?, ?, SYSTIMESTAMP)",
                    agentCode, linkIdx);
        }
    }

    private void saveSyncLog(String executionId, String sourceTable, String targetTable,
                              int readCount, int writeCount) {
        try {
            String sourceTablesJson = objectMapper.writeValueAsString(
                    List.of(Map.of("name", sourceTable)));
            String targetTablesJson = objectMapper.writeValueAsString(
                    List.of(Map.of("name", targetTable)));

            SyncLog syncLog = SyncLog.builder()
                    .executionId(executionId)
                    .stepId(stepId)
                    .mappingName(sourceTable.toLowerCase())
                    .sourceTables(sourceTablesJson)
                    .targetTables(targetTablesJson)
                    .readCount((long) readCount)
                    .writeCount((long) writeCount)
                    .build();
            syncLogRepository.save(syncLog);
        } catch (Exception e) {
            log.warn("[새올SND] SyncLog 저장 실패: {}", e.getMessage());
        }
    }
}
