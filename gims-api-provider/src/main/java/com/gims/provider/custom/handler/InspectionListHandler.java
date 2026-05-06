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
 * B14 파일럿 — 수질검사항목 목록 (searchInspection)
 *
 * 레거시 SQL:
 *   SELECT A.JOSACODE, A.DTA_STDR_YEAR, A.QLTWTR_INSPCT_IEM_CODE, B.CD_CN AS REMARK_CTNT
 *   FROM   TM_GD110310 A
 *   LEFT JOIN (SELECT TRIM(UGWTR_COM_CD) AS UGWTR_COM_CD, CD_CN
 *              FROM   TC_GD000002 WHERE GROUP_CD_SN = 'NGW_0026') B
 *     ON A.QLTWTR_INSPCT_IEM_CODE = B.UGWTR_COM_CD
 *   ORDER BY A.JOSACODE, A.DTA_STDR_YEAR, A.QLTWTR_INSPCT_IEM_CODE
 *
 * - 외부 GIMS Oracle 직접 쿼리 (datasourceId="internal")
 * - TC_GD000002.UGWTR_COM_CD 가 CHAR(50) 패딩이라 TRIM 필수
 * - 응답 키는 대문자 (레거시 호환)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InspectionListHandler implements CustomOperationHandler {

    private static final String DATASOURCE_ID = "internal";
    private static final String OPERATION_ID = "searchInspection";

    private static final String BASE_SQL =
            "SELECT A.JOSACODE, A.DTA_STDR_YEAR, A.QLTWTR_INSPCT_IEM_CODE, B.CD_CN AS REMARK_CTNT " +
            "FROM   TM_GD110310 A " +
            "LEFT JOIN (SELECT TRIM(UGWTR_COM_CD) AS UGWTR_COM_CD, CD_CN " +
            "           FROM   TC_GD000002 WHERE GROUP_CD_SN = 'NGW_0026') B " +
            "  ON A.QLTWTR_INSPCT_IEM_CODE = B.UGWTR_COM_CD " +
            "ORDER BY A.JOSACODE, A.DTA_STDR_YEAR, A.QLTWTR_INSPCT_IEM_CODE " +
            "OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";

    private static final String COUNT_SQL = "SELECT COUNT(*) FROM TM_GD110310";

    private final ProviderDataSourceService dataSourceService;

    @Override
    public CustomOperationMetadata getMetadata() {
        return CustomOperationMetadata.builder()
                .operationId(OPERATION_ID)
                .operationName("B14 수질검사항목 목록")
                .description(
                        "관련 테이블: TM_GD110310, TC_GD000002\n" +
                        "변환: LEFT JOIN (NGW_0026 공통코드 매핑)"
                )
                .datasourceId(DATASOURCE_ID)
                .tableName("TM_GD110310")
                .pageSize(100)
                .maxPageSize(1000)
                .column(CustomColumnSpec.builder().columnName("josacode").aliasName("josacode").displayOrder(1).build())
                .column(CustomColumnSpec.builder().columnName("dtaStdrYear").aliasName("dtaStdrYear").displayOrder(2).build())
                .column(CustomColumnSpec.builder().columnName("qltwtrInspctIemCode").aliasName("qltwtrInspctIemCode").displayOrder(3).build())
                .column(CustomColumnSpec.builder().columnName("remarkCtnt").aliasName("remarkCtnt").displayOrder(4).build())
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
            row.put("josacode", rs.getObject("JOSACODE"));
            row.put("dtaStdrYear", rs.getString("DTA_STDR_YEAR"));
            row.put("qltwtrInspctIemCode", rs.getString("QLTWTR_INSPCT_IEM_CODE"));
            row.put("remarkCtnt", rs.getString("REMARK_CTNT"));
            return row;
        });

        long duration = System.currentTimeMillis() - start;
        int totalPages = pageSize > 0 ? (int) Math.ceil((double) totalCount / pageSize) : 0;

        log.debug("[InspectionListHandler] {} rows (page={}/{}, total={}) — {}ms",
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
