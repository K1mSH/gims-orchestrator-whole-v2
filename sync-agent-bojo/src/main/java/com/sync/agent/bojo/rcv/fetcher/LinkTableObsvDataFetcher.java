package com.sync.agent.bojo.rcv.fetcher;

import com.sync.agent.common.controller.DataSourceProvider;
import com.sync.agent.common.step.DataFetcher;
import com.sync.agent.common.step.StepContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Link 테이블 기반 관측데이터(ObsvData) 조회 Fetcher
 *
 * 원본 보조측정망 연계 로직 구현:
 * 1. 제원(jewon) 테이블에서 obsv_code 목록 조회
 * 2. 각 obsv_code별로:
 *    - Target DB의 link_ngwis 테이블에서 마지막 동기화 시점 조회
 *    - link 없으면 Source DB에서 최소 날짜 조회 (첫 동기화)
 *    - 해당 시점 이후 데이터만 Source DB에서 조회
 * 3. 모든 결과를 합쳐서 반환
 *
 * link 테이블 기반 증분 동기화 사용
 */
@Slf4j
public class LinkTableObsvDataFetcher implements DataFetcher {

    private final DataSourceProvider dataSourceProvider;

    // Source 테이블 설정
    private final String jewonTable;      // 제원 테이블 (obsv_code 목록용)
    private final String obsvdataTable;   // 관측데이터 테이블

    // Target 테이블 설정
    private final String linkTable;       // link 테이블 (동기화 시점 추적용)

    // 컬럼 설정
    private final String keyColumn = "obsv_code";
    private final String dateColumn = "obsv_date";
    private final String timeColumn = "obsv_time";

