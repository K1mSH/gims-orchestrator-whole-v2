package com.infolink.provider.custom.handler;

import com.infolink.provider.custom.CustomColumnSpec;
import com.infolink.provider.custom.CustomOperationHandler;
import com.infolink.provider.custom.CustomOperationMetadata;
import com.infolink.provider.custom.CustomParamSpec;
import com.infolink.provider.dto.DynamicQueryResult;
import com.infolink.provider.service.ProviderDataSourceService;
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
 * B11 — 수질측정망 정보 (waterQualityInfo, 범용)
 *
 * 레거시: opn.waterQualityInfo
 * - source: TM_GD120001 + TM_GD110301 + TM_GD110302 + TC_GD000002 (NGW_0028 스칼라)
 * - helper: TM_GD110310 + TC_GD000002 (NGW_0026) — 동적 PIVOT 컬럼풀
 * - 구조: GROUP BY 2단 + 동적 PIVOT (CASE WHEN per inspection_iem_code) + outer NVL
 * - 응답: 14 고정 + N 동적 (`c{code}`)
 *
 * 파라미터: josacode (필수), year/odr/sido/sigungu/searchDt (선택, 동적 WHERE)
 *
 * v3 컬럼 매핑:
 *   TM_GD10001 → TM_GD120001 (GENNUM→GWEL_NO, JOSACODE→UGWTR_EXMN_CD, SPOT_NM→BRNCH_NM, SPT_GENNUM→GRNDS_GWEL_NO,
 *                              BRTC_NM→CTPV_NM, SIGUN_NM→SGG_NM, LO_VALUE→LOT, LA_VALUE→LAT, ...)
 *   TM_GD30301 → TM_GD110301 (QLTWTR_INSPCT_SN→WQ_INSP_SN, INVSTG_YEAR→EXMN_YR, ODR→CYCL,
 *                              UGRWTR_PRPOS_CODE→UGWTR_USG_CD, DRNK_AT→DKPP_YN,
 *                              UGRWTR_WQN_INPUT_INSTT_CODE→UGWTR_WQMN_INPT_INST_CD, ...)
 *   TM_GD30302 → TM_GD110302 (WLTTS_ID_CODE→WQ_INSP_ARTCL_CD, RESULT_VALUE→RSLT_VL)
 *
 * v3 OPNResultVO 의 ugrwtrPrposCode = CASE 변환된 한글 (생활용/공업용/농업용/기타용).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WaterQualityInfoHandler implements CustomOperationHandler {

    private static final String DATASOURCE_ID = "internal";
    private static final String OPERATION_ID  = "waterQualityInfo/waterQualityInfo";

    private static final String SEARCH_INSPECTION_SQL =
            "SELECT A.QLTWTR_INSPCT_IEM_CODE " +
            "FROM   TM_GD110310 A " +
            "WHERE  A.JOSACODE = ? AND A.DTA_STDR_YEAR = ? " +
            "ORDER BY A.QLTWTR_INSPCT_IEM_CODE";

    private static final String SEARCH_MAX_YEAR_SQL =
            "SELECT MAX(DTA_STDR_YEAR) FROM TM_GD110310";

    private final ProviderDataSourceService dataSourceService;

    @Override
    public CustomOperationMetadata getMetadata() {
        return CustomOperationMetadata.builder()
                .operationId(OPERATION_ID)
                .operationName("B11 수질측정망 정보 (범용)")
                .description(
                        "관련 테이블: TM_GD120001, TM_GD110301, TM_GD110302, TC_GD000002 + helper TM_GD110310\n" +
                        "변환: 동적 PIVOT (inspection 코드풀 + searchMaxDtaStdrYear fallback) + 2JOIN + 스칼라 (NGW_0028 입력기관)"
                )
                .datasourceId(DATASOURCE_ID)
                .tableName("TM_GD110301")
                .pageSize(100)
                .maxPageSize(1000)
                .column(CustomColumnSpec.builder().columnName("qltwtrInspctSn").aliasName("qltwtrInspctSn").displayOrder(1).build())
                .column(CustomColumnSpec.builder().columnName("invstgYear").aliasName("invstgYear").displayOrder(2).build())
                .column(CustomColumnSpec.builder().columnName("odr").aliasName("odr").displayOrder(3).build())
                .column(CustomColumnSpec.builder().columnName("spotNm").aliasName("spotNm").displayOrder(4).build())
                .column(CustomColumnSpec.builder().columnName("sptGennum").aliasName("sptGennum").displayOrder(5).build())
                .column(CustomColumnSpec.builder().columnName("address").aliasName("address").displayOrder(6).build())
                .column(CustomColumnSpec.builder().columnName("loValue").aliasName("loValue").displayOrder(7).build())
                .column(CustomColumnSpec.builder().columnName("laValue").aliasName("laValue").displayOrder(8).build())
                .column(CustomColumnSpec.builder().columnName("drnkAt").aliasName("drnkAt").displayOrder(9).build())
                .column(CustomColumnSpec.builder().columnName("ugrwtrPrposCode").aliasName("ugrwtrPrposCode").displayOrder(10).build())
                .column(CustomColumnSpec.builder().columnName("qltwtrInspctDe").aliasName("qltwtrInspctDe").displayOrder(11).build())
                .column(CustomColumnSpec.builder().columnName("registDt").aliasName("registDt").displayOrder(12).build())
                .column(CustomColumnSpec.builder().columnName("changeDt").aliasName("changeDt").displayOrder(13).build())
                .column(CustomColumnSpec.builder().columnName("usrNM").aliasName("usrNM").displayOrder(14).build())
                .param(CustomParamSpec.builder()
                        .paramName("josacode").columnName("UGWTR_EXMN_CD").operator("EQ")
                        .required(true).dataType("STRING").build())
                .param(CustomParamSpec.builder()
                        .paramName("year").columnName("EXMN_YR").operator("EQ")
                        .required(false).dataType("STRING").build())
                .param(CustomParamSpec.builder()
                        .paramName("odr").columnName("CYCL").operator("EQ")
                        .required(false).dataType("STRING").build())
                .param(CustomParamSpec.builder()
                        .paramName("sido").columnName("CTPV_NM").operator("LIKE")
                        .required(false).dataType("STRING").build())
                .param(CustomParamSpec.builder()
                        .paramName("sigungu").columnName("SGG_NM").operator("LIKE")
                        .required(false).dataType("STRING").build())
                .param(CustomParamSpec.builder()
                        .paramName("searchDt").columnName("FRST_REG_DT").operator("GTE")
                        .required(false).dataType("STRING").build())
                .build();
    }

    @Override
    public DynamicQueryResult handle(Map<String, String> params, int page, int pageSize) {
        long start = System.currentTimeMillis();

        String josacode = params.get("josacode");
        String year     = params.get("year");
        String odr      = params.get("odr");
        String sido     = params.get("sido");
        String sigungu  = params.get("sigungu");
        String searchDt = params.get("searchDt");

        if (josacode == null || josacode.isBlank()) {
            throw new IllegalArgumentException("필수 파라미터 누락: josacode");
        }

        JdbcTemplate jdbc = dataSourceService.getJdbcTemplate(DATASOURCE_ID);

        // 1. inspection 코드풀 + fallback (year 가 있을 때만)
        TreeSet<String> codePool = new TreeSet<>();
        if (year != null && !year.isBlank()) {
            List<String> codes = jdbc.queryForList(SEARCH_INSPECTION_SQL, String.class, josacode.trim(), year.trim());
            if (codes.isEmpty()) {
                String maxYear = jdbc.queryForObject(SEARCH_MAX_YEAR_SQL, String.class);
                log.debug("[WaterQualityInfoHandler] inspection empty for year={}, fallback maxYear={}", year, maxYear);
                if (maxYear != null) {
                    codes = jdbc.queryForList(SEARCH_INSPECTION_SQL, String.class, josacode.trim(), maxYear);
                }
            }
            codePool.addAll(codes);
        }

        // 2. 동적 SQL 조립
        List<Object> args = new ArrayList<>();
        String sql = buildDynamicSql(codePool, josacode, year, odr, sido, sigungu, searchDt, args);

        log.debug("[WaterQualityInfoHandler] codePool={}, args={}", codePool, args);

        // 3. 실행
        List<Map<String, Object>> rows = jdbc.query(sql, args.toArray(),
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("qltwtrInspctSn",  rs.getObject("qltwtrInspctSn"));
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
                    row.put("usrNM",           rs.getString("usrNM"));
                    for (String code : codePool) {
                        String alias = "c" + code;
                        String val = rs.getString(alias);
                        row.put(alias, val == null ? "-" : val);
                    }
                    return row;
                });

        long duration = System.currentTimeMillis() - start;
        int count = rows.size();
        log.debug("[WaterQualityInfoHandler] josacode={} year={} → {} rows ({}ms)", josacode, year, count, duration);

        return DynamicQueryResult.builder()
                .data(rows)
                .pagination(DynamicQueryResult.PaginationInfo.builder()
                        .page(1).pageSize(Math.max(1, count))
                        .totalCount(count).totalPages(count > 0 ? 1 : 0)
                        .build())
                .durationMs(duration)
                .build();
    }

    private String buildDynamicSql(TreeSet<String> codePool, String josacode, String year, String odr,
                                    String sido, String sigungu, String searchDt, List<Object> args) {
        StringBuilder pivotCols = new StringBuilder();
        for (String code : codePool) {
            pivotCols.append(",MAX(CASE WHEN WQ_INSP_ARTCL_CD = '").append(code)
                     .append("' THEN RSLT_VL END) AS \"c").append(code).append("\" ");
        }

        StringBuilder where = new StringBuilder();
        where.append("WHERE T1.UGWTR_EXMN_CD = ? ");
        args.add(josacode.trim());
        if (year != null && !year.isBlank()) {
            where.append("AND T3.EXMN_YR = ? ");
            args.add(year.trim());
        }
        if (odr != null && !odr.isBlank()) {
            where.append("AND T3.CYCL = ? ");
            args.add(Long.parseLong(odr.trim()));
        }
        if (sido != null && !sido.isBlank()) {
            where.append("AND T3.CTPV_NM LIKE ? ");
            args.add(sido.trim() + "%");
        }
        if (sigungu != null && !sigungu.isBlank()) {
            where.append("AND T3.SGG_NM LIKE ? ");
            args.add(sigungu.trim() + "%");
        }
        if (searchDt != null && !searchDt.isBlank()) {
            where.append("AND (T3.FRST_REG_DT >= TO_DATE(?, 'YYYYMMDD') OR T3.LAST_CHG_DT >= TO_DATE(?, 'YYYYMMDD')) ");
            args.add(searchDt.trim());
            args.add(searchDt.trim());
        }

        return "SELECT qltwtrInspctSn, invstgYear, odr, spotNm, sptGennum, address, loValue, laValue, drnkAt, " +
               "       CASE ugrwtrPrposCode " +
               "         WHEN '01' THEN '생활용' " +
               "         WHEN '02' THEN '공업용' " +
               "         WHEN '03' THEN '농업용' " +
               "         WHEN '04' THEN '기타용' " +
               "       END AS ugrwtrPrposCode, " +
               "       qltwtrInspctDe, registDt, changeDt, usrNM " +
               pivotCols.toString() +
               "FROM ( " +
               "  SELECT T1.GWEL_NO, " +
               "         T3.WQ_INSP_SN AS qltwtrInspctSn, " +
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
               "         TO_CHAR(T3.FRST_REG_DT, 'YYYY-MM-DD HH24:MI:SS') AS registDt, " +
               "         TO_CHAR(T3.LAST_CHG_DT, 'YYYY-MM-DD HH24:MI:SS') AS changeDt, " +
               "         (SELECT TRIM(B.CD_CN) FROM TC_GD000002 B " +
               "          WHERE B.GROUP_CD_SN = 'NGW_0028' AND TRIM(B.UGWTR_COM_CD) = T3.UGWTR_WQMN_INPT_INST_CD) AS usrNM, " +
               "         T32.WQ_INSP_ARTCL_CD, T32.RSLT_VL " +
               "  FROM   TM_GD120001 T1 " +
               "  INNER  JOIN TM_GD110301 T3 ON T1.GWEL_NO = T3.GWEL_NO " +
               "  LEFT   JOIN TM_GD110302 T32 ON T3.WQ_INSP_SN = T32.WQ_INSP_SN " +
               "  " + where +
               ") " +
               "GROUP BY qltwtrInspctSn, invstgYear, odr, spotNm, sptGennum, address, loValue, laValue, drnkAt, " +
               "         ugrwtrPrposCode, qltwtrInspctDe, registDt, changeDt, usrNM " +
               "ORDER BY spotNm, address";
    }
}
