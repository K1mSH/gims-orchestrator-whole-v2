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
 * B5 — 보조지하수관측망 상세 (groundwaterMonitoringNetworkService/getSupplementaryGroundwater)
 *
 * 레거시 v3: opn.info_general_105
 *  - 5-way LEFT JOIN: TM_GD120001(관정) + TM_GD970101(ODM결과) + TM_GD970130(관정사양)
 *                   + TM_GD970001(관측소) + TM_GD970002(관측소사양) + TM_GD980002(연계기록)
 *  - WHERE TB3.BRNCH_TYPE_MNG_TRM_NM IN ('보조지하수관측망','수동보조지하수관측망')
 *  - WHERE gennum=? 단건
 *
 * v3 → 표준화 컬럼 매핑 (standardized_detail.tsv 기반):
 *  - TM_GD10001.LEGALDONG_CODE/BRTC_NM/SIGUN_NM/LO_VALUE/LA_VALUE/AL_VALUE/WELL_STLE_CODE/GENNUM
 *    → TM_GD120001.STDG_CD/CTPV_NM/SGG_NM/LOT/LAT/ALTD_VL/GWEL_FRM_CD/GWEL_NO
 *  - TM_GD60130.SPOT_ID/INSTL_DPH_VALUE/SUPRR_DGG_CALBR/OBSR_IEM_NM/OBSR_CYCLE_CTNT/UGRWTR_DTL_PRPOS_CODE/DRNK_AT/PRMISN_DCLR_NO/CSNG_HG/GENNUM
 *    → TM_GD970130.BRNCH_ID/INSTL_DPH_VL/UPPRT_DGG_CALBR/OBSRVN_ARTCL_NM/OBSRVN_CYCL_CN/UGWTR_DTL_USG_CD/DKPP_YN/PRMSN_DCLR_NO/CSNG_HGT/GWEL_NO
 *  - TM_GD60001.OBSRVT_ID/OBSRVT_NM/INSTL_DE/REMARK_CTNT/FRST_REGIST_DT/SPOT_TY_MNG_WORD_NM/SPOT_ID
 *    → TM_GD970001.OBSVTR_ID/OBSVTR_NM/INSTL_YMD/RMRK_CN/FRST_REG_DT/BRNCH_TYPE_MNG_TRM_NM/BRNCH_ID
 *  - TM_GD60002.MNG_INSTT_NM/RDNM_ADDR/SPOT_ID → TM_GD970002.MGAGC_NM/RDNM/BRNCH_ID
 *  - TM_GD60101.TAG_CTNT/SPOT_ID → TM_GD970101.TAG_CN/BRNCH_ID
 *  - TM_GD70002.CNTC_TRGET_IP/CNTC_TRGET_PORT_CTNT/SPOT_ID → TM_GD980002.LINK_TRGT_IP/LINK_TRGT_PORT_CN/BRNCH_ID
 *
 * 응답 17 컬럼 (v3 alias 그대로): GENNUM, JIGUNAME, OBSV_CODE, INPERM_NO, ADDR, PYOGO,
 *   SOUR_GOV, INSDATE, OBSV_TYPE, WELL, CASING_HEIGHT, GULDEP, GULDIA, GIGWANMETHOD, GIGWANITEM,
 *   GROUNDUSE, UWATER_POTA_YN
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SupplementaryGroundwaterHandler implements CustomOperationHandler {

    private static final String DATASOURCE_ID = "internal";
    private static final String OPERATION_ID = "groundwaterMonitoringNetworkService/getSupplementaryGroundwater";

    private static final String SQL =
            "SELECT GWEL_NO AS GENNUM, " +
            "       OBSV_NAME AS JIGUNAME, " +
            "       OBSV_CODE, INPERM_NO, " +
            "       SIDO || ' ' || SIGUNGU || ' ' || UPMYUNDO || ' ' || NVL(RI, '') AS ADDR, " +
            "       PYOGO, " +
            "       MGR_ORG AS SOUR_GOV, " +
            "       INSDATE, OBSV_TYPE, WELL, CASING_HEIGHT, " +
            "       GULDEP, GULDIA, GIGWANMETHOD, GIGWANITEM, " +
            "       YONGDO_CD AS GROUNDUSE, " +
            "       UMYONG AS UWATER_POTA_YN " +
            "FROM (" +
            "  SELECT TB3.OBSVTR_ID AS OBSV_CODE, " +
            "         TB1.STDG_CD AS REGION_CODE, " +
            "         TB3.OBSVTR_NM AS OBSV_NAME, " +
            "         TB1.CTPV_NM AS SIDO, " +
            "         TB1.SGG_NM AS SIGUNGU, " +
            "         TB1.EMD_NM AS UPMYUNDO, " +
            "         TB1.LI_NM AS RI, " +
            "         TB1.ADDR AS BUNJI, " +
            "         TB4.RDNM AS ROADADDR, " +
            "         TB1.LAT AS X, TB1.LOT AS Y, " +
            "         TB1.ALTD_VL AS PYOGO, " +
            "         TB4.MGAGC_NM AS MGR_ORG, " +
            "         TB3.INSTL_YMD AS INSDATE, " +
            "         TB3.RMRK_CN AS REMARK, " +
            "         TB1.GWEL_FRM_CD AS WELL, " +
            "         TB2.INSTL_DPH_VL AS GULDEP, " +
            "         TB2.UPPRT_DGG_CALBR AS GULDIA, " +
            "         TB2.OBSRVN_ARTCL_NM AS GIGWANITEM, " +
            "         TB2.OBSRVN_CYCL_CN AS GIGWANMETHOD, " +
            "         TB2.UGWTR_DTL_USG_CD AS YONGDO_CD, " +
            "         TB2.DKPP_YN AS UMYONG, " +
            "         TB3.FRST_REG_DT AS REGDATE, " +
            "         TB1.GWEL_NO, " +
            "         TB2.PRMSN_DCLR_NO AS INPERM_NO, " +
            "         TB1.GWEL_FRM_CD AS OBSV_TYPE, " +
            "         TB2.CSNG_HGT AS CASING_HEIGHT, " +
            "         TB5.LINK_TRGT_IP AS SEARCHDBIP, " +
            "         TB5.LINK_TRGT_PORT_CN AS SEARCHPORT " +
            "  FROM TM_GD120001 TB1 " +
            "  LEFT OUTER JOIN (SELECT DISTINCT TAG_CN, BRNCH_ID FROM TM_GD970101) TB6 " +
            "       ON TB1.GWEL_NO = TB6.TAG_CN " +
            "  LEFT OUTER JOIN TM_GD970130 TB2 ON TB6.TAG_CN = TB2.GWEL_NO " +
            "  LEFT OUTER JOIN TM_GD970001 TB3 ON TB2.BRNCH_ID = TB3.BRNCH_ID " +
            "  LEFT OUTER JOIN TM_GD970002 TB4 ON TB2.BRNCH_ID = TB4.BRNCH_ID " +
            "  LEFT OUTER JOIN TM_GD980002 TB5 ON TB2.BRNCH_ID = TB5.BRNCH_ID " +
            "  WHERE TB3.BRNCH_TYPE_MNG_TRM_NM = '보조지하수관측망' " +
            "     OR TB3.BRNCH_TYPE_MNG_TRM_NM = '수동보조지하수관측망'" +
            ") " +
            "WHERE GWEL_NO = ?";

    private final ProviderDataSourceService dataSourceService;

    @Override
    public CustomOperationMetadata getMetadata() {
        return CustomOperationMetadata.builder()
                .operationId(OPERATION_ID)
                .operationName("B5 보조지하수관측망 상세")
                .description(
                        "관련 테이블: TM_GD120001, TM_GD970101, TM_GD970130, TM_GD970001, TM_GD970002, TM_GD980002\n" +
                        "변환: 5-way LEFT JOIN + 주소 결합 (보조지하수관측망 한정)"
                )
                .datasourceId(DATASOURCE_ID)
                .tableName("TM_GD120001")
                .pageSize(1)
                .maxPageSize(1)
                .column(CustomColumnSpec.builder().columnName("GENNUM").aliasName("GENNUM").displayOrder(1).build())
                .column(CustomColumnSpec.builder().columnName("JIGUNAME").aliasName("JIGUNAME").displayOrder(2).build())
                .column(CustomColumnSpec.builder().columnName("OBSV_CODE").aliasName("OBSV_CODE").displayOrder(3).build())
                .column(CustomColumnSpec.builder().columnName("INPERM_NO").aliasName("INPERM_NO").displayOrder(4).build())
                .column(CustomColumnSpec.builder().columnName("ADDR").aliasName("ADDR").displayOrder(5).build())
                .column(CustomColumnSpec.builder().columnName("PYOGO").aliasName("PYOGO").displayOrder(6).build())
                .column(CustomColumnSpec.builder().columnName("SOUR_GOV").aliasName("SOUR_GOV").displayOrder(7).build())
                .column(CustomColumnSpec.builder().columnName("INSDATE").aliasName("INSDATE").displayOrder(8).build())
                .column(CustomColumnSpec.builder().columnName("OBSV_TYPE").aliasName("OBSV_TYPE").displayOrder(9).build())
                .column(CustomColumnSpec.builder().columnName("WELL").aliasName("WELL").displayOrder(10).build())
                .column(CustomColumnSpec.builder().columnName("CASING_HEIGHT").aliasName("CASING_HEIGHT").displayOrder(11).build())
                .column(CustomColumnSpec.builder().columnName("GULDEP").aliasName("GULDEP").displayOrder(12).build())
                .column(CustomColumnSpec.builder().columnName("GULDIA").aliasName("GULDIA").displayOrder(13).build())
                .column(CustomColumnSpec.builder().columnName("GIGWANMETHOD").aliasName("GIGWANMETHOD").displayOrder(14).build())
                .column(CustomColumnSpec.builder().columnName("GIGWANITEM").aliasName("GIGWANITEM").displayOrder(15).build())
                .column(CustomColumnSpec.builder().columnName("GROUNDUSE").aliasName("GROUNDUSE").displayOrder(16).build())
                .column(CustomColumnSpec.builder().columnName("UWATER_POTA_YN").aliasName("UWATER_POTA_YN").displayOrder(17).build())
                .param(CustomParamSpec.builder()
                        .paramName("gennum").columnName("GWEL_NO").operator("EQ")
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

        List<Map<String, Object>> rows = jdbc.query(SQL,
                ps -> ps.setLong(1, gennum),
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("GENNUM",         rs.getObject("GENNUM"));
                    row.put("JIGUNAME",       rs.getString("JIGUNAME"));
                    row.put("OBSV_CODE",      rs.getString("OBSV_CODE"));
                    row.put("INPERM_NO",      rs.getString("INPERM_NO"));
                    row.put("ADDR",           rs.getString("ADDR"));
                    row.put("PYOGO",          rs.getObject("PYOGO"));
                    row.put("SOUR_GOV",       rs.getString("SOUR_GOV"));
                    row.put("INSDATE",        rs.getString("INSDATE"));
                    row.put("OBSV_TYPE",      rs.getString("OBSV_TYPE"));
                    row.put("WELL",           rs.getString("WELL"));
                    row.put("CASING_HEIGHT",  rs.getObject("CASING_HEIGHT"));
                    row.put("GULDEP",         rs.getObject("GULDEP"));
                    row.put("GULDIA",         rs.getObject("GULDIA"));
                    row.put("GIGWANMETHOD",   rs.getString("GIGWANMETHOD"));
                    row.put("GIGWANITEM",     rs.getString("GIGWANITEM"));
                    row.put("GROUNDUSE",      rs.getString("GROUNDUSE"));
                    row.put("UWATER_POTA_YN", rs.getString("UWATER_POTA_YN"));
                    return row;
                });

        long duration = System.currentTimeMillis() - start;
        int count = rows.size();
        log.debug("[SupplementaryGroundwaterHandler] gennum={} → {} rows ({}ms)", gennum, count, duration);

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
