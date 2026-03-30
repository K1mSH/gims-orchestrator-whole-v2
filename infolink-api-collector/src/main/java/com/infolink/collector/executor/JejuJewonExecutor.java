package com.infolink.collector.executor;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infolink.collector.entity.ApiEndpoint;
import com.infolink.collector.entity.ApiParam;
import com.infolink.collector.service.ApiCallService;
import com.infolink.collector.config.DynamicDataSourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.proj4j.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.*;

/**
 * 제주 보조관측망 관측점 마스터 커스텀 실행기
 *
 * 레거시: InsetTb_jeju_jewon.java
 * 타겟: TB_JEJU_JEWON (OBSRVT_ID PK)
 * API: POST selectObsv.json (파라미터 없이 전체 조회)
 *
 * 플로우:
 * 1. API 호출 → data 배열 파싱
 * 2. 건별 처리:
 *    - 코드변환 3종 (용도, 음용여부, 지역)
 *    - 좌표변환 EPSG:5186 → EPSG:4326 (siteLitd 비어있고 wX 존재 시)
 * 3. UPSERT (obsrvt_id 기준)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JejuJewonExecutor implements CustomExecutor {

    private final ApiCallService apiCallService;
    private final DynamicDataSourceService dynamicDataSourceService;
    private final DataSource defaultDataSource;
    private final ObjectMapper objectMapper;

    private final BasicCoordinateTransform coordTransform = createTransform();

    @Override
    public String getId() {
        return "jeju-jewon";
    }

    @Override
    public String getDisplayName() {
        return "제주 관측점 마스터 (좌표변환+코드변환)";
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

            // === STEP 1: API 호출 ===
            log.info("[제주제원] API 호출: {}", endpoint.getUrl());
            ApiCallService.CallResult result = apiCallService.call(endpoint, params, overrides);
            if (!result.isSuccess()) {
                return new CustomExecutionResult(result.statusCode(), 0, 0, 0, 0,
                        "API 호출 실패: " + result.error());
            }

            List<Map<String, Object>> items = parseDataArray(result.body());
            totalResponseCount = items.size();
            log.info("[제주제원] 수신: {}건", items.size());

            // 기존 PK 조회 (신규/갱신 카운트용)
            Set<String> existingIds = new HashSet<>(
                    jdbc.queryForList("SELECT obsrvt_id FROM tb_jeju_jewon", String.class));

            // === STEP 2: 건별 변환 + UPSERT ===
            for (Map<String, Object> item : items) {
                try {
                    convertCodes(item);
                    convertCoordinates(item);

                    String obsrvtId = str(item, "siteCode");
                    upsert(jdbc, item);

                    if (existingIds.contains(obsrvtId)) {
                        totalUpdateCount++;
                    } else {
                        totalInsertCount++;
                        existingIds.add(obsrvtId);
                    }
                } catch (Exception e) {
                    log.warn("[제주제원] 건별 처리 실패: siteCode={}, error={}",
                            item.get("siteCode"), e.getMessage());
                    totalSkipCount++;
                }
            }

            log.info("[제주제원] 완료 — 수신: {}건, 신규: {}건, 갱신: {}건, 스킵: {}건",
                    totalResponseCount, totalInsertCount, totalUpdateCount, totalSkipCount);

            return new CustomExecutionResult(200, totalResponseCount,
                    totalInsertCount, totalUpdateCount, totalSkipCount, null);

        } catch (Exception e) {
            log.error("[제주제원] 실행 실패: {}", e.getMessage(), e);
            return new CustomExecutionResult(0, totalResponseCount,
                    totalInsertCount, totalUpdateCount, totalSkipCount, e.getMessage());
        }
    }

    /**
     * 코드변환 3종 (레거시 InsetTb_jeju_jewon 로직 그대로)
     */
    private void convertCodes(Map<String, Object> item) {
        // 1. wDtlSrv → ugrwtr_prpos_code
        String wDtlSrv = str(item, "wDtlSrv");
        if (wDtlSrv != null) {
            if (wDtlSrv.contains("상수도")) {
                item.put("ugrwtr_prpos_code", "18");
            } else if (wDtlSrv.contains("농업")) {
                item.put("ugrwtr_prpos_code", "19");
            } else {
                item.put("ugrwtr_prpos_code", "40");
            }
        }

        // 2. wDrinkYn → drnk_at
        String wDrinkYn = str(item, "wDrinkYn");
        if (wDrinkYn != null) {
            if ("비음용".equals(wDrinkYn)) {
                item.put("drnk_at", "0");
            } else {
                item.put("drnk_at", "1");
            }
        }

        // 3. wDevlocCi → legaldong_code
        String wDevlocCi = str(item, "wDevlocCi");
        if (wDevlocCi != null) {
            if ("제주시".equals(wDevlocCi)) {
                item.put("legaldong_code", "6510000");
            } else if ("서귀포시".equals(wDevlocCi)) {
                item.put("legaldong_code", "6520000");
            } else {
                item.put("legaldong_code", "");
            }
        }
    }

    /**
     * 좌표변환 EPSG:5186 → EPSG:4326
     * 레거시 조건: siteLitd가 비어있고 wX가 있을 때만 변환
     */
    private void convertCoordinates(Map<String, Object> item) {
        String siteLitd = str(item, "siteLitd");
        String wX = str(item, "wX");

        if (siteLitd != null && !siteLitd.isEmpty()) return;
        if (wX == null || wX.isEmpty()) return;

        String wY = str(item, "wY");
        if (wY == null || wY.isEmpty()) return;

        try {
            double x = Double.parseDouble(wX);
            double y = Double.parseDouble(wY);

            ProjCoordinate src = new ProjCoordinate(x, y);
            ProjCoordinate dst = new ProjCoordinate();
            coordTransform.transform(src, dst);

            item.put("siteLitd", String.valueOf(dst.x));
            item.put("siteLttd", String.valueOf(dst.y));
        } catch (Exception e) {
            log.warn("[제주제원] 좌표변환 실패: wX={}, wY={}, error={}", wX, wY, e.getMessage());
        }
    }

    /**
     * UPSERT (obsrvt_id 기준 ON CONFLICT) — 실제 TB_JEJU_JEWON 컬럼 매핑
     */
    private void upsert(JdbcTemplate jdbc, Map<String, Object> item) {
        String sql = """
                INSERT INTO tb_jeju_jewon (
                    obsrvt_id, obsrvt_nm, spot_nm,
                    lo_value, la_value, tmx_value, tmy_value,
                    extn_csng_calbr, bunji, sigun_nm, emd_nm, ho, li_nm,
                    drnk_at, use_at, al_value, wal,
                    ugrwtr_prpos_code, legaldong_code
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '1', ?, ?, ?, ?)
                ON CONFLICT (obsrvt_id) DO UPDATE SET
                    obsrvt_nm = EXCLUDED.obsrvt_nm,
                    spot_nm = EXCLUDED.spot_nm,
                    lo_value = EXCLUDED.lo_value,
                    la_value = EXCLUDED.la_value,
                    tmx_value = EXCLUDED.tmx_value,
                    tmy_value = EXCLUDED.tmy_value,
                    extn_csng_calbr = EXCLUDED.extn_csng_calbr,
                    bunji = EXCLUDED.bunji,
                    sigun_nm = EXCLUDED.sigun_nm,
                    emd_nm = EXCLUDED.emd_nm,
                    ho = EXCLUDED.ho,
                    li_nm = EXCLUDED.li_nm,
                    drnk_at = EXCLUDED.drnk_at,
                    use_at = EXCLUDED.use_at,
                    al_value = EXCLUDED.al_value,
                    wal = EXCLUDED.wal,
                    ugrwtr_prpos_code = EXCLUDED.ugrwtr_prpos_code,
                    legaldong_code = EXCLUDED.legaldong_code
                """;

        String siteName = str(item, "siteName");

        jdbc.update(sql,
                str(item, "siteCode"),          // obsrvt_id
                siteName,                        // obsrvt_nm
                siteName,                        // spot_nm (= obsrvt_nm)
                str(item, "siteLitd"),           // lo_value
                str(item, "siteLttd"),           // la_value
                toBigDecimal(item, "wX"),        // tmx_value
                toBigDecimal(item, "wY"),        // tmy_value
                str(item, "wCsiDia"),            // extn_csng_calbr
                str(item, "wDevlocBunji"),       // bunji
                str(item, "wDevlocCi"),          // sigun_nm
                str(item, "wDevlocDo"),          // emd_nm
                str(item, "wDevlocHo"),          // ho
                str(item, "wDevlocLi"),          // li_nm
                str(item, "drnk_at"),            // drnk_at
                // use_at = '1' (고정값, SQL에 하드코딩)
                str(item, "wElev"),              // al_value
                toBigDecimal(item, "wNatWtlv"),  // wal
                str(item, "ugrwtr_prpos_code"),  // ugrwtr_prpos_code
                str(item, "legaldong_code")      // legaldong_code
        );
    }

    // === 유틸 ===

    private List<Map<String, Object>> parseDataArray(String body) throws Exception {
        Map<String, Object> response = objectMapper.readValue(body, new TypeReference<>() {});
        Object data = response.get("data");
        if (data instanceof List) {
            return (List<Map<String, Object>>) data;
        }
        throw new IllegalStateException("응답에 data 배열이 없습니다.");
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

    private BigDecimal toBigDecimal(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) return null;
        String s = v.toString().trim();
        if (s.isEmpty()) return null;
        try { return new BigDecimal(s); } catch (Exception e) { return null; }
    }

    private static BasicCoordinateTransform createTransform() {
        CRSFactory factory = new CRSFactory();
        CoordinateReferenceSystem src = factory.createFromName("EPSG:5186");
        CoordinateReferenceSystem dst = factory.createFromName("EPSG:4326");
        return new BasicCoordinateTransform(src, dst);
    }
}
