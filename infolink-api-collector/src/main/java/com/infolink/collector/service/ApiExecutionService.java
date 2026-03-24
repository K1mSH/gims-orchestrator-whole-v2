package com.infolink.collector.service;

import com.infolink.collector.config.DynamicDataSourceService;
import com.infolink.collector.entity.*;
import com.infolink.collector.repository.*;
import com.infolink.collector.dto.ApiExecutionHistoryDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.*;
import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * 범용 API 실행 엔진
 * 1. 설정 로드
 * 2. API 호출
 * 3. 응답 파싱 (dataRootPath 기준)
 * 4. 매핑 규칙 적용 → 행 데이터 생성
 * 5. JdbcTemplate batch INSERT/UPSERT
 * 6. 이력 기록
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApiExecutionService {

    private final ApiEndpointRepository endpointRepository;
    private final ApiExecutionHistoryRepository historyRepository;
    private final ApiCallService callService;
    private final ResponseParser responseParser;
    private final DataTransformer dataTransformer;
    private final LookupService lookupService;
    private final DataSource dataSource;  // 자체 DB (fallback)
    private final DynamicDataSourceService dynamicDataSourceService;

    @Transactional
    public ApiExecutionHistoryDto.Response run(Long endpointId, ApiExecutionHistory.TriggeredBy triggeredBy) {
        ApiEndpoint endpoint = endpointRepository.findById(endpointId)
                .orElseThrow(() -> new IllegalArgumentException("ApiEndpoint not found: " + endpointId));

        // 사전 검증
        if (endpoint.getDataRootPath() == null || endpoint.getDataRootPath().isBlank()) {
            throw new IllegalStateException("data_root_path가 설정되지 않았습니다. 테스트 호출 후 데이터 루트를 선택하세요.");
        }
        if (endpoint.getFieldMappings() == null || endpoint.getFieldMappings().isEmpty()) {
            throw new IllegalStateException("필드 매핑이 없습니다. 매핑을 먼저 설정하세요.");
        }
        if (endpoint.getTargetTableName() == null || endpoint.getTargetTableName().isBlank()) {
            throw new IllegalStateException("적재 테이블이 설정되지 않았습니다.");
        }

        String executionId = UUID.randomUUID().toString();
        LocalDateTime startedAt = LocalDateTime.now();

        // 이력 생성 (RUNNING)
        ApiExecutionHistory history = ApiExecutionHistory.builder()
                .apiEndpoint(endpoint)
                .executionId(executionId)
                .status(ApiExecutionHistory.Status.RUNNING)
                .startedAt(startedAt)
                .triggeredBy(triggeredBy)
                .build();
        historyRepository.save(history);

        try {
            // 1. API 호출
            ApiCallService.CallResult callResult = callService.call(endpoint, endpoint.getParams(), null);
            history.setHttpStatusCode(callResult.statusCode());

            if (!callResult.isSuccess()) {
                throw new RuntimeException("API 호출 실패: " + (callResult.error() != null ? callResult.error() : "HTTP " + callResult.statusCode()));
            }

            // 2. 응답 파싱
            List<Map<String, Object>> records = responseParser.extractRecords(callResult.body(), endpoint.getDataRootPath());
            history.setResponseCount(records.size());

            if (records.isEmpty()) {
                history.setStatus(ApiExecutionHistory.Status.SUCCESS);
                history.setInsertCount(0);
                history.setSkipCount(0);
                finishHistory(history, startedAt);
                return ApiExecutionHistoryDto.Response.from(history);
            }

            // 3. 매핑 분리: 일반(1:1) + 파생(LOOKUP)
            List<ApiFieldMapping> allMappings = endpoint.getFieldMappings();
            List<ApiFieldMapping> normalMappings = allMappings.stream()
                    .filter(m -> !Boolean.TRUE.equals(m.getIsDerived()))
                    .collect(Collectors.toList());
            List<ApiFieldMapping> derivedMappings = allMappings.stream()
                    .filter(m -> Boolean.TRUE.equals(m.getIsDerived()))
                    .collect(Collectors.toList());

            // 4. LOOKUP Map 사전 로딩
            lookupService.clearCache();
            Map<ApiFieldMapping, Map<String, String>> lookupMaps = new HashMap<>();
            for (ApiFieldMapping dm : derivedMappings) {
                if (dm.getTransformType() == ApiFieldMapping.TransformType.LOOKUP
                        && dm.getLookupParam() != null) {
                    log.info("LOOKUP 로딩: param={}, root={}, key={}, value={}",
                            dm.getLookupParam(), dm.getLookupDataRootPath(),
                            dm.getLookupKeyField(), dm.getLookupValueField());
                    Map<String, String> lookupMap = lookupService.loadLookupMap(
                            dm.getLookupParam(),
                            dm.getLookupDataRootPath(),
                            dm.getLookupKeyField(), dm.getLookupValueField());
                    log.info("LOOKUP Map 결과: {}건", lookupMap.size());
                    lookupMaps.put(dm, lookupMap);
                }
            }
            log.info("파생 매핑 {}건, LOOKUP Map {}건", derivedMappings.size(), lookupMaps.size());

            // 5. 전체 컬럼 목록 (일반 + 파생)
            List<ApiFieldMapping> effectiveMappings = new ArrayList<>(normalMappings);
            effectiveMappings.addAll(derivedMappings);

            int insertCount = 0;
            int skipCount = 0;

            List<String> columns = effectiveMappings.stream()
                    .map(ApiFieldMapping::getTargetColumnName)
                    .collect(Collectors.toList());

            List<String> pkColumns = effectiveMappings.stream()
                    .filter(m -> Boolean.TRUE.equals(m.getIsConflictKey()))
                    .map(ApiFieldMapping::getTargetColumnName)
                    .collect(Collectors.toList());

            String sql = buildSql(endpoint.getTargetTableName(), columns, pkColumns,
                    Boolean.TRUE.equals(endpoint.getUpsertEnabled()));

            log.info("실행 SQL: {}", sql);

            DataSource targetDs = resolveTargetDataSource(endpoint);

            // 행 단위 INSERT — java.sql.Savepoint(JDBC 표준)를 사용한 개별 에러 격리
            //
            // Savepoint는 우리가 구현한 것이 아니라 SQL 표준(SQL:1999)이며,
            // JDBC(java.sql.Connection)가 API로 제공한다.
            // 트랜잭션 안에 중간 저장점을 설정하여, 실패 시 트랜잭션 전체가 아닌
            // 해당 저장점까지만 롤백할 수 있다.
            //
            // PostgreSQL 특성상 트랜잭션 내 에러 발생 시 전체가 aborted 상태가 되어
            // 이후 모든 쿼리가 거부되므로, Savepoint 없이는 한 건 실패 = 전체 실패.
            // Savepoint를 쓰면 실패한 행만 롤백하고 나머지 행은 계속 처리 가능.
            try (Connection conn = targetDs.getConnection()) {
                conn.setAutoCommit(false);
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    for (Map<String, Object> record : records) {
                        java.sql.Savepoint sp = conn.setSavepoint();  // 행 처리 전 저장점 설정
                        try {
                            int paramIndex = 1;

                            // 일반 매핑
                            for (ApiFieldMapping mapping : normalMappings) {
                                Object rawValue = getNestedValue(record, mapping.getSourceFieldPath());
                                Object transformed = dataTransformer.transform(rawValue, mapping.getTransformType(), mapping.getTransformConfig());
                                ps.setObject(paramIndex++, transformed);
                            }

                            // 파생 매핑 (LOOKUP)
                            for (ApiFieldMapping dm : derivedMappings) {
                                Object rawValue = getNestedValue(record, dm.getSourceFieldPath());
                                if (dm.getTransformType() == ApiFieldMapping.TransformType.LOOKUP) {
                                    Map<String, String> lookupMap = lookupMaps.getOrDefault(dm, Collections.emptyMap());
                                    Object result = lookupService.lookup(rawValue,
                                            dm.getExtractPattern(), dm.getExtractGroup(),
                                            lookupMap, dm.getDefaultValue());
                                    ps.setObject(paramIndex++, result);
                                } else {
                                    Object transformed = dataTransformer.transform(rawValue, dm.getTransformType(), dm.getTransformConfig());
                                    ps.setObject(paramIndex++, transformed);
                                }
                            }

                            ps.executeUpdate();
                            conn.releaseSavepoint(sp);  // 성공 → 저장점 해제 (자원 반환)
                            insertCount++;
                        } catch (Exception e) {
                            conn.rollback(sp);  // 실패 → 이 행만 롤백, 트랜잭션은 유지
                            log.warn("행 적재 실패: {}", e.getMessage());
                            skipCount++;
                        }
                    }
                    conn.commit();
                }
            }

            history.setStatus(ApiExecutionHistory.Status.SUCCESS);
            history.setInsertCount(insertCount);
            history.setSkipCount(skipCount);

        } catch (Exception e) {
            log.error("실행 실패 [{}]: {}", executionId, e.getMessage(), e);
            history.setStatus(ApiExecutionHistory.Status.FAILED);
            history.setErrorMessage(e.getMessage());
        }

        finishHistory(history, startedAt);
        return ApiExecutionHistoryDto.Response.from(history);
    }

    private void finishHistory(ApiExecutionHistory history, LocalDateTime startedAt) {
        LocalDateTime finishedAt = LocalDateTime.now();
        history.setFinishedAt(finishedAt);
        history.setDurationMs(java.time.Duration.between(startedAt, finishedAt).toMillis());
        historyRepository.save(history);
    }

    /**
     * Target DataSource 결정
     * targetDatasourceId가 설정되어 있으면 Orchestrator 등록 외부 DB, 없으면 자체 DB
     */
    private DataSource resolveTargetDataSource(ApiEndpoint endpoint) {
        String dsId = endpoint.getTargetDatasourceId();
        if (dsId != null && !dsId.isBlank()) {
            log.info("외부 DataSource 사용: {}", dsId);
            return dynamicDataSourceService.getDataSource(dsId);
        }
        log.info("자체 DataSource 사용 (fallback)");
        return dataSource;
    }

    /**
     * INSERT 또는 UPSERT SQL 생성
     */
    private String buildSql(String tableName, List<String> columns, List<String> pkColumns, boolean upsert) {
        String cols = columns.stream().collect(Collectors.joining(", "));
        String placeholders = columns.stream().map(c -> "?").collect(Collectors.joining(", "));

        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO ").append(tableName)
                .append(" (").append(cols).append(")")
                .append(" VALUES (").append(placeholders).append(")");

        if (upsert && !pkColumns.isEmpty()) {
            // PostgreSQL ON CONFLICT
            String conflictCols = String.join(", ", pkColumns);
            String updateSet = columns.stream()
                    .filter(c -> !pkColumns.contains(c))
                    .map(c -> c + " = EXCLUDED." + c)
                    .collect(Collectors.joining(", "));

            sql.append(" ON CONFLICT (").append(conflictCols).append(")");
            if (!updateSet.isEmpty()) {
                sql.append(" DO UPDATE SET ").append(updateSet);
            } else {
                sql.append(" DO NOTHING");
            }
        }

        return sql.toString();
    }

    /**
     * dot notation으로 중첩 값 추출 (예: "address.city")
     */
    private Object getNestedValue(Map<String, Object> record, String path) {
        // flattenNode에서 이미 dot notation으로 flatten됨
        if (record.containsKey(path)) {
            return record.get(path);
        }
        // 단순 키 매칭
        return record.get(path);
    }
}
