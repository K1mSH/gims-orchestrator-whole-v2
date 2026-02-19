package com.sync.agent.bojo.rcv.step;

import com.sync.agent.common.controller.DataSourceProvider;
import com.sync.agent.common.step.Status;
import com.sync.agent.common.step.StepContext;
import com.sync.agent.common.step.StepExecutor;
import com.sync.agent.common.step.StepResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

/**
 * Link 테이블 업데이트 Step
 *
 * 관측데이터 동기화 완료 후 link_ngwis 테이블 업데이트
 * - IF 테이블에서 각 obsv_code별 max(obsv_date, obsv_time) 조회
 * - link_ngwis 테이블에 UPSERT (있으면 UPDATE, 없으면 INSERT)
 */
@Slf4j
public class LinkTableUpdateStep implements StepExecutor {

    private static final String STEP_ID = "link-table-update";
    private static final String STEP_NAME = "Link 테이블 업데이트";

    private final DataSourceProvider dataSourceProvider;
    private final String ifTable;       // IF 테이블 (현재 실행에서 동기화된 데이터)
    private final String linkTable;     // link 테이블

    private final String keyColumn = "obsv_code";
    private final String dateColumn = "obsv_date";
    private final String timeColumn = "obsv_time";

    public LinkTableUpdateStep(
            DataSourceProvider dataSourceProvider,
            String ifTable,
            String linkTable) {
        this.dataSourceProvider = dataSourceProvider;
        this.ifTable = ifTable;
        this.linkTable = linkTable;
    }

    @Override
    public String getStepId() {
        return STEP_ID;
    }

    @Override
    public String getStepName() {
        return STEP_NAME;
    }

    private static boolean isMysql(String dbType) {
        return "MYSQL".equalsIgnoreCase(dbType) || "MARIADB".equalsIgnoreCase(dbType);
    }

    @Override
    public StepResult execute(StepContext context) {
        long startTime = System.currentTimeMillis();
        int updateCount = 0;

        try {
            String targetDsId = dataSourceProvider.getTargetDatasourceId();
            String dbType = dataSourceProvider.getDbType(targetDsId);
            JdbcTemplate targetJdbc = dataSourceProvider.getJdbcTemplate(targetDsId);

            // 1. Link 테이블 존재 확인 및 생성
            ensureLinkTableExists(targetJdbc, dbType);

            // 2. IF 테이블에서 현재 실행 ID로 동기화된 데이터의 obsv_code별 max(date, time) 조회
            String executionId = context.getExecutionId();
            List<Map<String, Object>> maxRecords = getMaxDateTimePerObsvCode(targetJdbc, executionId);

            log.info("[{}] IF 테이블에서 {} 개의 obsv_code 조회됨", STEP_ID, maxRecords.size());

            if (maxRecords.isEmpty()) {
                log.info("[{}] 업데이트할 레코드가 없습니다", STEP_ID);
                return StepResult.builder()
                        .stepId(STEP_ID)
                        .status(Status.SUCCESS)
                        .readCount(0)
                        .writeCount(0)
                        .skipCount(0)
                        .durationMs(System.currentTimeMillis() - startTime)
                        .build();
            }

            // 3. 각 obsv_code에 대해 link 테이블 UPSERT
            for (Map<String, Object> record : maxRecords) {
                String obsvCode = (String) record.get(keyColumn);
                String obsvDate = record.get(dateColumn) != null ? record.get(dateColumn).toString() : null;
                String obsvTime = record.get(timeColumn) != null ? record.get(timeColumn).toString() : null;

                if (obsvCode == null || obsvDate == null) {
                    log.warn("[{}] 잘못된 레코드: obsvCode={}, obsvDate={}", STEP_ID, obsvCode, obsvDate);
                    continue;
                }

                int affected = upsertLinkRecord(targetJdbc, obsvCode, obsvDate, obsvTime, dbType);
                if (affected > 0) {
                    updateCount++;
                }
            }

            log.info("[{}] Link 테이블 업데이트 완료: {} 건", STEP_ID, updateCount);

            return StepResult.builder()
                    .stepId(STEP_ID)
                    .status(Status.SUCCESS)
                    .readCount(0)
                    .writeCount(updateCount)
                    .skipCount(0)
                    .durationMs(System.currentTimeMillis() - startTime)
                    .sourceTable(ifTable)
                    .targetTable(linkTable)
                    .build();

        } catch (Exception e) {
            log.error("[{}] Link 테이블 업데이트 실패: {}", STEP_ID, e.getMessage(), e);
            return StepResult.failed(STEP_ID, e.getMessage(), System.currentTimeMillis() - startTime);
        }
    }

    /**
     * Link 테이블이 없으면 생성
     */
    private void ensureLinkTableExists(JdbcTemplate targetJdbc, String dbType) {
        String timestampDefault = isMysql(dbType)
                ? "update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"
                : "update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP";

        String createSql = String.format(
                "CREATE TABLE IF NOT EXISTS %s (" +
                        "%s VARCHAR(50) PRIMARY KEY, " +
                        "%s DATE, " +
                        "%s VARCHAR(10), " +
                        "%s" +
                        ")",
                linkTable, keyColumn, dateColumn, timeColumn, timestampDefault);

        try {
            targetJdbc.execute(createSql);
            log.debug("[{}] Link 테이블 확인/생성 완료: {}", STEP_ID, linkTable);
        } catch (Exception e) {
            log.warn("[{}] Link 테이블 생성 중 오류 (이미 존재할 수 있음): {}", STEP_ID, e.getMessage());
        }
    }

