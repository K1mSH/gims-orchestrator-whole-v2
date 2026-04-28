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
 * B17 — 대전광역시 미등록시설 집계 (unRegitsFclySmrize)
 *
 * 레거시: opn.unRegitsFclySmrize
 * - source: TM_GD023001 (이전 TM_GD00301), 대전광역시 한정
 * - UNION ALL: 1) 대전 총계 1건 + 2) 시군구별 N건
 * - 응답 10 컬럼: SIDO, SIGUNGU, TOTAL, USED, UNUSED, UNDEFINED, PERMISSION, REGISTER, RESTORE, NONE
 * - PERMISSION/REGISTER/RESTORE/NONE 은 레거시 SQL 의 모순 조건 (PROCESS_CTNT='허가' AND TRIM(PROCESS_CTNT) IS NULL) 으로 항상 0 — 레거시 그대로
 * - ORDER BY SIGUNGU: 총계 → 동구 → 중구 → 서구 → 유성구 → 대덕구
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UnregitsFclySmrizeHandler implements CustomOperationHandler {

    private static final String DATASOURCE_ID = "internal";
    private static final String OPERATION_ID = "unRegitsFclySmrize/unRegitsFclySmrize";

    private static final String SQL =
            "SELECT * FROM (" +
            "  SELECT '대전광역시' SIDO, " +
            "         '총계' SIGUNGU, " +
            "         listUsed.USED + listUnused.UNUSED + listUndefined.UNDEFINED AS TOTAL, " +
            "         listUsed.USED, " +
            "         listUnused.UNUSED, " +
            "         listUndefined.UNDEFINED, " +
            "         resultPermission.PERMISSION, " +
            "         resultRegister.REGISTER, " +
            "         resultRestore.RESTORE, " +
            "         resultNone.NONE " +
            "  FROM (SELECT COUNT(*) USED      FROM TM_GD023001 WHERE USE_STTUS_CTNT='1' AND BRTC_NM='대전광역시' AND TRIM(PROCESS_CTNT) IS NULL) listUsed, " +
            "       (SELECT COUNT(*) UNUSED    FROM TM_GD023001 WHERE USE_STTUS_CTNT='0' AND BRTC_NM='대전광역시' AND TRIM(PROCESS_CTNT) IS NULL) listUnused, " +
            "       (SELECT COUNT(*) UNDEFINED FROM TM_GD023001 WHERE USE_STTUS_CTNT='2' AND BRTC_NM='대전광역시' AND TRIM(PROCESS_CTNT) IS NULL) listUndefined, " +
            "       (SELECT COUNT(*) PERMISSION FROM TM_GD023001 WHERE PROCESS_CTNT='허가'   AND BRTC_NM='대전광역시' AND TRIM(PROCESS_CTNT) IS NULL) resultPermission, " +
            "       (SELECT COUNT(*) REGISTER   FROM TM_GD023001 WHERE PROCESS_CTNT='신고'   AND BRTC_NM='대전광역시' AND TRIM(PROCESS_CTNT) IS NULL) resultRegister, " +
            "       (SELECT COUNT(*) RESTORE    FROM TM_GD023001 WHERE PROCESS_CTNT='원상복구' AND BRTC_NM='대전광역시' AND TRIM(PROCESS_CTNT) IS NULL) resultRestore, " +
            "       (SELECT COUNT(*) NONE       FROM TM_GD023001 WHERE PROCESS_CTNT='시설없음' AND BRTC_NM='대전광역시' AND TRIM(PROCESS_CTNT) IS NULL) resultNone " +
            "  UNION ALL " +
            "  SELECT MAX(list.BRTC_NM) SIDO, " +
            "         TO_CHAR(list.SIGUN_NM) SIGUNGU, " +
            "         (SELECT COUNT(*) FROM TM_GD023001 WHERE BRTC_NM=list.BRTC_NM AND SIGUN_NM=list.SIGUN_NM AND TRIM(PROCESS_CTNT) IS NULL) AS TOTAL, " +
            "         (SELECT COUNT(*) FROM TM_GD023001 WHERE USE_STTUS_CTNT='1' AND BRTC_NM=list.BRTC_NM AND SIGUN_NM=list.SIGUN_NM AND TRIM(PROCESS_CTNT) IS NULL) AS USED, " +
            "         (SELECT COUNT(*) FROM TM_GD023001 WHERE USE_STTUS_CTNT='0' AND BRTC_NM=list.BRTC_NM AND SIGUN_NM=list.SIGUN_NM AND TRIM(PROCESS_CTNT) IS NULL) AS UNUSED, " +
            "         (SELECT COUNT(*) FROM TM_GD023001 WHERE USE_STTUS_CTNT='2' AND BRTC_NM=list.BRTC_NM AND SIGUN_NM=list.SIGUN_NM AND TRIM(PROCESS_CTNT) IS NULL) AS UNDEFINED, " +
            "         (SELECT COUNT(*) FROM TM_GD023001 WHERE PROCESS_CTNT='허가'   AND BRTC_NM=list.BRTC_NM AND SIGUN_NM=list.SIGUN_NM AND TRIM(PROCESS_CTNT) IS NULL) AS PERMISSION, " +
            "         (SELECT COUNT(*) FROM TM_GD023001 WHERE PROCESS_CTNT='신고'   AND BRTC_NM=list.BRTC_NM AND SIGUN_NM=list.SIGUN_NM AND TRIM(PROCESS_CTNT) IS NULL) AS REGISTER, " +
            "         (SELECT COUNT(*) FROM TM_GD023001 WHERE PROCESS_CTNT='원상복구' AND BRTC_NM=list.BRTC_NM AND SIGUN_NM=list.SIGUN_NM AND TRIM(PROCESS_CTNT) IS NULL) AS RESTORE, " +
            "         (SELECT COUNT(*) FROM TM_GD023001 WHERE PROCESS_CTNT='시설없음' AND BRTC_NM=list.BRTC_NM AND SIGUN_NM=list.SIGUN_NM AND TRIM(PROCESS_CTNT) IS NULL) AS NONE " +
            "  FROM TM_GD023001 list " +
            "  WHERE list.BRTC_NM='대전광역시' AND TRIM(PROCESS_CTNT) IS NULL " +
            "  GROUP BY list.BRTC_NM, list.SIGUN_NM" +
            ") " +
            "ORDER BY CASE SIGUNGU " +
            "  WHEN '총계' THEN 1 WHEN '동구' THEN 2 WHEN '중구' THEN 3 " +
            "  WHEN '서구' THEN 4 WHEN '유성구' THEN 5 WHEN '대덕구' THEN 6 END";

    private final ProviderDataSourceService dataSourceService;

    @Override
    public CustomOperationMetadata getMetadata() {
        return CustomOperationMetadata.builder()
                .operationId(OPERATION_ID)
                .operationName("B17 대전 미등록시설 집계")
                .description(
                        "관련 테이블: TM_GD023001 (대전광역시 한정)\n" +
                        "변환: UNION ALL (총계 + 시군구별) + 스칼라서브쿼리 8개"
                )
                .datasourceId(DATASOURCE_ID)
                .tableName("TM_GD023001")
                .pageSize(10)
                .maxPageSize(50)
                .column(CustomColumnSpec.builder().columnName("sido").aliasName("sido").displayOrder(1).build())
                .column(CustomColumnSpec.builder().columnName("sigungu").aliasName("sigungu").displayOrder(2).build())
                .column(CustomColumnSpec.builder().columnName("total").aliasName("total").displayOrder(3).build())
                .column(CustomColumnSpec.builder().columnName("used").aliasName("used").displayOrder(4).build())
                .column(CustomColumnSpec.builder().columnName("unused").aliasName("unused").displayOrder(5).build())
                .column(CustomColumnSpec.builder().columnName("undefined").aliasName("undefined").displayOrder(6).build())
                .column(CustomColumnSpec.builder().columnName("permission").aliasName("permission").displayOrder(7).build())
                .column(CustomColumnSpec.builder().columnName("register").aliasName("register").displayOrder(8).build())
                .column(CustomColumnSpec.builder().columnName("restore").aliasName("restore").displayOrder(9).build())
                .column(CustomColumnSpec.builder().columnName("none").aliasName("none").displayOrder(10).build())
                .build();
    }

    @Override
    public DynamicQueryResult handle(Map<String, String> params, int page, int pageSize) {
        long start = System.currentTimeMillis();
        JdbcTemplate jdbc = dataSourceService.getJdbcTemplate(DATASOURCE_ID);

        List<Map<String, Object>> rows = jdbc.query(SQL, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sido",       rs.getString("SIDO"));
            row.put("sigungu",    rs.getString("SIGUNGU"));
            row.put("total",      rs.getLong("TOTAL"));
            row.put("used",       rs.getLong("USED"));
            row.put("unused",     rs.getLong("UNUSED"));
            row.put("undefined",  rs.getLong("UNDEFINED"));
            row.put("permission", rs.getLong("PERMISSION"));
            row.put("register",   rs.getLong("REGISTER"));
            row.put("restore",    rs.getLong("RESTORE"));
            row.put("none",       rs.getLong("NONE"));
            return row;
        });

        long duration = System.currentTimeMillis() - start;
        int count = rows.size();
        log.debug("[UnregitsFclySmrizeHandler] {} rows ({}ms)", count, duration);

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
