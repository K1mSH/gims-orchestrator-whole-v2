package com.sync.agent.common.controller;

import com.sync.agent.common.dto.TableStatsDto;
import com.sync.agent.common.entity.Execution;
import com.sync.agent.common.entity.SyncLog;
import com.sync.agent.common.repository.SyncLogRepository;
import com.sync.agent.common.service.ExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 실행 데이터 API 공통 컨트롤러
 * Agent DB에서 실행 데이터를 조회하는 표준 엔드포인트 제공
 *
 * Agent별 커스텀 구현이 필요한 경우 ComponentScan excludeFilters로 이 컨트롤러를 제외하고
 * 별도의 컨트롤러를 구현하세요.
 */
@Slf4j
@RestController
@RequestMapping("/api/execution-data")
@RequiredArgsConstructor
public class ExecutionDataController {

    private final DataSourceProvider dataSourceProvider;
    private final SyncLogRepository syncLogRepository;
    private final ExecutionService executionService;

    /**
     * 실행 데이터 요약 (SyncLog 요약 기반)
     */
    @GetMapping("/{executionId}/summary")
    public ResponseEntity<Map<String, Object>> getSummary(@PathVariable String executionId) {
        // SyncLog에서 총 성공/실패/스킵 건수 합계
        Object result = syncLogRepository.sumCountsByExecutionId(executionId);

        long successCount = 0L;
        long failedCount = 0L;
        long skipCount = 0L;

        if (result != null) {
            Object[] sums;
            // JPA 버전에 따라 Object[] 또는 단일 행 배열로 반환될 수 있음
            if (result instanceof Object[] arr) {
                // 첫 번째 요소가 배열인지 확인 (List<Object[]> 형태로 반환된 경우)
                if (arr.length > 0 && arr[0] instanceof Object[]) {
                    sums = (Object[]) arr[0];
                } else {
                    sums = arr;
                }
                successCount = sums.length > 0 && sums[0] != null ? ((Number) sums[0]).longValue() : 0L;
                failedCount = sums.length > 1 && sums[1] != null ? ((Number) sums[1]).longValue() : 0L;
                skipCount = sums.length > 2 && sums[2] != null ? ((Number) sums[2]).longValue() : 0L;
            }
        }
        long totalCount = successCount + failedCount + skipCount;

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("executionId", executionId);
        summary.put("successCount", successCount);
        summary.put("failedCount", failedCount);
        summary.put("skipCount", skipCount);
        summary.put("totalCount", totalCount);

        return ResponseEntity.ok(summary);
    }

