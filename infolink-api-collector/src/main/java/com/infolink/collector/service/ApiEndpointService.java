package com.infolink.collector.service;

import com.infolink.collector.entity.*;
import com.infolink.collector.repository.*;
import com.infolink.collector.dto.ApiEndpointDto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ApiEndpointService {

    private final ApiEndpointRepository endpointRepository;
    private final ApiParamRepository paramRepository;
    private final ApiFieldMappingRepository mappingRepository;
    private final ApiScheduleRepository scheduleRepository;
    private final ApiExecutionHistoryRepository historyRepository;

    @Transactional(readOnly = true)
    public List<ListResponse> getList() {
        return endpointRepository.findAll().stream()
                .map(ListResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DetailResponse getDetail(Long id) {
        ApiEndpoint endpoint = endpointRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("ApiEndpoint not found: " + id));
        return DetailResponse.from(endpoint);
    }

    @Transactional
    public DetailResponse create(CreateRequest request) {
        ApiEndpoint endpoint = ApiEndpoint.builder()
                .apiName(request.getApiName())
                .url(request.getUrl() != null ? request.getUrl() : "")
                .httpMethod(request.getHttpMethod() != null ? request.getHttpMethod() : "GET")
                .contentType(request.getContentType())
                .headers(request.getHeaders())
                .authType(request.getAuthType() != null ? request.getAuthType() : ApiEndpoint.AuthType.NONE)
                .authConfig(request.getAuthConfig())
                .description(request.getDescription())
                .zone(request.getZone() != null ? request.getZone() : ApiEndpoint.Zone.DMZ)
                .dataRootPath(request.getDataRootPath())
                .targetDatasourceId(request.getTargetDatasourceId())
                .targetTableName(request.getTargetTableName())
                .upsertEnabled(request.getUpsertEnabled() != null ? request.getUpsertEnabled() : false)
                .executorType(request.getExecutorType())
                .build();

        return DetailResponse.from(endpointRepository.save(endpoint));
    }

    @Transactional
    public DetailResponse update(Long id, UpdateRequest request) {
        ApiEndpoint endpoint = endpointRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("ApiEndpoint not found: " + id));

        endpoint.setApiName(request.getApiName());
        endpoint.setUrl(request.getUrl());
        endpoint.setHttpMethod(request.getHttpMethod());
        endpoint.setContentType(request.getContentType());
        endpoint.setHeaders(request.getHeaders());
        endpoint.setAuthType(request.getAuthType());
        endpoint.setAuthConfig(request.getAuthConfig());
        endpoint.setDataRootPath(request.getDataRootPath());
        endpoint.setTargetDatasourceId(request.getTargetDatasourceId());
        endpoint.setTargetTableName(request.getTargetTableName());
        if (request.getUpsertEnabled() != null) endpoint.setUpsertEnabled(request.getUpsertEnabled());
        endpoint.setDescription(request.getDescription());
        if (request.getIsActive() != null) endpoint.setIsActive(request.getIsActive());
        if (request.getExecutorType() != null) endpoint.setExecutorType(request.getExecutorType());

        return DetailResponse.from(endpointRepository.save(endpoint));
    }

    @Transactional
    public void delete(Long id) {
        if (!endpointRepository.existsById(id)) {
            throw new IllegalArgumentException("ApiEndpoint not found: " + id);
        }
        historyRepository.deleteByApiEndpointId(id);
        scheduleRepository.deleteByApiEndpointId(id);
        endpointRepository.deleteById(id);
    }

    // --- Params 일괄 저장 ---

    @Transactional
    public List<ParamResponse> saveParams(Long endpointId, List<ParamRequest> requests) {
        ApiEndpoint endpoint = endpointRepository.findById(endpointId)
                .orElseThrow(() -> new IllegalArgumentException("ApiEndpoint not found: " + endpointId));

        // 기존 삭제 후 새로 저장
        endpoint.getParams().clear();

        for (int i = 0; i < requests.size(); i++) {
            ParamRequest req = requests.get(i);
            ApiParam param = ApiParam.builder()
                    .apiEndpoint(endpoint)
                    .paramName(req.getParamName())
                    .paramType(req.getParamType())
                    .valueType(req.getValueType())
                    .staticValue(req.getStaticValue())
                    .isApiKeyRef(Boolean.TRUE.equals(req.getIsApiKeyRef()))
                    .dynamicType(req.getDynamicType())
                    .dynamicFormat(req.getDynamicFormat())
                    .dynamicOffset(req.getDynamicOffset())
                    .description(req.getDescription())
                    .displayOrder(req.getDisplayOrder() != null ? req.getDisplayOrder() : i)
                    .build();
            endpoint.getParams().add(param);
        }

        endpointRepository.save(endpoint);
        return endpoint.getParams().stream().map(ParamResponse::from).collect(Collectors.toList());
    }

    // --- FieldMappings 일괄 저장 ---

    @Transactional
    public List<FieldMappingResponse> saveMappings(Long endpointId, List<FieldMappingRequest> requests) {
        ApiEndpoint endpoint = endpointRepository.findById(endpointId)
                .orElseThrow(() -> new IllegalArgumentException("ApiEndpoint not found: " + endpointId));

        endpoint.getFieldMappings().clear();

        for (int i = 0; i < requests.size(); i++) {
            FieldMappingRequest req = requests.get(i);
            ApiFieldMapping mapping = ApiFieldMapping.builder()
                    .apiEndpoint(endpoint)
                    .sourceFieldPath(req.getSourceFieldPath())
                    .targetColumnName(req.getTargetColumnName())
                    .isConflictKey(req.getIsConflictKey() != null ? req.getIsConflictKey() : false)
                    .transformType(req.getTransformType() != null ? req.getTransformType() : ApiFieldMapping.TransformType.NONE)
                    .transformConfig(req.getTransformConfig())
                    .displayOrder(req.getDisplayOrder() != null ? req.getDisplayOrder() : i)
                    .isDerived(req.getIsDerived() != null ? req.getIsDerived() : false)
                    .extractPattern(req.getExtractPattern())
                    .extractGroup(req.getExtractGroup())
                    .lookupParam(req.getLookupParam())
                    .lookupKeyField(req.getLookupKeyField())
                    .lookupValueField(req.getLookupValueField())
                    .lookupDataRootPath(req.getLookupDataRootPath())
                    .lookupMatchType(req.getLookupMatchType() != null ? req.getLookupMatchType() : "EXACT")
                    .defaultValue(req.getDefaultValue())
                    .build();
            endpoint.getFieldMappings().add(mapping);
        }

        endpointRepository.save(endpoint);
        return endpoint.getFieldMappings().stream().map(FieldMappingResponse::from).collect(Collectors.toList());
    }
}
