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
 * B15 — 수질검사항목 코드 종류 (searchAllInspection)
 *
 * 레거시 SQL (B14 와 source 동일 + GROUP BY 로 코드별 DISTINCT):
 *   SELECT A.QLTWTR_INSPCT_IEM_CODE, B.CD_CN AS REMARK_CTNT
 *   FROM   TM_GD110310 A
 *   LEFT JOIN (SELECT TRIM(UGWTR_COM_CD) AS UGWTR_COM_CD, CD_CN
 *              FROM   TC_GD000002 WHERE GROUP_CD_SN = 'NGW_0026') B
 *     ON A.QLTWTR_INSPCT_IEM_CODE = B.UGWTR_COM_CD
 *   GROUP BY A.QLTWTR_INSPCT_IEM_CODE, B.CD_CN
 *   ORDER BY A.QLTWTR_INSPCT_IEM_CODE
 *
 * - B14 와 source/JOIN 동일하지만 응답 형태 다름 (4컬럼 → 2컬럼 + 코드별 DISTINCT)
 * - 1:1 원칙 — 별도 핸들러 박음 (SQL 중복 허용)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InspectionDistinctHandler implements CustomOperationHandler {

    private static final String DATASOURCE_ID = "internal";
    private static final String OPERATION_ID = "searchAllInspection";

    private static final String BASE_SQL =
            "SELECT A.QLTWTR_INSPCT_IEM_CODE, B.CD_CN AS REMARK_CTNT " +
            "FROM   TM_GD110310 A " +
            "LEFT JOIN (SELECT TRIM(UGWTR_COM_CD) AS UGWTR_COM_CD, CD_CN " +
            "           FROM   TC_GD000002 WHERE GROUP_CD_SN = 'NGW_0026') B " +
            "  ON A.QLTWTR_INSPCT_IEM_CODE = B.UGWTR_COM_CD " +
            "GROUP BY A.QLTWTR_INSPCT_IEM_CODE, B.CD_CN " +
            "ORDER BY A.QLTWTR_INSPCT_IEM_CODE " +
            "OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";

    private static final String COUNT_SQL =
            "SELECT COUNT(*) FROM (" +
            "  SELECT A.QLTWTR_INSPCT_IEM_CODE, B.CD_CN " +
            "  FROM   TM_GD110310 A " +
            "  LEFT JOIN (SELECT TRIM(UGWTR_COM_CD) AS UGWTR_COM_CD, CD_CN " +
            "             FROM   TC_GD000002 WHERE GROUP_CD_SN = 'NGW_0026') B " +
            "    ON A.QLTWTR_INSPCT_IEM_CODE = B.UGWTR_COM_CD " +
            "  GROUP BY A.QLTWTR_INSPCT_IEM_CODE, B.CD_CN" +
            ")";

    private final ProviderDataSourceService dataSourceService;

    @Override
    public CustomOperationMetadata getMetadata() {
        return CustomOperationMetadata.builder()
                .operationId(OPERATION_ID)
                .operationName("B15 수질검사항목 코드 종류")
                .description(
                        "관련 테이블: TM_GD110310, TC_GD000002\n" +
                        "변환: LEFT JOIN + GROUP BY (코드별 DISTINCT, NGW_0026 공통코드 매핑)"
                )
                .datasourceId(DATASOURCE_ID)
                .tableName("TM_GD110310")
                .pageSize(100)
                .maxPageSize(1000)
                .column(CustomColumnSpec.builder().columnName("qltwtrInspctIemCode").aliasName("qltwtrInspctIemCode").displayOrder(1).build())
                .column(CustomColumnSpec.builder().columnName("remarkCtnt").aliasName("remarkCtnt").displayOrder(2).build())
                .build();
    }

    @Override
    public DynamicQueryResult handle(Map<String, String> params, int page, int pageSize) {
        long start = System.currentTimeMillis();
        JdbcTemplate jdbc = dataSourceService.getJdbcTemplate(DATASOURCE_ID);

        long totalCount = jdbc.queryForObject(COUNT_SQL, Long.class);
        int offset = Math.max(0, (page - 1) * pageSize);

        List<Map<String, Object>> rows = jdbc.query(BASE_SQL, ps -> {
            ps.setInt(1, offset);
            ps.setInt(2, pageSize);
        }, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("qltwtrInspctIemCode", rs.getString("QLTWTR_INSPCT_IEM_CODE"));
            row.put("remarkCtnt", rs.getString("REMARK_CTNT"));
            return row;
        });

        long duration = System.currentTimeMillis() - start;
        int totalPages = pageSize > 0 ? (int) Math.ceil((double) totalCount / pageSize) : 0;

        log.debug("[InspectionDistinctHandler] {} rows (page={}/{}, total={}) — {}ms",
                rows.size(), page, totalPages, totalCount, duration);

        return DynamicQueryResult.builder()
                .data(rows)
                .pagination(DynamicQueryResult.PaginationInfo.builder()
                        .page(page)
                        .pageSize(pageSize)
                        .totalCount(totalCount)
                        .totalPages(totalPages)
                        .build())
                .durationMs(duration)
                .build();
    }
}
