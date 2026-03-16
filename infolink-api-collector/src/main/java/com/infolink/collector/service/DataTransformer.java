package com.infolink.collector.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infolink.collector.domain.ApiFieldMapping;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 범용 값 변환기
 * - NONE: 그대로
 * - DATE_FORMAT: 날짜 포맷 변환 (from → to)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataTransformer {

    private final ObjectMapper objectMapper;

    public Object transform(Object value, ApiFieldMapping.TransformType type, String configJson) {
        if (value == null || type == ApiFieldMapping.TransformType.NONE) {
            return value;
        }

        try {
            switch (type) {
                case DATE_FORMAT:
                    return transformDateFormat(value.toString(), configJson);
                case NUMBER:
                    return transformNumber(value.toString());
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
}
