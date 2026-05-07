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
 * B7 — 수위관측소 상세정보 (observationStationService/getWaterLevelObservationStation)
 *
 * 레거시: opn.info_observation_station1
 * - source: DBLINKUSR.DUBWLOBSIF + DUBMMWL + V_WP_WKSDAMSBSN + V_WR_HACHEON_MST
 *   (운영은 DBLINK 통한 외부 시스템 — 개발은 internal-oracle 의 DBLINKUSR 스키마로 흉내)
 * - 응답 15 컬럼 (소문자/snake): obsnm, mggvcd, addr, lat, lon, opndt, nrivnm, obskdcd, odambsna, mrjpdis, zrelm, wl, flw, sbsncd_nm, large_cnm
 * - 파라미터: wlobscd
 * - 동적 OBSDHM: 현재 시각 기준 10분 단위 (YYYYMMDDHH24 + MI 분기) — v3 SQL 그대로
 *
 * 컬럼 매핑 없음 — DBLINKUSR 외부 스키마라 표준화 적용 안 됨 (v3 SQL alias 그대로)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WaterLevelObservationHandler implements CustomOperationHandler {

    private static final String DATASOURCE_ID = "internal";
    private static final String OPERATION_ID  = "observationStationService/getWaterLevelObservationStation";

    private static final String SQL =
            "SELECT A.OBSNM AS OBSNM, " +
            "       TRIM(A.MGGVCD) MGGVCD, " +
            "       A.ADDR AS ADDR, " +
            "       TRIM(A.LAT) AS LAT, " +
            "       TRIM(A.LON) AS LON, " +
            "       TO_CHAR(A.OPNDT,'YYYY/MM/DD') AS OPNDT, " +
            "       D.NRIVNM NRIVNM, " +
            "       TRIM(A.OBSKDCD) OBSKDCD, " +
            "       A.ODAMBSNA ODAMBSNA, " +
            "       A.MRJPDIS MRJPDIS, " +
            "       TRUNC(A.ZRELM,2) AS ZRELM, " +
            "       TRUNC(TRIM(B.WL),2) AS WL, " +
            "       TRUNC(TRIM(B.FLW),2) AS FLW, " +
            "       C.SBSNCD_NM SBSNCD_NM, " +
            "       C.LARGE_CNM LARGE_CNM " +
            "FROM   DBLINKUSR.DUBWLOBSIF A " +
            "LEFT   OUTER JOIN DBLINKUSR.DUBMMWL B " +
            "       ON B.WLOBSCD = A.WLOBSCD " +
            "       AND B.OBSDHM = " +
            "         CASE WHEN TO_CHAR(SYSDATE,'MI') LIKE '0%' " +
            "              THEN TO_CHAR(SYSDATE - 1/24, 'YYYYMMDDHH24') " +
            "              ELSE TO_CHAR(SYSDATE, 'YYYYMMDDHH24') END " +
            "         || (CASE WHEN SUBSTR(TO_CHAR(SYSDATE - 1/24,'MI'),1,1)='0' THEN '50' " +
            "                  WHEN SUBSTR(TO_CHAR(SYSDATE,'MI'),1,1)='1' THEN '00' " +
            "                  WHEN SUBSTR(TO_CHAR(SYSDATE,'MI'),1,1)='2' THEN '10' " +
            "                  WHEN SUBSTR(TO_CHAR(SYSDATE,'MI'),1,1)='3' THEN '20' " +
            "                  WHEN SUBSTR(TO_CHAR(SYSDATE,'MI'),1,1)='4' THEN '30' " +
            "                  WHEN SUBSTR(TO_CHAR(SYSDATE,'MI'),1,1)='5' THEN '40' " +
            "                  ELSE '00' END) " +
            "       AND B.TRMDV = '10' " +
            "LEFT   OUTER JOIN DBLINKUSR.V_WP_WKSDAMSBSN C " +
            "       ON C.SBSNCD_OLD = A.SBSNCD " +
            "       AND C.LARGE_CODE = SUBSTR(A.SBSNCD, 0, 2) " +
            "LEFT   OUTER JOIN DBLINKUSR.V_WR_HACHEON_MST D " +
            "       ON D.NRIVCD = A.RIVCD " +
            "WHERE  TRIM(A.WLOBSCD) = ?";

    private final ProviderDataSourceService dataSourceService;

    @Override
    public CustomOperationMetadata getMetadata() {
        return CustomOperationMetadata.builder()
                .operationId(OPERATION_ID)
                .operationName("B7 수위관측소 상세")
                .description(
                        "관련 테이블: DBLINKUSR.DUBWLOBSIF, DUBMMWL, V_WP_WKSDAMSBSN, V_WR_HACHEON_MST (외부 DBLINK)\n" +
                        "변환: 4-way LEFT JOIN + 동적 OBSDHM (현재 시각 기준 10분 단위) + 단건 조회"
                )
                .datasourceId(DATASOURCE_ID)
                .tableName("DUBWLOBSIF")
                .pageSize(1)
                .maxPageSize(1)
                .column(CustomColumnSpec.builder().columnName("obsnm").aliasName("obsnm").displayOrder(1).build())
                .column(CustomColumnSpec.builder().columnName("mggvcd").aliasName("mggvcd").displayOrder(2).build())
                .column(CustomColumnSpec.builder().columnName("addr").aliasName("addr").displayOrder(3).build())
                .column(CustomColumnSpec.builder().columnName("lat").aliasName("lat").displayOrder(4).build())
                .column(CustomColumnSpec.builder().columnName("lon").aliasName("lon").displayOrder(5).build())
                .column(CustomColumnSpec.builder().columnName("opndt").aliasName("opndt").displayOrder(6).build())
                .column(CustomColumnSpec.builder().columnName("nrivnm").aliasName("nrivnm").displayOrder(7).build())
                .column(CustomColumnSpec.builder().columnName("obskdcd").aliasName("obskdcd").displayOrder(8).build())
                .column(CustomColumnSpec.builder().columnName("odambsna").aliasName("odambsna").displayOrder(9).build())
                .column(CustomColumnSpec.builder().columnName("mrjpdis").aliasName("mrjpdis").displayOrder(10).build())
                .column(CustomColumnSpec.builder().columnName("zrelm").aliasName("zrelm").displayOrder(11).build())
                .column(CustomColumnSpec.builder().columnName("wl").aliasName("wl").displayOrder(12).build())
                .column(CustomColumnSpec.builder().columnName("flw").aliasName("flw").displayOrder(13).build())
                .column(CustomColumnSpec.builder().columnName("sbsncd_nm").aliasName("sbsncd_nm").displayOrder(14).build())
                .column(CustomColumnSpec.builder().columnName("large_cnm").aliasName("large_cnm").displayOrder(15).build())
                .param(CustomParamSpec.builder()
                        .paramName("wlobscd").columnName("WLOBSCD").operator("EQ")
                        .required(true).dataType("STRING").build())
                .build();
    }

    @Override
    public DynamicQueryResult handle(Map<String, String> params, int page, int pageSize) {
        long start = System.currentTimeMillis();
        String wlobscd = params.get("wlobscd");
        if (wlobscd == null || wlobscd.isBlank()) {
            throw new IllegalArgumentException("필수 파라미터 누락: wlobscd");
        }

        JdbcTemplate jdbc = dataSourceService.getJdbcTemplate(DATASOURCE_ID);

        List<Map<String, Object>> rows = jdbc.query(SQL,
                ps -> ps.setString(1, wlobscd.trim()),
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("obsnm",     rs.getString("OBSNM"));
                    row.put("mggvcd",    rs.getString("MGGVCD"));
                    row.put("addr",      rs.getString("ADDR"));
                    row.put("lat",       rs.getString("LAT"));
                    row.put("lon",       rs.getString("LON"));
                    row.put("opndt",     rs.getString("OPNDT"));
                    row.put("nrivnm",    rs.getString("NRIVNM"));
                    row.put("obskdcd",   rs.getString("OBSKDCD"));
                    row.put("odambsna", rs.getObject("ODAMBSNA"));
                    row.put("mrjpdis",  rs.getObject("MRJPDIS"));
                    row.put("zrelm",    rs.getObject("ZRELM"));
                    row.put("wl",       rs.getObject("WL"));
                    row.put("flw",      rs.getObject("FLW"));
                    row.put("sbsncd_nm", rs.getString("SBSNCD_NM"));
                    row.put("large_cnm", rs.getString("LARGE_CNM"));
                    return row;
                });

        long duration = System.currentTimeMillis() - start;
        int count = rows.size();
        log.debug("[WaterLevelObservationHandler] wlobscd={} → {} rows ({}ms)", wlobscd, count, duration);

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
