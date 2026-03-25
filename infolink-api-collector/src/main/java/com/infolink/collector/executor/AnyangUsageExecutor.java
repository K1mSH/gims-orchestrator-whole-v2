package com.infolink.collector.executor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infolink.collector.entity.ApiEndpoint;
import com.infolink.collector.entity.ApiParam;
import com.infolink.collector.service.ApiCallService;
import com.infolink.collector.config.DynamicDataSourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 안양시 이용량 커스텀 실행기
 *
 * 플로우:
 * 1. API 1 (FAC) 호출 → anyang_api_fac 테이블 UPSERT
 * 2. API 2 (DATA) 호출 → anyang_api_data 테이블 INSERT + usgqty 산출
 * 3. FAC + DATA JOIN → use_legacy_data INSERT (중복 제외)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AnyangUsageExecutor implements CustomExecutor {

    private final ApiCallService apiCallService;
    private final DynamicDataSourceService dynamicDataSourceService;
    private final DataSource defaultDataSource;  // 자체 DB (fallback)
    private final ObjectMapper objectMapper;

    @Value("${anyang.api.fac-url:http://localhost:8084/mock/anyang/fac}")
    private String facApiUrl;

    @Value("${anyang.api.data-url:http://localhost:8084/mock/anyang/data}")
    private String dataApiUrl;

    private static final DateTimeFormatter DTM_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public String getId() {
        return "anyang-usage";
    }

    @Override
    public String getDisplayName() {
        return "안양시 이용량 (FAC + DATA + LEGACY)";
    }

    @Override
    public CustomExecutionResult execute(ApiEndpoint endpoint, List<ApiParam> params,
                                          Map<String, String> overrides, String triggeredBy) {
        int totalResponseCount = 0;
        int totalInsertCount = 0;
        int totalSkipCount = 0;

        try {
            // DataSource 확보
            DataSource ds = resolveDataSource(endpoint);
            JdbcTemplate jdbc = new JdbcTemplate(ds);

            // === STEP 1: FAC API 호출 + UPSERT ===
            log.info("[안양] STEP 1: 시설정보(FAC) API 호출 — {}", facApiUrl);
            ApiEndpoint facEndpoint = ApiEndpoint.builder()
                    .url(facApiUrl).httpMethod("GET")
                    .authType(endpoint.getAuthType()).authConfig(endpoint.getAuthConfig())
                    .build();
            ApiCallService.CallResult facResult = apiCallService.call(facEndpoint, params, overrides);
            if (!facResult.isSuccess()) {
                return new CustomExecutionResult(facResult.statusCode(), 0, 0, 0,
                        "FAC API 호출 실패: " + facResult.error());
            }

            List<Map<String, Object>> facItems = parseDataArray(facResult.body());
            totalResponseCount += facItems.size();
            log.info("[안양] FAC 수신: {}건", facItems.size());

            int facInserted = upsertFacData(jdbc, facItems);
            totalInsertCount += facInserted;
            log.info("[안양] FAC UPSERT: {}건", facInserted);

            // === STEP 2: DATA API 호출 + INSERT (usgqty 산출) ===
            log.info("[안양] STEP 2: 이용량(DATA) API 호출 — {}", dataApiUrl);
            ApiEndpoint dataEndpoint = ApiEndpoint.builder()
                    .url(dataApiUrl).httpMethod("GET")
                    .authType(endpoint.getAuthType()).authConfig(endpoint.getAuthConfig())
                    .build();

            ApiCallService.CallResult dataResult = apiCallService.call(dataEndpoint, params, overrides);
            if (!dataResult.isSuccess()) {
                return new CustomExecutionResult(dataResult.statusCode(), totalResponseCount, totalInsertCount, 0,
                        "DATA API 호출 실패: " + dataResult.error());
            }

            List<Map<String, Object>> dataItems = parseDataArray(dataResult.body());
            totalResponseCount += dataItems.size();
            log.info("[안양] DATA 수신: {}건", dataItems.size());

            // === STEP 3: DATA INSERT + USE_LEGACY_DATA INSERT (건별) ===
            log.info("[안양] STEP 3: DATA INSERT + LEGACY 기록 (건별 처리)");
            for (Map<String, Object> item : dataItems) {
                try {
                    insertDataRecord(jdbc, item);
                    insertLegacyRecord(jdbc, item);
                    totalInsertCount++;
                } catch (Exception e) {
                    log.warn("[안양] 건별 처리 실패: account_no={}, error={}", item.get("account_no"), e.getMessage());
                    totalSkipCount++;
                }
            }

            log.info("[안양] 완료 — 총 수신: {}건, INSERT: {}건, 스킵: {}건",
                    totalResponseCount, totalInsertCount, totalSkipCount);

            return new CustomExecutionResult(200, totalResponseCount, totalInsertCount, totalSkipCount, null);

        } catch (Exception e) {
            log.error("[안양] 실행 실패: {}", e.getMessage(), e);
            return new CustomExecutionResult(0, totalResponseCount, totalInsertCount, totalSkipCount, e.getMessage());
        }
    }

    /**
     * API 응답에서 data 배열 추출
     */
    private List<Map<String, Object>> parseDataArray(String body) throws Exception {
        Map<String, Object> response = objectMapper.readValue(body, new TypeReference<>() {});
        Object data = response.get("data");
        if (data instanceof List) {
            return (List<Map<String, Object>>) data;
        }
        throw new IllegalStateException("응답에 data 배열이 없습니다.");
    }

    /**
     * FAC 데이터 UPSERT (account_no 기준)
     */
    private int upsertFacData(JdbcTemplate jdbc, List<Map<String, Object>> items) {
        String sql = """
                INSERT INTO anyang_api_fac (account_no, company_cd, company_nm, account_nm, status_device,
                    connect_dtm, state_display, device_sn, gps_latitude, gps_longitude, meter_sn,
                    caliber_cd, mt_down, mt_down_dtm, mt_last_dtm, full_addr, cdma_no, nwk)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (account_no) DO UPDATE SET
                    company_cd = EXCLUDED.company_cd, company_nm = EXCLUDED.company_nm,
                    account_nm = EXCLUDED.account_nm, status_device = EXCLUDED.status_device,
                    connect_dtm = EXCLUDED.connect_dtm, state_display = EXCLUDED.state_display,
                    device_sn = EXCLUDED.device_sn, gps_latitude = EXCLUDED.gps_latitude,
                    gps_longitude = EXCLUDED.gps_longitude, meter_sn = EXCLUDED.meter_sn,
                    caliber_cd = EXCLUDED.caliber_cd, mt_down = EXCLUDED.mt_down,
                    mt_down_dtm = EXCLUDED.mt_down_dtm, mt_last_dtm = EXCLUDED.mt_last_dtm,
                    full_addr = EXCLUDED.full_addr, cdma_no = EXCLUDED.cdma_no, nwk = EXCLUDED.nwk
                """;

        int count = 0;
        for (Map<String, Object> item : items) {
            jdbc.update(sql,
                    str(item, "account_no"), str(item, "company_cd"), str(item, "company_nm"),
                    str(item, "account_nm"), str(item, "status_device"),
                    toTimestamp(item, "connect_dtm"), str(item, "state_display"),
                    str(item, "device_sn"), str(item, "gps_latitude"), str(item, "gps_longitude"),
                    str(item, "meter_sn"), str(item, "caliber_cd"), str(item, "mt_down"),
                    toTimestamp(item, "mt_down_dtm"), toTimestamp(item, "mt_last_dtm"),
                    str(item, "full_addr"), str(item, "cdma_no"), str(item, "nwk"));
            count++;
        }
        return count;
    }

    /**
     * DATA 건별 INSERT — usgqty는 서브쿼리로 산출
     */
    private void insertDataRecord(JdbcTemplate jdbc, Map<String, Object> item) {
        String sql = """
                INSERT INTO anyang_api_data (account_no, meter_dtm, value, digits, leak_state,
                    term_batt, m_low_batt, m_leak, m_over_load, m_reverse, m_not_use,
                    db_in_dtm, db_in_seq, last_meter_value, usgqty)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                    ?,
                    ? - COALESCE((SELECT MAX(last_meter_value) FROM anyang_api_data
                                  WHERE account_no = ? AND meter_dtm < ?), 0))
                ON CONFLICT (account_no, meter_dtm) DO UPDATE SET
                    value = EXCLUDED.value, digits = EXCLUDED.digits, leak_state = EXCLUDED.leak_state,
                    term_batt = EXCLUDED.term_batt, m_low_batt = EXCLUDED.m_low_batt, m_leak = EXCLUDED.m_leak,
                    m_over_load = EXCLUDED.m_over_load, m_reverse = EXCLUDED.m_reverse, m_not_use = EXCLUDED.m_not_use,
                    db_in_dtm = EXCLUDED.db_in_dtm, db_in_seq = EXCLUDED.db_in_seq,
                    last_meter_value = EXCLUDED.last_meter_value, usgqty = EXCLUDED.usgqty
                """;

        String accountNo = str(item, "account_no");
        Timestamp meterDtm = toTimestamp(item, "meter_dtm");
        Long value = toLong(item, "value");
        Long lastMeterValue = toLong(item, "last_meter_value");

        jdbc.update(sql,
                accountNo, meterDtm, value,
                toInt(item, "digits"), str(item, "leak_state"),
                toInt(item, "term_batt"), str(item, "m_low_batt"), str(item, "m_leak"),
                str(item, "m_over_load"), str(item, "m_reverse"), str(item, "m_not_use"),
                toTimestamp(item, "db_in_dtm"), toLong(item, "db_in_seq"),
                lastMeterValue,
                lastMeterValue, accountNo, meterDtm);
    }

    /**
     * USE_LEGACY_DATA INSERT — FAC JOIN DATA 기반, 중복 제외
     */
    private void insertLegacyRecord(JdbcTemplate jdbc, Map<String, Object> item) {
        String accountNo = str(item, "account_no");
        Long dbInSeq = toLong(item, "db_in_seq");

        // 중복 체크
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM use_legacy_data WHERE sn = ?",
                Integer.class, dbInSeq);
        if (exists != null && exists > 0) return;

        // FAC에서 cdma_no 조회 → telno 변환
        String cdmaNo = jdbc.queryForObject(
                "SELECT cdma_no FROM anyang_api_fac WHERE account_no = ?",
                String.class, accountNo);
        if (cdmaNo == null) return;

        // telno = '0' + cdma_no 8번째부터 끝까지 (Oracle: substr(cdma_no, 8, 11))
        String telno = "0" + cdmaNo.substring(7);

        jdbc.update("""
                INSERT INTO use_legacy_data (sn, telno, obsr_dt, last_measure_value, usgqty, last_change_dt)
                VALUES (?, ?, ?, ?, ?, NOW())
                """,
                dbInSeq, telno,
                toTimestamp(item, "meter_dtm"),
                toLong(item, "last_meter_value"),
                toLong(item, "useqty"));
    }

    private DataSource resolveDataSource(ApiEndpoint endpoint) {
        if (endpoint.getTargetDatasourceId() != null && !endpoint.getTargetDatasourceId().isBlank()) {
            return dynamicDataSourceService.getDataSource(endpoint.getTargetDatasourceId());
        }
        return defaultDataSource;
    }

    // --- 유틸 ---
    private String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }

    private Long toLong(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).longValue();
        try { return Long.parseLong(v.toString()); } catch (Exception e) { return null; }
    }

    private Integer toInt(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) return null;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return null; }
    }

    private Timestamp toTimestamp(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) return null;
        try {
            LocalDateTime ldt = LocalDateTime.parse(v.toString(), DTM_FORMAT);
            return Timestamp.valueOf(ldt);
        } catch (Exception e) { return null; }
    }
}
