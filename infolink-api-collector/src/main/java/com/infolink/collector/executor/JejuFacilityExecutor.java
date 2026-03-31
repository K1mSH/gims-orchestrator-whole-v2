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
import org.locationtech.proj4j.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.*;

/**
 * [D3] 제주 이용시설 커스텀 실행기
 *
 * 레거시: RgetstgmsProgram.java + yearProgram.java
 * API: POST selectJejuUse.json (reg_year, page 파라미터, 1000건 페이징)
 * 타겟: rgetnpmms01 (허가신고정보) + rgetstgms01 (이용실태정보)
 * PK: PERM_NT_NO (허가신고번호)
 *
 * 플로우:
 * 1. reg_year 동적 파라미터 resolve (YEAR 타입, offset=-1 → 작년)
 * 2. 첫 호출로 totalCount 확인 → 페이징 루프
 * 3. 건별: field 매핑 + 좌표변환(5186→4326 + 도분초) + 코드변환 8종
 * 4. UPSERT 2회 (pmms + stgms)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JejuFacilityExecutor implements CustomExecutor {

    private final ApiCallService apiCallService;
    private final DynamicParamResolver dynamicParamResolver;
    private final DynamicDataSourceService dynamicDataSourceService;
    private final DataSource defaultDataSource;
    private final ObjectMapper objectMapper;

    private final BasicCoordinateTransform coordTransform = createTransform();

    @Override
    public String getId() {
        return "jeju-facility";
    }

    @Override
    public String getDisplayName() {
        return "제주 이용시설 (페이징+좌표+코드변환, 2테이블)";
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

            // === STEP 1: reg_year resolve ===
            String regYear = resolveRegYear(params, overrides);
            log.info("[제주이용시설] reg_year={}", regYear);

            // === STEP 2: 첫 호출 → totalCount 확인 ===
            String baseUrl = endpoint.getUrl();
            Map<String, Object> firstResponse = callApi(endpoint, params, overrides, baseUrl, regYear, null);
            int totalCount = ((Number) firstResponse.getOrDefault("totalCount", 0)).intValue();
            log.info("[제주이용시설] totalCount={}", totalCount);

            if (totalCount == 0) {
                return new CustomExecutionResult(200, 0, 0, 0, 0, null);
            }

            // 기존 PK 조회 (신규/갱신 구분)
            Set<String> existingPmmsPks = new HashSet<>(
                    jdbc.queryForList("SELECT perm_nt_no FROM rgetnpmms01", String.class));
            Set<String> existingStgmsPks = new HashSet<>(
                    jdbc.queryForList("SELECT perm_nt_no FROM rgetstgms01", String.class));

            // === STEP 3: 페이징 루프 ===
            int totalPages = (int) Math.ceil((double) totalCount / 1000) + 1;
            for (int page = 1; page <= totalPages; page++) {
                Map<String, Object> response = callApi(endpoint, params, overrides, baseUrl, regYear, page);
                List<Map<String, Object>> items = extractDataArray(response);
                if (items.isEmpty()) break;

                totalResponseCount += items.size();
                log.info("[제주이용시설] page={}/{}, 수신={}건", page, totalPages, items.size());

                for (Map<String, Object> item : items) {
                    try {
                        Map<String, String> maps = new HashMap<>();

                        // field 매핑
                        mapFields(item, maps);

                        // 좌표변환 + 도분초 분리
                        convertCoordinates(item, maps);

                        // 코드변환 8종
                        convertCodes(item, maps);

                        String permNtNo = maps.get("perm_nt_no");
                        if (permNtNo == null || permNtNo.isBlank()) {
                            totalSkipCount++;
                            continue;
                        }

                        // UPSERT 2회
                        upsertPmms(jdbc, maps);
                        upsertStgms(jdbc, maps);

                        if (existingPmmsPks.contains(permNtNo) || existingStgmsPks.contains(permNtNo)) {
                            totalUpdateCount++;
                        } else {
                            totalInsertCount++;
                        }
                        existingPmmsPks.add(permNtNo);
                        existingStgmsPks.add(permNtNo);

                    } catch (Exception e) {
                        log.warn("[제주이용시설] 건별 처리 실패: wellNo={}, error={}",
                                item.get("wellNo"), e.getMessage());
                        totalSkipCount++;
                    }
                }
            }

            log.info("[제주이용시설] 완료 — 수신: {}건, 신규: {}건, 갱신: {}건, 스킵: {}건",
                    totalResponseCount, totalInsertCount, totalUpdateCount, totalSkipCount);

            return new CustomExecutionResult(200, totalResponseCount,
                    totalInsertCount, totalUpdateCount, totalSkipCount, null);

        } catch (Exception e) {
            log.error("[제주이용시설] 실행 실패: {}", e.getMessage(), e);
            return new CustomExecutionResult(0, totalResponseCount,
                    totalInsertCount, totalUpdateCount, totalSkipCount, e.getMessage());
        }
    }

    // === reg_year resolve ===

    private String resolveRegYear(List<ApiParam> params, Map<String, String> overrides) {
        for (ApiParam p : params) {
            if ("reg_year".equals(p.getParamName())) {
                String overrideVal = overrides != null ? overrides.get("reg_year") : null;
                return dynamicParamResolver.resolve(p, overrideVal);
            }
        }
        // 파라미터 미등록 시 기본값: 작년
        return String.valueOf(java.time.LocalDate.now().getYear() - 1);
    }

    // === API 호출 ===

    private Map<String, Object> callApi(ApiEndpoint endpoint, List<ApiParam> params,
                                         Map<String, String> overrides,
                                         String baseUrl, String regYear, Integer page) throws Exception {
        StringBuilder urlBuilder = new StringBuilder(baseUrl);
        urlBuilder.append("?reg_year=").append(regYear);
        if (page != null) {
            urlBuilder.append("&page=").append(page);
        }

        ApiEndpoint callEndpoint = ApiEndpoint.builder()
                .url(urlBuilder.toString())
                .httpMethod("POST")
                .contentType("application/x-www-form-urlencoded")
                .authType(endpoint.getAuthType())
                .authConfig(endpoint.getAuthConfig())
                .build();

        ApiCallService.CallResult result = apiCallService.call(callEndpoint, Collections.emptyList(), overrides);
        if (!result.isSuccess()) {
            throw new RuntimeException("API 호출 실패: status=" + result.statusCode() + ", error=" + result.error());
        }

        return objectMapper.readValue(result.body(), new TypeReference<>() {});
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractDataArray(Map<String, Object> response) {
        Object data = response.get("data");
        if (data instanceof List) {
            return (List<Map<String, Object>>) data;
        }
        return List.of();
    }

    // === field 매핑 (properties field_1 → field_2 대응) ===

    private void mapFields(Map<String, Object> item, Map<String, String> maps) {
        maps.put("perm_nt_no", str(item, "wellNo"));
        maps.put("dig_dph", str(item, "wellDepth"));
        maps.put("dig_diam", str(item, "wellDiameter"));
        maps.put("frw_pln_qua", str(item, "fwPlanQty"));
        maps.put("dyn_eqn_hrp", str(item, "pumpHrp"));
        maps.put("pipe_diam", str(item, "pipeDiameter"));
        maps.put("rwt_cap", str(item, "rwtCapacity"));
        maps.put("dvop_loc_regn_code", str(item, "wellDong"));
        maps.put("dvop_loc_san", str(item, "wellSan"));
        maps.put("dvop_loc_bunji", str(item, "wellBunji"));
        maps.put("dvop_loc_ho", str(item, "wellHo"));
        maps.put("elev", str(item, "wellElev"));
        maps.put("nat_wtlv", str(item, "natWtlv"));
        maps.put("stb_wtlv", str(item, "stbWtlv"));
        maps.put("y_use_qua", str(item, "yearUseQty"));
        maps.put("perm_yn", str(item, "permYn"));
        maps.put("yy_gbn", str(item, "yyGbn"));
        maps.put("uwater_souc_code", str(item, "uwaterSoucCode"));
    }

    // === 좌표변환 EPSG:5186→4326 + 도분초 문자열 분리 (레거시 방식 그대로) ===

    private void convertCoordinates(Map<String, Object> item, Map<String, String> maps) {
        String strX = str(item, "wellX");
        String strY = str(item, "wellY");
        if (strX == null || strX.isEmpty() || strY == null || strY.isEmpty()) return;

        try {
            double x = Double.parseDouble(strX);
            double y = Double.parseDouble(strY);

            ProjCoordinate src = new ProjCoordinate(x, y);
            ProjCoordinate dst = new ProjCoordinate();
            coordTransform.transform(src, dst);

            String coorX = Double.toString(dst.x);
            String coorY = Double.toString(dst.y);

            // 레거시 방식: 소수점 기준 문자열 자르기
            int idxX = coorX.indexOf(".");
            maps.put("litd_dg", coorX.substring(0, idxX));
            maps.put("litd_mint", coorX.substring(idxX + 1, Math.min(idxX + 3, coorX.length())));
            maps.put("litd_sc", coorX.substring(Math.min(idxX + 3, coorX.length()), Math.min(idxX + 5, coorX.length())));

            int idxY = coorY.indexOf(".");
            maps.put("lttd_dg", coorY.substring(0, idxY));
            maps.put("lttd_mint", coorY.substring(idxY + 1, Math.min(idxY + 3, coorY.length())));
            maps.put("lttd_sc", coorY.substring(Math.min(idxY + 3, coorY.length()), Math.min(idxY + 5, coorY.length())));
        } catch (Exception e) {
            log.warn("[제주이용시설] 좌표변환 실패: wellX={}, wellY={}, error={}", strX, strY, e.getMessage());
        }
    }

    // === 코드변환 8종 (레거시 RgetstgmsProgram 로직 그대로) ===

    private void convertCodes(Map<String, Object> item, Map<String, String> maps) {
        // 1. wellDrinkYn → uwater_pota_yn
        String wellDrinkYn = str(item, "wellDrinkYn");
        if (wellDrinkYn == null || wellDrinkYn.isEmpty()) {
            maps.put("uwater_pota_yn", "");
        } else if (wellDrinkYn.substring(1).equals("0")) {
            maps.put("uwater_pota_yn", "1");
        } else {
            maps.put("uwater_pota_yn", "0");
        }

        // 2. wellSrvCode → uwater_srv, uwater_srv_code
        String wellSrvCodeRaw = str(item, "wellSrvCode");
        String wellSrvCode = (wellSrvCodeRaw != null && wellSrvCodeRaw.length() > 1)
                ? wellSrvCodeRaw.substring(1) : "";
        if ("1".equals(wellSrvCode)) {
            maps.put("uwater_srv", "생활용");
            maps.put("uwater_srv_code", "1");
        } else if ("2".equals(wellSrvCode)) {
            maps.put("uwater_srv", "공업용");
            maps.put("uwater_srv_code", "2");
        } else if ("3".equals(wellSrvCode)) {
            maps.put("uwater_srv", "농어업용");
            maps.put("uwater_srv_code", "3");
        } else {
            maps.put("uwater_srv", "기타");
            maps.put("uwater_srv_code", "4");
        }

        // 3. wellDtlsrv_code → uwater_dtl_srv_code (복합 분기)
        String wellDtlsrvCode = str(item, "wellDtlsrv_code");
        if (wellDtlsrvCode == null) wellDtlsrvCode = "";
        if ("1".equals(wellSrvCode)) {
            if ("14".equals(wellDtlsrvCode)) maps.put("uwater_dtl_srv_code", "13");
            else if ("17".equals(wellDtlsrvCode)) maps.put("uwater_dtl_srv_code", "18");
            else if ("18".equals(wellDtlsrvCode)) maps.put("uwater_dtl_srv_code", "11");
            else if ("2".equals(wellDtlsrvCode)) maps.put("uwater_dtl_srv_code", "41");
            else maps.put("uwater_dtl_srv_code", "12");
        } else if ("2".equals(wellSrvCode)) {
            maps.put("uwater_dtl_srv_code", "24");
        } else if ("3".equals(wellSrvCode)) {
            if ("25".equals(wellDtlsrvCode)) maps.put("uwater_dtl_srv_code", "31");
            else if ("26".equals(wellDtlsrvCode)) maps.put("uwater_dtl_srv_code", "35");
            else if ("27".equals(wellDtlsrvCode)) maps.put("uwater_dtl_srv_code", "33");
            else if ("28".equals(wellDtlsrvCode)) maps.put("uwater_dtl_srv_code", "34");
            else maps.put("uwater_dtl_srv_code", "30");
        } else {
            if ("29".equals(wellDtlsrvCode)) maps.put("uwater_dtl_srv_code", "42");
            else maps.put("uwater_dtl_srv_code", "40");
        }

        // 4. countynm → rel_trans_cgg_code
        String countynm = str(item, "countynm");
        if ("제주시".equals(countynm)) {
            maps.put("rel_trans_cgg_code", "6510000");
        } else {
            maps.put("rel_trans_cgg_code", "6520000");
        }

        // 5. wellType → perm_nt_form_code
        String wellType = str(item, "wellType");
        if (wellType != null && wellType.equalsIgnoreCase("permit")) {
            maps.put("perm_nt_form_code", "1");
        } else if (wellType != null && wellType.equalsIgnoreCase("report")) {
            maps.put("perm_nt_form_code", "2");
        } else {
            maps.put("perm_nt_form_code", "0");
        }

        // 6. wellPublic → aplr_gbn_code
        String wellPublic = str(item, "wellPublic");
        if (wellPublic != null && wellPublic.equalsIgnoreCase("pub")) {
            maps.put("aplr_gbn_code", "05");
        } else {
            maps.put("aplr_gbn_code", "01");
        }

        // 7. wellStatusCode → lnho_raise_yn, end_nt_yn, perm_cancel_yn + 날짜
        String wellStatusCode = str(item, "wellStatusCode");
        String wellDealDt = str(item, "wellDealDt");
        String dealYmd = (wellDealDt != null) ? wellDealDt.replaceAll("-", "") : "";

        if ("03".equals(wellStatusCode)) {
            maps.put("lnho_raise_yn", "1");
            maps.put("end_nt_yn", " ");
            maps.put("lnho_raise_ymd", dealYmd);
        } else if ("06".equals(wellStatusCode)) {
            maps.put("end_nt_yn", "1");
            maps.put("lnho_raise_yn", " ");
            maps.put("dvus_enddt", dealYmd);
        } else {
            maps.put("end_nt_yn", "0");
            maps.put("perm_cancel_yn", "0");
            maps.put("lnho_raise_yn", "0");
            maps.put("lnho_raise_ymd", " ");
        }

        // 8. wellFstPermitDt → first_reg_dthr, perm_nt_ymd
        String registDt = str(item, "wellFstPermitDt");
        if (registDt != null) {
            maps.put("first_reg_dthr", registDt.replaceAll("-", "/"));
            maps.put("perm_nt_ymd", registDt.replaceAll("-", ""));
        }
    }

    // === UPSERT: rgetnpmms01 (허가신고정보, 33컬럼) ===

    private void upsertPmms(JdbcTemplate jdbc, Map<String, String> maps) {
        String sql = """
                INSERT INTO rgetnpmms01 (
                    perm_nt_no, rel_trans_cgg_code, sf_team_code, perm_nt_form_code,
                    aplr_gbn_code, perm_yn, lnho_raise_yn, end_nt_yn, perm_cancel_yn,
                    jgong_deal_yn, first_reg_dthr, dvop_loc_regn_code, dvop_loc_san,
                    dvop_loc_bunji, dvop_loc_ho, org_sno, perm_nt_ymd,
                    uwater_srv, uwater_srv_code, uwater_pota_yn,
                    dig_dph, dig_diam, litd_dg, litd_mint, litd_sc,
                    lttd_dg, lttd_mint, lttd_sc, frw_pln_qua, dyn_eqn_hrp,
                    pipe_diam, rwt_cap, uwater_dtl_srv_code, last_mod_dthr
                ) VALUES (
                    ?, ?, ?, ?, ?, ?, ?, ?, ?, '1',
                    TO_TIMESTAMP(?, 'YYYY/MM/DD HH24:MI:SS'),
                    ?, ?, ?, ?, '16', ?,
                    ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                    TO_TIMESTAMP(?, 'YYYY/MM/DD HH24:MI:SS')
                )
                ON CONFLICT (perm_nt_no) DO UPDATE SET
                    rel_trans_cgg_code = EXCLUDED.rel_trans_cgg_code,
                    sf_team_code = EXCLUDED.sf_team_code,
                    perm_nt_form_code = EXCLUDED.perm_nt_form_code,
                    aplr_gbn_code = EXCLUDED.aplr_gbn_code,
                    perm_yn = EXCLUDED.perm_yn,
                    lnho_raise_yn = EXCLUDED.lnho_raise_yn,
                    end_nt_yn = EXCLUDED.end_nt_yn,
                    perm_cancel_yn = EXCLUDED.perm_cancel_yn,
                    jgong_deal_yn = EXCLUDED.jgong_deal_yn,
                    first_reg_dthr = EXCLUDED.first_reg_dthr,
                    dvop_loc_regn_code = EXCLUDED.dvop_loc_regn_code,
                    dvop_loc_san = EXCLUDED.dvop_loc_san,
                    dvop_loc_bunji = EXCLUDED.dvop_loc_bunji,
                    dvop_loc_ho = EXCLUDED.dvop_loc_ho,
                    org_sno = EXCLUDED.org_sno,
                    perm_nt_ymd = EXCLUDED.perm_nt_ymd,
                    uwater_srv = EXCLUDED.uwater_srv,
                    uwater_srv_code = EXCLUDED.uwater_srv_code,
                    uwater_pota_yn = EXCLUDED.uwater_pota_yn,
                    dig_dph = EXCLUDED.dig_dph,
                    dig_diam = EXCLUDED.dig_diam,
                    litd_dg = EXCLUDED.litd_dg,
                    litd_mint = EXCLUDED.litd_mint,
                    litd_sc = EXCLUDED.litd_sc,
                    lttd_dg = EXCLUDED.lttd_dg,
                    lttd_mint = EXCLUDED.lttd_mint,
                    lttd_sc = EXCLUDED.lttd_sc,
                    frw_pln_qua = EXCLUDED.frw_pln_qua,
                    dyn_eqn_hrp = EXCLUDED.dyn_eqn_hrp,
                    pipe_diam = EXCLUDED.pipe_diam,
                    rwt_cap = EXCLUDED.rwt_cap,
                    uwater_dtl_srv_code = EXCLUDED.uwater_dtl_srv_code,
                    last_mod_dthr = EXCLUDED.last_mod_dthr
                """;

        String cggCode = maps.get("rel_trans_cgg_code");
        String firstRegDthr = maps.get("first_reg_dthr");

        jdbc.update(sql,
                maps.get("perm_nt_no"), cggCode, cggCode, maps.get("perm_nt_form_code"),
                maps.get("aplr_gbn_code"), maps.get("perm_yn"),
                maps.get("lnho_raise_yn"), maps.get("end_nt_yn"), maps.get("perm_cancel_yn"),
                firstRegDthr,
                maps.get("dvop_loc_regn_code"), maps.get("dvop_loc_san"),
                maps.get("dvop_loc_bunji"), maps.get("dvop_loc_ho"), maps.get("perm_nt_ymd"),
                maps.get("uwater_srv"), maps.get("uwater_srv_code"), maps.get("uwater_pota_yn"),
                maps.get("dig_dph"), maps.get("dig_diam"),
                maps.get("litd_dg"), maps.get("litd_mint"), maps.get("litd_sc"),
                maps.get("lttd_dg"), maps.get("lttd_mint"), maps.get("lttd_sc"),
                maps.get("frw_pln_qua"), maps.get("dyn_eqn_hrp"),
                maps.get("pipe_diam"), maps.get("rwt_cap"), maps.get("uwater_dtl_srv_code"),
                firstRegDthr);
    }

    // === UPSERT: rgetstgms01 (이용실태정보, 32컬럼) ===

    private void upsertStgms(JdbcTemplate jdbc, Map<String, String> maps) {
        String sql = """
                INSERT INTO rgetstgms01 (
                    perm_nt_no, rel_trans_cgg_code, yy_gbn, sf_team_code,
                    perm_nt_form_code, regn_code, san, bunji, ho,
                    litd_dg, litd_mint, litd_sc, lttd_dg, lttd_mint, lttd_sc,
                    elev, uwater_srv_code, pub_pri_gbn, pota_yn, y_use_qua,
                    uwater_souc_code, dph, dig_diam, pump_hrp, rwt_cap, pipe_diam,
                    nat_wtlv, stb_wtlv, frw_pln_qua,
                    first_reg_dthr, last_mod_dthr, uwater_dtl_srv_code
                ) VALUES (
                    ?, ?, ?, ?, ?, ?, ?, ?, ?,
                    ?, ?, ?, ?, ?, ?,
                    ?, ?, ?, ?, ?,
                    ?, ?, ?, ?, ?, ?,
                    ?, ?, ?,
                    TO_TIMESTAMP(?, 'YYYY/MM/DD HH24:MI:SS'),
                    TO_TIMESTAMP(?, 'YYYY/MM/DD HH24:MI:SS'),
                    ?
                )
                ON CONFLICT (perm_nt_no) DO UPDATE SET
                    rel_trans_cgg_code = EXCLUDED.rel_trans_cgg_code,
                    yy_gbn = EXCLUDED.yy_gbn,
                    sf_team_code = EXCLUDED.sf_team_code,
                    perm_nt_form_code = EXCLUDED.perm_nt_form_code,
                    regn_code = EXCLUDED.regn_code,
                    san = EXCLUDED.san,
                    bunji = EXCLUDED.bunji,
                    ho = EXCLUDED.ho,
                    litd_dg = EXCLUDED.litd_dg,
                    litd_mint = EXCLUDED.litd_mint,
                    litd_sc = EXCLUDED.litd_sc,
                    lttd_dg = EXCLUDED.lttd_dg,
                    lttd_mint = EXCLUDED.lttd_mint,
                    lttd_sc = EXCLUDED.lttd_sc,
                    elev = EXCLUDED.elev,
                    uwater_srv_code = EXCLUDED.uwater_srv_code,
                    pub_pri_gbn = EXCLUDED.pub_pri_gbn,
                    pota_yn = EXCLUDED.pota_yn,
                    y_use_qua = EXCLUDED.y_use_qua,
                    uwater_souc_code = EXCLUDED.uwater_souc_code,
                    dph = EXCLUDED.dph,
                    dig_diam = EXCLUDED.dig_diam,
                    pump_hrp = EXCLUDED.pump_hrp,
                    rwt_cap = EXCLUDED.rwt_cap,
                    pipe_diam = EXCLUDED.pipe_diam,
                    nat_wtlv = EXCLUDED.nat_wtlv,
                    stb_wtlv = EXCLUDED.stb_wtlv,
                    frw_pln_qua = EXCLUDED.frw_pln_qua,
                    first_reg_dthr = EXCLUDED.first_reg_dthr,
                    last_mod_dthr = EXCLUDED.last_mod_dthr,
                    uwater_dtl_srv_code = EXCLUDED.uwater_dtl_srv_code
                """;

        String cggCode = maps.get("rel_trans_cgg_code");
        String firstRegDthr = maps.get("first_reg_dthr");
        String uwaterSoucCode = maps.get("uwater_souc_code");
        if (uwaterSoucCode == null || uwaterSoucCode.isEmpty()) uwaterSoucCode = "1";

        jdbc.update(sql,
                maps.get("perm_nt_no"), cggCode, maps.get("yy_gbn"), cggCode,
                maps.get("perm_nt_form_code"), maps.get("dvop_loc_regn_code"),
                maps.get("dvop_loc_san"), maps.get("dvop_loc_bunji"), maps.get("dvop_loc_ho"),
                maps.get("litd_dg"), maps.get("litd_mint"), maps.get("litd_sc"),
                maps.get("lttd_dg"), maps.get("lttd_mint"), maps.get("lttd_sc"),
                maps.get("elev"), maps.get("uwater_srv_code"), maps.get("perm_nt_form_code"),
                maps.get("uwater_pota_yn"), maps.get("y_use_qua"),
                uwaterSoucCode, maps.get("dig_dph"), maps.get("dig_diam"),
                maps.get("dyn_eqn_hrp"), maps.get("rwt_cap"), maps.get("pipe_diam"),
                maps.get("nat_wtlv"), maps.get("stb_wtlv"), maps.get("frw_pln_qua"),
                firstRegDthr, firstRegDthr,
                maps.get("uwater_dtl_srv_code"));
    }

    // === 유틸 ===

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

    private static BasicCoordinateTransform createTransform() {
        CRSFactory factory = new CRSFactory();
        CoordinateReferenceSystem src = factory.createFromName("EPSG:5186");
        CoordinateReferenceSystem dst = factory.createFromName("EPSG:4326");
        return new BasicCoordinateTransform(src, dst);
    }
}