    /**
     * 실행 상세 정보 조회
     */
    @GetMapping("/{executionId}")
    public ResponseEntity<?> getExecution(@PathVariable String executionId) {
        return executionService.getExecution(executionId)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> {
                    log.warn("Execution not found in local DB: {}", executionId);
                    // 404 대신 빈 Execution 객체 반환 (프론트엔드 에러 방지)
                    Execution empty = Execution.builder()
                            .executionId(executionId)
                            .status("UNKNOWN")
                            .build();
                    return ResponseEntity.ok(empty);
                });
    }

    /**
     * 최근 실행 목록 조회
     */
    @GetMapping("/recent")
    public ResponseEntity<List<Execution>> getRecentExecutions() {
        return ResponseEntity.ok(executionService.getRecentExecutions());
    }

    /**
     * 전체 실행 목록 조회
     */
    @GetMapping("")
    public ResponseEntity<List<Execution>> getAllExecutions() {
        return ResponseEntity.ok(executionService.getAllExecutions());
    }

    /**
     * Source 테이블 데이터 조회 (해당 실행에서 읽어온 데이터)
     * Relay 에이전트의 경우 SOURCE 테이블에 execution_id가 없으므로 전체 데이터 조회
     */
    @GetMapping("/{executionId}/source")
    public ResponseEntity<Map<String, Object>> getSourceData(
            @PathVariable String executionId,
            @RequestParam(required = false) String tableName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String searchColumn,
            @RequestParam(required = false) String sortColumn,
            @RequestParam(defaultValue = "asc") String sortDirection) {

        try {
            // tableName 필수 체크
            if (tableName == null || tableName.isBlank()) {
                return ResponseEntity.ok(buildEmptyPageResultWithMessage("unknown", page, size,
                        "테이블명이 지정되지 않았습니다. tableName 파라미터를 전달해주세요."));
            }

            // Execution에서 sourceDatasourceId 조회
            Execution execution = executionService.getExecution(executionId).orElse(null);
            if (execution == null) {
                return ResponseEntity.ok(buildEmptyPageResultWithMessage(tableName, page, size,
                        "실행 정보를 찾을 수 없습니다: " + executionId));
            }

            String datasourceId = execution.getSourceDatasourceId();
            if (datasourceId == null || datasourceId.isBlank()) {
                return ResponseEntity.ok(buildEmptyPageResultWithMessage(tableName, page, size,
                        "Source datasource가 설정되지 않았습니다."));
            }

            JdbcTemplate sourceJdbc;
            try {
                sourceJdbc = dataSourceProvider.getJdbcTemplate(datasourceId);
            } catch (Exception e) {
                // 소스 데이터 조회이므로 SOURCE(외부 DB) 먼저 시도
                log.warn("DataSource '{}' not found, trying fallback datasources...", datasourceId);
                String fallbackId = null;
                try {
                    fallbackId = dataSourceProvider.getSourceDatasourceId();
                    sourceJdbc = dataSourceProvider.getJdbcTemplate(fallbackId);
                    datasourceId = fallbackId;
                    log.info("Using fallback SOURCE datasource: {} for source endpoint", fallbackId);
                } catch (Exception e2) {
                    try {
                        fallbackId = dataSourceProvider.getTargetDatasourceId();
                        sourceJdbc = dataSourceProvider.getJdbcTemplate(fallbackId);
                        datasourceId = fallbackId;
                        log.info("Using fallback TARGET datasource: {} for source endpoint", fallbackId);
                    } catch (Exception e3) {
                        return ResponseEntity.ok(buildEmptyPageResultWithMessage(tableName, page, size,
                                "DataSource를 찾을 수 없습니다: " + datasourceId));
                    }
                }
            }

            // 테이블 존재 여부 확인 (대소문자 무시)
            String actualTableName = findActualTableName(sourceJdbc, tableName);
            if (actualTableName == null) {
                log.warn("Source table '{}' does not exist in datasource '{}'", tableName, datasourceId);
                return ResponseEntity.ok(buildEmptyPageResultWithMessage(tableName, page, size,
                        "Source 테이블이 존재하지 않습니다: " + tableName));
            }

            // 테이블명을 DB 안전 형식으로 (대소문자 유지를 위해 인용)
            String srcDbType = dataSourceProvider.getDbType(datasourceId);
            String quotedTableName = qi(actualTableName, srcDbType);

            // SOURCE 테이블은 execution_id 필터링 안 함
            // (외부 시스템이거나, 다른 agent가 생성한 데이터일 수 있음)
            StringBuilder whereClause = new StringBuilder("1=1");
            List<Object> params = new ArrayList<>();

            // 검색 조건 추가 (특정 컬럼 또는 전체 컬럼)
            String searchClause = buildSearchClause(sourceJdbc, actualTableName, search, searchColumn, params);
            whereClause.append(searchClause);

            // 전체 건수 조회
            String countSql = String.format("SELECT COUNT(*) FROM %s WHERE %s", quotedTableName, whereClause);
            Integer totalCount = sourceJdbc.queryForObject(countSql, Integer.class, params.toArray());
            if (totalCount == null) totalCount = 0;

            // 정렬 처리
            String orderBy = buildOrderByClause(sourceJdbc, actualTableName, sortColumn, sortDirection);

            // 페이징된 데이터 조회
            String dataSql = String.format(
                    "SELECT * FROM %s WHERE %s %s LIMIT %d OFFSET %d",
                    quotedTableName, whereClause, orderBy, size, page * size);
            List<Map<String, Object>> data = sourceJdbc.queryForList(dataSql, params.toArray());

            List<String> columns = data.isEmpty() ? getTableColumns(sourceJdbc, actualTableName) : new ArrayList<>(data.get(0).keySet());

            return ResponseEntity.ok(buildPageResult(tableName, columns, data, totalCount, page, size));
        } catch (Exception e) {
            log.error("Failed to get source data for execution: {}", executionId, e);
            return ResponseEntity.ok(buildEmptyPageResultWithMessage(tableName, page, size,
                    "Source 데이터를 조회할 수 없습니다: " + e.getMessage()));
        }
    }

    /**
     * Target IF 테이블 데이터 조회
     */
    @GetMapping("/{executionId}/target-if")
    public ResponseEntity<Map<String, Object>> getTargetIfData(
            @PathVariable String executionId,
            @RequestParam(required = false) String tableName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String searchColumn,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sortColumn,
            @RequestParam(defaultValue = "asc") String sortDirection) {

        try {
            // tableName 필수 체크
            if (tableName == null || tableName.isBlank()) {
                return ResponseEntity.ok(buildEmptyPageResultWithMessage("unknown", page, size,
                        "테이블명이 지정되지 않았습니다. tableName 파라미터를 전달해주세요."));
            }

            // Execution에서 targetDatasourceId 조회 후 JdbcTemplate 획득
            Execution execution = executionService.getExecution(executionId).orElse(null);
            String datasourceId = (execution != null && execution.getTargetDatasourceId() != null)
                    ? execution.getTargetDatasourceId()
                    : null;

            JdbcTemplate targetJdbc;
            try {
                targetJdbc = dataSourceProvider.getJdbcTemplate(
                        datasourceId != null ? datasourceId : dataSourceProvider.getTargetDatasourceId());
            } catch (Exception e) {
                log.warn("DataSource '{}' not found, falling back to default target", datasourceId);
                targetJdbc = dataSourceProvider.getJdbcTemplate(dataSourceProvider.getTargetDatasourceId());
            }

            // 테이블 존재 여부 확인
            if (!tableExists(targetJdbc, tableName)) {
                log.warn("Target IF table '{}' does not exist", tableName);
                return ResponseEntity.ok(buildEmptyPageResultWithMessage(tableName, page, size,
                        "IF 테이블이 존재하지 않습니다."));
            }

            // IF 테이블은 통합된 execution_id 컬럼 사용
            String executionIdColumn = "execution_id";

            StringBuilder whereClause = new StringBuilder(executionIdColumn + " = ?");
            List<Object> params = new ArrayList<>();
            params.add(executionId);

            // 검색 조건 추가 (특정 컬럼 또는 전체 컬럼)
            String searchClause = buildSearchClause(targetJdbc, tableName, search, searchColumn, params);
            whereClause.append(searchClause);

            // 검색 조건 기반 성공/실패 건수 (상태 필터 적용 전)
            String baseWhere = whereClause.toString();
            Object[] baseParams = params.toArray();
            Integer successCount = targetJdbc.queryForObject(
                    String.format("SELECT COUNT(*) FROM %s WHERE %s AND link_status = 'SUCCESS'", tableName, baseWhere),
                    Integer.class, baseParams);
            Integer failedCount = targetJdbc.queryForObject(
                    String.format("SELECT COUNT(*) FROM %s WHERE %s AND link_status = 'FAILED'", tableName, baseWhere),
                    Integer.class, baseParams);

            if (status != null && !status.isBlank()) {
                whereClause.append(" AND link_status = ?");
                params.add(status);
            }

            // 전체 건수 조회
            String countSql = String.format("SELECT COUNT(*) FROM %s WHERE %s", tableName, whereClause);
            Integer totalCount = targetJdbc.queryForObject(countSql, Integer.class, params.toArray());
            if (totalCount == null) totalCount = 0;

            // 정렬 처리
            String orderBy = buildOrderByClause(targetJdbc, tableName, sortColumn, sortDirection);

            // 페이징된 데이터 조회
            String dataSql = String.format(
                    "SELECT * FROM %s WHERE %s %s LIMIT %d OFFSET %d",
                    tableName, whereClause, orderBy, size, page * size);
            List<Map<String, Object>> data = targetJdbc.queryForList(dataSql, params.toArray());

            List<String> columns = data.isEmpty() ? getTableColumns(targetJdbc, tableName) : new ArrayList<>(data.get(0).keySet());

            Map<String, Object> result = buildPageResult(tableName, columns, data, totalCount, page, size);
            result.put("successCount", successCount != null ? successCount : 0);
            result.put("failedCount", failedCount != null ? failedCount : 0);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to get target IF data for execution: {}", executionId, e);
            return ResponseEntity.ok(buildEmptyPageResultWithMessage(tableName, page, size,
                    "IF 테이블 데이터를 조회할 수 없습니다: " + e.getMessage()));
        }
    }

    /**
     * Target 최종 테이블 데이터 조회
     */
    @GetMapping("/{executionId}/target")
    public ResponseEntity<Map<String, Object>> getTargetData(
            @PathVariable String executionId,
            @RequestParam(required = false) String tableName,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String searchColumn,
            @RequestParam(required = false) String sortColumn,
            @RequestParam(defaultValue = "asc") String sortDirection) {

        try {
            // tableName 필수 체크
            if (tableName == null || tableName.isBlank()) {
                return ResponseEntity.ok(buildEmptyPageResultWithMessage("unknown", page, size,
                        "테이블명이 지정되지 않았습니다. tableName 파라미터를 전달해주세요."));
            }

            // Execution에서 targetDatasourceId 조회
            Execution execution = executionService.getExecution(executionId).orElse(null);
            String datasourceId = (execution != null && execution.getTargetDatasourceId() != null)
                    ? execution.getTargetDatasourceId()
                    : dataSourceProvider.getTargetDatasourceId();  // 대체 값

            JdbcTemplate targetJdbc = dataSourceProvider.getJdbcTemplate(datasourceId);

            // WHERE 조건 구성 - execution_id 기반 (IF 테이블도 통합된 execution_id 사용)
            String executionIdColumn = "execution_id";

            StringBuilder whereClause;
            List<Object> params = new ArrayList<>();

            // execution_id 컬럼 존재 여부 확인
            boolean useExecutionIdFilter = hasColumn(targetJdbc, tableName, executionIdColumn);

            if (useExecutionIdFilter) {
                whereClause = new StringBuilder(executionIdColumn + " = ?");
                params.add(executionId);
            } else {
                // execution_id 없으면 전체 조회
                whereClause = new StringBuilder("1=1");
            }

            // 검색 조건 추가 (특정 컬럼 또는 전체 컬럼)
            String searchClause = buildSearchClause(targetJdbc, tableName, search, searchColumn, params);
            whereClause.append(searchClause);

            // 검색 조건 기반 성공/실패 건수 (link_status 컬럼이 있는 경우)
            Integer successCount = null;
            Integer failedCount = null;
            if (hasColumn(targetJdbc, tableName, "link_status")) {
                String baseWhere = whereClause.toString();
                Object[] baseParams = params.toArray();
                successCount = targetJdbc.queryForObject(
                        String.format("SELECT COUNT(*) FROM %s WHERE %s AND link_status = 'SUCCESS'", tableName, baseWhere),
                        Integer.class, baseParams);
                failedCount = targetJdbc.queryForObject(
                        String.format("SELECT COUNT(*) FROM %s WHERE %s AND link_status = 'FAILED'", tableName, baseWhere),
                        Integer.class, baseParams);
            }

            // 전체 건수 조회
            String countSql = String.format("SELECT COUNT(*) FROM %s WHERE %s", tableName, whereClause);
            Integer totalCount = targetJdbc.queryForObject(countSql, Integer.class, params.toArray());
            if (totalCount == null) totalCount = 0;

            // 정렬 처리
            String orderBy = buildOrderByClause(targetJdbc, tableName, sortColumn, sortDirection);

            // 페이징된 데이터 조회
            String dataSql = String.format(
                    "SELECT * FROM %s WHERE %s %s LIMIT %d OFFSET %d",
                    tableName, whereClause, orderBy, size, page * size);
            List<Map<String, Object>> data = targetJdbc.queryForList(dataSql, params.toArray());

            List<String> columns = data.isEmpty() ? getTableColumns(targetJdbc, tableName) : new ArrayList<>(data.get(0).keySet());

            Map<String, Object> result = buildPageResult(tableName, columns, data, totalCount, page, size);
            if (successCount != null) result.put("successCount", successCount);
            if (failedCount != null) result.put("failedCount", failedCount);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to get target data for execution: {}", executionId, e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 실패한 레코드 정보 (테이블별 실패 요약 + 실패 키 목록)
     */
    @GetMapping("/{executionId}/failed")
    public ResponseEntity<List<Map<String, Object>>> getFailedRecords(@PathVariable String executionId) {
        List<SyncLog> failedLogs = syncLogRepository.findFailedByExecutionId(executionId);

        List<Map<String, Object>> result = failedLogs.stream()
                .map(log -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("tableName", log.getTableName());
                    item.put("tableType", log.getTableType());
                    item.put("failedCount", log.getFailedCount());
                    item.put("failedKeys", log.getFailedKeys());
                    item.put("errorSummary", log.getErrorSummary());
                    return item;
                })
                .toList();

        return ResponseEntity.ok(result);
    }

    /**
     * 테이블별 통계 조회 (SyncLog 요약 기반)
     */
    @GetMapping("/{executionId}/tables")
    public ResponseEntity<List<TableStatsDto>> getTableStats(@PathVariable String executionId) {
        List<SyncLog> syncLogs = syncLogRepository.findByExecutionId(executionId);

        List<TableStatsDto> tableStats = new ArrayList<>();
        for (SyncLog log : syncLogs) {
            String tableName = log.getTableName();
            String tableType = log.getTableType();

            if (tableName == null || tableName.isBlank()) {
                continue;
            }

            // tableType이 없으면 테이블명으로 추론
            if (tableType == null || tableType.isBlank()) {
                if (tableName.startsWith("if_")) {
                    tableType = "IF";
                } else {
                    tableType = "TARGET";
                }
            }

            // IF → TARGET_IF로 변환 (프론트엔드 호환)
            String displayType = "IF".equals(tableType) ? "TARGET_IF" : tableType;

            tableStats.add(TableStatsDto.builder()
                    .tableName(tableName)
                    .tableType(displayType)
                    .totalCount(log.getTotalCount())
                    .successCount(log.getSuccessCount() != null ? log.getSuccessCount() : 0L)
                    .failedCount(log.getFailedCount() != null ? log.getFailedCount() : 0L)
                    .skipCount(log.getSkipCount() != null ? log.getSkipCount() : 0L)
                    .build());
        }

        return ResponseEntity.ok(tableStats);
    }

    /**
     * 특정 테이블의 처리 로그 조회
     */
    @GetMapping("/{executionId}/tables/{tableName}")
    public ResponseEntity<SyncLog> getTableLog(
            @PathVariable String executionId,
            @PathVariable String tableName) {
        return syncLogRepository.findByExecutionIdAndTableName(executionId, tableName)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 특정 테이블의 실패 정보 조회
     */
    @GetMapping("/{executionId}/tables/{tableName}/failed")
    public ResponseEntity<Map<String, Object>> getTableFailedInfo(
            @PathVariable String executionId,
            @PathVariable String tableName) {
        return syncLogRepository.findByExecutionIdAndTableName(executionId, tableName)
                .filter(log -> log.getFailedCount() != null && log.getFailedCount() > 0)
                .map(log -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("tableName", log.getTableName());
                    result.put("failedCount", log.getFailedCount());
                    result.put("failedKeys", log.getFailedKeys());
                    result.put("errorSummary", log.getErrorSummary());
                    return ResponseEntity.ok(result);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Source PK로 데이터 추적 (Source → Target)
     * Orchestrator가 Agent의 테이블 매핑 정보를 기반으로 targetTable(구 ifTableName)을 자동 해석
     *
     * 조회 방식:
     * 1. 먼저 PK 컬럼으로 직접 조회 시도 (IF 테이블의 id = Source의 id인 경우)
     * 2. 없으면 source_refs 컬럼에서 검색 (IF 테이블의 id ≠ Source의 id인 경우)
     */
    @GetMapping("/{executionId}/trace")
    public ResponseEntity<Map<String, Object>> traceBySourcePk(
            @PathVariable String executionId,
            @RequestParam String pkValue,
            @RequestParam(defaultValue = "id") String pkColumn,
            @RequestParam(required = false) String sourceTable,
            @RequestParam(required = false) String ifTableName) {  // ifTableName = 실제 target 테이블

        try {
            // 필수 파라미터 체크
            if (sourceTable == null || sourceTable.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "sourceTable 파라미터가 필요합니다."));
            }

            // ifTableName이 없으면 /tables 엔드포인트와 동일한 방식으로 조회
            if (ifTableName == null || ifTableName.isBlank()) {
                // sourceTable 이름에서 베이스 이름 추출 (예: SEC_JEWON_VIEW → sec_jewon)
                // toLowerCase() 후 _view 제거하면 대소문자 모두 처리됨
                String sourceBase = sourceTable.toLowerCase().replace("_view", "");

                // 전체 SyncLog에서 TARGET/TARGET_IF 타입 중 sourceBase를 포함하는 테이블 찾기
                List<SyncLog> allLogs = syncLogRepository.findByExecutionId(executionId);
                log.debug("Found {} sync logs for execution: {}", allLogs.size(), executionId);

                // DB에는 tableType이 "IF" 또는 "TARGET"으로 저장됨 (TARGET_IF는 표시용)
                ifTableName = allLogs.stream()
                        .filter(l -> "TARGET".equals(l.getTableType()) || "IF".equals(l.getTableType()))
                        .filter(l -> l.getTableName() != null &&
                                     l.getTableName().toLowerCase().contains(sourceBase))
                        .map(SyncLog::getTableName)
                        .findFirst()
                        .orElse(null);

                // Loader 대응: sourceBase가 if_rsv_sec_jewon처럼 IF 접두사를 포함하면
                // 접두사 제거 후 재시도 (sec_jewon으로 매칭)
                if (ifTableName == null) {
                    String strippedBase = sourceBase
                            .replaceFirst("^if_rsv_", "")
                            .replaceFirst("^if_snd_", "");
                    if (!strippedBase.equals(sourceBase)) {
                        String finalStripped = strippedBase;
                        ifTableName = allLogs.stream()
                                .filter(l -> "TARGET".equals(l.getTableType()))
                                .filter(l -> l.getTableName() != null &&
                                             l.getTableName().toLowerCase().contains(finalStripped))
                                .map(SyncLog::getTableName)
                                .findFirst()
                                .orElse(null);
                        if (ifTableName != null) {
                            log.debug("Resolved target via stripped IF prefix: {} -> {}", sourceBase, ifTableName);
                        }
                    }
                }

                log.debug("Resolved ifTableName: {} for sourceTable: {}", ifTableName, sourceTable);
            }

            if (ifTableName == null || ifTableName.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "target 테이블을 찾을 수 없습니다.",
                    "sourceTable", sourceTable,
                    "executionId", executionId
                ));
            }

            JdbcTemplate targetJdbc = dataSourceProvider.getJdbcTemplate(dataSourceProvider.getTargetDatasourceId());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("pkColumn", pkColumn);
            result.put("pkValue", pkValue);
            result.put("executionId", executionId);

            // pkValue를 적절한 타입으로 변환 (숫자면 Long, 아니면 String)
            Object pkValueTyped;
            try {
                pkValueTyped = Long.parseLong(pkValue);
            } catch (NumberFormatException e) {
                pkValueTyped = pkValue;
            }

            List<Map<String, Object>> targetRecords;

            // 1차 시도: PK 컬럼으로 직접 조회 (IF 테이블 id = Source id인 경우)
            String executionIdCol = "execution_id";
            String directSql = String.format(
                    "SELECT * FROM %s WHERE %s = ? AND %s = ?",
                    ifTableName, pkColumn, executionIdCol);
            targetRecords = targetJdbc.queryForList(directSql, pkValueTyped, executionId);

            // 2차 시도: source_refs에서 검색 (IF 테이블 id ≠ Source id인 경우)
            if (targetRecords.isEmpty()) {
                log.debug("Direct PK lookup returned no results, trying source_refs search...");
                // source_refs 형식: ["D:dsId:tbId:pk"] - pk 부분이 pkValue와 일치하는지 검색
                String sourceRefsSql = String.format(
                        "SELECT * FROM %s WHERE source_refs LIKE ? AND %s = ?",
                        ifTableName, executionIdCol);
                String searchPattern = "%:" + pkValue + "\"]";  // 예: %:493"]
                targetRecords = targetJdbc.queryForList(sourceRefsSql, searchPattern, executionId);

                if (!targetRecords.isEmpty()) {
                    log.debug("Found {} records via source_refs search", targetRecords.size());
                }
            }

            // 3차 시도: 유니크 키로 검색 (source PK → source record → UK 값 → IF 테이블 검색)
            if (targetRecords.isEmpty()) {
                log.debug("source_refs search failed, trying business key lookup...");
                targetRecords = traceByBusinessKey(targetJdbc, sourceTable, ifTableName,
                        pkColumn, pkValueTyped, executionId);
            }

            // 4차 시도: 같은 Agent의 다른 execution에서 조회 (UPSERT로 덮어씌워진 경우)
            // execution_id를 완전히 제거하지 않고, agentCode 접두사로 필터링
            boolean traceFallback = false;
            if (targetRecords.isEmpty()) {
                String agentCode = extractAgentCode(executionId);
                String agentPattern = agentCode + "_%";
                log.debug("Trying agent-scoped fallback with pattern: {}", agentPattern);

                // PK로 직접 조회 (같은 Agent의 execution만)
                String agentFilterSql = String.format(
                        "SELECT * FROM %s WHERE %s = ? AND %s LIKE ?",
                        ifTableName, pkColumn, executionIdCol);
                targetRecords = targetJdbc.queryForList(agentFilterSql, pkValueTyped, agentPattern);

                // source_refs로 재시도 (같은 Agent의 execution만)
                if (targetRecords.isEmpty()) {
                    String agentFilterRefsSql = String.format(
                            "SELECT * FROM %s WHERE source_refs LIKE ? AND %s LIKE ?",
                            ifTableName, executionIdCol);
                    String searchPattern = "%:" + pkValue + "\"]";
                    targetRecords = targetJdbc.queryForList(agentFilterRefsSql, searchPattern, agentPattern);
                }

                if (!targetRecords.isEmpty()) {
                    log.debug("Found {} records via agent-scoped fallback (agent: {})", targetRecords.size(), agentCode);
                    traceFallback = true;
                }
            }

            result.put("targetTableName", ifTableName);  // ifTableName이 실제 target
            result.put("targetRecords", targetRecords);
            result.put("targetCount", targetRecords.size());

            // 처리 상태 요약
            String traceStatus = targetRecords.isEmpty() ? "NOT_SYNCED" : "SYNCED";
            if (traceFallback) {
                traceStatus = "FOUND_IN_IF";
                result.put("fallbackMode", true);
            }
            result.put("traceStatus", traceStatus);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to trace data for {}={}", pkColumn, pkValue, e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Target에서 Source로 역추적 (sourceRefs 기반)
     * Target/IF 행 클릭 시 원본 Source 레코드 조회
     * sourceRefs 형식: ["E:datasourceId:tableId:pk"] 또는 ["D:datasourceId:tableId:pk"]
     *
     * SND Relay 특수 처리:
     * - SND의 Source는 Target DB의 테이블 (sec_jewon, sec_obsvdata)
     * - sourceRefs의 pk로 Source 테이블 조회
     * - 조회 실패 시, IF 테이블에서 해당 레코드를 찾아 비즈니스 키로 재조회 시도
     */
    @GetMapping("/{executionId}/trace-source")
    public ResponseEntity<Map<String, Object>> traceToSource(
            @PathVariable String executionId,
            @RequestParam String sourceRefs,
            @RequestParam(required = false) String sourceTable) {

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("executionId", executionId);
        result.put("sourceRefs", sourceRefs);

        try {
            // sourceRefs 파싱: ["E:1:4:26"] 또는 ["D:1:4:26", "D:1:4:27"]
            List<String> pkValues = parseSourceRefsPks(sourceRefs);
            if (pkValues.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "sourceRefs에서 PK를 추출할 수 없습니다.", "sourceRefs", sourceRefs));
            }

            result.put("pkValues", pkValues);

            // 원본 IF 테이블명 저장 (SND 특수 처리용)
            String originalIfTable = sourceTable;
            boolean isSndRelay = sourceTable != null && sourceTable.toLowerCase().startsWith("if_snd_");
            boolean isLoaderTarget = false;  // Loader TARGET → SOURCE 역추적 여부

            // sourceTable 파라미터가 IF 테이블명일 경우 SOURCE 테이블명으로 변환
            // 예: if_rsv_sec_obsvdata → sec_obsvdata_view, if_snd_sec_jewon → sec_jewon
            if (sourceTable != null && !sourceTable.isBlank()) {
                String lowerTable = sourceTable.toLowerCase();
                if (lowerTable.startsWith("if_rsv_") || lowerTable.startsWith("if_snd_")) {
                    // IF 테이블명에서 SOURCE 테이블명 추론
                    String baseName = lowerTable
                            .replaceFirst("^if_rsv_", "")
                            .replaceFirst("^if_snd_", "");

                    // SyncLog에서 해당 base를 포함하는 SOURCE 테이블 찾기
                    List<SyncLog> allLogs = syncLogRepository.findByExecutionId(executionId);
                    String finalBaseName = baseName;
                    sourceTable = allLogs.stream()
                            .filter(l -> "SOURCE".equals(l.getTableType()))
                            .filter(l -> l.getTableName() != null &&
                                        l.getTableName().toLowerCase().contains(finalBaseName))
                            .map(SyncLog::getTableName)
                            .findFirst()
                            .orElse(sourceTable);  // 못 찾으면 원본 유지
                    log.debug("Resolved sourceTable from IF table: {} -> {}", lowerTable, sourceTable);
                } else {
                    // Loader 대응: sourceTable이 TARGET 테이블(sec_jewon 등)인 경우
                    // sync_log SOURCE에서 해당 base를 포함하는 테이블로 변환
                    // sec_jewon → if_rsv_sec_jewon (SOURCE), source_refs로 매칭
                    List<SyncLog> allLogs = syncLogRepository.findByExecutionId(executionId);
                    String resolvedSource = allLogs.stream()
                            .filter(l -> "SOURCE".equals(l.getTableType()))
                            .filter(l -> l.getTableName() != null &&
                                        l.getTableName().toLowerCase().contains(lowerTable))
                            .map(SyncLog::getTableName)
                            .findFirst()
                            .orElse(null);
                    if (resolvedSource != null) {
                        log.debug("Loader trace: resolved TARGET {} -> SOURCE {}", sourceTable, resolvedSource);
                        sourceTable = resolvedSource;
                        isLoaderTarget = true;
                    }
                }
            }

            // sourceTable이 아직 없으면 syncLog에서 첫 번째 SOURCE 테이블 사용
            if (sourceTable == null || sourceTable.isBlank()) {
                List<SyncLog> allLogs = syncLogRepository.findByExecutionId(executionId);
                sourceTable = allLogs.stream()
                        .filter(l -> "SOURCE".equals(l.getTableType()))
                        .map(SyncLog::getTableName)
                        .findFirst()
                        .orElse(null);
            }

            if (sourceTable == null || sourceTable.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "source 테이블을 찾을 수 없습니다."));
            }

            result.put("sourceTableName", sourceTable);

            // Execution에서 sourceDatasourceId 조회 (/source 엔드포인트와 동일한 방식)
            Execution execution = executionService.getExecution(executionId).orElse(null);
            if (execution == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "실행 정보를 찾을 수 없습니다: " + executionId));
            }

            String datasourceId = execution.getSourceDatasourceId();
            if (datasourceId == null || datasourceId.isBlank()) {
                // 기본 source datasource로 대체
                datasourceId = dataSourceProvider.getSourceDatasourceId();
            }

            JdbcTemplate sourceJdbc;
            try {
                sourceJdbc = dataSourceProvider.getJdbcTemplate(datasourceId);
            } catch (Exception e) {
                // Orchestrator datasource ID가 Agent에 없을 수 있음 (재시작 후 캐시 소실)
                // trace-source는 소스 데이터 조회이므로 SOURCE 먼저 시도
                // - RCV: 외부 DB(SOURCE) → IF(TARGET) 순서
                // - Loader: SOURCE/TARGET 같은 DB이므로 어느 쪽이든 OK
                log.warn("DataSource '{}' not found, trying fallback datasources...", datasourceId);
                String fallbackId = null;
                try {
                    fallbackId = dataSourceProvider.getSourceDatasourceId();
                    sourceJdbc = dataSourceProvider.getJdbcTemplate(fallbackId);
                    datasourceId = fallbackId;
                    log.info("Using fallback SOURCE datasource: {} for trace-source", fallbackId);
                } catch (Exception e2) {
                    try {
                        fallbackId = dataSourceProvider.getTargetDatasourceId();
                        sourceJdbc = dataSourceProvider.getJdbcTemplate(fallbackId);
                        datasourceId = fallbackId;
                        log.info("Using fallback TARGET datasource: {} for trace-source", fallbackId);
                    } catch (Exception e3) {
                        log.error("All datasource fallbacks failed for '{}'", datasourceId);
                        return ResponseEntity.badRequest().body(Map.of("error", "DataSource를 찾을 수 없습니다: " + datasourceId));
                    }
                }
            }

            // 테이블명 해석 (대소문자 무시) - /source 엔드포인트와 동일한 방식
            String actualTableName = findActualTableName(sourceJdbc, sourceTable);
            if (actualTableName == null) {
                log.warn("Source table '{}' does not exist in datasource '{}'", sourceTable, datasourceId);
                return ResponseEntity.badRequest().body(Map.of("error", "Source 테이블이 존재하지 않습니다: " + sourceTable));
            }

            // DB 타입에 따라 식별자 인용 방식 결정
            String sourceDbType = dataSourceProvider.getDbType(datasourceId);
            String quotedTableName = qi(actualTableName, sourceDbType);

            // PK 컬럼 목록 감지 (JDBC 메타데이터 기반, 복합 PK 지원)
            List<String> pkColumns = findPkColumns(sourceJdbc, actualTableName, sourceDbType);
            log.debug("Using pkColumns: {} for table: {}", pkColumns, actualTableName);

            List<Map<String, Object>> sourceRecords = new ArrayList<>();
            for (String pk : pkValues) {
                try {
                    // 복합 PK 여부 확인: pk에 "|"가 포함되면 복합 PK
                    if (pk.contains("|") && pkColumns.size() > 1) {
                        // 복합 PK: "DJ-DJC-G1-0001|2026-02-19|00:00:00" → 각 컬럼에 매핑
                        String[] pkParts = pk.split("\\|");
                        if (pkParts.length != pkColumns.size()) {
                            log.warn("Composite PK part count mismatch: pk parts={}, columns={}",
                                    pkParts.length, pkColumns.size());
                            continue;
                        }

                        StringBuilder whereClause = new StringBuilder();
                        List<Object> params = new ArrayList<>();
                        for (int i = 0; i < pkColumns.size(); i++) {
                            if (i > 0) whereClause.append(" AND ");
                            whereClause.append(qi(pkColumns.get(i), sourceDbType)).append(" = ?");
                            params.add(typedValue(pkParts[i]));
                        }

                        String sql = String.format("SELECT * FROM %s WHERE %s", quotedTableName, whereClause);
                        log.debug("Executing composite PK trace-source query: {} with params={}", sql, params);

                        List<Map<String, Object>> records = sourceJdbc.queryForList(sql, params.toArray());
                        if (!records.isEmpty()) {
                            log.debug("Found {} records for composite pk={}", records.size(), pk);
                            sourceRecords.addAll(records);
                        }
                    } else {
                        // 단일 PK
                        String pkColumn = pkColumns.isEmpty() ? "id" : pkColumns.get(0);
                        Object pkTyped = typedValue(pk);

                        String sql = String.format("SELECT * FROM %s WHERE %s = ?", quotedTableName, qi(pkColumn, sourceDbType));
                        log.debug("Executing trace-source query: {} with pk={}", sql, pkTyped);

                        List<Map<String, Object>> records = sourceJdbc.queryForList(sql, pkTyped);
                        if (!records.isEmpty()) {
                            log.debug("Found {} records for pk={}", records.size(), pk);
                            sourceRecords.addAll(records);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to query source record for pk={}: {}", pk, e.getMessage());
                }
            }

            // Loader TARGET → SOURCE 역추적: source_refs 정확 매칭
            // PK 조회 실패 시 SOURCE 테이블(if_rsv)의 source_refs 컬럼에서 동일 값 검색
            // 정확 매칭(=) 우선, 실패 시 LIKE fallback (복합 PK 등)
            if (sourceRecords.isEmpty() && isLoaderTarget) {
                log.debug("Loader trace: PK lookup failed, trying source_refs exact match in {}", quotedTableName);
                try {
                    // 1차: source_refs 정확 매칭 (sourceRefs 원본 사용)
                    String refsSql = String.format(
                            "SELECT * FROM %s WHERE source_refs = ?", quotedTableName);
                    List<Map<String, Object>> records = sourceJdbc.queryForList(refsSql, sourceRefs);
                    if (!records.isEmpty()) {
                        log.debug("Loader trace: Found {} records via source_refs exact match", records.size());
                        sourceRecords.addAll(records);
                    }
                } catch (Exception e) {
                    log.warn("Loader trace: source_refs exact match failed: {}", e.getMessage());
                }

                // 2차: 정확 매칭 실패 시 LIKE fallback (복합 source_refs 등)
                if (sourceRecords.isEmpty()) {
                    for (String pk : pkValues) {
                        try {
                            String searchPattern = "%" + pk + "%";
                            String refsSql = String.format(
                                    "SELECT * FROM %s WHERE source_refs LIKE ?", quotedTableName);
                            List<Map<String, Object>> records = sourceJdbc.queryForList(refsSql, searchPattern);
                            if (!records.isEmpty()) {
                                log.debug("Loader trace: Found {} records via source_refs LIKE for pk={}", records.size(), pk);
                                sourceRecords.addAll(records);
                            }
                        } catch (Exception e) {
                            log.warn("Loader trace: source_refs LIKE search failed for pk={}: {}", pk, e.getMessage());
                        }
                    }
                }
            }

            // SND Relay 특수 처리: Source에서 못 찾으면 IF 테이블에서 비즈니스 키로 재조회
            if (sourceRecords.isEmpty() && isSndRelay && originalIfTable != null) {
                log.debug("SND Relay: Source lookup failed, trying business key fallback...");
                String sndPkColumn = pkColumns.isEmpty() ? "id" : pkColumns.get(0);
                sourceRecords = traceSourceBySndBusinessKey(sourceJdbc, quotedTableName, sndPkColumn,
                        originalIfTable, pkValues, executionId, sourceDbType);
            }

            result.put("sourceRecords", sourceRecords);
            result.put("sourceCount", sourceRecords.size());

            // 추적 상태
            String traceStatus = sourceRecords.isEmpty() ? "SOURCE_NOT_FOUND" : "FOUND";
            result.put("traceStatus", traceStatus);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to trace source for sourceRefs: {}", sourceRefs, e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }


    /**
     * SND Relay용: IF 테이블에서 유니크 키를 추출하여 Source 테이블 재조회
     * sourceRefs의 pk가 IF 테이블의 id인 경우를 처리
     */
    private List<Map<String, Object>> traceSourceBySndBusinessKey(
            JdbcTemplate sourceJdbc, String quotedSourceTable, String pkColumn,
            String ifTableName, List<String> pkValues, String executionId,
            String sourceDbType) {

        List<Map<String, Object>> result = new ArrayList<>();

        try {
            JdbcTemplate targetJdbc = dataSourceProvider.getJdbcTemplate(dataSourceProvider.getTargetDatasourceId());

            // IF 테이블의 유니크 키 컬럼 동적 조회
            List<String> ukColumns = getUniqueKeyColumns(targetJdbc, ifTableName);
            if (ukColumns.isEmpty()) {
                log.debug("No unique key columns found for IF table: {}", ifTableName);
                return result;
            }

            for (String pk : pkValues) {
                try {
                    Object pkTyped;
                    try {
                        pkTyped = Long.parseLong(pk);
                    } catch (NumberFormatException e) {
                        pkTyped = pk;
                    }

                    // IF 테이블에서 해당 레코드 조회
                    String ifSql = String.format("SELECT * FROM %s WHERE id = ?", ifTableName.toLowerCase());
                    List<Map<String, Object>> ifRecords = targetJdbc.queryForList(ifSql, pkTyped);

                    if (!ifRecords.isEmpty()) {
                        Map<String, Object> ifRecord = ifRecords.get(0);

                        // 유니크 키 값으로 Source WHERE 절 구성
                        StringBuilder whereClause = new StringBuilder();
                        List<Object> params = new ArrayList<>();
                        boolean allFound = true;

                        for (int i = 0; i < ukColumns.size(); i++) {
                            String ukCol = ukColumns.get(i);
                            Object value = findValueIgnoreCase(ifRecord, ukCol);
                            if (value == null) { allFound = false; break; }
                            if (i > 0) whereClause.append(" AND ");
                            whereClause.append(qi(ukCol, sourceDbType)).append(" = ?");
                            params.add(value);
                        }

                        if (allFound && !params.isEmpty()) {
                            String sourceSql = String.format(
                                    "SELECT * FROM %s WHERE %s", quotedSourceTable, whereClause);
                            List<Map<String, Object>> sourceRecords = sourceJdbc.queryForList(sourceSql, params.toArray());
                            if (!sourceRecords.isEmpty()) {
                                log.debug("Found source record via unique key {} from IF table", ukColumns);
                                result.addAll(sourceRecords);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to trace source via unique key for pk={}: {}", pk, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("SND unique key fallback failed: {}", e.getMessage());
        }

        return result;
    }

    /**
     * trace API용: Source 레코드의 유니크 키로 IF 테이블 검색
     * Source PK/source_refs 검색 실패 시 fallback으로 사용
     * IF 테이블의 유니크 제약 컬럼을 DB 메타데이터에서 동적으로 조회
     */
    private List<Map<String, Object>> traceByBusinessKey(
            JdbcTemplate targetJdbc, String sourceTable, String ifTableName,
            String pkColumn, Object pkValue, String executionId) {

        List<Map<String, Object>> result = new ArrayList<>();

        try {
            // IF 테이블의 유니크 키 컬럼 동적 조회 (PK 제외)
            List<String> ukColumns = getUniqueKeyColumns(targetJdbc, ifTableName);
            if (ukColumns.isEmpty()) {
                log.debug("No unique key columns found for IF table: {}", ifTableName);
                return result;
            }

            // Execution에서 sourceDatasourceId 조회
            Execution execution = executionService.getExecution(executionId).orElse(null);
            if (execution == null) {
                return result;
            }

            String datasourceId = execution.getSourceDatasourceId();
            if (datasourceId == null || datasourceId.isBlank()) {
                datasourceId = dataSourceProvider.getSourceDatasourceId();
            }

            JdbcTemplate sourceJdbc = dataSourceProvider.getJdbcTemplate(datasourceId);

            // Source 테이블 이름 해석
            String actualSourceTable = findActualTableName(sourceJdbc, sourceTable);
            if (actualSourceTable == null) {
                return result;
            }

            // Source 테이블에서 PK로 레코드 조회
            String srcDbType = dataSourceProvider.getDbType(datasourceId);
            String sourceSql = String.format(
                    "SELECT * FROM %s WHERE %s = ?",
                    qi(actualSourceTable, srcDbType), qi(pkColumn, srcDbType));
            List<Map<String, Object>> sourceRecords = sourceJdbc.queryForList(sourceSql, pkValue);

            if (sourceRecords.isEmpty()) {
                return result;
            }

            Map<String, Object> sourceRecord = sourceRecords.get(0);

            // 유니크 키 컬럼 값을 source record에서 추출하여 WHERE 절 구성
            StringBuilder whereClause = new StringBuilder();
            List<Object> params = new ArrayList<>();
            boolean allFound = true;

            for (int i = 0; i < ukColumns.size(); i++) {
                String ukCol = ukColumns.get(i);
                Object value = findValueIgnoreCase(sourceRecord, ukCol);
                if (value == null) { allFound = false; break; }
                if (i > 0) whereClause.append(" AND ");
                whereClause.append(ukCol).append(" = ?");
                params.add(value);
            }

            if (!allFound || params.isEmpty()) {
                log.debug("Could not extract all unique key values {} from source record", ukColumns);
                return result;
            }

            // IF 테이블에서 유니크 키로 검색 (execution_id 포함)
            List<Object> paramsWithExecId = new ArrayList<>(params);
            paramsWithExecId.add(executionId);
            String ifSql = String.format(
                    "SELECT * FROM %s WHERE %s AND execution_id = ?",
                    ifTableName.toLowerCase(), whereClause);
            result = targetJdbc.queryForList(ifSql, paramsWithExecId.toArray());

            // Fallback: execution_id 없이 재조회 (UPSERT 덮어쓰기 대응)
            if (result.isEmpty()) {
                String noFilterSql = String.format(
                        "SELECT * FROM %s WHERE %s",
                        ifTableName.toLowerCase(), whereClause);
                result = targetJdbc.queryForList(noFilterSql, params.toArray());
                if (!result.isEmpty()) {
                    log.debug("Found {} IF records via unique key (without execution_id filter)", result.size());
                }
            } else {
                log.debug("Found {} IF records via unique key {}", result.size(), ukColumns);
            }
        } catch (Exception e) {
            log.warn("Business key trace failed for sourceTable={}, pkValue={}: {}",
                    sourceTable, pkValue, e.getMessage());
        }

        return result;
    }

    /**
     * 테이블의 유니크 키 컬럼 조회 (PK 제외)
     * ON CONFLICT 대상이 되는 유니크 제약 조건의 컬럼들을 반환
     */
    private List<String> getUniqueKeyColumns(JdbcTemplate jdbcTemplate, String tableName) {
        try {
            // JDBC 표준 메타데이터로 유니크 인덱스 컬럼 조회 (DB 독립)
            java.sql.Connection conn = jdbcTemplate.getDataSource().getConnection();
            try {
                java.sql.DatabaseMetaData metaData = conn.getMetaData();
                String catalog = conn.getCatalog();

                // PK 컬럼 먼저 수집 (제외용)
                Set<String> pkCols = new HashSet<>();
                try (java.sql.ResultSet pkRs = metaData.getPrimaryKeys(catalog, null, tableName)) {
                    while (pkRs.next()) {
                        pkCols.add(pkRs.getString("COLUMN_NAME"));
                    }
                }

                // 유니크 인덱스 컬럼 조회 (non-PK)
                Map<String, List<String>> indexColumns = new LinkedHashMap<>();
                try (java.sql.ResultSet rs = metaData.getIndexInfo(catalog, null, tableName, true, false)) {
                    while (rs.next()) {
                        String indexName = rs.getString("INDEX_NAME");
                        String colName = rs.getString("COLUMN_NAME");
                        if (indexName == null || colName == null) continue;
                        // PK 인덱스 제외
                        if (pkCols.contains(colName) && indexColumns.getOrDefault(indexName, List.of()).isEmpty()) {
                            // PK만으로 구성된 인덱스는 건너뜀
                        }
                        indexColumns.computeIfAbsent(indexName, k -> new ArrayList<>()).add(colName);
                    }
                }

                // PK 컬럼만으로 구성된 인덱스 제외하고 첫 번째 유니크 인덱스 선택
                for (Map.Entry<String, List<String>> entry : indexColumns.entrySet()) {
                    List<String> cols = entry.getValue();
                    boolean allPk = cols.stream().allMatch(pkCols::contains);
                    if (!allPk) {
                        log.debug("Unique key columns for '{}': {} (index: {})", tableName, cols, entry.getKey());
                        return cols;
                    }
                }
            } finally {
                conn.close();
            }
        } catch (Exception e) {
            log.warn("Failed to get unique key columns for table '{}': {}", tableName, e.getMessage());
        }
        return List.of();
    }

    /**
     * Map에서 대소문자 무시하고 값 찾기
     */
    private Object findValueIgnoreCase(Map<String, Object> record, String key) {
        if (record.containsKey(key)) return record.get(key);
        for (Map.Entry<String, Object> entry : record.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) return entry.getValue();
        }
        return null;
    }

    /**
     * 테이블에서 PK 컬럼 목록 찾기 (JDBC DatabaseMetaData 활용)
     * SourceToIfStep.detectSourcePrimaryKey() 패턴 재사용
     * 복합 PK 지원: obsv_code, obsv_date, obsv_time 등
     */
    private List<String> findPkColumns(JdbcTemplate jdbcTemplate, String tableName, String dbType) {
        List<String> pkColumns = new ArrayList<>();
        try {
            java.sql.Connection conn = jdbcTemplate.getDataSource().getConnection();
            try {
                java.sql.DatabaseMetaData metaData = conn.getMetaData();
                String catalog = conn.getCatalog();
                String[] variants = {tableName, tableName.toLowerCase(), tableName.toUpperCase()};
                for (String variant : variants) {
                    try (java.sql.ResultSet rs = metaData.getPrimaryKeys(catalog, null, variant)) {
                        while (rs.next()) {
                            String colName = rs.getString("COLUMN_NAME");
                            int keySeq = rs.getInt("KEY_SEQ");
                            while (pkColumns.size() < keySeq) pkColumns.add(null);
                            pkColumns.set(keySeq - 1, colName);
                        }
                    }
                    if (!pkColumns.isEmpty()) {
                        pkColumns.removeIf(java.util.Objects::isNull);
                        break;
                    }
                }
            } finally {
                conn.close();
            }
        } catch (Exception e) {
            log.warn("Failed to detect PK from metadata for table: {}", tableName);
        }
        // fallback: 첫 번째 컬럼 사용
        if (pkColumns.isEmpty()) {
            try {
                String sql = String.format("SELECT * FROM %s LIMIT 1", qi(tableName, dbType));
                List<Map<String, Object>> sample = jdbcTemplate.queryForList(sql);
                if (!sample.isEmpty()) {
                    pkColumns.add(sample.get(0).keySet().iterator().next());
                }
            } catch (Exception e) {
                log.warn("Fallback PK detection also failed for table: {}", tableName);
            }
        }
        log.debug("Detected PK columns for '{}': {}", tableName, pkColumns);
        return pkColumns;
    }

    /**
     * sourceRefs에서 PK 값들 추출
     * 형식: ["E:1:4:26"] 또는 ["D:1:4:26", "D:1:4:27"] 또는 레거시 JSON
     */
    private List<String> parseSourceRefsPks(String sourceRefs) {
        List<String> pks = new ArrayList<>();
        if (sourceRefs == null || sourceRefs.isBlank()) {
            return pks;
        }

        try {
            // JSON 배열 파싱 시도
            if (sourceRefs.startsWith("[")) {
                // ["E:1:4:26"] 형식
                String content = sourceRefs.substring(1, sourceRefs.length() - 1);
                String[] refs = content.split(",");
                for (String ref : refs) {
                    String trimmed = ref.trim().replace("\"", "");
                    // E:datasourceId:tableId:pk 또는 D:datasourceId:tableId:pk
                    // limit=4 필수: pk에 ":"가 포함될 수 있음 (예: 시간값 00:00:00)
                    String[] parts = trimmed.split(":", 4);
                    if (parts.length >= 4) {
                        // tbId=0 경고 (소스 테이블 미등록 상태)
                        if ("0".equals(parts[2])) {
                            log.warn("sourceRef has tbId=0 (source table not registered): {}", trimmed);
                        }
                        pks.add(parts[3]); // pk는 4번째 이후 전체 (복합PK도 포함)
                    }
                }
            } else {
                // 단일 값이면 그대로 사용
                pks.add(sourceRefs);
            }
        } catch (Exception e) {
            log.warn("Failed to parse sourceRefs: {}", sourceRefs, e);
        }

        return pks;
    }

    // ==================== DB Dialect Helpers ====================

    private static boolean isMysql(String dbType) {
        return "MYSQL".equalsIgnoreCase(dbType) || "MARIADB".equalsIgnoreCase(dbType);
    }

    /** Quoted Identifier: MySQL → backtick, others → double-quote */
    private static String qi(String name, String dbType) {
        if (isMysql(dbType)) return "`" + name + "`";
        return "\"" + name + "\"";
    }

    /** JdbcTemplate으로부터 DB 타입을 추론 (JDBC URL 기반) */
    private static String detectDbType(JdbcTemplate jdbcTemplate) {
        try {
            java.sql.Connection conn = jdbcTemplate.getDataSource().getConnection();
            try {
                String url = conn.getMetaData().getURL();
                if (url != null) {
                    if (url.startsWith("jdbc:mysql")) return "MYSQL";
                    if (url.startsWith("jdbc:mariadb")) return "MARIADB";
                    if (url.startsWith("jdbc:oracle")) return "ORACLE";
                    if (url.startsWith("jdbc:postgresql")) return "POSTGRESQL";
                    if (url.startsWith("jdbc:sqlserver")) return "MSSQL";
                }
            } finally {
                conn.close();
            }
        } catch (Exception e) { /* ignore */ }
        return "POSTGRESQL"; // default
    }

    // ==================== Helper Methods ====================

    /**
     * executionId에서 agentCode 추출
     * 형식: {agentCode}_{uuid} → agentCode
     */
    private String extractAgentCode(String executionId) {
        if (executionId == null) return "";
        int lastUnderscore = executionId.lastIndexOf('_');
        return lastUnderscore > 0 ? executionId.substring(0, lastUnderscore) : executionId;
    }

    /**
     * PK 값 타입 변환
     * - 숫자면 Long
     * - yyyy-MM-dd 형식이면 java.sql.Date
     * - HH:mm:ss 형식이면 java.sql.Time
     * - 그 외 String
     */
    private Object typedValue(String value) {
        if (value == null) return null;
        // Long
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) { /* not a number */ }
        // Date (yyyy-MM-dd)
        if (value.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return java.sql.Date.valueOf(value);
        }
        // Time (HH:mm:ss)
        if (value.matches("\\d{2}:\\d{2}:\\d{2}")) {
            return java.sql.Time.valueOf(value);
        }
        return value;
    }

    private Map<String, Object> buildPageResult(String tableName, List<String> columns,
                                                 List<Map<String, Object>> data,
                                                 int totalCount, int page, int size) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tableName", tableName);
        result.put("columns", columns);
        result.put("data", data);
        result.put("totalCount", totalCount);
        result.put("page", page);
        result.put("size", size);
        result.put("totalPages", (int) Math.ceil((double) totalCount / size));
        result.put("hasNext", (page + 1) * size < totalCount);
        result.put("hasPrevious", page > 0);
        return result;
    }

    private Map<String, Object> buildEmptyPageResult(String tableName, int page, int size) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tableName", tableName);
        result.put("columns", List.of());
        result.put("data", List.of());
        result.put("totalCount", 0);
        result.put("page", page);
        result.put("size", size);
        result.put("totalPages", 0);
        result.put("hasNext", false);
        result.put("hasPrevious", false);
        return result;
    }

    private List<String> getTableColumns(JdbcTemplate jdbcTemplate, String tableName) {
        try {
            String dbType = detectDbType(jdbcTemplate);
            String sql = String.format("SELECT * FROM %s LIMIT 1", qi(tableName, dbType));
            List<Map<String, Object>> result = jdbcTemplate.queryForList(sql);
            if (!result.isEmpty()) {
                return new ArrayList<>(result.get(0).keySet());
            }
        } catch (Exception e) {
            log.warn("Failed to get columns for table: {}", tableName);
        }
        return List.of();
    }

    /**
     * 테이블에 특정 컬럼이 존재하는지 확인
     */
    private boolean hasColumn(JdbcTemplate jdbcTemplate, String tableName, String columnName) {
        return findActualColumnName(jdbcTemplate, tableName, columnName) != null;
    }

    /**
     * 검색 조건절 생성 (특정 컬럼 또는 전체 컬럼 검색)
     * @param jdbcTemplate DB 연결
     * @param tableName 테이블명
     * @param search 검색어
     * @param searchColumn 검색 컬럼 (null이면 전체 컬럼 검색)
     * @param params 쿼리 파라미터 리스트 (이 메서드에서 검색 파라미터 추가)
     * @return 검색 조건절 문자열 (비어있으면 검색 조건 없음)
     */
    private String buildSearchClause(JdbcTemplate jdbcTemplate, String tableName, String search, String searchColumn, List<Object> params) {
        if (search == null || search.isBlank()) {
            return "";
        }

        String dbType = detectDbType(jdbcTemplate);
        String castType = isMysql(dbType) ? "CHAR" : "TEXT";
        String likeOp = isMysql(dbType) ? "LIKE" : "ILIKE";

        // 특정 컬럼 검색
        if (searchColumn != null && !searchColumn.isBlank()) {
            String actualColumn = findActualColumnName(jdbcTemplate, tableName, searchColumn);
            if (actualColumn != null) {
                params.add("%" + search + "%");
                return " AND CAST(" + qi(actualColumn, dbType) + " AS " + castType + ") " + likeOp + " ?";
            }
            return "";
        }

        // 전체 컬럼 검색
        List<String> columns = getTableColumns(jdbcTemplate, tableName);
        if (columns.isEmpty()) {
            return "";
        }

        // 모든 컬럼에 대해 OR 조건 생성
        StringBuilder searchClause = new StringBuilder(" AND (");
        boolean first = true;
        for (String col : columns) {
            if (!first) {
                searchClause.append(" OR ");
            }
            searchClause.append("CAST(").append(qi(col, dbType)).append(" AS ").append(castType).append(") ").append(likeOp).append(" ?");
            params.add("%" + search + "%");
            first = false;
        }
        searchClause.append(")");

        return searchClause.toString();
    }

    /**
     * ORDER BY 절 생성
     * @param jdbcTemplate DB 연결
     * @param tableName 테이블명
     * @param sortColumn 정렬 컬럼 (null이면 정렬 안 함)
     * @param sortDirection 정렬 방향 (asc/desc)
     * @return ORDER BY 절 문자열 (비어있으면 정렬 안 함)
     */
    private String buildOrderByClause(JdbcTemplate jdbcTemplate, String tableName, String sortColumn, String sortDirection) {
        if (sortColumn == null || sortColumn.isBlank()) {
            return "";
        }

        // 실제 컬럼명 찾기 (대소문자 처리)
        String actualColumn = findActualColumnName(jdbcTemplate, tableName, sortColumn);
        if (actualColumn == null) {
            log.warn("Sort column '{}' not found in table '{}'", sortColumn, tableName);
            return "";
        }

        // 정렬 방향 검증
        String direction = "asc".equalsIgnoreCase(sortDirection) ? "ASC" : "DESC";
        String dbType = detectDbType(jdbcTemplate);

        return String.format("ORDER BY %s %s", qi(actualColumn, dbType), direction);
    }

    /**
     * 대소문자 무시하고 실제 컬럼명 찾기
     * PostgreSQL은 quoted 되지 않은 식별자를 소문자로 변환하므로, 실제 컬럼명을 찾아서 quoted 처리해야 함
     */
    private String findActualColumnName(JdbcTemplate jdbcTemplate, String tableName, String columnName) {
        try {
            String dbType = detectDbType(jdbcTemplate);
            String sql = String.format("SELECT * FROM %s LIMIT 1", qi(tableName, dbType));
            List<Map<String, Object>> result = jdbcTemplate.queryForList(sql);
            if (!result.isEmpty()) {
                // 대소문자 무시 비교로 실제 컬럼명 찾기
                return result.get(0).keySet().stream()
                        .filter(col -> col.equalsIgnoreCase(columnName))
                        .findFirst()
                        .orElse(null);
            }
        } catch (Exception e) {
            log.warn("Failed to find column {} for table: {}", columnName, tableName);
        }
        return null;
    }

    /**
     * 테이블이 존재하는지 확인
     */
    private boolean tableExists(JdbcTemplate jdbcTemplate, String tableName) {
        return findActualTableName(jdbcTemplate, tableName) != null;
    }

    /**
     * 대소문자 무시하고 실제 테이블명 찾기
     * PostgreSQL의 information_schema에서 테이블명을 조회하여 대소문자가 다른 테이블도 찾을 수 있음
     */
    private String findActualTableName(JdbcTemplate jdbcTemplate, String tableName) {
        String dbType = detectDbType(jdbcTemplate);
        try {
            // information_schema에서 대소문자 무시하고 테이블 찾기
            String schemaFilter = isMysql(dbType)
                    ? "TABLE_SCHEMA = DATABASE()"
                    : "table_schema = 'public'";
            String sql = "SELECT table_name FROM information_schema.tables " +
                        "WHERE " + schemaFilter + " AND LOWER(table_name) = LOWER(?)";
            List<String> tables = jdbcTemplate.queryForList(sql, String.class, tableName);
            if (!tables.isEmpty()) {
                String actualName = tables.get(0);
                if (!actualName.equals(tableName)) {
                    log.debug("Table name case mismatch: requested='{}', actual='{}'", tableName, actualName);
                }
                return actualName;
            }
        } catch (Exception e) {
            log.warn("Failed to find table name in information_schema: {}", e.getMessage());
            // 대체: 직접 SELECT 시도
            try {
                String sql = String.format("SELECT 1 FROM %s LIMIT 1", qi(tableName, dbType));
                jdbcTemplate.queryForList(sql);
                return tableName;
            } catch (Exception e2) {
                // 대문자로도 시도
                try {
                    String upperName = tableName.toUpperCase();
                    String sql = String.format("SELECT 1 FROM %s LIMIT 1", qi(upperName, dbType));
                    jdbcTemplate.queryForList(sql);
                    return upperName;
                } catch (Exception e3) {
                    // 테이블 없음
                }
            }
        }
        return null;
    }

    /**
     * 메시지가 포함된 빈 페이지 결과 생성
     */
    private Map<String, Object> buildEmptyPageResultWithMessage(String tableName, int page, int size, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("tableName", tableName);
        result.put("columns", List.of());
        result.put("data", List.of());
        result.put("totalCount", 0);
        result.put("page", page);
        result.put("size", size);
        result.put("totalPages", 0);
        result.put("hasNext", false);
        result.put("hasPrevious", false);
        result.put("message", message);
        return result;
    }
}
