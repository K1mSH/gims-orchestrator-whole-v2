package com.infolink.collector.service;

import com.infolink.collector.entity.ApiParam;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 동적 파라미터 치환기
 * - STATIC: 고정값 그대로
 * - DYNAMIC + TODAY: LocalDate.now() + offset → format
 * - DYNAMIC + NOW: LocalDateTime.now() + offset(hours) → format
 */
@Component
public class DynamicParamResolver {

    public String resolve(ApiParam param) {
        return resolve(param, null);
    }

    /**
     * @param overrideValue 테스트 호출 시 오버라이드 값 (null이면 정상 치환)
     */
    public String resolve(ApiParam param, String overrideValue) {
        if (overrideValue != null) {
            return overrideValue;
        }

        if (param.getValueType() == ApiParam.ValueType.STATIC) {
            return param.getStaticValue();
        }

        // DYNAMIC
        String format = param.getDynamicFormat();
        int offset = param.getDynamicOffset() != null ? param.getDynamicOffset() : 0;

        if (param.getDynamicType() == ApiParam.DynamicType.TODAY) {
            LocalDate date = LocalDate.now().plusDays(offset);
            return date.format(DateTimeFormatter.ofPattern(format));
        } else if (param.getDynamicType() == ApiParam.DynamicType.NOW) {
            LocalDateTime dateTime = LocalDateTime.now().plusHours(offset);
            return dateTime.format(DateTimeFormatter.ofPattern(format));
        } else if (param.getDynamicType() == ApiParam.DynamicType.YEAR) {
            LocalDate date = LocalDate.now().plusYears(offset);
            return date.format(DateTimeFormatter.ofPattern(format));
        }

        // CUSTOM 등 미구현 타입
        return param.getStaticValue() != null ? param.getStaticValue() : "";
    }
}
