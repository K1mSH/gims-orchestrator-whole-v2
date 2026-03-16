package com.infolink.collector.service;

import com.infolink.collector.domain.*;
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
    private final DataSource dataSource;

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

            // 3. 매핑 적용 + DB 적재
            List<ApiFieldMapping> mappings = endpoint.getFieldMappings();
            int insertCount = 0;
            int skipCount = 0;

            // 컬럼 목록
            List<String> columns = mappings.stream()
                    .map(ApiFieldMapping::getTargetColumnName)
                    .collect(Collectors.toList());

            // PK 컬럼
            List<String> pkColumns = mappings.stream()
                    .filter(m -> Boolean.TRUE.equals(m.getIsPk()))
                    .map(ApiFieldMapping::getTargetColumnName)
                    .collect(Collectors.toList());

            // SQL 생성
            String sql = buildSql(endpoint.getTargetTableName(), columns, pkColumns,
                    Boolean.TRUE.equals(endpoint.getUpsertEnabled()));

            log.info("실행 SQL: {}", sql);

            // 행 단위 INSERT — PG 트랜잭션 aborted 방지를 위해 savepoint 사용
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    for (Map<String, Object> record : records) {
                        java.sql.Savepoint sp = conn.setSavepoint();
                        try {
                            for (int i = 0; i < mappings.size(); i++) {
                                ApiFieldMapping mapping = mappings.get(i);
                                Object rawValue = getNestedValue(record, mapping.getSourceFieldPath());
                                Object transformed = dataTransformer.transform(rawValue, mapping.getTransformType(), mapping.getTransformConfig());
                                ps.setObject(i + 1, transformed);
                            }
                            ps.executeUpdate();
                            conn.releaseSavepoint(sp);
                            insertCount++;
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
