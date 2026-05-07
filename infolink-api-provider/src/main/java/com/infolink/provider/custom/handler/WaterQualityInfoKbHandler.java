package com.infolink.provider.custom.handler;

import com.infolink.provider.custom.CustomColumnSpec;
import com.infolink.provider.custom.CustomOperationHandler;
import com.infolink.provider.custom.CustomOperationMetadata;
import com.infolink.provider.custom.CustomParamSpec;
import com.infolink.provider.dto.DynamicQueryResult;
import com.infolink.provider.service.ProviderDataSourceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * B12-KB — 경상북도 수질측정망 정보 (waterQualityInfoKB)
 *
 * 레거시: opn.waterQualityInfoDJ 재호출 (controller 에서 brtcNm='경상북도' set)
 * - SQL/구조 = WaterQualityInfoDjHandler 와 동일
 * - 차이: brtcNm = '경상북도'
 * - 1:1 원칙: SQL 중복이지만 별도 핸들러 (controller 분기 패턴)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WaterQualityInfoKbHandler implements CustomOperationHandler {

    private static final String DATASOURCE_ID = "internal";
    private static final String OPERATION_ID  = "waterQualityInfoKB/waterQualityInfoKB";
    private static final String BRTC_NM       = "경상북도";

    private final ProviderDataSourceService dataSourceService;

    @Override
    public CustomOperationMetadata getMetadata() {
        return CustomOperationMetadata.builder()
                .operationId(OPERATION_ID)
                .operationName("B12 경북 수질측정망 정보")
                .description(
                        "관련 테이블: TM_GD120001, TM_GD110301, TM_GD110302 + helper TM_GD110310 (경상북도 한정)\n" +
                        "변환: B12-DJ 와 동일 SQL + brtcNm='경상북도' (controller 분기 패턴)"
                )
                .datasourceId(DATASOURCE_ID)
                .tableName("TM_GD110301")
                .pageSize(100)
                .maxPageSize(1000)
                .column(CustomColumnSpec.builder().columnName("invstgYear").aliasName("invstgYear").displayOrder(1).build())
                .column(CustomColumnSpec.builder().columnName("odr").aliasName("odr").displayOrder(2).build())
                .column(CustomColumnSpec.builder().columnName("spotNm").aliasName("spotNm").displayOrder(3).build())
                .column(CustomColumnSpec.builder().columnName("sptGennum").aliasName("sptGennum").displayOrder(4).build())
                .column(CustomColumnSpec.builder().columnName("address").aliasName("address").displayOrder(5).build())
                .column(CustomColumnSpec.builder().columnName("loValue").aliasName("loValue").displayOrder(6).build())
                .column(CustomColumnSpec.builder().columnName("laValue").aliasName("laValue").displayOrder(7).build())
                .column(CustomColumnSpec.builder().columnName("drnkAt").aliasName("drnkAt").displayOrder(8).build())
                .column(CustomColumnSpec.builder().columnName("ugrwtrPrposCode").aliasName("ugrwtrPrposCode").displayOrder(9).build())
                .column(CustomColumnSpec.builder().columnName("qltwtrInspctDe").aliasName("qltwtrInspctDe").displayOrder(10).build())
                .column(CustomColumnSpec.builder().columnName("registDt").aliasName("registDt").displayOrder(11).build())
                .column(CustomColumnSpec.builder().columnName("changeDt").aliasName("changeDt").displayOrder(12).build())
                .param(CustomParamSpec.builder()
                        .paramName("josacode").columnName("UGWTR_EXMN_CD").operator("EQ")
                        .required(true).dataType("STRING").build())
                .param(CustomParamSpec.builder()
                        .paramName("year").columnName("EXMN_YR").operator("EQ")
                        .required(true).dataType("STRING").build())
                .param(CustomParamSpec.builder()
                        .paramName("searchDt").columnName("FRST_REG_DT").operator("GTE")
                        .required(true).dataType("STRING").build())
                .param(CustomParamSpec.builder()
                        .paramName("currentDt").columnName("FRST_REG_DT").operator("LTE")
                        .required(false).dataType("STRING").build())
                .build();
    }

    @Override
    public DynamicQueryResult handle(Map<String, String> params, int page, int pageSize) {
        return WaterQualityInfoDjHandler.executeDj(params, BRTC_NM, dataSourceService, log);
    }
}