    /**
     * IF 테이블에서 현재 실행에서 동기화된 obsv_code별 max(date, time) 조회
     */
    private List<Map<String, Object>> getMaxDateTimePerObsvCode(JdbcTemplate targetJdbc, String executionId) {
        String sql = String.format(
                "SELECT %s, MAX(%s) as %s, MAX(%s) as %s " +
                        "FROM %s WHERE execution_id = ? " +
                        "GROUP BY %s",
                keyColumn, dateColumn, dateColumn, timeColumn, timeColumn,
                ifTable, keyColumn);

        try {
            return targetJdbc.queryForList(sql, executionId);
        } catch (Exception e) {
            log.warn("[{}] IF 테이블 조회 실패, 전체 데이터로 시도: {}", STEP_ID, e.getMessage());

            String fallbackSql = String.format(
                    "SELECT %s, MAX(%s) as %s, MAX(%s) as %s " +
                            "FROM %s GROUP BY %s",
                    keyColumn, dateColumn, dateColumn, timeColumn, timeColumn,
                    ifTable, keyColumn);
            return targetJdbc.queryForList(fallbackSql);
        }
    }

    /**
     * Link 테이블에 UPSERT
     * PostgreSQL: ON CONFLICT ... DO UPDATE
     * MySQL: ON DUPLICATE KEY UPDATE
     */
    private int upsertLinkRecord(JdbcTemplate targetJdbc, String obsvCode, String obsvDate, String obsvTime, String dbType) {
        String cleanDate = obsvDate.replaceAll("-", "");
        String cleanTime = obsvTime != null ? obsvTime : "00:00:00";

        String upsertSql;

        if (isMysql(dbType)) {
            // MySQL: ON DUPLICATE KEY UPDATE + STR_TO_DATE
            upsertSql = String.format(
                    "INSERT INTO %s (%s, %s, %s, update_time) " +
                            "VALUES (?, STR_TO_DATE(?, '%%Y%%m%%d'), ?, CURRENT_TIMESTAMP) " +
                            "ON DUPLICATE KEY UPDATE " +
                            "%s = IF(" +
                            "  %s IS NULL OR %s < VALUES(%s) OR (%s = VALUES(%s) AND %s < VALUES(%s)), " +
                            "  VALUES(%s), %s), " +
                            "%s = IF(" +
                            "  %s IS NULL OR %s < VALUES(%s) OR (%s = VALUES(%s) AND %s < VALUES(%s)), " +
                            "  VALUES(%s), %s), " +
                            "update_time = IF(" +
                            "  %s IS NULL OR %s < VALUES(%s) OR (%s = VALUES(%s) AND %s < VALUES(%s)), " +
                            "  CURRENT_TIMESTAMP, update_time)",
                    linkTable, keyColumn, dateColumn, timeColumn,
                    // date column update
                    dateColumn,
                    dateColumn, dateColumn, dateColumn, dateColumn, dateColumn, timeColumn, timeColumn,
                    dateColumn, dateColumn,
                    // time column update
                    timeColumn,
                    dateColumn, dateColumn, dateColumn, dateColumn, dateColumn, timeColumn, timeColumn,
                    timeColumn, timeColumn,
                    // update_time update
                    dateColumn, dateColumn, dateColumn, dateColumn, dateColumn, timeColumn, timeColumn);
        } else {
            // PostgreSQL: ON CONFLICT ... DO UPDATE + TO_DATE
            upsertSql = String.format(
                    "INSERT INTO %s (%s, %s, %s, update_time) " +
                            "VALUES (?, TO_DATE(?, 'YYYYMMDD'), ?, CURRENT_TIMESTAMP) " +
                            "ON CONFLICT (%s) DO UPDATE SET " +
                            "%s = EXCLUDED.%s, " +
                            "%s = EXCLUDED.%s, " +
                            "update_time = CURRENT_TIMESTAMP " +
                            "WHERE %s.%s IS NULL " +
                            "   OR %s.%s < EXCLUDED.%s " +
                            "   OR (%s.%s = EXCLUDED.%s AND %s.%s < EXCLUDED.%s)",
                    linkTable, keyColumn, dateColumn, timeColumn,
                    keyColumn,
                    dateColumn, dateColumn,
                    timeColumn, timeColumn,
                    linkTable, dateColumn,
                    linkTable, dateColumn, dateColumn,
                    linkTable, dateColumn, dateColumn, linkTable, timeColumn, timeColumn);
        }

        try {
            int affected = targetJdbc.update(upsertSql, obsvCode, cleanDate, cleanTime);
            if (affected == 0) {
                log.debug("[{}] Link 레코드 스킵 (기존 데이터가 더 최신): {}", STEP_ID, obsvCode);
            }
            return affected;
        } catch (Exception e) {
            log.error("[{}] Link 레코드 UPSERT 실패: {} - {}", STEP_ID, obsvCode, e.getMessage());
            return 0;
        }
    }
}
