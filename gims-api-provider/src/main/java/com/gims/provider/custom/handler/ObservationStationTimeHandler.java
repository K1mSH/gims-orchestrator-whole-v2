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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * B10 — 관측소 시 단위 차트 (observationStationTimeService)
 *
 * 레거시: opn.observationStationTimeService
 * - source: PM_GD970201 + TM_GD970101 (HR_UNIT_ID=3, 시 단위) + TM_GD120001
 * - 정적 PIVOT (5/163/52/333)
 * - 응답 6컬럼: GENNUM, YMD(YYYYMMDDHH24), ELEV=TRUNC("5",2), WTEMP=TRUNC("163",1), LEV=ROUND(ALTD_VL-"5",2), EC="52"
 * - 파라미터: gennum, begindate(YYYYMMDD), enddate(YYYYMMDD), datatype
 *   - datatype="1" → QLT_ID=1 (실시간/raw)
 *   - 그 외 (default) → QLT_ID=5 (검증)
 *
 * B9 와 차이:
 *   HR_UNIT_ID=3 (B9 는 4) / YMD 형식 YYYYMMDDHH24 / TRUNC 적용 / UNION 없음 / datatype 분기
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ObservationStationTimeHandler implements CustomOperationHandler {

    private static final String DATASOURCE_ID = "internal";
    private static final String OPERATION_ID  = "observationStationTimeService/observationStationTimeService";

    private static final String SQL =
            "SELECT TAG_CN AS GENNUM, " +
            "       TO_CHAR(OBSRVN_DT, 'YYYYMMDDHH24') YMD, " +
            "       TRUNC(\"5\", 2) AS ELEV, " +
            "       TRUNC(\"163\", 1) WTEMP, " +
            "       ROUND((SELECT ALTD_VL FROM TM_GD120001 WHERE GWEL_NO = ?) - \"5\", 2) AS LEV, " +
            "       \"52\" AS EC " +
            "FROM ( " +
            "  SELECT * FROM ( " +
            "    SELECT OBSRVN_DATA_VL, OBSRVN_DT, OBSRVN_ARTCL_ID, B.BRNCH_ID, B.TAG_CN " +
            "    FROM   PM_GD970201 A, " +
            "           (SELECT RSLT_ID, TAG_CN, OBSRVN_ARTCL_ID, BRNCH_ID " +
            "            FROM   TM_GD970101 " +
            "            WHERE  HR_UNIT_ID = 3 AND TAG_CN = ?) B " +
            "    WHERE  A.RSLT_ID = B.RSLT_ID " +
            "    AND    OBSRVN_DT BETWEEN TO_DATE(? || '00', 'YYYYMMDDHH24') " +
            "                        AND TO_DATE(? || '23', 'YYYYMMDDHH24') " +
            "    AND    A.QLT_ID = ? " +
            "  ) PIVOT (MAX(OBSRVN_DATA_VL) FOR OBSRVN_ARTCL_ID IN (5, 163, 52, 333)) " +
            ") RR " +
            "ORDER BY YMD";

    private final ProviderDataSourceService dataSourceService;

    @Override
    public CustomOperationMetadata getMetadata() {
        return CustomOperationMetadata.builder()
                .operationId(OPERATION_ID)
                .operationName("B10 관측소 시 단위 차트")
                .description(
                        "관련 테이블: PM_GD970201, TM_GD970101 (HR_UNIT_ID=3 시 단위), TM_GD120001\n" +
                        "변환: 정적 PIVOT (수위/수온/전도도) + datatype 분기 (1=raw, 그 외=검증)"
                )
                .datasourceId(DATASOURCE_ID)
                .tableName("PM_GD970201")
                .pageSize(100)
                .maxPageSize(1000)
                .column(CustomColumnSpec.builder().columnName("GENNUM").aliasName("GENNUM").displayOrder(1).build())
                .column(CustomColumnSpec.builder().columnName("YMD").aliasName("YMD").displayOrder(2).build())
                .column(CustomColumnSpec.builder().columnName("ELEV").aliasName("ELEV").displayOrder(3).build())
                .column(CustomColumnSpec.builder().columnName("WTEMP").aliasName("WTEMP").displayOrder(4).build())
                .column(CustomColumnSpec.builder().columnName("LEV").aliasName("LEV").displayOrder(5).build())
                .column(CustomColumnSpec.builder().columnName("EC").aliasName("EC").displayOrder(6).build())
                .param(CustomParamSpec.builder()
                        .paramName("gennum").columnName("TAG_CN").operator("EQ")
                        .required(true).dataType("STRING").build())
                .param(CustomParamSpec.builder()
                        .paramName("begindate").columnName("OBSRVN_DT").operator("GTE")
                        .required(true).dataType("STRING").build())
                .param(CustomParamSpec.builder()
                        .paramName("enddate").columnName("OBSRVN_DT").operator("LTE")
                        .required(true).dataType("STRING").build())
                .param(CustomParamSpec.builder()
                        .paramName("datatype").columnName("QLT_ID").operator("EQ")
                        .required(false).dataType("STRING").build())
                .build();
    }

    @Override
    public DynamicQueryResult handle(Map<String, String> params, int page, int pageSize) {
        long start = System.currentTimeMillis();

        String gennumStr = params.get("gennum");
        String begindate = params.get("begindate");
        String enddate   = params.get("enddate");
        String datatype  = params.get("datatype");

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
        final int qltId = "1".equals(datatype != null ? datatype.trim() : null) ? 1 : 5;

        JdbcTemplate jdbc = dataSourceService.getJdbcTemplate(DATASOURCE_ID);

        List<Map<String, Object>> rows = jdbc.query(SQL,
                ps -> {
                    ps.setLong(1, gennumNum);     // ALTD_VL WHERE GWEL_NO = ?
                    ps.setString(2, gennum);      // TAG_CN = ?
                    ps.setString(3, begindate);   // BETWEEN ? || '00'
                    ps.setString(4, enddate);     // AND ? || '23'
                    ps.setInt(5, qltId);          // QLT_ID = ?
                },
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("GENNUM", rs.getString("GENNUM"));
                    row.put("YMD",    rs.getString("YMD"));
                    row.put("ELEV",   rs.getObject("ELEV"));
                    row.put("WTEMP",  rs.getObject("WTEMP"));
                    row.put("LEV",    rs.getObject("LEV"));
                    row.put("EC",     rs.getString("EC"));
                    return row;
                });

        long duration = System.currentTimeMillis() - start;
        int count = rows.size();
        log.debug("[ObservationStationTimeHandler] gennum={} {}~{} datatype={} (QLT_ID={}) → {} rows ({}ms)",
                gennum, begindate, enddate, datatype, qltId, count, duration);

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
