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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * B9 — 관측망 일 단위 차트 (observationStationService/getGroundwaterMonitoringNetwork)
 *
 * 레거시: opn.linkage_analy_chart_general
 * - source: PM_GD970201 (관측자료) + TM_GD970101 (ODM결과) + TM_GD120001 (관정)
 * - WITH CTE + UNION (분기1: 최근 QLT_ID=1 / 분기2: 과거 QLT_ID=5) + 정적 PIVOT
 * - PIVOT 항목: OBSRVN_ARTCL_ID IN (5, 163, 52, 333) — "5"=수위, "163"=수온, "52"=전기전도도, "333"=미사용
 * - 응답 6컬럼: GENNUM, YMD(YYYYMMDD), ELEV("5"), WTEMP("163"), LEV(ROUND(ALTD_VL-"5",2)), EC("52")
 * - 파라미터: gennum (String, TAG_CN 비교), begindate/enddate (YYYYMMDD)
 *
 * v3 컬럼 매핑:
 *   PM_GD60201 → PM_GD970201 (OBSR_DTA_VALUE→OBSRVN_DATA_VL, OBSR_DT→OBSRVN_DT, QLITY_ID→QLT_ID, RESULT_ID→RSLT_ID)
 *   TM_GD60101 → TM_GD970101 (OBSR_IEM_ID→OBSRVN_ARTCL_ID, SPOT_ID→BRNCH_ID, TAG_CTNT→TAG_CN, TIME_UNIT_ID→HR_UNIT_ID)
 *   TM_GD10001 → TM_GD120001 (GENNUM→GWEL_NO, AL_VALUE→ALTD_VL)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LinkageChartDailyHandler implements CustomOperationHandler {

    private static final String DATASOURCE_ID = "internal";
    private static final String OPERATION_ID  = "observationStationService/getGroundwaterMonitoringNetwork";

    private static final String SQL =
            "WITH TB AS ( " +
            "  SELECT TAG_CN AS GENNUM, " +
            "         TO_CHAR(OBSRVN_DT, 'YYYYMMDD') YMD, " +
            "         \"5\" AS ELEV, \"163\" AS WTEMP, " +
            "         ROUND((SELECT ALTD_VL FROM TM_GD120001 WHERE GWEL_NO = ?) - \"5\", 2) AS LEV, " +
            "         \"52\" AS EC " +
            "  FROM ( " +
            "    SELECT * FROM ( " +
            "      SELECT OBSRVN_DATA_VL, OBSRVN_DT, OBSRVN_ARTCL_ID, B.BRNCH_ID, B.TAG_CN " +
            "      FROM   PM_GD970201 A, " +
            "             (SELECT RSLT_ID, TAG_CN, OBSRVN_ARTCL_ID, BRNCH_ID " +
            "              FROM   TM_GD970101 " +
            "              WHERE  HR_UNIT_ID = 4 AND TAG_CN = ?) B " +
            "      WHERE  A.RSLT_ID = B.RSLT_ID " +
            "      AND    OBSRVN_DT BETWEEN TO_DATE(TO_CHAR(SYSDATE,'YYYY')-1||'0101','YYYYMMDD') " +
            "                          AND TO_DATE(TO_CHAR(SYSDATE,'YYYY')||'1231','YYYYMMDD') " +
            "      AND    A.QLT_ID = 1 " +
            "    ) PIVOT (MAX(OBSRVN_DATA_VL) FOR OBSRVN_ARTCL_ID IN (5, 163, 52, 333)) " +
            "  ) RR " +
            "  UNION " +
            "  SELECT TAG_CN AS GENNUM, " +
            "         TO_CHAR(OBSRVN_DT, 'YYYYMMDD') YMD, " +
            "         \"5\" AS ELEV, \"163\" AS WTEMP, " +
            "         ROUND((SELECT ALTD_VL FROM TM_GD120001 WHERE GWEL_NO = ?) - \"5\", 2) AS LEV, " +
            "         \"52\" AS EC " +
            "  FROM ( " +
            "    SELECT * FROM ( " +
            "      SELECT OBSRVN_DATA_VL, OBSRVN_DT, OBSRVN_ARTCL_ID, B.BRNCH_ID, B.TAG_CN " +
            "      FROM   PM_GD970201 A, " +
            "             (SELECT RSLT_ID, TAG_CN, OBSRVN_ARTCL_ID, BRNCH_ID " +
            "              FROM   TM_GD970101 " +
            "              WHERE  HR_UNIT_ID = 4 AND TAG_CN = ?) B " +
            "      WHERE  A.RSLT_ID = B.RSLT_ID " +
            "      AND    OBSRVN_DT BETWEEN TO_DATE('19951201','YYYYMMDD') " +
            "                          AND TO_DATE(TO_CHAR(SYSDATE,'YYYY')-2||'1231','YYYYMMDD') " +
            "      AND    A.QLT_ID = 5 " +
            "    ) PIVOT (MAX(OBSRVN_DATA_VL) FOR OBSRVN_ARTCL_ID IN (5, 163, 52, 333)) " +
            "  ) RR " +
            ") " +
            "SELECT * FROM TB " +
            "WHERE YMD BETWEEN ? AND ? " +
            "ORDER BY YMD ASC";

    private final ProviderDataSourceService dataSourceService;

    @Override
    public CustomOperationMetadata getMetadata() {
        return CustomOperationMetadata.builder()
                .operationId(OPERATION_ID)
                .operationName("B9 관측망 일 단위 차트")
                .description(
                        "관련 테이블: PM_GD970201, TM_GD970101, TM_GD120001\n" +
                        "변환: CTE + UNION (최근 QLT_ID=1 / 과거 QLT_ID=5) + 정적 PIVOT (수위/수온/전도도)"
                )
                .datasourceId(DATASOURCE_ID)
                .tableName("PM_GD970201")
                .pageSize(100)
                .maxPageSize(1000)
                .column(CustomColumnSpec.builder().columnName("gennum").aliasName("gennum").displayOrder(1).build())
                .column(CustomColumnSpec.builder().columnName("ymd").aliasName("ymd").displayOrder(2).build())
                .column(CustomColumnSpec.builder().columnName("elev").aliasName("elev").displayOrder(3).build())
                .column(CustomColumnSpec.builder().columnName("wtemp").aliasName("wtemp").displayOrder(4).build())
                .column(CustomColumnSpec.builder().columnName("lev").aliasName("lev").displayOrder(5).build())
                .column(CustomColumnSpec.builder().columnName("ec").aliasName("ec").displayOrder(6).build())
                .param(CustomParamSpec.builder()
                        .paramName("gennum").columnName("TAG_CN").operator("EQ")
                        .required(true).dataType("STRING").build())
                .param(CustomParamSpec.builder()
                        .paramName("begindate").columnName("YMD").operator("GTE")
                        .required(true).dataType("STRING").build())
                .param(CustomParamSpec.builder()
                        .paramName("enddate").columnName("YMD").operator("LTE")
                        .required(true).dataType("STRING").build())
                .build();
    }

    @Override
    public DynamicQueryResult handle(Map<String, String> params, int page, int pageSize) {
        long start = System.currentTimeMillis();

        String gennumStr = params.get("gennum");
        String begindate = params.get("begindate");
        String enddate   = params.get("enddate");
        if (gennumStr == null || gennumStr.isBlank()) {
            throw new IllegalArgumentException("필수 파라미터 누락: gennum");
        }
        if (begindate == null || begindate.isBlank()) {
            throw new IllegalArgumentException("필수 파라미터 누락: begindate (YYYYMMDD)");
        }
        if (enddate == null || enddate.isBlank()) {
            throw new IllegalArgumentException("필수 파라미터 누락: enddate (YYYYMMDD)");
        }
        long gennumNum;
        try {
            gennumNum = Long.parseLong(gennumStr.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("gennum 은 숫자여야 합니다: " + gennumStr);
        }
        final String gennum = gennumStr.trim();

        JdbcTemplate jdbc = dataSourceService.getJdbcTemplate(DATASOURCE_ID);

        List<Map<String, Object>> rows = jdbc.query(SQL,
                ps -> {
                    ps.setLong(1, gennumNum);     // 분기1: TM_GD120001.GWEL_NO 비교 (NUMBER)
                    ps.setString(2, gennum);      // 분기1: TM_GD970101.TAG_CN 비교 (VARCHAR2)
                    ps.setLong(3, gennumNum);     // 분기2: TM_GD120001.GWEL_NO 비교
                    ps.setString(4, gennum);      // 분기2: TM_GD970101.TAG_CN 비교
                    ps.setString(5, begindate);   // 외부: YMD >=
                    ps.setString(6, enddate);     // 외부: YMD <=
                },
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("gennum", rs.getString("GENNUM"));
                    row.put("ymd",    rs.getString("YMD"));
                    row.put("elev",   rs.getString("ELEV"));
                    row.put("wtemp",  rs.getString("WTEMP"));
                    row.put("lev",    rs.getObject("LEV"));
                    row.put("ec",     rs.getString("EC"));
                    return row;
                });

        long duration = System.currentTimeMillis() - start;
        int count = rows.size();
        log.debug("[LinkageChartDailyHandler] gennum={} {}~{} → {} rows ({}ms)",
                gennum, begindate, enddate, count, duration);

        return DynamicQueryResult.builder()
                .data(rows)
                .pagination(DynamicQueryResult.PaginationInfo.builder()
                        .page(1).pageSize(Math.max(1, count))
                        .totalCount(count).totalPages(count > 0 ? 1 : 0)
                        .build())
                .durationMs(duration)
                .build();
    }
}
