package com.infolink.agent.common.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infolink.agent.common.step.StepContext;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Source Reference 유틸리티
 *
 * 형식: "zone:dsId:tbId:pk"
 * - zone: 망 약어 (E=EXTERNAL, D=DMZ, IC=INTERNAL_COMMON, IS=INTERNAL_SERVICE)
 * - dsId: datasource DB PK (숫자)
 * - tbId: table DB PK (숫자)
 * - pk: 원본 레코드 PK 값
 *
 * 예시: "D:3:12:GPM-3050-001"
 */
@Slf4j
public class SourceRefUtils {

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String DELIMITER = ":";

    private SourceRefUtils() {
        // 유틸리티 클래스
    }

    /**
     * sourceRef 생성 (단일/복합 PK 통합)
     *
     * <p>호출쪽은 복합키 여부를 알 필요 없이 PK 구성요소를 그대로 넘기면 된다.
     * 인자가 1개면 단일 PK, 2개 이상이면 {@code |} 로 연결한 복합 PK.</p>
     *
     * @param context StepContext (zone, dsId, tableIds 정보 포함)
     * @param tableName 테이블명 (tableIds에서 tbId 조회용)
     * @param pkValues 원본 레코드 PK 값(들)
     * @return "zone:dsId:tbId:pk" (또는 "...:pk1|pk2|...") 형식 문자열, 정보 없으면 fallback, pk 없으면 null
     */
    public static String build(StepContext context, String tableName, Object... pkValues) {
        if (context == null || pkValues == null || pkValues.length == 0) {
            return null;
        }

        String pk = (pkValues.length == 1)
                ? (pkValues[0] != null ? String.valueOf(pkValues[0]) : null)
                : Arrays.stream(pkValues)
                        .map(v -> v != null ? v.toString() : "")
                        .collect(Collectors.joining("|"));
        if (pk == null) {
            return null;
        }

        String zone = context.getSourceZoneShortCode();
        Long dsId = context.getSourceDatasourceDbId();
        Long tbId = getTableId(context, tableName);

        // 필수 정보 없으면 fallback 형식 사용
        if (zone == null || dsId == null || tbId == null) {
            log.warn("SourceRef 필수 정보 누락 - zone:{}, dsId:{}, tbId:{}, fallback 사용",
                    zone, dsId, tbId);
            return buildFallback(context, pk);
        }

        return zone + DELIMITER + dsId + DELIMITER + tbId + DELIMITER + pk;
    }

    /**
     * sourceRef 를 단일 원소 JSON 배열 문자열로 바로 생성 (build + toJsonSingle).
     * <p>단건 source_refs JSON 의 단일 진입점 — 손코딩 금지.</p>
     *
     * @return ["zone:dsId:tbId:pk"] 형식, 정보 없으면 null
     */
    public static String buildJson(StepContext context, String tableName, Object... pkValues) {
        return toJsonSingle(build(context, tableName, pkValues));
    }

    /**
     * 여러 레코드의 sourceRef 리스트 생성
     *
     * @param context StepContext
     * @param tableName 테이블명
     * @param pks PK 값 리스트
     * @return sourceRef 문자열 리스트
     */
    public static List<String> buildList(StepContext context, String tableName, List<?> pks) {
        if (pks == null || pks.isEmpty()) {
            return Collections.emptyList();
        }
        return pks.stream()
                .map(pk -> build(context, tableName, pk))
                .filter(ref -> ref != null)
                .collect(Collectors.toList());
    }

    /**
     * sourceRef 리스트를 JSON 문자열로 변환
     */
    public static String toJson(List<String> refs) {
        if (refs == null || refs.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(refs);
        } catch (JsonProcessingException e) {
            log.error("Failed to convert sourceRefs to JSON", e);
            return null;
        }
    }

    /**
     * 단일 sourceRef를 JSON 배열 문자열로 변환
     */
    public static String toJsonSingle(String ref) {
        if (ref == null) {
            return null;
        }
        return toJson(Collections.singletonList(ref));
    }

    /**
     * JSON 문자열을 sourceRef 리스트로 파싱
     */
    public static List<String> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            log.error("Failed to parse sourceRefs from JSON: {}", json, e);
            return Collections.emptyList();
        }
    }

    /**
     * sourceRef에서 zone 추출
     */
    public static String parseZone(String ref) {
        return parsePart(ref, 0);
    }

    /**
     * sourceRef에서 dsId 추출
     */
    public static Long parseDsId(String ref) {
        String part = parsePart(ref, 1);
        return part != null ? Long.parseLong(part) : null;
    }

    /**
     * sourceRef에서 tbId 추출
     */
    public static Long parseTbId(String ref) {
        String part = parsePart(ref, 2);
        return part != null ? Long.parseLong(part) : null;
    }

    /**
     * sourceRef에서 pk 추출
     */
    public static String parsePk(String ref) {
        return parsePart(ref, 3);
    }

    private static String parsePart(String ref, int index) {
        if (ref == null || ref.isBlank()) {
            return null;
        }
        String[] parts = ref.split(DELIMITER, 4); // 최대 4개로 분리 (pk에 : 포함될 수 있음)
        if (parts.length > index) {
            return parts[index];
        }
        return null;
    }

    private static Long getTableId(StepContext context, String tableName) {
        Map<String, Long> tableIds = context.getSourceTableIds();
        if (tableIds == null || tableName == null) {
            return null;
        }
        // 정확히 일치하거나, 대소문자 무시 검색
        Long id = tableIds.get(tableName);
        if (id == null) {
            id = tableIds.get(tableName.toLowerCase());
        }
        if (id == null) {
            id = tableIds.get(tableName.toUpperCase());
        }
        return id;
    }

    /**
     * 필수 정보 없을 때 fallback 형식
     * "zone:dsId:0:pk" (tbId를 0으로 대체)
     *
     * 예시: "D:3:0:GPM-3050-001"
     * - tbId를 못 찾은 경우 0으로 표시 (추적 시 테이블 미식별 상태)
     */
    private static String buildFallback(StepContext context, String pk) {
        // 약어 우선 사용
        String zone = context.getSourceZoneShortCode();
        if (zone == null) zone = "U"; // 알 수 없음

        Long dsDbId = context.getSourceDatasourceDbId();
        String dsId = dsDbId != null ? dsDbId.toString() : "0";

        // tbId를 못 찾으면 0 (미식별)
        return zone + DELIMITER + dsId + DELIMITER + "0" + DELIMITER + pk;
    }
}
