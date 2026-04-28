package com.gims.provider.custom.handler;

import com.gims.provider.custom.CustomColumnSpec;
import com.gims.provider.custom.CustomOperationHandler;
import com.gims.provider.custom.CustomOperationMetadata;
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
 * B16-KB — 경상북도 이용실태 상세 (actualUseDetailKB)
 *
 * 레거시 v3: opn.actualUseDetailDJ SQL 재호출 — controller 가 brtcNm='경상북도' set
 * DJ 핸들러와 SQL 동일 (1:1 원칙 — operationId 별 별도 클래스, SQL 중복 허용)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActualUseDetailKbHandler implements CustomOperationHandler {

    private static final String DATASOURCE_ID = "internal";
    private static final String OPERATION_ID = "actualUseDetailKB/actualUseDetailKB";
    private static final String BRTC_NM = "경상북도";

    private static final String SQL =
            "WITH CMM AS (" +
            "  SELECT CTPV_NM, " +
            "         DECODE(CTPV_NM, '세종특별자치시', CTPV_NM, SGG_NM) SGG_NM, " +
            "         EMD_NM, A.STDG_CD, LCLGV_CD " +
            "  FROM TC_GD00100 A JOIN TM_GD010930 B ON A.STDG_CD = B.STDG_CD " +
            "  WHERE A.DEL_YMD IS NULL AND EMD_NM IS NULL" +
            "), " +
            "RST AS (" +
            "  SELECT * FROM (" +
            "    SELECT g.*, ROW_NUMBER() OVER (PARTITION BY SF_TEAM_CODE ORDER BY SF_TEAM_CODE) RN " +
            "    FROM RGETNTGMS02 g " +
            "    WHERE CRIT_YY = TO_CHAR(SYSDATE, 'YYYY') - 1" +
            "  ) WHERE RN = 1" +
            ") " +
            "SELECT CTPV_NM || ' ' || SGG_NM AS SIGUNGU, " +
            "       CRIT_YY AS YEAR, " +
            "       SF_TEAM_NM AS DEPART, " +
            "       WRK_CMPT_YMD AS YMD, " +
            "       DECODE(NVL(WRK_CMPT_YMD, '0'), '0', '미완료', '완료') AS YN " +
            "FROM (" +
            "  SELECT R.*, C.STDG_CD, C.CTPV_NM, C.SGG_NM " +
            "  FROM CMM C LEFT JOIN RST R " +
            "    ON DECODE(R.SF_TEAM_CODE, '3510500', '3510000', R.SF_TEAM_CODE) = C.LCLGV_CD" +
            ") " +
            "WHERE CTPV_NM = ? " +
            "ORDER BY STDG_CD";

    private final ProviderDataSourceService dataSourceService;

    @Override
    public CustomOperationMetadata getMetadata() {
        return CustomOperationMetadata.builder()
                .operationId(OPERATION_ID)
                .operationName("B16 경북 이용실태 상세")
                .description(
                        "관련 테이블: TC_GD00100, TM_GD010930, RGETNTGMS02 (경상북도 한정)\n" +
                        "변환: CTE 2개 + LEFT JOIN + ROW_NUMBER + DECODE (작년 데이터 기준)"
                )
                .datasourceId(DATASOURCE_ID)
                .tableName("RGETNTGMS02")
                .pageSize(50)
                .maxPageSize(200)
                .column(CustomColumnSpec.builder().columnName("sigungu").aliasName("sigungu").displayOrder(1).build())
                .column(CustomColumnSpec.builder().columnName("year").aliasName("year").displayOrder(2).build())
                .column(CustomColumnSpec.builder().columnName("depart").aliasName("depart").displayOrder(3).build())
                .column(CustomColumnSpec.builder().columnName("ymd").aliasName("ymd").displayOrder(4).build())
                .column(CustomColumnSpec.builder().columnName("YN").aliasName("YN").displayOrder(5).build())
                .build();
    }

    @Override
    public DynamicQueryResult handle(Map<String, String> params, int page, int pageSize) {
        long start = System.currentTimeMillis();
        JdbcTemplate jdbc = dataSourceService.getJdbcTemplate(DATASOURCE_ID);

        List<Map<String, Object>> rows = jdbc.query(SQL,
                ps -> ps.setString(1, BRTC_NM),
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("sigungu", rs.getString("SIGUNGU"));
                    row.put("year",    rs.getString("YEAR"));
                    row.put("depart",  rs.getString("DEPART"));
                    row.put("ymd",     rs.getString("YMD"));
                    row.put("YN",      rs.getString("YN"));
                    return row;
                });

        long duration = System.currentTimeMillis() - start;
        int count = rows.size();
        log.debug("[ActualUseDetailKbHandler] {} rows ({}ms)", count, duration);

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
