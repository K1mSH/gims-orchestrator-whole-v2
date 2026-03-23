package com.infolink.collector.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LOOKUP 파생 컬럼 처리 서비스
 * 1. 공통코드 API 호출 (설정 기반 URL + 그룹코드) → 코드-값 Map 캐싱
 * 2. 정규식 키 추출
 * 3. Map에서 LOOKUP → 값 반환
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LookupService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ResponseParser responseParser;

    @Value("${lookup.common-code-url:}")
    private String commonCodeUrl;

    /** 실행 단위 캐시: lookupApiUrl+lookupParam → Map<key, value> */
    private final Map<String, Map<String, String>> lookupCache = new ConcurrentHashMap<>();

    /**
     * 실행 시작 전 캐시 초기화
     */
    public void clearCache() {
        lookupCache.clear();
    }

    /**
     * LOOKUP Map 로딩 — 공통코드 API 호출 후 캐싱
     * URL은 application.yml의 lookup.common-code-url에서 가져오고, groupCode만 파라미터로 치환
     */
    public Map<String, String> loadLookupMap(String lookupParam,
                                              String lookupDataRootPath,
                                              String lookupKeyField, String lookupValueField) {
        if (commonCodeUrl == null || commonCodeUrl.isBlank()) {
            log.error("lookup.common-code-url 설정이 없습니다.");
            return Collections.emptyMap();
        }

        // URL에 {groupCode} 치환
        String resolvedUrl = commonCodeUrl.replaceAll("\\{[^}]+\\}", lookupParam);

        String cacheKey = resolvedUrl;
        if (lookupCache.containsKey(cacheKey)) {
            return lookupCache.get(cacheKey);
        }

        try {
            log.info("LOOKUP API 호출: {}", resolvedUrl);
            ResponseEntity<String> response = restTemplate.getForEntity(resolvedUrl, String.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("LOOKUP API 호출 실패: status={}", response.getStatusCode());
                return Collections.emptyMap();
            }

            // 응답에서 배열 추출
            List<Map<String, Object>> records = responseParser.extractRecords(response.getBody(), lookupDataRootPath);

            // key-value Map 구성
            Map<String, String> lookupMap = new LinkedHashMap<>();
            for (Map<String, Object> record : records) {
                Object keyObj = record.get(lookupKeyField);
                Object valObj = record.get(lookupValueField);
                if (keyObj != null && valObj != null) {
                    lookupMap.put(keyObj.toString(), valObj.toString());
                }
            }

            log.info("LOOKUP Map 로딩 완료: {}건 (url={})", lookupMap.size(), resolvedUrl);
            lookupCache.put(cacheKey, lookupMap);
            return lookupMap;

        } catch (Exception e) {
            log.error("LOOKUP API 호출 실패: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * 정규식으로 키 추출
     */
    public String extractKey(String value, String extractPattern, Integer extractGroup) {
        if (value == null || extractPattern == null || extractPattern.isBlank()) {
            return value;
        }
        try {
            int group = (extractGroup != null) ? extractGroup : 1;
            Matcher m = Pattern.compile(extractPattern).matcher(value);
            return m.find() ? m.group(group) : value;
        } catch (Exception e) {
            log.warn("정규식 추출 실패 (pattern={}, value={}): {}", extractPattern, value, e.getMessage());
            return value;
        }
    }

    /**
     * LOOKUP 실행: 값 추출 → 키 추출 → Map 조회 → 결과 반환
     */
    public Object lookup(Object rawValue, String extractPattern, Integer extractGroup,
                         Map<String, String> lookupMap, String defaultValue) {
        if (rawValue == null) {
            return defaultValue;
        }

        String key = extractKey(rawValue.toString(), extractPattern, extractGroup);
        String result = lookupMap.get(key);

        if (result != null) {
            return result;
        }

        log.debug("LOOKUP 매칭 실패: key={} (원본={})", key, rawValue);
        return defaultValue;
    }
}
