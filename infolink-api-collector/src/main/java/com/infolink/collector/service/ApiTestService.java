package com.infolink.collector.service;

import com.infolink.collector.entity.ApiEndpoint;
import com.infolink.collector.repository.ApiEndpointRepository;
import com.infolink.collector.entity.ApiParam;
import com.infolink.collector.dto.TestCallDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApiTestService {

    private final ApiEndpointRepository endpointRepository;
    private final ApiCallService callService;
    private final DynamicParamResolver paramResolver;
    private final ResponseParser responseParser;

    @Transactional(readOnly = true)
    public TestCallDto.Response testCall(Long endpointId, Map<String, String> paramOverrides) {
        ApiEndpoint endpoint = endpointRepository.findById(endpointId)
                .orElseThrow(() -> new IllegalArgumentException("ApiEndpoint not found: " + endpointId));

        List<ApiParam> params = endpoint.getParams();

        // 파라미터 치환 결과 미리보기
        Map<String, String> resolvedParams = new LinkedHashMap<>();
        for (ApiParam param : params) {
            String override = paramOverrides != null ? paramOverrides.get(param.getParamName()) : null;
            resolvedParams.put(param.getParamName(), paramResolver.resolve(param, override));
        }

        // HTTP 호출
        ApiCallService.CallResult result = callService.call(endpoint, params, paramOverrides);

        if (!result.isSuccess()) {
            return TestCallDto.Response.builder()
                    .httpStatusCode(result.statusCode())
                    .success(false)
                    .errorMessage(result.error() != null ? result.error() : "HTTP " + result.statusCode())
                    .resolvedParams(resolvedParams)
                    .build();
        }

        try {
            // 응답 트리 생성
            ResponseParser.TreeNode tree = responseParser.parseToTree(result.body());

            // data_root_path가 설정되어 있으면 필드 목록도 추출
            List<ResponseParser.FieldInfo> fields = null;
            if (endpoint.getDataRootPath() != null && !endpoint.getDataRootPath().isBlank()) {
                fields = responseParser.extractFields(result.body(), endpoint.getDataRootPath());
            }

            return TestCallDto.Response.builder()
                    .httpStatusCode(result.statusCode())
                    .success(true)
                    .responseTree(tree)
                    .dataRootPath(endpoint.getDataRootPath())
                    .fields(fields)
                    .resolvedParams(resolvedParams)
                    .build();

        } catch (Exception e) {
            log.error("응답 파싱 실패: {}", e.getMessage());
            return TestCallDto.Response.builder()
                    .httpStatusCode(result.statusCode())
                    .success(false)
                    .errorMessage("응답 파싱 실패: " + e.getMessage())
                    .resolvedParams(resolvedParams)
                    .build();
        }
    }

    /**
     * 저장 없이 인라인 테스트 — 프론트에서 폼 데이터를 직접 전달
     */
    public TestCallDto.Response testCallInline(TestCallDto.InlineRequest req) {
        // 임시 ApiEndpoint 객체 조립 (DB 저장 없음)
        ApiEndpoint tempEndpoint = ApiEndpoint.builder()
                .url(req.getUrl())
                .httpMethod(req.getHttpMethod() != null ? req.getHttpMethod() : "GET")
                .contentType(req.getContentType())
                .headers(req.getHeaders())
                .authType(req.getAuthType() != null
                        ? ApiEndpoint.AuthType.valueOf(req.getAuthType())
                        : ApiEndpoint.AuthType.NONE)
                .authConfig(req.getAuthConfig())
                .build();

        // 임시 ApiParam 리스트 조립
        List<ApiParam> tempParams = new ArrayList<>();
        if (req.getParams() != null) {
            for (int i = 0; i < req.getParams().size(); i++) {
                TestCallDto.InlineParam ip = req.getParams().get(i);
                tempParams.add(ApiParam.builder()
                        .paramName(ip.getParamName())
                        .paramType(ApiParam.ParamType.valueOf(ip.getParamType() != null ? ip.getParamType() : "QUERY"))
                        .valueType(ApiParam.ValueType.valueOf(ip.getValueType() != null ? ip.getValueType() : "STATIC"))
                        .staticValue(ip.getStaticValue())
                        .isApiKeyRef(Boolean.TRUE.equals(ip.getIsApiKeyRef()))
                        .description(ip.getDescription())
                        .dynamicType(ip.getDynamicType() != null ? ApiParam.DynamicType.valueOf(ip.getDynamicType()) : null)
                        .dynamicFormat(ip.getDynamicFormat())
                        .dynamicOffset(ip.getDynamicOffset())
                        .displayOrder(i)
                        .build());
            }
        }

        // 파라미터 치환 결과
        Map<String, String> resolvedParams = new LinkedHashMap<>();
        for (ApiParam param : tempParams) {
            resolvedParams.put(param.getParamName(), paramResolver.resolve(param, null));
        }

        // HTTP 호출
        ApiCallService.CallResult result = callService.call(tempEndpoint, tempParams, null);

        if (!result.isSuccess()) {
            return TestCallDto.Response.builder()
                    .httpStatusCode(result.statusCode())
                    .success(false)
                    .errorMessage(result.error() != null ? result.error() : "HTTP " + result.statusCode())
                    .resolvedParams(resolvedParams)
                    .build();
        }

        try {
            ResponseParser.TreeNode tree = responseParser.parseToTree(result.body());
            return TestCallDto.Response.builder()
                    .httpStatusCode(result.statusCode())
                    .success(true)
                    .responseTree(tree)
                    .resolvedParams(resolvedParams)
                    .build();
        } catch (Exception e) {
            log.error("응답 파싱 실패: {}", e.getMessage());
            return TestCallDto.Response.builder()
                    .httpStatusCode(result.statusCode())
                    .success(false)
                    .errorMessage("응답 파싱 실패: " + e.getMessage())
                    .resolvedParams(resolvedParams)
                    .build();
        }
    }
}
