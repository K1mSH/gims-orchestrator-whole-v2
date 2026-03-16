package com.infolink.collector.dto;

import com.infolink.collector.service.ResponseParser;
import lombok.*;

import java.util.List;
import java.util.Map;

public class TestCallDto {

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class Request {
        /** 동적 파라미터 오버라이드 (paramName → 테스트용 값) */
        private Map<String, String> paramOverrides;
    }

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class Response {
        private int httpStatusCode;
        private boolean success;
        private String errorMessage;

        /** 응답 JSON 트리 구조 */
        private ResponseParser.TreeNode responseTree;

        /** 현재 설정된 data_root_path */
        private String dataRootPath;

        /** data_root_path 기준 추출된 필드 목록 (매핑 후보) */
        private List<ResponseParser.FieldInfo> fields;

        /** 파라미터 치환 결과 미리보기 */
        private Map<String, String> resolvedParams;
    }
}
