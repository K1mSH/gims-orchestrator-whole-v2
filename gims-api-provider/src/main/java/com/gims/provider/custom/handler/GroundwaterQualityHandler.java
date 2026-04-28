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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * B6 — 수질측정망 상세정보 (groundwaterMonitoringNetworkService/getGroundwaterQualityMeasurement)
 *
 * 레거시 SQL (opn.info_general_211215):
 *   SELECT DISTINCT
 *     SIDO||' '||SIGUNGU||' '||UPMYUNDO||' '||NVL(RI,'')||' '||ADDR ADDR,
 *     JIGUNAME, WELLNUM, GROUNDUSE, DRINKOX, GUBUN
 *   FROM   VIEW_GTEST
 *   WHERE  GENNUM = ?
 *   GROUP BY GENNUM, SIDO, SIGUNGU, UPMYUNDO, RI, ADDR, JOSACODE,
 *            JIGUNAME, GWMYR, WELLNUM, GROUNDUSE, DRINKOX, GUBUN
 *
 * - GENNUM 단건 조회 (필수 파라미터)
 * - VIEW_GTEST 가 운영은 Oracle 뷰, 개발은 물리 테이블로 단순화
 * - 응답 6 컬럼 (주소 결합 + 5 메타)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GroundwaterQualityHandler implements CustomOperationHandler {

    private static final String DATASOURCE_ID = "internal";
    private static final String OPERATION_ID = "groundwaterMonitoringNetworkService/getGroundwaterQualityMeasurement";

    private static final String SQL =
            "SELECT DISTINCT " +
            "  SIDO || ' ' || SIGUNGU || ' ' || UPMYUNDO || ' ' || NVL(RI, '') || ' ' || ADDR AS ADDR, " +
            "  JIGUNAME, WELLNUM, GROUNDUSE, DRINKOX, GUBUN " +
            "FROM   VIEW_GTEST " +
            "WHERE  GENNUM = ? " +
            "GROUP BY GENNUM, SIDO, SIGUNGU, UPMYUNDO, RI, ADDR, JOSACODE, " +
            "         JIGUNAME, GWMYR, WELLNUM, GROUNDUSE, DRINKOX, GUBUN";

    private final ProviderDataSourceService dataSourceService;

    @Override
    public CustomOperationMetadata getMetadata() {
        return CustomOperationMetadata.builder()
                .operationId(OPERATION_ID)
                .operationName("B6 수질측정망 상세")
                .description(
                        "관련 테이블: VIEW_GTEST (운영 Oracle 뷰 / 개발 물리 테이블)\n" +
                        "변환: 단건 조회 + 주소 컬럼 결합 (SIDO~ADDR)"
                )
                .datasourceId(DATASOURCE_ID)
                .tableName("VIEW_GTEST")
                .pageSize(1)
                .maxPageSize(1)
                .column(CustomColumnSpec.builder().columnName("addr").aliasName("addr").displayOrder(1).build())
                .column(CustomColumnSpec.builder().columnName("jiguname").aliasName("jiguname").displayOrder(2).build())
                .column(CustomColumnSpec.builder().columnName("wellnum").aliasName("wellnum").displayOrder(3).build())
                .column(CustomColumnSpec.builder().columnName("grounduse").aliasName("grounduse").displayOrder(4).build())
                .column(CustomColumnSpec.builder().columnName("drinkox").aliasName("drinkox").displayOrder(5).build())
                .column(CustomColumnSpec.builder().columnName("gubun").aliasName("gubun").displayOrder(6).build())
                .param(CustomParamSpec.builder()
                        .paramName("gennum").columnName("GENNUM").operator("EQ")
                        .required(true).dataType("NUMBER").build())
                .build();
    }

    @Override
    public DynamicQueryResult handle(Map<String, String> params, int page, int pageSize) {
        long start = System.currentTimeMillis();
        String gennumStr = params.get("gennum");
        if (gennumStr == null || gennumStr.isBlank()) {
            throw new IllegalArgumentException("필수 파라미터 누락: gennum");
        }
        long gennum;
        try {
            gennum = Long.parseLong(gennumStr.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("gennum 은 숫자여야 합니다: " + gennumStr);
        }

        JdbcTemplate jdbc = dataSourceService.getJdbcTemplate(DATASOURCE_ID);

        List<Map<String, Object>> rows = jdbc.query(SQL, ps -> ps.setLong(1, gennum), (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("addr", rs.getString("ADDR"));
            row.put("jiguname", rs.getString("JIGUNAME"));
            row.put("wellnum", rs.getString("WELLNUM"));
            row.put("grounduse", rs.getString("GROUNDUSE"));
            row.put("drinkox", rs.getString("DRINKOX"));
            row.put("gubun", rs.getString("GUBUN"));
            return row;
        });

        long duration = System.currentTimeMillis() - start;
        int count = rows.size();
        log.debug("[GroundwaterQualityHandler] gennum={} → {} rows ({}ms)", gennum, count, duration);

        return DynamicQueryResult.builder()
                .data(rows)
                .pagination(DynamicQueryResult.PaginationInfo.builder()
                        .page(1)
                        .pageSize(Math.max(1, count))
                        .totalCount(count)
                        .totalPages(count > 0 ? 1 : 0)
                        .build())
                .durationMs(duration)
                .build();
    }
}
