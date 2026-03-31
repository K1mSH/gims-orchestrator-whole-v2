package com.infolink.collector.executor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infolink.collector.entity.ApiEndpoint;
import com.infolink.collector.entity.ApiParam;
import com.infolink.collector.service.ApiCallService;
import com.infolink.collector.service.DynamicParamResolver;
import com.infolink.collector.config.DynamicDataSourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.*;

/**
 * [D4] 제주 수질검사 커스텀 실행기
 *
 * 레거시: RgetnwaviProgram.java
 * API: POST selectSujil.json (data_date 파라미터, 페이징 없음)
 * 타겟: rgetnwavi05 (수질검사, PK: perm_nt_no)
 *       rgetnwavi06 (수질검사내역, 복합PK: perm_nt_no + list_code)
 *
 * 플로우:
 * 1. data_date 동적 파라미터 resolve (YEAR, offset=-1)
 * 2. API 1회 호출 (페이징 없음)
 * 3. 건별: 항목명 한→영 매핑 + 코드변환 3종
 * 4. UPSERT 2회 (wavi05 + wavi06)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JejuWaterQualityExecutor implements CustomExecutor {

    private final ApiCallService apiCallService;
    private final DynamicParamResolver dynamicParamResolver;
    private final DynamicDataSourceService dynamicDataSourceService;
    private final DataSource defaultDataSource;
    private final ObjectMapper objectMapper;

    /** 항목명 한→영 매핑 (레거시 if-else 11종 그대로) */
    private static final Map<String, String> ITEM_NAME_MAP = Map.ofEntries(
            Map.entry("탁도(Turbidity)", "Turbidity"),
            Map.entry("총대장균군", "TotalColiforms"),
            Map.entry("일반세균", "Coliforms"),
            Map.entry("암모니아성질소", "NH3-N"),
            Map.entry("색도(Colority)", "Color"),
            Map.entry("불소(F)", "F"),
            Map.entry("분원성대장균군", "Ec/Fe Coliforms"),
            Map.entry("망간(Mn)", "Mn"),
            Map.entry("맛(Taste)", "Taste"),
            Map.entry("냄새(Odor)", "Odor"),
            Map.entry("알루미늄(Al)", "Al")
    );

    @Override
    public String getId() {
        return "jeju-water-quality";
    }

    @Override
    public String getDisplayName() {
        return "제주 수질검사 (항목매핑+코드변환, 2테이블)";
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

            // === STEP 1: data_date resolve ===
            String dataDate = resolveDataDate(params, overrides);
            log.info("[제주수질] data_date={}", dataDate);

            // === STEP 2: API 호출 (1회, 페이징 없음) ===
            String callUrl = endpoint.getUrl() + "?data_date=" + dataDate;
            ApiEndpoint callEndpoint = ApiEndpoint.builder()
                    .url(callUrl)
                    .httpMethod("POST")
                    .contentType("application/x-www-form-urlencoded")
                    .authType(endpoint.getAuthType())
                    .authConfig(endpoint.getAuthConfig())
                    .build();

            ApiCallService.CallResult result = apiCallService.call(callEndpoint, Collections.emptyList(), overrides);
            if (!result.isSuccess()) {
                return new CustomExecutionResult(result.statusCode(), 0, 0, 0, 0,
                        "API 호출 실패: " + result.error());
            }

            List<Map<String, Object>> items = parseDataArray(result.body());
            totalResponseCount = items.size();
            log.info("[제주수질] 수신: {}건", items.size());

            // 기존 PK 조회 (신규/갱신 구분)
            Set<String> existing05 = new HashSet<>(
                    jdbc.queryForList("SELECT perm_nt_no FROM rgetnwavi05", String.class));
            Set<String> existing06 = new HashSet<>();
            jdbc.query("SELECT perm_nt_no || '|' || list_code FROM rgetnwavi06",
                    rs -> { existing06.add(rs.getString(1)); });

            // === STEP 3: 건별 처리 ===
            for (Map<String, Object> item : items) {
                try {
                    // 항목명 한→영 매핑
                    String itemName = str(item, "itemName");
                    String listCode = ITEM_NAME_MAP.get(itemName);
                    if (listCode == null) {
                        log.warn("[제주수질] 매핑 없는 항목 스킵: itemName={}", itemName);
                        totalSkipCount++;
                        continue;
                    }

                    // 코드변환
                    String permissionNum = str(item, "permissionNum");
                    String acceptNum = str(item, "acceptNum");
                    String qwIspSno = (acceptNum != null && acceptNum.contains("-"))
                            ? acceptNum.substring(acceptNum.lastIndexOf("-") + 1) : acceptNum;

                    String checkAddress = str(item, "checkAddress");
                    String relTransCggCode = (checkAddress != null && checkAddress.contains("제주시"))
                            ? "6510000" : "6520000";

                    String codeNm = str(item, "codeNm");
                    String qwIspSortCode = "음용수(원수)".equals(codeNm) ? "A" : "D";

                    String dataIndate = str(item, "dataIndate");
                    String dataValue = str(item, "dataValue");

                    // UPSERT wavi05
                    upsert05(jdbc, permissionNum, relTransCggCode, qwIspSno, qwIspSortCode, dataIndate);

                    // UPSERT wavi06
                    upsert06(jdbc, permissionNum, listCode, relTransCggCode, qwIspSno, qwIspSortCode, dataValue);

                    // 카운팅 (wavi06 복합키 기준)
                    String key06 = permissionNum + "|" + listCode;
                    if (existing06.contains(key06)) {
                        totalUpdateCount++;
                    } else {
                        totalInsertCount++;
                        existing06.add(key06);
                        existing05.add(permissionNum);
                    }

                } catch (Exception e) {
                    log.warn("[제주수질] 건별 처리 실패: permissionNum={}, error={}",
                            item.get("permissionNum"), e.getMessage());
                    totalSkipCount++;
                }
            }

            log.info("[제주수질] 완료 — 수신: {}건, 신규: {}건, 갱신: {}건, 스킵: {}건",
                    totalResponseCount, totalInsertCount, totalUpdateCount, totalSkipCount);

            return new CustomExecutionResult(200, totalResponseCount,
                    totalInsertCount, totalUpdateCount, totalSkipCount, null);

        } catch (Exception e) {
            log.error("[제주수질] 실행 실패: {}", e.getMessage(), e);
            return new CustomExecutionResult(0, totalResponseCount,
                    totalInsertCount, totalUpdateCount, totalSkipCount, e.getMessage());
        }
    }

    // === data_date resolve ===

    private String resolveDataDate(List<ApiParam> params, Map<String, String> overrides) {
        for (ApiParam p : params) {
            if ("data_date".equals(p.getParamName())) {
                String overrideVal = overrides != null ? overrides.get("data_date") : null;
                return dynamicParamResolver.resolve(p, overrideVal);
            }
        }
        return String.valueOf(java.time.LocalDate.now().getYear() - 1);
    }

    // === UPSERT: rgetnwavi05 (수질검사) ===

    private void upsert05(JdbcTemplate jdbc, String permNtNo, String relTransCggCode,
                           String qwIspSno, String qwIspSortCode, String dataIndate) {
        String sql = """
                INSERT INTO rgetnwavi05 (perm_nt_no, rel_trans_cgg_code, qw_isp_sno, qw_isp_sort_code, first_reg_dthr)
                VALUES (?, ?, ?, ?, TO_TIMESTAMP(?, 'YYYY/MM/DD HH24:MI:SS'))
                ON CONFLICT (perm_nt_no) DO UPDATE SET
                    rel_trans_cgg_code = EXCLUDED.rel_trans_cgg_code,
                    qw_isp_sno = EXCLUDED.qw_isp_sno,
                    qw_isp_sort_code = EXCLUDED.qw_isp_sort_code,
                    first_reg_dthr = EXCLUDED.first_reg_dthr
                """;
        jdbc.update(sql, permNtNo, relTransCggCode, qwIspSno, qwIspSortCode, dataIndate);
    }

    // === UPSERT: rgetnwavi06 (수질검사내역, 복합PK) ===

    private void upsert06(JdbcTemplate jdbc, String permNtNo, String listCode,
                           String relTransCggCode, String qwIspSno, String qwIspSortCode, String dataValue) {
        String sql = """
                INSERT INTO rgetnwavi06 (perm_nt_no, list_code, rel_trans_cgg_code, qw_isp_sno, qw_isp_sort_code, rt, elig_yn)
                VALUES (?, ?, ?, ?, ?, ?, '1')
                ON CONFLICT (perm_nt_no, list_code) DO UPDATE SET
                    rel_trans_cgg_code = EXCLUDED.rel_trans_cgg_code,
                    qw_isp_sno = EXCLUDED.qw_isp_sno,
                    qw_isp_sort_code = EXCLUDED.qw_isp_sort_code,
                    rt = EXCLUDED.rt,
                    elig_yn = EXCLUDED.elig_yn
                """;
        jdbc.update(sql, permNtNo, listCode, relTransCggCode, qwIspSno, qwIspSortCode, dataValue);
    }

    // === 유틸 ===

    @SuppressWarnings("unchecked")
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
        if (v == null) return null;
        String s = v.toString();
        return s.isEmpty() ? null : s;
    }
}
