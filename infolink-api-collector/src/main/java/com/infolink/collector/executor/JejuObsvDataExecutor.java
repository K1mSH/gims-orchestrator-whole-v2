package com.infolink.collector.executor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infolink.collector.entity.ApiEndpoint;
import com.infolink.collector.entity.ApiParam;
import com.infolink.collector.service.ApiCallService;
import com.infolink.collector.config.DynamicDataSourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 제주 보조관측망 수위 관측 데이터 커스텀 실행기
 *
 * 레거시: InsertJeju.java
 * 타겟: TB_JEJU (RID PK, obsrvt_id+ymd+data_time+msn UK)
 * API: POST selectObsvData.json (site_code, data_time 파라미터)
 *
 * 플로우:
 * 1. tb_jeju_jewon에서 site_code(obsrvt_id) 목록 조회
 * 2. 오늘 날짜로 data_time 생성
 * 3. site_code별 루프 → API 호출 → dataTime 분리 → UPSERT
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JejuObsvDataExecutor implements CustomExecutor {

    private final ApiCallService apiCallService;
    private final DynamicDataSourceService dynamicDataSourceService;
    private final DataSource defaultDataSource;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter YMD_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Override
    public String getId() {
        return "jeju-obsv-data";
    }

    @Override
    public String getDisplayName() {
        return "제주 수위 관측 (site_code별 일일 수집)";
    }

    @Override
    public CustomExecutionResult execute(ApiEndpoint endpoint, List<ApiParam> params,
                                          Map<String, String> overrides, String triggeredBy) {
        int totalResponseCount = 0;
        int totalInsertCount = 0;
        int totalUpdateCount = 0;
        int totalSkipCount = 0;

        try {
            DataSource ds = resolveDataSource(endpoint);
            JdbcTemplate jdbc = new JdbcTemplate(ds);

            // === STEP 1: site_code 목록 조회 (tb_jeju_jewon) ===
            List<String> siteCodes = jdbc.queryForList(
                    "SELECT obsrvt_id FROM tb_jeju_jewon", String.class);
            if (siteCodes.isEmpty()) {
                return new CustomExecutionResult(200, 0, 0, 0, 0,
                        "tb_jeju_jewon에 데이터 없음 — 제원 수집을 먼저 실행하세요.");
            }
            log.info("[제주관측] site_code {}건 조회, API 루프 시작", siteCodes.size());

            // === STEP 2: data_time 생성 (오늘 00:00:00) ===
            String dataTime = LocalDate.now().format(YMD_FORMAT) + "000000";

            // === STEP 3: site_code별 API 호출 + UPSERT ===
            for (String siteCode : siteCodes) {
                try {
                    // API 호출 — URL에 쿼리 파라미터 직접 추가
                    String callUrl = endpoint.getUrl()
                            + "?site_code=" + siteCode
                            + "&data_time=" + dataTime;

                    ApiEndpoint callEndpoint = ApiEndpoint.builder()
                            .url(callUrl)
                            .httpMethod("POST")
                            .contentType("application/x-www-form-urlencoded")
                            .authType(endpoint.getAuthType())
                            .authConfig(endpoint.getAuthConfig())
                            .build();

                    ApiCallService.CallResult result = apiCallService.call(callEndpoint, params, overrides);
                    if (!result.isSuccess()) {
                        log.warn("[제주관측] API 실패: siteCode={}, status={}", siteCode, result.statusCode());
                        totalSkipCount++;
                        continue;
                    }

                    List<Map<String, Object>> items = parseDataArray(result.body());
                    totalResponseCount += items.size();

                    // 기존 데이터 조회 (신규/갱신 구분)
                    Set<String> existingKeys = new HashSet<>();
                    jdbc.query(
                            "SELECT obsrvt_id || '|' || ymd || '|' || data_time || '|' || msn FROM tb_jeju WHERE obsrvt_id = ?",
                            rs -> { existingKeys.add(rs.getString(1)); },
                            siteCode);

                    for (Map<String, Object> item : items) {
                        try {
                            String fullDataTime = str(item, "dataTime");
                            if (fullDataTime == null || fullDataTime.length() < 14) {
                                totalSkipCount++;
                                continue;
                            }
                            String ymd = fullDataTime.substring(0, 8);
                            String time = fullDataTime.substring(8, 14);
                            String msn = str(item, "mSn");
                            String key = siteCode + "|" + ymd + "|" + time + "|" + msn;

                            upsert(jdbc, siteCode, ymd, time, item);

                            if (existingKeys.contains(key)) {
                                totalUpdateCount++;
                            } else {
                                totalInsertCount++;
                                existingKeys.add(key);
                            }
                        } catch (Exception e) {
                            log.warn("[제주관측] 건별 실패: siteCode={}, error={}", siteCode, e.getMessage());
                            totalSkipCount++;
                        }
                    }

                    if (!items.isEmpty()) {
                        log.debug("[제주관측] siteCode={}: {}건 처리", siteCode, items.size());
                    }

                } catch (Exception e) {
                    log.warn("[제주관측] siteCode={} 처리 실패: {}", siteCode, e.getMessage());
                    totalSkipCount++;
                }
            }

            log.info("[제주관측] 완료 — site: {}개, 수신: {}건, 신규: {}건, 갱신: {}건, 스킵: {}건",
                    siteCodes.size(), totalResponseCount, totalInsertCount, totalUpdateCount, totalSkipCount);

            return new CustomExecutionResult(200, totalResponseCount,
                    totalInsertCount, totalUpdateCount, totalSkipCount, null);

        } catch (Exception e) {
            log.error("[제주관측] 실행 실패: {}", e.getMessage(), e);
            return new CustomExecutionResult(0, totalResponseCount,
                    totalInsertCount, totalUpdateCount, totalSkipCount, e.getMessage());
        }
    }

    /**
     * UPSERT (obsrvt_id + ymd + data_time + msn 복합 UK)
     */
    private void upsert(JdbcTemplate jdbc, String siteCode, String ymd, String time,
                         Map<String, Object> item) {
        String sql = """
                INSERT INTO tb_jeju (obsrvt_id, ymd, data_time, gl, scond, wtemp, msn)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT ON CONSTRAINT uk_tb_jeju_obs DO UPDATE SET
                    gl = EXCLUDED.gl,
                    scond = EXCLUDED.scond,
                    wtemp = EXCLUDED.wtemp
                """;

        jdbc.update(sql,
                siteCode,
                ymd,
                time,
                str(item, "gl"),
                str(item, "scond"),
                str(item, "wTemp"),
                str(item, "mSn"));
    }

    // === 유틸 ===

    private List<Map<String, Object>> parseDataArray(String body) throws Exception {
        Map<String, Object> response = objectMapper.readValue(body, new TypeReference<>() {});
        Object data = response.get("data");
        if (data instanceof List) {
            return (List<Map<String, Object>>) data;
        }
        return List.of();
    }

    private DataSource resolveDataSource(ApiEndpoint endpoint) {
        if (endpoint.getTargetDatasourceId() != null && !endpoint.getTargetDatasourceId().isBlank()) {
            return dynamicDataSourceService.getDataSource(endpoint.getTargetDatasourceId());
        }
        return defaultDataSource;
    }

    private String str(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }
}
