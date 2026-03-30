package com.infolink.collector.service;

import com.infolink.collector.config.DynamicDataSourceService;
import com.infolink.collector.entity.*;
import com.infolink.collector.executor.CustomExecutionResult;
import com.infolink.collector.executor.CustomExecutor;
import com.infolink.collector.executor.CustomExecutorRegistry;
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
import java.util.HashSet;
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
    private final CustomExecutorRegistry customExecutorRegistry;

    @Transactional
    public ApiExecutionHistoryDto.Response run(Long endpointId, ApiExecutionHistory.TriggeredBy triggeredBy) {
        ApiEndpoint endpoint = endpointRepository.findById(endpointId)
                .orElseThrow(() -> new IllegalArgumentException("ApiEndpoint not found: " + endpointId));

        // 커스텀 실행기 분기
        if (endpoint.getExecutorType() != null && !endpoint.getExecutorType().isBlank()) {
            return runCustom(endpoint, triggeredBy);
        }

        // 범용 매핑 사전 검증
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
                history.setUpdateCount(0);
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
            int updateCount = 0;
            int skipCount = 0;

            List<String> columns = effectiveMappings.stream()
                    .map(ApiFieldMapping::getTargetColumnName)
                    .collect(Collectors.toList());

            List<String> pkColumns = effectiveMappings.stream()
                    .filter(m -> Boolean.TRUE.equals(m.getIsConflictKey()))
                    .map(ApiFieldMapping::getTargetColumnName)
                    .collect(Collectors.toList());

            boolean useUpsert = !pkColumns.isEmpty();
            String sql = buildSql(endpoint.getTargetTableName(), columns, pkColumns, useUpsert);

            log.info("실행 SQL: {}", sql);

            DataSource targetDs = resolveTargetDataSource(endpoint);

            // conflict key가 있을 때 기존 PK Set 조회 (insert/update 구분용)
            Set<String> existingPkSet = new HashSet<>();
            if (useUpsert) {
                existingPkSet = loadExistingPkSet(targetDs, endpoint.getTargetTableName(), pkColumns);
                log.info("기존 PK {}건 조회 완료", existingPkSet.size());
            }

            // 행 단위 INSERT — Savepoint를 사용한 개별 에러 격리
            // PG 특성상 트랜잭션 내 에러 시 전체 aborted → Savepoint로 행 단위 롤백
            try (Connection conn = targetDs.getConnection()) {
                conn.setAutoCommit(false);
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    for (Map<String, Object> record : records) {
                        java.sql.Savepoint sp = conn.setSavepoint();
                        try {
                            int paramIndex = 1;

                            // PK 값 수집 (insert/update 판별용)
                            List<String> currentPkValues = new ArrayList<>();

                            // 일반 매핑
                            for (ApiFieldMapping mapping : normalMappings) {
                                Object rawValue = getNestedValue(record, mapping.getSourceFieldPath());
                                Object transformed = dataTransformer.transform(rawValue, mapping.getTransformType(), mapping.getTransformConfig());
                                ps.setObject(paramIndex++, transformed);
                                if (useUpsert && pkColumns.contains(mapping.getTargetColumnName())) {
                                    currentPkValues.add(String.valueOf(transformed));
                                }
                            }

                            // 파생 매핑 (LOOKUP / DEFAULT_VALUE)
                            for (ApiFieldMapping dm : derivedMappings) {
                                if (dm.getTransformType() == ApiFieldMapping.TransformType.DEFAULT_VALUE) {
                                    ps.setObject(paramIndex++, dm.getDefaultValue());
                                    if (useUpsert && pkColumns.contains(dm.getTargetColumnName())) {
                                        currentPkValues.add(String.valueOf(dm.getDefaultValue()));
                                    }
                                    continue;
                                }
                                Object rawValue = getNestedValue(record, dm.getSourceFieldPath());
                                Object result;
                                if (dm.getTransformType() == ApiFieldMapping.TransformType.LOOKUP) {
                                    Map<String, String> lookupMap = lookupMaps.getOrDefault(dm, Collections.emptyMap());
                                    result = lookupService.lookup(rawValue,
                                            dm.getExtractPattern(), dm.getExtractGroup(),
                                            lookupMap, dm.getDefaultValue());
                                } else {
                                    result = dataTransformer.transform(rawValue, dm.getTransformType(), dm.getTransformConfig());
                                }
                                ps.setObject(paramIndex++, result);
                                if (useUpsert && pkColumns.contains(dm.getTargetColumnName())) {
                                    currentPkValues.add(String.valueOf(result));
                                }
                            }

                            ps.executeUpdate();
                            conn.releaseSavepoint(sp);

                            // insert/update 판별
                            if (useUpsert) {
                                String pkKey = String.join("|", currentPkValues);
                                if (existingPkSet.contains(pkKey)) {
                                    updateCount++;
                                } else {
                                    insertCount++;
                                    existingPkSet.add(pkKey);  // 이후 중복 행 대비
                                }
                            } else {
                                insertCount++;
                            }
                        } catch (Exception e) {
                            conn.rollback(sp);
                            log.warn("행 적재 실패: {}", e.getMessage());
                            skipCount++;
                        }
                    }
                    conn.commit();
                }
            }

            history.setStatus(ApiExecutionHistory.Status.SUCCESS);
            history.setInsertCount(insertCount);
            history.setUpdateCount(updateCount);
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
     * 기존 PK Set 조회 — insert/update 구분용
     */
    private Set<String> loadExistingPkSet(DataSource ds, String tableName, List<String> pkColumns) {
        Set<String> pkSet = new HashSet<>();
        String pkCols = String.join(", ", pkColumns);
        String query = "SELECT " + pkCols + " FROM " + tableName;
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(query);
             java.sql.ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                List<String> values = new ArrayList<>();
                for (int i = 1; i <= pkColumns.size(); i++) {
                    values.add(String.valueOf(rs.getObject(i)));
                }
                pkSet.add(String.join("|", values));
            }
        } catch (Exception e) {
            log.warn("기존 PK 조회 실패 (신규 테이블일 수 있음): {}", e.getMessage());
        }
        return pkSet;
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

    /**
     * 커스텀 실행기로 실행 — 이력 기록은 동일
     */
    private ApiExecutionHistoryDto.Response runCustom(ApiEndpoint endpoint, ApiExecutionHistory.TriggeredBy triggeredBy) {
        CustomExecutor executor = customExecutorRegistry.get(endpoint.getExecutorType())
                .orElseThrow(() -> new IllegalStateException("커스텀 실행기를 찾을 수 없습니다: " + endpoint.getExecutorType()));

        String executionId = UUID.randomUUID().toString();
        LocalDateTime startedAt = LocalDateTime.now();

        ApiExecutionHistory history = ApiExecutionHistory.builder()
                .apiEndpoint(endpoint)
                .executionId(executionId)
                .status(ApiExecutionHistory.Status.RUNNING)
                .startedAt(startedAt)
                .triggeredBy(triggeredBy)
                .build();
        historyRepository.save(history);

        try {
            CustomExecutionResult result = executor.execute(endpoint, endpoint.getParams(), null, triggeredBy.name());

            history.setHttpStatusCode(result.httpStatusCode());
            history.setResponseCount(result.responseCount());
            history.setInsertCount(result.insertCount());
            history.setUpdateCount(result.updateCount());
            history.setSkipCount(result.skipCount());

            if (result.isSuccess()) {
                history.setStatus(ApiExecutionHistory.Status.SUCCESS);
            } else {
                history.setStatus(ApiExecutionHistory.Status.FAILED);
                history.setErrorMessage(result.errorMessage());
            }
        } catch (Exception e) {
            log.error("커스텀 실행기 오류: {}", e.getMessage(), e);
            history.setStatus(ApiExecutionHistory.Status.FAILED);
            history.setErrorMessage(e.getMessage());
        } finally {
            history.setFinishedAt(LocalDateTime.now());
            history.setDurationMs(java.time.Duration.between(startedAt, history.getFinishedAt()).toMillis());
            historyRepository.save(history);
        }

        return ApiExecutionHistoryDto.Response.from(history);
    }
}
