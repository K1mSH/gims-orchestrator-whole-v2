package com.infolink.collector.service;

import com.infolink.collector.domain.ApiEndpoint;
import com.infolink.collector.domain.ApiEndpointRepository;
import com.infolink.collector.domain.ApiParam;
import com.infolink.collector.dto.TestCallDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

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
}
