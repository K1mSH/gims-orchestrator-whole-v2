package com.gims.provider.custom.handler;

import com.gims.provider.custom.CustomColumnSpec;
import com.gims.provider.custom.CustomOperationHandler;
import com.gims.provider.custom.CustomOperationMetadata;
import com.gims.provider.custom.CustomParamSpec;
import com.gims.provider.dto.DynamicQueryResult;
import com.gims.provider.service.ProviderDataSourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * B12-DJ — 대전광역시 수질측정망 정보 (waterQualityInfoDJ)
 *
 * 레거시: opn.waterQualityInfoDJ — controller 가 brtcNm='대전광역시' set 후 호출
 * - source: TM_GD120001 + TM_GD110301 + TM_GD110302 (B11 과 동일)
 * - 응답: 12 고정 (B11 의 14 중 qltwtrInspctSn / usrNM 제외) + 동적 c{code}
 * - 차이: brtcNm 필수 필터, ugrwtrPrposCode 코드값 그대로 (CASE 변환 X), registDt/changeDt YYYY-MM-DD 포맷, searchDt BETWEEN
 *
 * 1:1 원칙: B11 과 SQL 부분 중복이지만 별도 핸들러 (controller 분기 패턴)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WaterQualityInfoDjHandler implements CustomOperationHandler {

    private static final String DATASOURCE_ID = "internal";
    private static final String OPERATION_ID  = "waterQualityInfoDJ/waterQualityInfoDJ";
    private static final String BRTC_NM       = "대전광역시";

    private static final String SEARCH_INSPECTION_SQL =
            "SELECT A.QLTWTR_INSPCT_IEM_CODE FROM TM_GD110310 A " +
            "WHERE A.JOSACODE = ? AND A.DTA_STDR_YEAR = ? ORDER BY A.QLTWTR_INSPCT_IEM_CODE";

    private static final String SEARCH_MAX_YEAR_SQL = "SELECT MAX(DTA_STDR_YEAR) FROM TM_GD110310";

    private final ProviderDataSourceService dataSourceService;

    @Override
    public CustomOperationMetadata getMetadata() {
        return CustomOperationMetadata.builder()
                .operationId(OPERATION_ID)
                .operationName("B12 대전 수질측정망 정보")
                .description(
                        "관련 테이블: TM_GD120001, TM_GD110301, TM_GD110302 + helper TM_GD110310 (대전광역시 한정)\n" +
                        "변환: 동적 PIVOT + 2JOIN + searchDt BETWEEN (controller 가 brtcNm='대전광역시' set)"
                )
                .datasourceId(DATASOURCE_ID)
                .tableName("TM_GD110301")
                .pageSize(100)
                .maxPageSize(1000)
                .column(CustomColumnSpec.builder().columnName("invstgYear").aliasName("invstgYear").displayOrder(1).build())
                .column(CustomColumnSpec.builder().columnName("odr").aliasName("odr").displayOrder(2).build())
                .column(CustomColumnSpec.builder().columnName("spotNm").aliasName("spotNm").displayOrder(3).build())
                .column(CustomColumnSpec.builder().columnName("sptGennum").aliasName("sptGennum").displayOrder(4).build())
                .column(CustomColumnSpec.builder().columnName("address").aliasName("address").displayOrder(5).build())
                .column(CustomColumnSpec.builder().columnName("loValue").aliasName("loValue").displayOrder(6).build())
                .column(CustomColumnSpec.builder().columnName("laValue").aliasName("laValue").displayOrder(7).build())
                .column(CustomColumnSpec.builder().columnName("drnkAt").aliasName("drnkAt").displayOrder(8).build())
                .column(CustomColumnSpec.builder().columnName("ugrwtrPrposCode").aliasName("ugrwtrPrposCode").displayOrder(9).build())
                .column(CustomColumnSpec.builder().columnName("qltwtrInspctDe").aliasName("qltwtrInspctDe").displayOrder(10).build())
                .column(CustomColumnSpec.builder().columnName("registDt").aliasName("registDt").displayOrder(11).build())
                .column(CustomColumnSpec.builder().columnName("changeDt").aliasName("changeDt").displayOrder(12).build())
                .param(CustomParamSpec.builder()
                        .paramName("josacode").columnName("UGWTR_EXMN_CD").operator("EQ")
                        .required(true).dataType("STRING").build())
                .param(CustomParamSpec.builder()
                        .paramName("year").columnName("EXMN_YR").operator("EQ")
                        .required(true).dataType("STRING").build())
                .param(CustomParamSpec.builder()
                        .paramName("searchDt").columnName("FRST_REG_DT").operator("GTE")
                        .required(true).dataType("STRING").build())
                .param(CustomParamSpec.builder()
                        .paramName("currentDt").columnName("FRST_REG_DT").operator("LTE")
                        .required(false).dataType("STRING").build())
                .build();
    }

    @Override
    public DynamicQueryResult handle(Map<String, String> params, int page, int pageSize) {
        return executeDj(params, BRTC_NM, dataSourceService, log);
    }

    /** B12-DJ / B12-KB 공통 — brtcNm 만 다름 */
    static DynamicQueryResult executeDj(Map<String, String> params, String brtcNm,
                                         ProviderDataSourceService dataSourceService,
                                         org.slf4j.Logger log) {
        long start = System.currentTimeMillis();

        String josacode  = params.get("josacode");
        String year      = params.get("year");
        String searchDt  = params.get("searchDt");
        String currentDt = params.get("currentDt");

        if (josacode == null || josacode.isBlank()) throw new IllegalArgumentException("필수 파라미터 누락: josacode");
        if (year == null || year.isBlank())         throw new IllegalArgumentException("필수 파라미터 누락: year");
        if (searchDt == null || searchDt.isBlank()) throw new IllegalArgumentException("필수 파라미터 누락: searchDt (YYYYMMDD)");

        JdbcTemplate jdbc = dataSourceService.getJdbcTemplate(DATASOURCE_ID);

        // 1. inspection 코드풀 + fallback
        TreeSet<String> codePool = new TreeSet<>();
        List<String> codes = jdbc.queryForList(SEARCH_INSPECTION_SQL, String.class, josacode.trim(), year.trim());
        if (codes.isEmpty()) {
            String maxYear = jdbc.queryForObject(SEARCH_MAX_YEAR_SQL, String.class);
            if (maxYear != null) {
                codes = jdbc.queryForList(SEARCH_INSPECTION_SQL, String.class, josacode.trim(), maxYear);
            }
        }
        codePool.addAll(codes);

        // 2. 동적 SQL 조립
        List<Object> args = new ArrayList<>();
        String sql = buildDjSql(codePool, brtcNm, josacode, year, searchDt, currentDt, args);

        // 3. 실행
        List<Map<String, Object>> rows = jdbc.query(sql, args.toArray(),
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("invstgYear",      rs.getString("invstgYear"));
                    row.put("odr",             rs.getObject("odr"));
                    row.put("spotNm",          rs.getString("spotNm"));
                    row.put("sptGennum",       rs.getString("sptGennum"));
                    row.put("address",         rs.getString("address"));
                    row.put("loValue",         rs.getString("loValue"));
                    row.put("laValue",         rs.getString("laValue"));
                    row.put("drnkAt",          rs.getString("drnkAt"));
                    row.put("ugrwtrPrposCode", rs.getString("ugrwtrPrposCode"));
                    row.put("qltwtrInspctDe",  rs.getString("qltwtrInspctDe"));
                    row.put("registDt",        rs.getString("registDt"));
                    row.put("changeDt",        rs.getString("changeDt"));
                    for (String code : codePool) {
                        String alias = "c" + code;
                        String val = rs.getString(alias);
                        row.put(alias, val == null ? "-" : val);
                    }
                    return row;
                });

        long duration = System.currentTimeMillis() - start;
        int count = rows.size();
        log.debug("[WaterQualityInfo {}] josacode={} year={} → {} rows ({}ms)", brtcNm, josacode, year, count, duration);

        return DynamicQueryResult.builder()
                .data(rows)
                .pagination(DynamicQueryResult.PaginationInfo.builder()
                        .page(1).pageSize(Math.max(1, count))
                        .totalCount(count).totalPages(count > 0 ? 1 : 0)
                        .build())
                .durationMs(duration)
                .build();
    }

    private static String buildDjSql(TreeSet<String> codePool, String brtcNm, String josacode, String year,
                                      String searchDt, String currentDt, List<Object> args) {
        StringBuilder pivotCols = new StringBuilder();
        for (String code : codePool) {
            pivotCols.append(",MAX(CASE WHEN WQ_INSP_ARTCL_CD = '").append(code)
                     .append("' THEN RSLT_VL END) AS \"c").append(code).append("\" ");
        }

        // 인자 순서 (?): brtcNm, year, josacode, searchDt(start), endDt(start_or_end), searchDt(start), endDt(start_or_end)
        args.add(brtcNm);
        args.add(year.trim());
        args.add(josacode.trim());
        String endDt = (currentDt != null && !currentDt.isBlank()) ? currentDt.trim() : searchDt.trim();
        args.add(searchDt.trim());
        args.add(endDt);
        args.add(searchDt.trim());
        args.add(endDt);

        return "SELECT invstgYear, odr, spotNm, sptGennum, address, loValue, laValue, drnkAt, ugrwtrPrposCode, " +
               "       qltwtrInspctDe, registDt, changeDt " +
               pivotCols.toString() +
               "FROM ( " +
               "  SELECT T1.GWEL_NO, " +
               "         T3.EXMN_YR AS invstgYear, " +
               "         T3.CYCL AS odr, " +
               "         T1.BRNCH_NM AS spotNm, " +
               "         T1.GRNDS_GWEL_NO AS sptGennum, " +
               "         T1.CTPV_NM || ' ' || T1.SGG_NM || ' ' || NVL(T1.EMD_NM,'') || ' ' || NVL(T1.LI_NM,'') || ' ' || NVL(T1.ADDR,'') || ' ' || NVL(T1.DTL_PSTN_CN,'') AS address, " +
               "         T1.LOT AS loValue, " +
               "         T1.LAT AS laValue, " +
               "         T3.DKPP_YN AS drnkAt, " +
               "         T3.UGWTR_USG_CD AS ugrwtrPrposCode, " +
               "         T3.WQ_INSP_YMD AS qltwtrInspctDe, " +
               "         TO_CHAR(T3.FRST_REG_DT, 'YYYY-MM-DD') AS registDt, " +
               "         TO_CHAR(T3.LAST_CHG_DT, 'YYYY-MM-DD') AS changeDt, " +
               "         T32.WQ_INSP_ARTCL_CD, T32.RSLT_VL " +
               "  FROM   TM_GD120001 T1 " +
               "  INNER  JOIN (SELECT * FROM TM_GD110301 WHERE CTPV_NM = ? AND EXMN_YR = ?) T3 ON T1.GWEL_NO = T3.GWEL_NO " +
               "  LEFT   JOIN TM_GD110302 T32 ON T3.WQ_INSP_SN = T32.WQ_INSP_SN " +
               "  WHERE  T1.UGWTR_EXMN_CD = ? " +
               "  AND    (T3.FRST_REG_DT BETWEEN TO_DATE(? || '000000', 'YYYYMMDDHH24MISS') AND TO_DATE(? || '235959', 'YYYYMMDDHH24MISS') " +
               "          OR T3.LAST_CHG_DT BETWEEN TO_DATE(? || '000000', 'YYYYMMDDHH24MISS') AND TO_DATE(? || '235959', 'YYYYMMDDHH24MISS')) " +
               ") " +
               "GROUP BY invstgYear, odr, spotNm, sptGennum, address, loValue, laValue, drnkAt, ugrwtrPrposCode, " +
               "         qltwtrInspctDe, registDt, changeDt " +
               "ORDER BY spotNm, address";
    }
}
