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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * B4 — 인허가관정 상세정보 ({@code opnService/getWellInfo}).
 *
 * <p>레거시 v3 SQL: {@code sql_opn.xml:13~39 opn.info_permwell}
 * <ul>
 *   <li>source: RGETNPMMS01 + TC_GD000100 (5/6 표준화 RENAME) LEFT OUTER JOIN</li>
 *   <li>함수: FN_GD_GET_GUBUN, FN_GD_GET_CMMTNDCODE (5/4 사전 배치)</li>
 *   <li>응답 13컬럼 (v3 의 PERM_NT_FORM_CODE 중복 제거 — 외부 응답 동등)</li>
 *   <li>파라미터: rel_trans_cgg_code (필수) + perm_nt_no (옵션)</li>
 * </ul>
 *
 * <p>SQL 컬럼 매핑:
 * <ul>
 *   <li>RGETNPMMS01 (alias A) — v3 컬럼명 그대로 (표준화 자료에 매핑 없음, 레거시 보존)</li>
 *   <li>TC_GD000100 (alias B) — 표준화 후 컬럼: BRTC_NM→CTPV_NM, SIGUN_NM→SGG_NM, LEGALDONG_CODE→STDG_CD</li>
 *   <li>응답 alias = camelCase (다른 16 핸들러 통일 패턴)</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WellInfoHandler implements CustomOperationHandler {

    private static final String DATASOURCE_ID = "internal";
    private static final String OPERATION_ID  = "opnService/getWellInfo";

    private static final String BASE_SELECT =
            "SELECT " +
            "  FN_GD_GET_GUBUN(A.PERM_NT_FORM_CODE, 1) AS PERM_NT_FORM_CODE, " +
            "  B.CTPV_NM || ' ' || B.SGG_NM || ' ' || B.EMD_NM || ' ' || DECODE(B.LI_NM, NULL, '', B.LI_NM || ' ') AS ADDR, " +
            "  NVL(FN_GD_GET_CMMTNDCODE('NGW_0003', '0' || A.UWATER_SRV_CODE), ' ') AS UWATER_SRV_CODE, " +
            "  NVL(FN_GD_GET_CMMTNDCODE('NGW_0013', A.UWATER_DTL_SRV_CODE), ' ') AS UWATER_DTL_SRV_CODE, " +
            "  A.UWATER_POTA_YN AS UWATER_POTA_YN, " +
            "  DECODE(A.DIG_DPH, NULL, ' ', A.DIG_DPH) AS DIG_DPH, " +
            "  DECODE(A.DIG_DIAM, NULL, ' ', A.DIG_DIAM) AS DIG_DIAM, " +
            "  DECODE(A.ESB_DPH, NULL, ' ', A.ESB_DPH) AS ESB_DPH, " +
            "  DECODE(A.ND_QT, NULL, ' ', A.ND_QT) AS ND_QT, " +
            "  DECODE(A.FRW_PLN_QUA, NULL, ' ', A.FRW_PLN_QUA) AS FRW_PLN_QUA, " +
            "  DECODE(A.RWT_CAP, NULL, ' ', A.RWT_CAP) AS RWT_CAP, " +
            "  NVL(A.DYN_EQN_HRP, 0) AS DYN_EQN_HRP, " +
            "  DECODE(A.PIPE_DIAM, NULL, ' ', A.PIPE_DIAM) AS PIPE_DIAM " +
            "FROM RGETNPMMS01 A " +
            "LEFT OUTER JOIN TC_GD000100 B ON B.STDG_CD = A.DVOP_LOC_REGN_CODE " +
            "WHERE A.REL_TRANS_CGG_CODE = ?";

    private static final String COUNT_BASE =
            "SELECT COUNT(*) FROM RGETNPMMS01 A WHERE A.REL_TRANS_CGG_CODE = ?";

    private final ProviderDataSourceService dataSourceService;

    @Override
    public CustomOperationMetadata getMetadata() {
        return CustomOperationMetadata.builder()
                .operationId(OPERATION_ID)
                .operationName("B4 인허가관정 상세")
                .description(
                        "관련 테이블: RGETNPMMS01 + TC_GD000100 LEFT JOIN\n" +
                        "함수: FN_GD_GET_GUBUN, FN_GD_GET_CMMTNDCODE (5/4 배치)\n" +
                        "파라미터: rel_trans_cgg_code (필수), perm_nt_no (옵션)"
                )
                .datasourceId(DATASOURCE_ID)
                .tableName("RGETNPMMS01")
                .pageSize(20)
                .maxPageSize(100)
                // 13 응답 컬럼 — camelCase (v3 호환 정책 + 다른 16 핸들러 통일)
                .column(CustomColumnSpec.builder().columnName("permNtFormCode").aliasName("permNtFormCode").displayOrder(1).build())
                .column(CustomColumnSpec.builder().columnName("addr").aliasName("addr").displayOrder(2).build())
                .column(CustomColumnSpec.builder().columnName("uwaterSrvCode").aliasName("uwaterSrvCode").displayOrder(3).build())
                .column(CustomColumnSpec.builder().columnName("uwaterDtlSrvCode").aliasName("uwaterDtlSrvCode").displayOrder(4).build())
                .column(CustomColumnSpec.builder().columnName("uwaterPotaYn").aliasName("uwaterPotaYn").displayOrder(5).build())
                .column(CustomColumnSpec.builder().columnName("digDph").aliasName("digDph").displayOrder(6).build())
                .column(CustomColumnSpec.builder().columnName("digDiam").aliasName("digDiam").displayOrder(7).build())
                .column(CustomColumnSpec.builder().columnName("esbDph").aliasName("esbDph").displayOrder(8).build())
                .column(CustomColumnSpec.builder().columnName("ndQt").aliasName("ndQt").displayOrder(9).build())
                .column(CustomColumnSpec.builder().columnName("frwPlnQua").aliasName("frwPlnQua").displayOrder(10).build())
                .column(CustomColumnSpec.builder().columnName("rwtCap").aliasName("rwtCap").displayOrder(11).build())
                .column(CustomColumnSpec.builder().columnName("dynEqnHrp").aliasName("dynEqnHrp").displayOrder(12).build())
                .column(CustomColumnSpec.builder().columnName("pipeDiam").aliasName("pipeDiam").displayOrder(13).build())
                // 파라미터 2종
                .param(CustomParamSpec.builder()
                        .paramName("rel_trans_cgg_code").columnName("REL_TRANS_CGG_CODE").operator("EQ")
                        .required(true).dataType("STRING").build())
                .param(CustomParamSpec.builder()
                        .paramName("perm_nt_no").columnName("PERM_NT_NO").operator("EQ")
                        .required(false).dataType("STRING").build())
                .build();
    }

    @Override
    public DynamicQueryResult handle(Map<String, String> params, int page, int pageSize) {
        long start = System.currentTimeMillis();

        String relTransCggCode = params.get("rel_trans_cgg_code");
        if (relTransCggCode == null || relTransCggCode.isBlank()) {
            throw new IllegalArgumentException("필수 파라미터 누락: rel_trans_cgg_code");
        }
        String permNtNo = params.get("perm_nt_no");
        boolean hasPermNtNo = permNtNo != null && !permNtNo.isBlank();

        // 동적 SQL — perm_nt_no 옵션이면 AND 추가
        StringBuilder sql = new StringBuilder(BASE_SELECT);
        StringBuilder countSql = new StringBuilder(COUNT_BASE);
        List<Object> args = new ArrayList<>();
        args.add(relTransCggCode);
        if (hasPermNtNo) {
            sql.append(" AND A.PERM_NT_NO = ?");
            countSql.append(" AND A.PERM_NT_NO = ?");
            args.add(permNtNo);
        }
        // 페이징
        sql.append(" OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
        int offset = Math.max(0, (page - 1) * pageSize);
        Object[] dataArgs = new Object[args.size() + 2];
        for (int i = 0; i < args.size(); i++) dataArgs[i] = args.get(i);
        dataArgs[args.size()] = offset;
        dataArgs[args.size() + 1] = pageSize;

        JdbcTemplate jdbc = dataSourceService.getJdbcTemplate(DATASOURCE_ID);

        long totalCount = jdbc.queryForObject(countSql.toString(), Long.class, args.toArray());

        List<Map<String, Object>> rows = jdbc.query(sql.toString(), dataArgs, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("permNtFormCode",   rs.getString("PERM_NT_FORM_CODE"));
            row.put("addr",             rs.getString("ADDR"));
            row.put("uwaterSrvCode",    rs.getString("UWATER_SRV_CODE"));
            row.put("uwaterDtlSrvCode", rs.getString("UWATER_DTL_SRV_CODE"));
            row.put("uwaterPotaYn",     rs.getString("UWATER_POTA_YN"));
            row.put("digDph",           rs.getString("DIG_DPH"));
            row.put("digDiam",          rs.getString("DIG_DIAM"));
            row.put("esbDph",           rs.getString("ESB_DPH"));
            row.put("ndQt",             rs.getString("ND_QT"));
            row.put("frwPlnQua",        rs.getString("FRW_PLN_QUA"));
            row.put("rwtCap",           rs.getString("RWT_CAP"));
            row.put("dynEqnHrp",        rs.getObject("DYN_EQN_HRP"));
            row.put("pipeDiam",         rs.getString("PIPE_DIAM"));
            return row;
        });

        long duration = System.currentTimeMillis() - start;
        int totalPages = pageSize > 0 ? (int) Math.ceil((double) totalCount / pageSize) : 0;

        log.debug("[WellInfoHandler] {} rows (page={}/{}, total={}, perm_nt_no={}) — {}ms",
                rows.size(), page, totalPages, totalCount, hasPermNtNo ? permNtNo : "(none)", duration);

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