    public LinkTableObsvDataFetcher(
            DataSourceProvider dataSourceProvider,
            String jewonTable,
            String obsvdataTable,
            String linkTable) {
        this.dataSourceProvider = dataSourceProvider;
        this.jewonTable = jewonTable;
        this.obsvdataTable = obsvdataTable;
        this.linkTable = linkTable;
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
    public List<Map<String, Object>> fetch(StepContext context) {
        log.info("[LinkTableFetcher] 시작 - link 테이블 기반 증분 동기화");

        String sourceDsId = dataSourceProvider.getSourceDatasourceId();
        String targetDsId = dataSourceProvider.getTargetDatasourceId();

        JdbcTemplate sourceJdbc = dataSourceProvider.getJdbcTemplate(sourceDsId);
        JdbcTemplate targetJdbc = dataSourceProvider.getJdbcTemplate(targetDsId);
        String sourceDbType = dataSourceProvider.getDbType(sourceDsId);

        // 수동 시간 지정이 있는 경우 기존 방식 사용 (전체 시간 범위 조회)
        LocalDateTime paramStartTime = context.getParam("startTime");
        LocalDateTime paramEndTime = context.getParam("endTime");

        // obsv-code 파라미터 추출 (프리셋 방식: Agent가 직접 해석)
        String filterObsvCode = context.getExecutionOptions().getParamValue("obsv-code");

        if (paramStartTime != null && paramEndTime != null) {
            log.info("[LinkTableFetcher] 수동 시간 지정 모드: {} ~ {}", paramStartTime, paramEndTime);
            return fetchByTimeRange(sourceJdbc, paramStartTime, paramEndTime, filterObsvCode, sourceDbType);
        }

        List<Map<String, Object>> allRecords = new ArrayList<>();

        // 1. 제원 테이블에서 obsv_code 목록 조회
        List<String> obsvCodes = getObsvCodeList(sourceJdbc, sourceDbType);
        log.info("[LinkTableFetcher] 제원 테이블에서 {} 개의 obsv_code 조회됨", obsvCodes.size());

        // obsv_code 필터 적용 (in-memory)
        if (filterObsvCode != null && !filterObsvCode.isBlank()) {
            int beforeCount = obsvCodes.size();
            if (filterObsvCode.contains(",")) {
                // 콤마 구분 → 여러 코드 매칭
                List<String> inValues = List.of(filterObsvCode.split(","));
                obsvCodes = new ArrayList<>(obsvCodes.stream()
                        .filter(code -> inValues.stream().anyMatch(v -> v.trim().equals(code)))
                        .toList());
            } else {
                // 단일 값 → 정확 매칭
                obsvCodes = new ArrayList<>(obsvCodes.stream()
                        .filter(code -> code.equals(filterObsvCode.trim()))
                        .toList());
            }
            log.info("[LinkTableFetcher] obsv_code 필터 적용: {} -> {} 개", beforeCount, obsvCodes.size());
        }

        if (obsvCodes.isEmpty()) {
            log.warn("[LinkTableFetcher] 제원 테이블에 데이터가 없습니다: {}", jewonTable);
            return allRecords;
        }

        int processedCount = 0;
        int totalFetched = 0;
        int newCodeCount = 0;

        // 2. 각 obsv_code별로 데이터 조회
        for (String obsvCode : obsvCodes) {
            processedCount++;

            // 2-1. link 테이블에서 마지막 동기화 시점 조회
            boolean existsInLink = existsInLinkTable(targetJdbc, obsvCode);
            LinkData linkData = existsInLink ? getLinkData(targetJdbc, obsvCode) : null;

            String lastDate;
            String lastTime;

            if (linkData != null) {
                // link에 있고 date도 있음 → 해당 시점 이후 데이터 조회
                lastDate = linkData.obsvDate;
                lastTime = linkData.obsvTime;
                log.debug("[LinkTableFetcher] {} - link 존재: {} {}", obsvCode, lastDate, lastTime);
            } else {
                // link에 없거나 date가 NULL인 경우
                if (!existsInLink) {
                    log.info("[LinkTableFetcher] 신규 obsv_code 감지: {} (link 테이블에 미존재)", obsvCode);
                    registerNewObsvCode(targetJdbc, obsvCode);
                    newCodeCount++;
                } else {
                    log.info("[LinkTableFetcher] {} - link 존재하나 date=NULL (첫 동기화 대상)", obsvCode);
                }

                // Source에서 최소 날짜 조회 (첫 동기화 또는 date=NULL인 경우)
                LinkData minData = getMinDateFromSource(sourceJdbc, obsvCode, sourceDbType);
                if (minData == null) {
                    log.info("[LinkTableFetcher] {} - Source obsvdata에 데이터 없음, 스킵", obsvCode);
                    continue;
                }
                lastDate = minData.obsvDate;
                lastTime = "000000";
                log.info("[LinkTableFetcher] {} - 첫 동기화, 최소 날짜부터: {} {}", obsvCode, lastDate, lastTime);
            }

            // 2-2. Source에서 해당 시점 이후 데이터 조회
            List<Map<String, Object>> records = getObsvDataAfterLink(sourceJdbc, obsvCode, lastDate, lastTime, sourceDbType);

            if (!records.isEmpty()) {
                allRecords.addAll(records);
                totalFetched += records.size();
                log.debug("[LinkTableFetcher] {} - {} 건 조회됨", obsvCode, records.size());
            }

            // 진행률 로그 (10개 단위)
            if (processedCount % 10 == 0 || processedCount == obsvCodes.size()) {
                log.info("[LinkTableFetcher] 진행: {}/{} obsv_code 처리, 총 {} 건 조회",
                        processedCount, obsvCodes.size(), totalFetched);
            }
        }

        log.info("[LinkTableFetcher] 완료 - {} 개 obsv_code 중 신규 {} 개 등록, 총 {} 건 조회됨",
                obsvCodes.size(), newCodeCount, allRecords.size());

        return allRecords;
    }

    /**
     * 제원 테이블에서 obsv_code 목록 조회
     * DB 타입별 식별자 인용 처리
     */
    private List<String> getObsvCodeList(JdbcTemplate sourceJdbc, String dbType) {
        String[] tableVariants = {jewonTable.toUpperCase(), jewonTable, jewonTable.toLowerCase()};
        String[] columnVariants = {keyColumn.toUpperCase(), keyColumn, keyColumn.toLowerCase()};

        for (String table : tableVariants) {
            for (String column : columnVariants) {
                try {
                    String sql = String.format("SELECT %s FROM %s ORDER BY %s",
                            qi(column, dbType), qi(table, dbType), qi(column, dbType));
                    log.debug("[LinkTableFetcher] 제원 조회 시도: {}", sql);
                    List<String> result = sourceJdbc.queryForList(sql, String.class);
                    if (!result.isEmpty()) {
                        log.info("[LinkTableFetcher] 제원 테이블 조회 성공: {} ({}건)", table, result.size());
                        return result;
                    }
                } catch (Exception e) {
                    log.debug("[LinkTableFetcher] 제원 조회 실패: {} - {}", table, e.getMessage());
                }
            }
        }

        log.error("[LinkTableFetcher] 제원 테이블 조회 실패 (모든 변형 시도): {}", jewonTable);
        return new ArrayList<>();
    }

    /**
     * link 테이블에 obsv_code가 존재하는지 확인
     */
    private boolean existsInLinkTable(JdbcTemplate targetJdbc, String obsvCode) {
        try {
            String sql = String.format("SELECT COUNT(*) FROM %s WHERE %s = ?", linkTable, keyColumn);
            Integer count = targetJdbc.queryForObject(sql, Integer.class, obsvCode);
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Target DB의 link 테이블에서 마지막 동기화 시점 조회
     * date가 NULL이면 null 반환 (아직 obsvdata 동기화 이력 없음)
     */
    private LinkData getLinkData(JdbcTemplate targetJdbc, String obsvCode) {
        String sql = String.format(
                "SELECT %s, to_char(%s, 'YYYYMMDD') as %s, %s " +
                        "FROM %s WHERE %s = ?",
                keyColumn, dateColumn, dateColumn, timeColumn,
                linkTable, keyColumn);

        try {
            List<Map<String, Object>> results = targetJdbc.queryForList(sql, obsvCode);
            if (results.isEmpty()) {
                return null;
            }

            Map<String, Object> row = results.get(0);
            String date = row.get(dateColumn) != null ? row.get(dateColumn).toString() : null;
            String time = row.get(timeColumn) != null ? row.get(timeColumn).toString() : null;

            if (date == null) {
                return null;
            }

            return new LinkData(obsvCode, date, time != null ? normalizeTime(time) : "000000");
        } catch (Exception e) {
            log.debug("[LinkTableFetcher] link 테이블 조회 실패 (테이블 없을 수 있음): {}", e.getMessage());
            return null;
        }
    }

    /**
     * Source에서 해당 obsv_code의 최소 날짜/시간 조회 (첫 동기화용)
     * DB 타입별 식별자 인용 처리
     */
    private LinkData getMinDateFromSource(JdbcTemplate sourceJdbc, String obsvCode, String dbType) {
        String[] tableVariants = {obsvdataTable.toUpperCase(), obsvdataTable, obsvdataTable.toLowerCase()};

        for (String table : tableVariants) {
            try {
                // 컬럼 case를 테이블 variant에 맞춤
                String colCase = table.equals(obsvdataTable.toUpperCase()) ? "upper" : "lower";
                String kc = qi(colCase.equals("upper") ? keyColumn.toUpperCase() : keyColumn.toLowerCase(), dbType);
                String dc = qi(colCase.equals("upper") ? dateColumn.toUpperCase() : dateColumn.toLowerCase(), dbType);
                String tc = qi(colCase.equals("upper") ? timeColumn.toUpperCase() : timeColumn.toLowerCase(), dbType);

                String sql = String.format(
                        "SELECT %s, MIN(%s) as min_date, MIN(%s) as min_time " +
                                "FROM %s WHERE %s = ? GROUP BY %s",
                        kc, dc, tc, qi(table, dbType), kc, kc);

                List<Map<String, Object>> results = sourceJdbc.queryForList(sql, obsvCode);
                if (results.isEmpty()) {
                    continue;
                }

                Map<String, Object> row = results.get(0);
                String date = row.get("min_date") != null ? row.get("min_date").toString() : null;
                String time = row.get("min_time") != null ? row.get("min_time").toString() : null;

                if (date == null) {
                    continue;
                }

                // 날짜 형식 정리 (YYYYMMDD 형태로)
                date = date.replaceAll("-", "");

                return new LinkData(obsvCode, date, time != null ? normalizeTime(time) : "000000");
            } catch (Exception e) {
                log.debug("[LinkTableFetcher] Source 최소 날짜 조회 시도 실패: {} - {}", table, e.getMessage());
            }
        }

        log.warn("[LinkTableFetcher] Source 최소 날짜 조회 실패 (모든 변형): {}", obsvCode);
        return null;
    }

    /**
     * Source에서 link 시점 이후 데이터 조회 (원본 getObsvdata 로직)
     * DB 타입별 SQL 생성: PostgreSQL(TO_DATE, ::text), MySQL(STR_TO_DATE, CAST)
     */
    private List<Map<String, Object>> getObsvDataAfterLink(
            JdbcTemplate sourceJdbc, String obsvCode, String lastDate, String lastTime,
            String dbType) {

        String[] tableVariants = {obsvdataTable.toUpperCase(), obsvdataTable, obsvdataTable.toLowerCase()};

        for (String table : tableVariants) {
            try {
                // 컬럼 case를 테이블 variant에 맞춤
                String colCase = table.equals(obsvdataTable.toUpperCase()) ? "upper" : "lower";
                String kc, dc, tc;
                String sql;
                if (isMysql(dbType)) {
                    // MySQL: STR_TO_DATE, DATE_FORMAT (HHmmss 비교)
                    kc = qi(keyColumn, dbType);
                    dc = qi(dateColumn, dbType);
                    tc = qi(timeColumn, dbType);
                    sql = String.format(
                            "SELECT * FROM %s WHERE %s = ? " +
                                    "AND ((%s = STR_TO_DATE(?, '%%Y%%m%%d') AND DATE_FORMAT(%s, '%%H%%i%%s') >= ?) " +
                                    "OR %s > STR_TO_DATE(?, '%%Y%%m%%d')) " +
                                    "ORDER BY %s, %s",
                            qi(table, dbType), kc, dc, tc, dc, dc, tc);
                } else {
                    // PostgreSQL: TO_DATE, TO_CHAR (HHmmss 비교) - 컬럼 case를 테이블에 맞춤
                    kc = qi(colCase.equals("upper") ? keyColumn.toUpperCase() : keyColumn.toLowerCase(), dbType);
                    dc = qi(colCase.equals("upper") ? dateColumn.toUpperCase() : dateColumn.toLowerCase(), dbType);
                    tc = qi(colCase.equals("upper") ? timeColumn.toUpperCase() : timeColumn.toLowerCase(), dbType);
                    sql = String.format(
                            "SELECT * FROM %s WHERE %s = ? " +
                                    "AND ((%s = TO_DATE(?, 'YYYYMMDD') AND TO_CHAR(%s, 'HH24MISS') >= ?) " +
                                    "OR %s > TO_DATE(?, 'YYYYMMDD')) " +
                                    "ORDER BY %s, %s",
                            qi(table, dbType), kc, dc, tc, dc, dc, tc);
                }

                // 파라미터: obsvCode, lastDate(for =), lastTime, lastDate(for >)
                List<Map<String, Object>> result = sourceJdbc.queryForList(sql, obsvCode, lastDate, lastTime, lastDate);
                return result;  // 빈 결과여도 반환 (테이블은 존재하지만 조건에 맞는 데이터 없음)
            } catch (Exception e) {
                log.debug("[LinkTableFetcher] 관측데이터 조회 시도 실패: {} - {}", table, e.getMessage());
            }
        }

        log.warn("[LinkTableFetcher] 관측데이터 조회 실패 (모든 변형): {}", obsvCode);
        return new ArrayList<>();
    }

    /**
     * 수동 시간 지정 시 사용하는 전체 시간 범위 조회
     * DB 타입별 날짜 함수 사용: PostgreSQL(TO_DATE), MySQL(STR_TO_DATE)
     */
    private List<Map<String, Object>> fetchByTimeRange(
            JdbcTemplate sourceJdbc, LocalDateTime startTime, LocalDateTime endTime,
            String filterObsvCode, String dbType) {

        String startDate = String.format("%04d%02d%02d",
                startTime.getYear(), startTime.getMonthValue(), startTime.getDayOfMonth());
        String endDate = String.format("%04d%02d%02d",
                endTime.getYear(), endTime.getMonthValue(), endTime.getDayOfMonth());

        String[] tableVariants = {obsvdataTable.toUpperCase(), obsvdataTable, obsvdataTable.toLowerCase()};

        for (String table : tableVariants) {
            try {
                // 컬럼 case를 테이블 variant에 맞춤
                String colCase = table.equals(obsvdataTable.toUpperCase()) ? "upper" : "lower";

                // obsv_code 필터 조건 생성
                StringBuilder filterClause = new StringBuilder();
                List<Object> filterParams = new ArrayList<>();
                if (filterObsvCode != null && !filterObsvCode.isBlank()) {
                    String colName = isMysql(dbType) ? keyColumn
                            : (colCase.equals("upper") ? keyColumn.toUpperCase() : keyColumn.toLowerCase());
                    if (filterObsvCode.contains(",")) {
                        String[] values = filterObsvCode.split(",");
                        String placeholders = String.join(", ", java.util.Collections.nCopies(values.length, "?"));
                        filterClause.append(" AND ").append(qi(colName, dbType)).append(" IN (").append(placeholders).append(")");
                        for (String v : values) filterParams.add(v.trim());
                    } else {
                        filterClause.append(" AND ").append(qi(colName, dbType)).append(" = ?");
                        filterParams.add(filterObsvCode.trim());
                    }
                }

                String sql;
                if (isMysql(dbType)) {
                    String dc = qi(dateColumn, dbType);
                    String tc = qi(timeColumn, dbType);
                    sql = String.format(
                            "SELECT * FROM %s WHERE %s >= STR_TO_DATE(?, '%%Y%%m%%d') AND %s <= STR_TO_DATE(?, '%%Y%%m%%d')%s ORDER BY %s, %s",
                            qi(table, dbType), dc, dc, filterClause, dc, tc);
                } else {
                    String dc = qi(colCase.equals("upper") ? dateColumn.toUpperCase() : dateColumn.toLowerCase(), dbType);
                    String tc = qi(colCase.equals("upper") ? timeColumn.toUpperCase() : timeColumn.toLowerCase(), dbType);
                    sql = String.format(
                            "SELECT * FROM %s WHERE %s >= TO_DATE(?, 'YYYYMMDD') AND %s <= TO_DATE(?, 'YYYYMMDD')%s ORDER BY %s, %s",
                            qi(table, dbType), dc, dc, filterClause, dc, tc);
                }

                List<Object> allParams = new ArrayList<>();
                allParams.add(startDate);
                allParams.add(endDate);
                allParams.addAll(filterParams);

                List<Map<String, Object>> result = sourceJdbc.queryForList(sql, allParams.toArray());
                log.info("[LinkTableFetcher] 시간 범위 조회 성공: {} ({}건)", table, result.size());
                return result;
            } catch (Exception e) {
                log.debug("[LinkTableFetcher] 시간 범위 조회 시도 실패: {} - {}", table, e.getMessage());
            }
        }

        log.error("[LinkTableFetcher] 시간 범위 조회 실패 (모든 변형 시도): {}", obsvdataTable);
        return new ArrayList<>();
    }

    /**
     * obsv_time을 HHmmss 6자리 형식으로 정규화
     * TIME 타입 → "HH:MM:SS" → "HHmmss", null → "000000"
     */
    private String normalizeTime(String time) {
        if (time == null) return "000000";
        String s = time.replace(":", "");
        if (s.length() < 6) s = s + "0".repeat(6 - s.length());
        return s.substring(0, 6);
    }

    /**
     * 새 obsv_code를 link 테이블에 등록 (제원에는 있지만 link에는 없는 경우)
     * date/time은 NULL로 등록 → 다음 동기화에서 obsvdata가 있으면 업데이트됨
     */
    private void registerNewObsvCode(JdbcTemplate targetJdbc, String obsvCode) {
        try {
            String sql = String.format(
                    "INSERT INTO %s (%s, %s, %s, update_time) VALUES (?, NULL, NULL, CURRENT_TIMESTAMP) " +
                            "ON CONFLICT (%s) DO NOTHING",
                    linkTable, keyColumn, dateColumn, timeColumn, keyColumn);
            log.info("[LinkTableFetcher] link 등록 SQL 실행: {} / obsvCode={}", sql, obsvCode);
            int affected = targetJdbc.update(sql, obsvCode);
            log.info("[LinkTableFetcher] 새 obsv_code link 등록 완료: {} (affected={})", obsvCode, affected);
        } catch (Exception e) {
            log.error("[LinkTableFetcher] link 등록 실패: {} - {}", obsvCode, e.getMessage(), e);
        }
    }

    /**
     * Link 데이터 VO
     */
    private static class LinkData {
        final String obsvCode;
        final String obsvDate;  // YYYYMMDD 형식
        final String obsvTime;  // HHMMSS 형식

        LinkData(String obsvCode, String obsvDate, String obsvTime) {
            this.obsvCode = obsvCode;
            this.obsvDate = obsvDate;
            this.obsvTime = obsvTime;
        }
    }
}
