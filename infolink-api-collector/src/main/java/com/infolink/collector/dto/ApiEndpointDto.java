package com.infolink.collector.dto;

import com.infolink.collector.entity.*;
import lombok.*;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

public class ApiEndpointDto {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreateRequest {
        @NotBlank private String apiName;
        private String url;
        private String httpMethod;
        private String contentType;
        private String headers;
        private ApiEndpoint.AuthType authType;
        private String authConfig;
        private String description;
        private ApiEndpoint.Zone zone;
        // 등록 시 한번에 설정
        private String dataRootPath;
        private String targetDatasourceId;
        private String targetTableName;
        private Boolean upsertEnabled;
        private String executorType;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UpdateRequest {
        @NotBlank private String apiName;
        @NotBlank private String url;
        @NotBlank private String httpMethod;
        private String contentType;
        private String headers;
        @NotNull private ApiEndpoint.AuthType authType;
        private String authConfig;
        private String dataRootPath;
        private String targetDatasourceId;
        private String targetTableName;
        private Boolean upsertEnabled;
        private String description;
        private Boolean isActive;
        private String executorType;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ListResponse {
        private Long id;
        private String apiName;
        private String url;
        private String httpMethod;
        private ApiEndpoint.AuthType authType;
        private String targetTableName;
        private Boolean isActive;
        private ApiEndpoint.Zone zone;
        private String executorType;
        private boolean hasMappings;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static ListResponse from(ApiEndpoint e) {
            return ListResponse.builder()
                    .id(e.getId())
                    .apiName(e.getApiName())
                    .url(e.getUrl())
                    .httpMethod(e.getHttpMethod())
                    .authType(e.getAuthType())
                    .targetTableName(e.getTargetTableName())
                    .isActive(e.getIsActive())
                    .zone(e.getZone())
                    .executorType(e.getExecutorType())
                    .hasMappings(e.getFieldMappings() != null && !e.getFieldMappings().isEmpty())
                    .createdAt(e.getCreatedAt())
                    .updatedAt(e.getUpdatedAt())
                    .build();
        }
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DetailResponse {
        private Long id;
        private String apiName;
        private String url;
        private String httpMethod;
        private String contentType;
        private String headers;
        private ApiEndpoint.AuthType authType;
        private String authConfig;
        private String dataRootPath;
        private String targetDatasourceId;
        private String targetTableName;
        private Boolean upsertEnabled;
        private String description;
        private Boolean isActive;
        private ApiEndpoint.Zone zone;
        private String executorType;
        private List<ParamResponse> params;
        private List<FieldMappingResponse> fieldMappings;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public static DetailResponse from(ApiEndpoint e) {
            return DetailResponse.builder()
                    .id(e.getId())
                    .apiName(e.getApiName())
                    .url(e.getUrl())
                    .httpMethod(e.getHttpMethod())
                    .contentType(e.getContentType())
                    .headers(e.getHeaders())
                    .authType(e.getAuthType())
                    .authConfig(e.getAuthConfig())
                    .dataRootPath(e.getDataRootPath())
                    .targetDatasourceId(e.getTargetDatasourceId())
                    .targetTableName(e.getTargetTableName())
                    .upsertEnabled(e.getUpsertEnabled())
                    .description(e.getDescription())
                    .isActive(e.getIsActive())
                    .zone(e.getZone())
                    .executorType(e.getExecutorType())
                    .params(e.getParams().stream().map(ParamResponse::from).collect(Collectors.toList()))
                    .fieldMappings(e.getFieldMappings().stream().map(FieldMappingResponse::from).collect(Collectors.toList()))
                    .createdAt(e.getCreatedAt())
                    .updatedAt(e.getUpdatedAt())
                    .build();
        }
    }

    // --- Param ---

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ParamRequest {
        @NotBlank private String paramName;
        @NotNull private ApiParam.ParamType paramType;
        @NotNull private ApiParam.ValueType valueType;
        private String staticValue;
        private Boolean isApiKeyRef;
        private ApiParam.DynamicType dynamicType;
        private String dynamicFormat;
        private Integer dynamicOffset;
        private String description;
        private Integer displayOrder;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ParamResponse {
        private Long id;
        private String paramName;
        private ApiParam.ParamType paramType;
        private ApiParam.ValueType valueType;
        private String staticValue;
        private Boolean isApiKeyRef;
        private ApiParam.DynamicType dynamicType;
        private String dynamicFormat;
        private Integer dynamicOffset;
        private String description;
        private Integer displayOrder;

        public static ParamResponse from(ApiParam p) {
            return ParamResponse.builder()
                    .id(p.getId())
                    .paramName(p.getParamName())
                    .paramType(p.getParamType())
                    .valueType(p.getValueType())
                    .staticValue(p.getStaticValue())
                    .isApiKeyRef(p.getIsApiKeyRef())
                    .dynamicType(p.getDynamicType())
                    .dynamicFormat(p.getDynamicFormat())
                    .dynamicOffset(p.getDynamicOffset())
                    .description(p.getDescription())
                    .displayOrder(p.getDisplayOrder())
                    .build();
        }
    }

    // --- FieldMapping ---

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FieldMappingRequest {
        @NotBlank private String sourceFieldPath;
        @NotBlank private String targetColumnName;
        private Boolean isConflictKey;
        private ApiFieldMapping.TransformType transformType;
        private String transformConfig;
        private Integer displayOrder;
        // 파생 컬럼
        private Boolean isDerived;
        // LOOKUP 전용
        private String extractPattern;
        private Integer extractGroup;
        private String lookupParam;
        private String lookupKeyField;
        private String lookupValueField;
        private String lookupDataRootPath;
        private String lookupMatchType;
        private String defaultValue;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FieldMappingResponse {
        private Long id;
        private String sourceFieldPath;
        private String targetColumnName;
        private Boolean isConflictKey;
        private ApiFieldMapping.TransformType transformType;
        private String transformConfig;
        private Integer displayOrder;
        // 파생 컬럼
        private Boolean isDerived;
        // LOOKUP 전용
        private String extractPattern;
        private Integer extractGroup;
        private String lookupParam;
        private String lookupKeyField;
        private String lookupValueField;
        private String lookupDataRootPath;
        private String lookupMatchType;
        private String defaultValue;

        public static FieldMappingResponse from(ApiFieldMapping m) {
            return FieldMappingResponse.builder()
                    .id(m.getId())
                    .sourceFieldPath(m.getSourceFieldPath())
                    .targetColumnName(m.getTargetColumnName())
                    .isConflictKey(m.getIsConflictKey())
                    .transformType(m.getTransformType())
                    .transformConfig(m.getTransformConfig())
                    .displayOrder(m.getDisplayOrder())
                    .isDerived(m.getIsDerived())
                    .extractPattern(m.getExtractPattern())
                    .extractGroup(m.getExtractGroup())
                    .lookupParam(m.getLookupParam())
                    .lookupKeyField(m.getLookupKeyField())
                    .lookupValueField(m.getLookupValueField())
                    .lookupDataRootPath(m.getLookupDataRootPath())
                    .lookupMatchType(m.getLookupMatchType())
                    .defaultValue(m.getDefaultValue())
                    .build();
        }
    }
}
