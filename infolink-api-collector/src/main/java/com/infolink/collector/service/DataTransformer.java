package com.infolink.collector.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infolink.collector.entity.ApiFieldMapping;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 범용 값 변환기 — 1:1 매핑의 단순 변환 처리
 * LOOKUP은 LookupService에서 별도 처리
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataTransformer {

    private final ObjectMapper objectMapper;

    public Object transform(Object value, ApiFieldMapping.TransformType type, String configJson) {
        if (type == ApiFieldMapping.TransformType.NONE) {
            return value;
        }
        if (type == ApiFieldMapping.TransformType.DEFAULT_VALUE) {
            return transformDefaultValue(value, configJson);
        }
        if (value == null) {
            return null;
        }

        try {
            switch (type) {
                case DATE_FORMAT:
                    return transformDateFormat(value.toString(), configJson);
                case NUMBER:
                    return transformNumber(value.toString());
                case SUBSTRING:
                    return transformSubstring(value.toString(), configJson);
                case TRIM:
                    return value.toString().trim();
                case REPLACE:
                    return transformReplace(value.toString(), configJson);
                default:
                    return value;
            }
        } catch (Exception e) {
            log.warn("변환 실패 (type={}, value={}): {}", type, value, e.getMessage());
            return value;
        }
    }

    private String transformDateFormat(String value, String configJson) throws Exception {
        Map<String, String> config = objectMapper.readValue(configJson, new TypeReference<>() {});
        String fromFormat = config.get("from");
        String toFormat = config.get("to");

        LocalDate date = LocalDate.parse(value, DateTimeFormatter.ofPattern(fromFormat));
        return date.format(DateTimeFormatter.ofPattern(toFormat));
    }

    private Number transformNumber(String value) {
        if (value.contains(".")) {
            return Double.parseDouble(value);
        }
        return Long.parseLong(value);
    }

    private String transformSubstring(String value, String configJson) throws Exception {
        Map<String, Object> config = objectMapper.readValue(configJson, new TypeReference<>() {});
        int start = ((Number) config.getOrDefault("start", 0)).intValue();
        int length = ((Number) config.getOrDefault("length", value.length())).intValue();
        int end = Math.min(start + length, value.length());
        return value.substring(Math.min(start, value.length()), end);
    }

    private String transformReplace(String value, String configJson) throws Exception {
        Map<String, String> config = objectMapper.readValue(configJson, new TypeReference<>() {});
        String from = config.get("from");
        String to = config.getOrDefault("to", "");
        return value.replace(from, to);
    }

    private Object transformDefaultValue(Object value, String configJson) {
        if (value != null) return value;
        try {
            Map<String, String> config = objectMapper.readValue(configJson, new TypeReference<>() {});
            return config.get("value");
        } catch (Exception e) {
            log.warn("DEFAULT_VALUE 설정 파싱 실패: {}", e.getMessage());
            return null;
        }
    }
}
