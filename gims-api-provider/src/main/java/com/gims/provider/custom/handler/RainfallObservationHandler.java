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
 * B8 — 우량관측소 상세정보 (observationStationService/getRainfallStation)
 *
 * 레거시: opn.info_observation_station0
 * - source: DBLINKUSR.DUBRFOBSIF + DUBMMRF + V_WP_WKSDAMSBSN
 *   (v3 SQL 의 DUBRFOBSIF/DUBMMRF 는 prefix 누락 — DBLINKUSR 로 가정, 다른 테이블과 일관)
 * - 응답 12 컬럼: con1(ROWNUM), obsnm, sbsncd_nm, gvcd, addr, lon, lat, obsopndt, rf, acurf, obsggtyp, large_cnm
 * - 파라미터: rfobscd
 * - 단건 조회 (ROWNUM=1) + 동적 OBSDHM (현재 시각 10분 단위, B7 동일)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RainfallObservationHandler implements CustomOperationHandler {

    private static final String DATASOURCE_ID = "internal";
    private static final String OPERATION_ID  = "observationStationService/getRainfallStation";

    private static final String SQL =
            "SELECT * FROM ( " +
            "  SELECT ROWNUM AS CON1, " +
            "         A.OBSNM AS OBSNM, " +
            "         C.SBSNCD_NM SBSNCD_NM, " +
            "         TRIM(A.GVCD) AS GVCD, " +
            "         A.ADDR AS ADDR, " +
            "         TRIM(A.LON) AS LON, " +
            "         TRIM(A.LAT) AS LAT, " +
            "         TO_CHAR(A.OBSOPNDT,'YYYY/MM/DD') AS OBSOPNDT, " +
            "         TRUNC(TRIM(B.RF),2) AS RF, " +
            "         TRUNC(TRIM(B.ACURF),2) AS ACURF, " +
            "         TRIM(A.OBSGGTYP) AS OBSGGTYP, " +
            "         C.LARGE_CNM LARGE_CNM " +
            "  FROM   DBLINKUSR.DUBRFOBSIF A " +
            "  LEFT   OUTER JOIN DBLINKUSR.DUBMMRF B " +
            "         ON B.RFOBSCD = A.RFOBSCD " +
            "         AND B.OBSDHM = " +
            "           CASE WHEN TO_CHAR(SYSDATE,'MI') LIKE '0%' " +
            "                THEN TO_CHAR(SYSDATE - 1/24, 'YYYYMMDDHH24') " +
            "                ELSE TO_CHAR(SYSDATE, 'YYYYMMDDHH24') END " +
            "           || (CASE WHEN SUBSTR(TO_CHAR(SYSDATE - 1/24,'MI'),1,1)='0' THEN '50' " +
            "                    WHEN SUBSTR(TO_CHAR(SYSDATE,'MI'),1,1)='1' THEN '00' " +
            "                    WHEN SUBSTR(TO_CHAR(SYSDATE,'MI'),1,1)='2' THEN '10' " +
            "                    WHEN SUBSTR(TO_CHAR(SYSDATE,'MI'),1,1)='3' THEN '20' " +
            "                    WHEN SUBSTR(TO_CHAR(SYSDATE,'MI'),1,1)='4' THEN '30' " +
            "                    WHEN SUBSTR(TO_CHAR(SYSDATE,'MI'),1,1)='5' THEN '40' " +
            "                    ELSE '00' END) " +
            "         AND B.TRMDV = '10' " +
            "  LEFT   OUTER JOIN DBLINKUSR.V_WP_WKSDAMSBSN C " +
            "         ON C.LARGE_CODE = SUBSTR(A.SBSNCD, 0, 2) " +
            "         AND C.SBSNCD = A.SBSNCD " +
            "  WHERE  TRIM(A.RFOBSCD) = ? " +
            ") WHERE ROWNUM = 1";

    private final ProviderDataSourceService dataSourceService;

    @Override
    public CustomOperationMetadata getMetadata() {
        return CustomOperationMetadata.builder()
                .operationId(OPERATION_ID)
                .operationName("B8 우량관측소 상세")
                .description(
                        "관련 테이블: DBLINKUSR.DUBRFOBSIF, DUBMMRF, V_WP_WKSDAMSBSN (외부 DBLINK)\n" +
                        "변환: 3-way LEFT JOIN + 동적 OBSDHM + 단건 조회 (ROWNUM=1)"
                )
                .datasourceId(DATASOURCE_ID)
                .tableName("DUBRFOBSIF")
                .pageSize(1)
                .maxPageSize(1)
                .column(CustomColumnSpec.builder().columnName("con1").aliasName("con1").displayOrder(1).build())
                .column(CustomColumnSpec.builder().columnName("obsnm").aliasName("obsnm").displayOrder(2).build())
                .column(CustomColumnSpec.builder().columnName("sbsncd_nm").aliasName("sbsncd_nm").displayOrder(3).build())
                .column(CustomColumnSpec.builder().columnName("gvcd").aliasName("gvcd").displayOrder(4).build())
                .column(CustomColumnSpec.builder().columnName("addr").aliasName("addr").displayOrder(5).build())
                .column(CustomColumnSpec.builder().columnName("lon").aliasName("lon").displayOrder(6).build())
                .column(CustomColumnSpec.builder().columnName("lat").aliasName("lat").displayOrder(7).build())
                .column(CustomColumnSpec.builder().columnName("obsopndt").aliasName("obsopndt").displayOrder(8).build())
                .column(CustomColumnSpec.builder().columnName("rf").aliasName("rf").displayOrder(9).build())
                .column(CustomColumnSpec.builder().columnName("acurf").aliasName("acurf").displayOrder(10).build())
                .column(CustomColumnSpec.builder().columnName("obsggtyp").aliasName("obsggtyp").displayOrder(11).build())
                .column(CustomColumnSpec.builder().columnName("large_cnm").aliasName("large_cnm").displayOrder(12).build())
                .param(CustomParamSpec.builder()
                        .paramName("rfobscd").columnName("RFOBSCD").operator("EQ")
                        .required(true).dataType("STRING").build())
                .build();
    }

    @Override
    public DynamicQueryResult handle(Map<String, String> params, int page, int pageSize) {
        long start = System.currentTimeMillis();
        String rfobscd = params.get("rfobscd");
        if (rfobscd == null || rfobscd.isBlank()) {
            throw new IllegalArgumentException("필수 파라미터 누락: rfobscd");
        }

        JdbcTemplate jdbc = dataSourceService.getJdbcTemplate(DATASOURCE_ID);

        List<Map<String, Object>> rows = jdbc.query(SQL,
                ps -> ps.setString(1, rfobscd.trim()),
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("con1",      rs.getObject("CON1"));
                    row.put("obsnm",     rs.getString("OBSNM"));
                    row.put("sbsncd_nm", rs.getString("SBSNCD_NM"));
                    row.put("gvcd",      rs.getString("GVCD"));
                    row.put("addr",      rs.getString("ADDR"));
                    row.put("lon",       rs.getString("LON"));
                    row.put("lat",       rs.getString("LAT"));
                    row.put("obsopndt",  rs.getString("OBSOPNDT"));
                    row.put("rf",        rs.getObject("RF"));
                    row.put("acurf",     rs.getObject("ACURF"));
                    row.put("obsggtyp",  rs.getString("OBSGGTYP"));
                    row.put("large_cnm", rs.getString("LARGE_CNM"));
                    return row;
                });

        long duration = System.currentTimeMillis() - start;
        int count = rows.size();
        log.debug("[RainfallObservationHandler] rfobscd={} → {} rows ({}ms)", rfobscd, count, duration);

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
