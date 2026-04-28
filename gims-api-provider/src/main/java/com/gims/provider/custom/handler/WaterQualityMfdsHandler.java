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
 * B13 — 식약처 정기수질검사 (waterQualityMfdsInfo)
 *
 * 레거시: opn.waterQualityMfdsInfo
 * - source: TM_GD110350 (메인) + TM_GD110351 (결과) + TM_GD010910 (사용자, 스칼라)
 * - helper: TM_GD110310 + TC_GD00002 (NGW_0026) — 동적 PIVOT 컬럼풀 추출
 * - 구조: GROUP BY + 동적 PIVOT (CASE WHEN per inspection_iem_code) + outer NVL
 * - 응답: 19 고정 컬럼 + N 동적 (`C{code}`)
 *
 * 동적 PIVOT 패턴 (B11/B12 의 사전 검증):
 *   1. searchInspection(year, josacode) 실행 → IEM_CODE 풀 추출
 *   2. 비어있으면 searchMaxDtaStdrYear() fallback 후 재실행
 *   3. 코드풀로 SQL CASE WHEN 컬럼 동적 조립
 *
 * 파라미터: josacode (필수), year (필수, YYYY), odr (선택, 1~4 분기)
 *
 * v3 컬럼 매핑:
 *   TM_GD70201 → TM_GD110350 (QLTWTR_INSPCT_SN→WQ_INSP_SN, JOSACODE→UGWTR_EXMN_CD, REQUST_DE→DMND_YMD, ...)
 *   TM_GD70202 → TM_GD110351 (QLTWTR_INSPCT_IEM_CODE→WQ_INSP_ARTCL_CD, RESULT_VALUE→RSLT_VL)
 *   TM_GD20910 → TM_GD010910 (USR_ID→USER_ID, USR_NM→USER_NM)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WaterQualityMfdsHandler implements CustomOperationHandler {

    private static final String DATASOURCE_ID = "internal";
    private static final String OPERATION_ID  = "waterQualityMfdsInfo/waterQualityMfdsInfo";

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
                .operationName("B13 식약처 정기수질검사")
                .description(
                        "관련 테이블: TM_GD110350, TM_GD110351, TM_GD010910 + helper TM_GD110310/TC_GD00002\n" +
                        "변환: 동적 PIVOT (inspection 코드풀 + searchMaxDtaStdrYear fallback) + 2JOIN + 스칼라"
                )
                .datasourceId(DATASOURCE_ID)
                .tableName("TM_GD110350")
                .pageSize(100)
                .maxPageSize(1000)
                .column(CustomColumnSpec.builder().columnName("QLTWTR_INSPCT_SN").aliasName("qltwtrInspctSn").displayOrder(1).build())
                .column(CustomColumnSpec.builder().columnName("ORGWATR_CL_CODE").aliasName("orgwatrClCode").displayOrder(2).build())
                .column(CustomColumnSpec.builder().columnName("CMPNM_NM").aliasName("cmpnmNm").displayOrder(3).build())
                .column(CustomColumnSpec.builder().columnName("BRTC_NM").aliasName("brtcNm").displayOrder(4).build())
                .column(CustomColumnSpec.builder().columnName("SIGUN_NM").aliasName("sigunNm").displayOrder(5).build())
                .column(CustomColumnSpec.builder().columnName("EMD_NM").aliasName("emdNm").displayOrder(6).build())
                .column(CustomColumnSpec.builder().columnName("LI_NM").aliasName("liNm").displayOrder(7).build())
                .column(CustomColumnSpec.builder().columnName("WELL_ADDR").aliasName("wellAddr").displayOrder(8).build())
                .column(CustomColumnSpec.builder().columnName("UGRWTR_PRPOS_CODE").aliasName("ugrwtrPrposCode").displayOrder(9).build())
                .column(CustomColumnSpec.builder().columnName("INVSTG_YEAR").aliasName("invstgYear").displayOrder(10).build())
                .column(CustomColumnSpec.builder().columnName("ODR").aliasName("odr").displayOrder(11).build())
                .column(CustomColumnSpec.builder().columnName("RESULT_DSPTH_DE").aliasName("resultDspthDe").displayOrder(12).build())
                .column(CustomColumnSpec.builder().columnName("QLTWTR_INSPCT_DE").aliasName("qltwtrInspctDe").displayOrder(13).build())
                .column(CustomColumnSpec.builder().columnName("REQUST_DE").aliasName("requstDe").displayOrder(14).build())
                .column(CustomColumnSpec.builder().columnName("QLTWTR_STDR_PRPOS_CODE").aliasName("qltwtrStdrPrposCode").displayOrder(15).build())
                .column(CustomColumnSpec.builder().columnName("QLTWTR_INSPCT_RESULT_CODE").aliasName("qltwtrInspctResultCode").displayOrder(16).build())
                .column(CustomColumnSpec.builder().columnName("QLTWTR_INSPCT_ETC_CTNT").aliasName("qltwtrInspctEtcCtnt").displayOrder(17).build())
                .column(CustomColumnSpec.builder().columnName("QLTWTR_INSPCT_PURPS_CTNT").aliasName("qltwtrInspctPurpsCtnt").displayOrder(18).build())
                .column(CustomColumnSpec.builder().columnName("PRMISN_DCLR_NO").aliasName("prmisnDclrNo").displayOrder(19).build())
                .column(CustomColumnSpec.builder().columnName("USR_NM").aliasName("usrNm").displayOrder(20).build())
                // 동적 컬럼 C{code} 는 응답 시점에 추가 — 메타에는 표시하지 않음 (코드풀 가변)
                .param(CustomParamSpec.builder()
                        .paramName("josacode").columnName("UGWTR_EXMN_CD").operator("EQ")
                        .required(true).dataType("STRING").build())
                .param(CustomParamSpec.builder()
                        .paramName("year").columnName("DMND_YMD").operator("EQ")
                        .required(true).dataType("STRING").build())
                .param(CustomParamSpec.builder()
                        .paramName("odr").columnName("DMND_YMD").operator("EQ")
                        .required(false).dataType("STRING").build())
                .build();
    }

    @Override
    public DynamicQueryResult handle(Map<String, String> params, int page, int pageSize) {
        long start = System.currentTimeMillis();

        String josacode = params.get("josacode");
        String year     = params.get("year");
        String odr      = params.get("odr");

        if (josacode == null || josacode.isBlank()) {
            throw new IllegalArgumentException("필수 파라미터 누락: josacode");
        }
        if (year == null || year.isBlank()) {
            throw new IllegalArgumentException("필수 파라미터 누락: year (YYYY)");
        }

        JdbcTemplate jdbc = dataSourceService.getJdbcTemplate(DATASOURCE_ID);

        // 1. inspection 코드풀 추출 + fallback
        List<String> codes = jdbc.queryForList(SEARCH_INSPECTION_SQL, String.class, josacode.trim(), year.trim());
        String effectiveYear = year.trim();
        if (codes.isEmpty()) {
            String maxYear = jdbc.queryForObject(SEARCH_MAX_YEAR_SQL, String.class);
            log.debug("[WaterQualityMfdsHandler] inspection empty for year={}, fallback to maxYear={}", year, maxYear);
            if (maxYear != null) {
                codes = jdbc.queryForList(SEARCH_INSPECTION_SQL, String.class, josacode.trim(), maxYear);
                effectiveYear = maxYear;
            }
        }

        // 2. 코드풀 정렬 + 중복 제거 (TreeSet)
        TreeSet<String> codePool = new TreeSet<>(codes);
        log.debug("[WaterQualityMfdsHandler] josacode={} year={} (effective={}) → {} codes: {}",
                josacode, year, effectiveYear, codePool.size(), codePool);

        // 3. 동적 SQL 조립
        String sql = buildDynamicSql(codePool, odr);

        // 4. 실행
        final String fJosacode = josacode.trim();
        final String fYear = effectiveYear;
        List<Map<String, Object>> rows = jdbc.query(sql,
                ps -> {
                    ps.setString(1, fJosacode);
                    ps.setString(2, fYear);
                },
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("qltwtrInspctSn",         rs.getObject("qltwtrInspctSn"));
                    row.put("orgwatrClCode",          rs.getString("orgwatrClCode"));
                    row.put("cmpnmNm",                rs.getString("cmpnmNm"));
                    row.put("brtcNm",                 rs.getString("brtcNm"));
                    row.put("sigunNm",                rs.getString("sigunNm"));
                    row.put("emdNm",                  rs.getString("emdNm"));
                    row.put("liNm",                   rs.getString("liNm"));
                    row.put("wellAddr",               rs.getString("wellAddr"));
                    row.put("ugrwtrPrposCode",        rs.getString("ugrwtrPrposCode"));
                    row.put("invstgYear",             rs.getString("invstgYear"));
                    row.put("odr",                    rs.getObject("odr"));
                    row.put("resultDspthDe",          rs.getString("resultDspthDe"));
                    row.put("qltwtrInspctDe",         rs.getString("qltwtrInspctDe"));
                    row.put("requstDe",               rs.getString("requstDe"));
                    row.put("qltwtrStdrPrposCode",    rs.getString("qltwtrStdrPrposCode"));
                    row.put("qltwtrInspctResultCode", rs.getString("qltwtrInspctResultCode"));
                    row.put("qltwtrInspctEtcCtnt",    rs.getString("qltwtrInspctEtcCtnt"));
                    row.put("qltwtrInspctPurpsCtnt",  rs.getString("qltwtrInspctPurpsCtnt"));
                    row.put("prmisnDclrNo",           rs.getString("prmisnDclrNo"));
                    row.put("usrNm",                  rs.getString("usrNm"));
                    for (String code : codePool) {
                        String alias = "c" + code;
                        String val = rs.getString(alias);
                        row.put(alias, val == null ? "-" : val);
                    }
                    return row;
                });

        long duration = System.currentTimeMillis() - start;
        int count = rows.size();
        log.debug("[WaterQualityMfdsHandler] josacode={} year={} odr={} → {} rows ({}ms)",
                josacode, effectiveYear, odr, count, duration);

        return DynamicQueryResult.builder()
                .data(rows)
                .pagination(DynamicQueryResult.PaginationInfo.builder()
                        .page(1).pageSize(Math.max(1, count))
                        .totalCount(count).totalPages(count > 0 ? 1 : 0)
                        .build())
                .durationMs(duration)
                .build();
    }

    /**
     * 동적 SQL 조립 — 19 고정 + N 개 동적 PIVOT 컬럼.
     */
    private String buildDynamicSql(TreeSet<String> codePool, String odr) {
        StringBuilder pivotCols = new StringBuilder();
        for (String code : codePool) {
            pivotCols.append(",MAX(CASE WHEN WQ_INSP_ARTCL_CD = '").append(code)
                     .append("' THEN RSLT_VL END) AS \"c").append(code).append("\" ");
        }

        StringBuilder odrFilter = new StringBuilder();
        if (odr != null && !odr.isBlank()) {
            switch (odr.trim()) {
                case "1": odrFilter.append("AND TO_NUMBER(SUBSTR(T.DMND_YMD, 5, 2)) BETWEEN 1 AND 3 "); break;
                case "2": odrFilter.append("AND TO_NUMBER(SUBSTR(T.DMND_YMD, 5, 2)) BETWEEN 4 AND 6 "); break;
                case "3": odrFilter.append("AND TO_NUMBER(SUBSTR(T.DMND_YMD, 5, 2)) BETWEEN 7 AND 9 "); break;
                case "4": odrFilter.append("AND TO_NUMBER(SUBSTR(T.DMND_YMD, 5, 2)) BETWEEN 10 AND 12 "); break;
                default:  /* invalid odr → ignore */ break;
            }
        }

        return "SELECT qltwtrInspctSn, orgwatrClCode, cmpnmNm, brtcNm, sigunNm, emdNm, liNm, wellAddr, " +
               "       ugrwtrPrposCode, invstgYear, odr, resultDspthDe, qltwtrInspctDe, requstDe, " +
               "       qltwtrStdrPrposCode, qltwtrInspctResultCode, qltwtrInspctEtcCtnt, qltwtrInspctPurpsCtnt, " +
               "       prmisnDclrNo, usrNm " +
               (codePool.isEmpty() ? "" : pivotCols.toString().replace(",MAX", ",MAX")) +  // dynamic pivot already starts with comma
               "FROM ( " +
               "  SELECT T.WQ_INSP_SN AS qltwtrInspctSn, " +
               "         T.ORGWT_CLSF_CD AS orgwatrClCode, " +
               "         T.CONM_NM AS cmpnmNm, " +
               "         T.CTPV_NM AS brtcNm, " +
               "         T.SGG_NM AS sigunNm, " +
               "         T.EMD_NM AS emdNm, " +
               "         T.LI_NM AS liNm, " +
               "         T.GWEL_ADDR AS wellAddr, " +
               "         T.UGWTR_USG_CD AS ugrwtrPrposCode, " +
               "         SUBSTR(T.DMND_YMD, 1, 4) AS invstgYear, " +
               "         CASE WHEN TO_NUMBER(SUBSTR(T.DMND_YMD, 5, 2)) BETWEEN 1 AND 3 THEN 1 " +
               "              WHEN TO_NUMBER(SUBSTR(T.DMND_YMD, 5, 2)) BETWEEN 4 AND 6 THEN 2 " +
               "              WHEN TO_NUMBER(SUBSTR(T.DMND_YMD, 5, 2)) BETWEEN 7 AND 9 THEN 3 " +
               "              ELSE 4 END AS odr, " +
               "         T.RSLT_NTFCTN_YMD AS resultDspthDe, " +
               "         T.WQ_INSP_YMD AS qltwtrInspctDe, " +
               "         T.DMND_YMD AS requstDe, " +
               "         T.WQ_CRTR_USG_CD AS qltwtrStdrPrposCode, " +
               "         T.WQ_INSP_RSLT_CD AS qltwtrInspctResultCode, " +
               "         T.WQ_INSP_ETC_CN AS qltwtrInspctEtcCtnt, " +
               "         T.WQ_INSP_PRPS_CN AS qltwtrInspctPurpsCtnt, " +
               "         T.PRMSN_DCLR_NO AS prmisnDclrNo, " +
               "         (SELECT USER_NM FROM TM_GD010910 WHERE USER_ID = T.RGTR_ID) AS usrNm, " +
               "         R.WQ_INSP_ARTCL_CD, R.RSLT_VL " +
               "  FROM   TM_GD110350 T " +
               "  LEFT   JOIN TM_GD110351 R ON T.WQ_INSP_SN = R.WQ_INSP_SN " +
               "  WHERE  T.UGWTR_EXMN_CD = ? " +
               "  AND    SUBSTR(T.DMND_YMD, 1, 4) = ? " +
               odrFilter +
               ") " +
               "GROUP BY qltwtrInspctSn, orgwatrClCode, cmpnmNm, brtcNm, sigunNm, emdNm, liNm, wellAddr, " +
               "         ugrwtrPrposCode, invstgYear, odr, resultDspthDe, qltwtrInspctDe, requstDe, " +
               "         qltwtrStdrPrposCode, qltwtrInspctResultCode, qltwtrInspctEtcCtnt, qltwtrInspctPurpsCtnt, " +
               "         prmisnDclrNo, usrNm " +
               "ORDER BY qltwtrInspctSn";
    }
}
