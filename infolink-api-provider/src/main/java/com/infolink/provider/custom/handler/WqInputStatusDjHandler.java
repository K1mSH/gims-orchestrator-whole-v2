package com.infolink.provider.custom.handler;

import com.infolink.provider.custom.CustomColumnSpec;
import com.infolink.provider.custom.CustomOperationHandler;
import com.infolink.provider.custom.CustomOperationMetadata;
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
 * B18 — 대전광역시 수질측정망 입력 현황 (gnlwtqltinfo_inputsittn)
 *
 * 레거시: opn.gnlwtqltinfo_inputsittn
 * - source: TM_GD120001 (관정) + TM_GD110301 (수질검사개요) LEFT JOIN TM_GD110302 (수질검사결과)
 * - 필터: UGWTR_EXMN_CD='215', EXMN_YR=현재년도, STDG_CD like '30%' (대전 한정)
 * - 시군구 추출: SUBSTR(BRNCH_NM,1,2) + DECODE(끝글자 != '구', '구', '')
 * - UNION ALL: 총계 1행 + 시군구별 N행 → 동구/중구/서구/유성구/대덕구 5행
 * - 응답 7컬럼: SIDO, SIGUNGU, YEAR, ODR, TOTAL, COMPLT, NCOMPLT
 * - ORDER BY: ODR DESC, CASE SIGUNGU (총계 → 동구 → 중구 → 서구 → 유성구 → 대덕구)
 *
 * v3 컬럼 매핑:
 *   TM_GD10001 → TM_GD120001 (GENNUM→GWEL_NO, SPOT_NM→BRNCH_NM, BRTC_NM→CTPV_NM, LEGALDONG_CODE→STDG_CD, JOSACODE→UGWTR_EXMN_CD)
 *   TM_GD30301 → TM_GD110301 (QLTWTR_INSPCT_SN→WQ_INSP_SN, INVSTG_YEAR→EXMN_YR, ODR→CYCL)
 *   TM_GD30302 → TM_GD110302 (WLTTS_ID_CODE→WQ_INSP_ARTCL_CD)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WqInputStatusDjHandler implements CustomOperationHandler {

    private static final String DATASOURCE_ID = "internal";
    private static final String OPERATION_ID  = "gnlwtqltinfo_inputsittn/gnlwtqltinfo_inputsittn";

    /**
     * 공통 inner 서브쿼리 — UNION 양쪽이 동일하게 사용 (대전 1차 검사 + 결과 LEFT JOIN)
     */
    private static final String INNER_SUBQUERY =
            "  SELECT BRNCH_NM, CTPV_NM, EXMN_YR, CYCL, " +
            "         CASE WHEN TEST IS NOT NULL THEN 1 ELSE NULL END AS yn " +
            "  FROM ( " +
            "    SELECT DISTINCT * FROM ( " +
            "      SELECT A.GWEL_NO, A.BRNCH_NM, A.GRNDS_GWEL_NO, A.CTPV_NM, " +
            "             SUBSTR(A.STDG_CD, 0, 2) STDG_CD, " +
            "             B.WQ_INSP_SN, B.EXMN_YR, B.CYCL, B.CFMTN_YMD " +
            "      FROM   TM_GD120001 A JOIN TM_GD110301 B ON A.GWEL_NO = B.GWEL_NO " +
            "      WHERE  A.UGWTR_EXMN_CD = '215' " +
            "      AND    B.EXMN_YR = TO_CHAR(SYSDATE, 'YYYY') " +
            "      AND    A.STDG_CD LIKE '30%' " +
            "    ) TB1 LEFT JOIN ( " +
            "      SELECT B.WQ_INSP_SN AS TEST " +
            "      FROM   TM_GD110301 A, TM_GD110302 B " +
            "      WHERE  A.WQ_INSP_SN = B.WQ_INSP_SN " +
            "    ) TB2 ON TB1.WQ_INSP_SN = TB2.TEST " +
            "  ) ";

    private static final String SQL =
            "SELECT * FROM ( " +
            "  SELECT CTPV_NM AS SIDO, '총계' AS SIGUNGU, EXMN_YR AS YEAR, CYCL AS ODR, " +
            "         COUNT(*) AS TOTAL, COUNT(yn) AS COMPLT, COUNT(*) - COUNT(yn) AS NCOMPLT " +
            "  FROM ( " + INNER_SUBQUERY + " ) " +
            "  GROUP BY CTPV_NM, EXMN_YR, CYCL " +
            "  UNION ALL " +
            "  SELECT '대전광역시' AS SIDO, " +
            "         SUBSTR(BRNCH_NM, 1, 2) || DECODE(SUBSTR(SUBSTR(BRNCH_NM,1,2), -1), '구', '', '구') AS SIGUNGU, " +
            "         EXMN_YR AS YEAR, CYCL AS ODR, " +
            "         COUNT(*) AS TOTAL, COUNT(yn) AS COMPLT, COUNT(*) - COUNT(yn) AS NCOMPLT " +
            "  FROM ( " + INNER_SUBQUERY + " ) " +
            "  GROUP BY SUBSTR(BRNCH_NM, 1, 2), EXMN_YR, CYCL " +
            ") " +
            "ORDER BY ODR DESC, " +
            "  CASE SIGUNGU " +
            "    WHEN '총계' THEN 1 " +
            "    WHEN '동구' THEN 2 " +
            "    WHEN '중구' THEN 3 " +
            "    WHEN '서구' THEN 4 " +
            "    WHEN '유성구' THEN 5 " +
            "    WHEN '대덕구' THEN 6 " +
            "  END";

    private final ProviderDataSourceService dataSourceService;

    @Override
    public CustomOperationMetadata getMetadata() {
        return CustomOperationMetadata.builder()
                .operationId(OPERATION_ID)
                .operationName("B18 대전 수질측정망 입력 현황")
                .description(
                        "관련 테이블: TM_GD120001, TM_GD110301, TM_GD110302 (대전광역시 한정)\n" +
                        "변환: UNION ALL (총계 + 시군구별) + 3중 서브 + LEFT JOIN — 현재년도 1차 검사 입력 진척도"
                )
                .datasourceId(DATASOURCE_ID)
                .tableName("TM_GD120001")
                .pageSize(10)
                .maxPageSize(50)
                .column(CustomColumnSpec.builder().columnName("sido").aliasName("sido").displayOrder(1).build())
                .column(CustomColumnSpec.builder().columnName("sigungu").aliasName("sigungu").displayOrder(2).build())
                .column(CustomColumnSpec.builder().columnName("year").aliasName("year").displayOrder(3).build())
                .column(CustomColumnSpec.builder().columnName("odr").aliasName("odr").displayOrder(4).build())
                .column(CustomColumnSpec.builder().columnName("total").aliasName("total").displayOrder(5).build())
                .column(CustomColumnSpec.builder().columnName("complt").aliasName("complt").displayOrder(6).build())
                .column(CustomColumnSpec.builder().columnName("ncomplt").aliasName("ncomplt").displayOrder(7).build())
                .build();
    }

    @Override
    public DynamicQueryResult handle(Map<String, String> params, int page, int pageSize) {
        long start = System.currentTimeMillis();
        JdbcTemplate jdbc = dataSourceService.getJdbcTemplate(DATASOURCE_ID);

        List<Map<String, Object>> rows = jdbc.query(SQL, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sido",    rs.getString("SIDO"));
            row.put("sigungu", rs.getString("SIGUNGU"));
            row.put("year",    rs.getString("YEAR"));
            row.put("odr",     rs.getLong("ODR"));
            row.put("total",   rs.getLong("TOTAL"));
            row.put("complt",  rs.getLong("COMPLT"));
            row.put("ncomplt", rs.getLong("NCOMPLT"));
            return row;
        });

        long duration = System.currentTimeMillis() - start;
        int count = rows.size();
        log.debug("[WqInputStatusDjHandler] {} rows ({}ms)", count, duration);

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
