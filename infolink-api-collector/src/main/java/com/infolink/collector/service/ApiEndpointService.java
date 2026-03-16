package com.infolink.collector.service;

import com.infolink.collector.domain.*;
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
        if (endpointRepository.existsByApiCode(request.getApiCode())) {
            throw new IllegalArgumentException("apiCode 중복: " + request.getApiCode());
        }

        ApiEndpoint endpoint = ApiEndpoint.builder()
                .apiName(request.getApiName())
                .apiCode(request.getApiCode())
                .url(request.getUrl())
                .httpMethod(request.getHttpMethod())
                .contentType(request.getContentType())
                .headers(request.getHeaders())
                .authType(request.getAuthType())
                .authConfig(request.getAuthConfig())
                .description(request.getDescription())
                .zone(request.getZone())
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

        return DetailResponse.from(endpointRepository.save(endpoint));
    }

    @Transactional
    public void delete(Long id) {
        if (!endpointRepository.existsById(id)) {
            throw new IllegalArgumentException("ApiEndpoint not found: " + id);
        }
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
                    .isPk(req.getIsPk() != null ? req.getIsPk() : false)
                    .transformType(req.getTransformType() != null ? req.getTransformType() : ApiFieldMapping.TransformType.NONE)
                    .transformConfig(req.getTransformConfig())
                    .displayOrder(req.getDisplayOrder() != null ? req.getDisplayOrder() : i)
                    .build();
            endpoint.getFieldMappings().add(mapping);
        }

        endpointRepository.save(endpoint);
        return endpoint.getFieldMappings().stream().map(FieldMappingResponse::from).collect(Collectors.toList());
    }
}
