package com.sync.agent.common.controller;

import com.sync.agent.common.dto.TableStatsDto;
import com.sync.agent.common.entity.Execution;
import com.sync.agent.common.entity.SyncLog;
import com.sync.agent.common.repository.SyncLogRepository;
import com.sync.agent.common.service.ExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Agent 로컬 DB의 실행 데이터를 조회하는 공통 REST API 컨트롤러
 *
 * Orchestrator가 프론트 모니터링/추적 화면에서 Agent 데이터를 조회할 때 사용.
 * 모든 Agent가 동일한 엔드포인트로 응답하도록 common 모듈에 표준화.
 *
 * ── 주요 엔드포인트 ──
 * GET /{executionId}          — 실행 상세 (상태, 시간, 오류)
 * GET /{executionId}/summary  — 실행 통계 (read/write/failed/skip)
 * GET /{executionId}/source   — Source 테이블 데이터 (페이징)
 * GET /{executionId}/target-if — IF 테이블 데이터
 * GET /{executionId}/target   — Target 테이블 데이터
 * GET /{executionId}/tables   — 매핑별 통계 (SyncLog 기반)
 * GET /{executionId}/trace    — Source PK → Target 추적 (forward)
 * GET /{executionId}/trace-source — Target → Source 역추적 (backward)
 *
 * ── Source 필터링 3단계 (buildSourceFilter) ──
 * execution_id는 항상 target에만 존재하므로, source 조회 시 source_refs 매칭이 필요.
 * 1. source에 source_refs 없음 → PK 파싱 매칭 (RCV — 외부 DB)
 * 2. source_refs 있고 target 값과 동일 → source_refs IN 매칭 (Loader — IF→Target 복사)
 * 3. source_refs 있지만 target 값과 다름 → PK 파싱 매칭 (SND — 새 source_refs 생성)
 * 판별: 샘플 1건 SELECT COUNT(*) WHERE source_refs = ? 체크
 *
 * ── 사용처 ──
 * - sync-agent-bojo-int, sync-proxy-dmz, sync-proxy-internal: 그대로 사용
 * - sync-agent-bojo: excludeFilters로 제외 (bojo는 Proxy 경유만 허용)
 *
 * Agent별 커스텀 구현이 필요한 경우 ComponentScan excludeFilters로 이 컨트롤러를 제외하고
 * 별도의 컨트롤러를 구현하세요.
 */
@Slf4j
@RestController
@RequestMapping("/api/execution-data")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "common.controller.execution-data.enabled", havingValue = "true")
public class ExecutionDataController {

    private final DataSourceProvider dataSourceProvider;
    private final SyncLogRepository syncLogRepository;
    private final ExecutionService executionService;

    /**
     * 실행 데이터 요약 (SyncLog 요약 기반)
     */
    @GetMapping("/{executionId}/summary")
    public ResponseEntity<Map<String, Object>> getSummary(@PathVariable String executionId) {
        // SyncLog에서 총 read/write/failed/skip 건수 합계
        Object result = syncLogRepository.sumCountsByExecutionId(executionId);

        long readCount = 0L;
        long writeCount = 0L;
        long failedCount = 0L;
        long skipCount = 0L;

        if (result != null) {
            Object[] sums;
            if (result instanceof Object[] arr) {
                if (arr.length > 0 && arr[0] instanceof Object[]) {
                    sums = (Object[]) arr[0];
                } else {
                    sums = arr;
                }
                readCount = sums.length > 0 && sums[0] != null ? ((Number) sums[0]).longValue() : 0L;
                writeCount = sums.length > 1 && sums[1] != null ? ((Number) sums[1]).longValue() : 0L;
                failedCount = sums.length > 2 && sums[2] != null ? ((Number) sums[2]).longValue() : 0L;
                skipCount = sums.length > 3 && sums[3] != null ? ((Number) sums[3]).longValue() : 0L;
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("executionId", executionId);
        summary.put("readCount", readCount);
        summary.put("writeCount", writeCount);
        summary.put("successCount", writeCount);  // 하위 호환
        summary.put("failedCount", failedCount);
        summary.put("skipCount", skipCount);
        summary.put("totalCount", writeCount + failedCount);

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
     * Source 테이블 데이터 조회 (해당 실행에서 추출된 데이터만)
     * IF 테이블의 source_refs에서 Source PK를 추출하여 필터링
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

            // target 테이블에서 이번 실행의 source_refs 수집 → source 필터링
            String targetDsId = execution.getTargetDatasourceId();
            SourceFilterResult sourceFilter = buildSourceFilter(executionId, tableName, targetDsId, sourceJdbc, actualTableName, srcDbType);

            StringBuilder whereClause = new StringBuilder("1=1");
            List<Object> params = new ArrayList<>();

            if (sourceFilter != null && "pk_batch".equals(sourceFilter.mode)) {
                // 대량 PK — 배치 분할 실행
                return executeBatchSourceQuery(sourceJdbc, actualTableName, quotedTableName,
                        srcDbType, sourceFilter.pkColumn, sourceFilter.allPks,
                        page, size, search, searchColumn, sortColumn, sortDirection);
            } else if (sourceFilter != null && !sourceFilter.isEmpty()) {
                whereClause.append(" AND ").append(sourceFilter.whereFragment);
                params.addAll(sourceFilter.params);
                log.debug("Source 필터: {}건 (executionId={}, mode={})", sourceFilter.params.size(), executionId, sourceFilter.mode);
            } else if (sourceFilter != null) {
                // 매핑은 있지만 해당 실행의 데이터가 없음
                return ResponseEntity.ok(buildPageResult(tableName, getTableColumns(sourceJdbc, actualTableName),
                        List.of(), 0, page, size));
            }
            // sourceFilter == null: 매핑을 찾지 못한 경우 → 전체 데이터 표시 (fallback)

            // 검색 조건 추가
            String searchClause = buildSearchClause(sourceJdbc, actualTableName, search, searchColumn, params);
            whereClause.append(searchClause);

            // 전체 건수 조회
            String countSql = String.format("SELECT COUNT(*) FROM %s WHERE %s", quotedTableName, whereClause);
            Integer totalCount = sourceJdbc.queryForObject(countSql, Integer.class, params.toArray());
            if (totalCount == null) totalCount = 0;

            // 정렬 처리
            String orderBy = buildOrderByClause(sourceJdbc, actualTableName, sortColumn, sortDirection);

            // 페이징된 데이터 조회
            String baseSql = String.format("SELECT * FROM %s WHERE %s %s", quotedTableName, whereClause, orderBy);
            String dataSql = pagingSql(baseSql, srcDbType, size, page * size);
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
     * Source 필터 결과 (WHERE 절 조각 + 파라미터)
     */
    private static class SourceFilterResult {
        final String whereFragment;
        final List<Object> params;
        final String mode; // "source_refs", "subquery", "pk", "pk_batch", "empty"
        final String pkColumn;       // pk_batch 모드용
        final List<Object> allPks;   // pk_batch 모드용 — 전체 PK 리스트

        SourceFilterResult(String whereFragment, List<Object> params, String mode) {
            this(whereFragment, params, mode, null, null);
        }

        SourceFilterResult(String whereFragment, List<Object> params, String mode,
                           String pkColumn, List<Object> allPks) {
            this.whereFragment = whereFragment;
            this.params = params;
            this.mode = mode;
            this.pkColumn = pkColumn;
            this.allPks = allPks;
        }

        boolean isEmpty() {
            return params.isEmpty() && (allPks == null || allPks.isEmpty());
        }
    }

    /**
     * SyncLog 매핑 기반으로 source 필터 조건 생성
     *
     * 1. SyncLog에서 sourceTable → targetTable 매핑 조회
     * 2. target WHERE execution_id = ? → source_refs 수집
     * 3. source에 source_refs 컬럼이 있으면 → source_refs IN (값) 매칭
     *    source에 source_refs 컬럼이 없으면 → id IN (PK 파싱) 매칭
     *
     * @return 필터 결과. 매핑을 찾지 못하면 null (전체 데이터 fallback)
     */
    private SourceFilterResult buildSourceFilter(String executionId, String sourceTableName,
                                                  String targetDatasourceId,
                                                  JdbcTemplate sourceJdbc, String actualSourceTable,
                                                  String srcDbType) {
        try {
            // SyncLog에서 source_tables에 요청된 sourceTable이 포함된 매핑 찾기
            List<SyncLog> syncLogs = syncLogRepository.findByExecutionId(executionId);
            SyncLog matchedMapping = syncLogs.stream()
                    .filter(l -> l.containsSourceTable(sourceTableName))
                    .findFirst()
                    .orElse(null);

            if (matchedMapping == null) {
                log.debug("Source '{}'에 매칭되는 매핑 없음 (executionId={})", sourceTableName, executionId);
                return null;
            }

            // target_tables에서 테이블명 추출
            List<String> targetTableNames = parseJsonArray(matchedMapping.getTargetTables());
            if (targetTableNames.isEmpty()) {
                log.debug("매핑 '{}'에 target 테이블 없음", matchedMapping.getMappingName());
                return null;
            }

            // target 테이블에서 source_refs 조회
            JdbcTemplate targetJdbc;
            try {
                String dsId = (targetDatasourceId != null) ? targetDatasourceId : dataSourceProvider.getTargetDatasourceId();
                targetJdbc = dataSourceProvider.getJdbcTemplate(dsId);
            } catch (Exception e) {
                log.warn("Target JdbcTemplate 획득 실패, 기본 target 시도: {}", e.getMessage());
                try {
                    targetJdbc = dataSourceProvider.getJdbcTemplate(dataSourceProvider.getTargetDatasourceId());
                } catch (Exception e2) {
                    log.warn("기본 Target JdbcTemplate도 실패: {}", e2.getMessage());
                    return null;
                }
            }

            // 모든 target 테이블에서 source_refs 원본값 수집
            Set<String> sourceRefsSet = new LinkedHashSet<>();
            for (String targetTable : targetTableNames) {
                try {
                    if (!tableExists(targetJdbc, targetTable)) continue;
                    String sql = String.format("SELECT source_refs FROM %s WHERE execution_id = ?", targetTable);
                    List<String> refsList = targetJdbc.queryForList(sql, String.class, executionId);
                    sourceRefsSet.addAll(refsList.stream().filter(r -> r != null && !r.isBlank()).toList());
                } catch (Exception e) {
                    log.debug("target 테이블 '{}' source_refs 조회 실패: {}", targetTable, e.getMessage());
                }
            }

            if (sourceRefsSet.isEmpty()) {
                log.info("Source 필터: target에서 source_refs 0건 (executionId={})", executionId);
                return new SourceFilterResult("", List.of(), "empty");
            }

            // source 테이블에 source_refs 컬럼이 있는지 확인
            boolean hasSourceRefs = hasColumn(sourceJdbc, actualSourceTable, "source_refs");

            if (hasSourceRefs) {
                // source_refs 값 매칭 시도 (Loader 패턴: IF → Target에서 source_refs 복사)
                // target의 source_refs 값이 source의 source_refs와 동일한지 샘플 1건으로 확인
                String sampleRef = sourceRefsSet.iterator().next();
                boolean sourceRefsMatch = false;
                try {
                    String checkSql = String.format("SELECT COUNT(*) FROM %s WHERE %s = ?",
                            qi(actualSourceTable, srcDbType), qi("source_refs", srcDbType));
                    Integer matchCount = sourceJdbc.queryForObject(checkSql, Integer.class, sampleRef);
                    sourceRefsMatch = (matchCount != null && matchCount > 0);
                } catch (Exception e) {
                    log.debug("source_refs 샘플 매칭 확인 실패: {}", e.getMessage());
                }

                if (sourceRefsMatch) {
                    // source_refs 값 동일 → source_refs IN 매칭 (Loader 패턴)
                    List<Object> params = new ArrayList<>(sourceRefsSet);
                    String placeholders = String.join(",", sourceRefsSet.stream().map(r -> "?").toList());
                    String fragment = qi("source_refs", srcDbType) + " IN (" + placeholders + ")";

                    log.info("Source 필터(source_refs): {} → {} ({}건, executionId={})",
                            sourceTableName, targetTableNames, sourceRefsSet.size(), executionId);
                    return new SourceFilterResult(fragment, params, "source_refs");
                }
                // source_refs 값이 다름 → PK 파싱으로 fallthrough (SND 패턴)
                log.debug("source_refs 값 불일치, PK 파싱으로 전환 (source={}, sampleRef={})", sourceTableName, sampleRef);
            }
            {
                // PK 파싱 매칭 (source_refs 불일치 또는 source에 source_refs 없음)

                // PK 컬럼 감지
                String actualPkCol = findActualColumnName(sourceJdbc, actualSourceTable, "id");
                if (actualPkCol == null) {
                    String fallbackPkCol = syncLogs.stream()
                            .filter(l -> l.containsSourceTable(sourceTableName))
                            .map(SyncLog::getSourcePkColumn)
                            .filter(Objects::nonNull)
                            .findFirst().orElse(null);
                    if (fallbackPkCol != null) {
                        actualPkCol = findActualColumnName(sourceJdbc, actualSourceTable, fallbackPkCol);
                    }
                }
                String pkColumn = qi(actualPkCol != null ? actualPkCol : "id", srcDbType);

                // 같은 DB + 소량이면 서브쿼리, 그 외(대량 또는 cross-DB)는 배치 분할
                Execution exec = executionService.getExecution(executionId).orElse(null);
                String execSourceDsId = (exec != null) ? exec.getSourceDatasourceId() : null;
                String execTargetDsId = (exec != null) ? exec.getTargetDatasourceId() : targetDatasourceId;
                boolean sameDb = execSourceDsId != null && execSourceDsId.equals(execTargetDsId);

                // 복합PK 감지: source_refs에서 파싱한 PK에 "|"가 포함되면 서브쿼리 불가 → 배치 모드로 전환
                boolean isCompositePk = sourceRefsSet.stream()
                        .flatMap(refs -> parseSourceRefsPks(refs).stream())
                        .anyMatch(pk -> pk.contains("|"));

                if (sameDb && !targetTableNames.isEmpty() && sourceRefsSet.size() <= 5000 && !isCompositePk) {
                    // 소량 + 같은 DB: 서브쿼리
                    String targetTable = targetTableNames.get(0);
                    String tgtDbType = dataSourceProvider.getDbType(execTargetDsId);

                    // PK 컬럼 타입 확인 → 숫자면 BIGINT/NUMBER, 아니면 TEXT 캐스팅
                    String rawPkCol = actualPkCol != null ? actualPkCol : "id";
                    boolean isNumericPk = isNumericPkColumn(sourceJdbc, actualSourceTable, rawPkCol, srcDbType);

                    String subquery;
                    if (isOracle(tgtDbType)) {
                        String cleaned = "REPLACE(REPLACE(REPLACE(source_refs, '[', ''), ']', ''), '\"', '')";
                        String castType = isNumericPk ? "NUMBER" : "VARCHAR2(200)";
                        subquery = String.format(
                            "SELECT CAST(SUBSTR(%s, INSTR(%s, ':', 1, 3) + 1) AS %s) " +
                            "FROM %s WHERE execution_id = ?", cleaned, cleaned, castType, targetTable);
                    } else {
                        String cleaned = "TRIM(BOTH '\"' FROM TRIM(BOTH '[]' FROM source_refs))";
                        String castType = isNumericPk ? "BIGINT" : "TEXT";
                        subquery = String.format(
                            "SELECT CAST(SPLIT_PART(%s, ':', 4) AS %s) " +
                            "FROM %s WHERE execution_id = ?", cleaned, castType, targetTable);
                    }
                    String fragment = pkColumn + " IN (" + subquery + ")";
                    log.info("Source 필터(서브쿼리): {} → {} ({}건, executionId={}, numericPk={})",
                            sourceTableName, targetTableNames, sourceRefsSet.size(), executionId, isNumericPk);
                    return new SourceFilterResult(fragment, List.of(executionId), "subquery");
                }

                // 대량 또는 cross-DB: PK 파싱 후 배치 분할
                Set<String> pkSet = new LinkedHashSet<>();
                for (String refs : sourceRefsSet) {
                    pkSet.addAll(parseSourceRefsPks(refs));
                }

                if (pkSet.isEmpty()) {
                    return new SourceFilterResult("", List.of(), "empty");
                }

                // 복합PK: 개별 (col1=? AND col2=?) OR (...) 생성
                if (isCompositePk) {
                    List<String> sourcePkCols = findPkColumns(sourceJdbc, actualSourceTable, srcDbType);
                    List<String> orClauses = new ArrayList<>();
                    List<Object> params = new ArrayList<>();
                    for (String pk : pkSet) {
                        String[] parts = pk.split("\\|");
                        if (parts.length != sourcePkCols.size()) continue;
                        List<String> andParts = new ArrayList<>();
                        for (int i = 0; i < sourcePkCols.size(); i++) {
                            andParts.add(qi(sourcePkCols.get(i), srcDbType) + " = ?");
                            boolean isNumeric = isNumericPkColumn(sourceJdbc, actualSourceTable, sourcePkCols.get(i), srcDbType);
                            params.add(isNumeric ? typedValue(parts[i]) : parts[i]);
                        }
                        orClauses.add("(" + String.join(" AND ", andParts) + ")");
                    }
                    if (orClauses.isEmpty()) {
                        return new SourceFilterResult("", List.of(), "empty");
                    }
                    String fragment = String.join(" OR ", orClauses);
                    log.info("Source 필터(복합PK): {} → {} ({}건, executionId={})",
                            sourceTableName, targetTableNames, pkSet.size(), executionId);
                    return new SourceFilterResult(fragment, params, "composite_pk");
                }

                List<Object> allPks = new ArrayList<>();
                for (String pk : pkSet) {
                    try {
                        allPks.add(Long.parseLong(pk));
                    } catch (NumberFormatException e) {
                        allPks.add(pk);
                    }
                }

                // 소량(<=1000)이면 단일 IN절, 대량이면 배치 모드
                if (allPks.size() <= 1000) {
                    String placeholders = String.join(",", allPks.stream().map(pk -> "?").toList());
                    String fragment = pkColumn + " IN (" + placeholders + ")";
                    log.info("Source 필터(PK): {} → {} ({}건, executionId={})",
                            sourceTableName, targetTableNames, allPks.size(), executionId);
                    return new SourceFilterResult(fragment, allPks, "pk");
                }

                log.info("Source 필터(PK배치): {} → {} ({}건, executionId={})",
                        sourceTableName, targetTableNames, allPks.size(), executionId);
                return new SourceFilterResult("", List.of(), "pk_batch", pkColumn, allPks);
            }
        } catch (Exception e) {
            log.warn("Source 필터 생성 실패 (fallback to 전체): {}", e.getMessage());
            return null;
        }
    }

    /**
     * SyncLog의 sourceTables/targetTables에 테이블명이 포함되어 있는지 확인
     */
    private boolean containsTable(SyncLog syncLog, String tableName) {
        return parseJsonArray(syncLog.getSourceTables()).stream()
                .anyMatch(t -> t.equalsIgnoreCase(tableName))
                || parseJsonArray(syncLog.getTargetTables()).stream()
                .anyMatch(t -> t.equalsIgnoreCase(tableName));
    }

    /**
     * JSON 배열 문자열 파싱 (간이)
     * 예: ["if_rsv_sec_obsvdata","pm_gd970201"] → List.of("if_rsv_sec_obsvdata", "pm_gd970201")
     */
    private List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank()) return List.of();
        List<String> result = new ArrayList<>();
        // 간단한 파싱: 따옴표 사이 문자열 추출
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"([^\"]+)\"").matcher(json);
        while (m.find()) {
            result.add(m.group(1));
        }
        return result;
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

            // 상태 필터 적용
            if (status != null && !status.isBlank()) {
                whereClause.append(" AND link_status = ?");
                params.add(status);
            }

            // 현재 조건(검색+상태 필터) 기반 성공/실패 건수
            String currentWhere = whereClause.toString();
            Object[] currentParams = params.toArray();
            Integer successCount = targetJdbc.queryForObject(
                    String.format("SELECT COUNT(*) FROM %s WHERE %s AND link_status = 'SUCCESS'", tableName, currentWhere),
                    Integer.class, currentParams);
            Integer failedCount = targetJdbc.queryForObject(
                    String.format("SELECT COUNT(*) FROM %s WHERE %s AND link_status = 'FAILED'", tableName, currentWhere),
                    Integer.class, currentParams);

            // 전체 건수 조회
            String countSql = String.format("SELECT COUNT(*) FROM %s WHERE %s", tableName, whereClause);
            Integer totalCount = targetJdbc.queryForObject(countSql, Integer.class, params.toArray());
            if (totalCount == null) totalCount = 0;

            // 정렬 처리
            String orderBy = buildOrderByClause(targetJdbc, tableName, sortColumn, sortDirection);

            // 페이징된 데이터 조회
            String tgtDbType = detectDbType(targetJdbc);
            String baseSql = String.format("SELECT * FROM %s WHERE %s %s", tableName, whereClause, orderBy);
            String dataSql = pagingSql(baseSql, tgtDbType, size, page * size);
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
            @RequestParam(required = false) String status,
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

            // 상태 필터 적용
            boolean hasLinkStatus = hasColumn(targetJdbc, tableName, "link_status");
            if (status != null && !status.isBlank() && hasLinkStatus) {
                whereClause.append(" AND link_status = ?");
                params.add(status);
            }

            // 현재 조건(검색+상태 필터) 기반 성공/실패 건수
            Integer successCount = null;
            Integer failedCount = null;
            if (hasLinkStatus) {
                String currentWhere = whereClause.toString();
                Object[] currentParams = params.toArray();
                successCount = targetJdbc.queryForObject(
                        String.format("SELECT COUNT(*) FROM %s WHERE %s AND link_status = 'SUCCESS'", tableName, currentWhere),
                        Integer.class, currentParams);
                failedCount = targetJdbc.queryForObject(
                        String.format("SELECT COUNT(*) FROM %s WHERE %s AND link_status = 'FAILED'", tableName, currentWhere),
                        Integer.class, currentParams);
            }

            // 전체 건수 조회
            String countSql = String.format("SELECT COUNT(*) FROM %s WHERE %s", tableName, whereClause);
            Integer totalCount = targetJdbc.queryForObject(countSql, Integer.class, params.toArray());
            if (totalCount == null) totalCount = 0;

            // 정렬 처리
            String orderBy = buildOrderByClause(targetJdbc, tableName, sortColumn, sortDirection);

            // 페이징된 데이터 조회
            String tgtDbType2 = detectDbType(targetJdbc);
            String baseSql2 = String.format("SELECT * FROM %s WHERE %s %s", tableName, whereClause, orderBy);
            String dataSql = pagingSql(baseSql2, tgtDbType2, size, page * size);
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
                .map(syncLog -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("mappingName", syncLog.getMappingName());
                    item.put("sourceTables", syncLog.getSourceTables());
                    item.put("targetTables", syncLog.getTargetTables());
                    item.put("failedCount", syncLog.getFailedCount());
                    item.put("failedKeys", syncLog.getFailedKeys());
                    item.put("errorSummary", syncLog.getErrorSummary());
                    return item;
                })
                .toList();

        return ResponseEntity.ok(result);
    }

    /**
     * 매핑별 통계 조회 (SyncLog 매핑 단위)
     */
    @GetMapping("/{executionId}/tables")
    public ResponseEntity<List<TableStatsDto>> getTableStats(@PathVariable String executionId) {
        List<SyncLog> syncLogs = syncLogRepository.findByExecutionId(executionId);

        List<TableStatsDto> tableStats = new ArrayList<>();
        for (SyncLog syncLog : syncLogs) {
            String mappingName = syncLog.getMappingName();
            if (mappingName == null || mappingName.isBlank()) {
                continue;
            }

            tableStats.add(TableStatsDto.builder()
                    .mappingName(mappingName)
                    .sourceTables(parseJsonArray(syncLog.getSourceTables()))
                    .targetTables(parseJsonArray(syncLog.getTargetTables()))
                    .readCount(syncLog.getReadCount() != null ? syncLog.getReadCount() : 0L)
                    .writeCount(syncLog.getWriteCount() != null ? syncLog.getWriteCount() : 0L)
                    .failedCount(syncLog.getFailedCount() != null ? syncLog.getFailedCount() : 0L)
                    .skipCount(syncLog.getSkipCount() != null ? syncLog.getSkipCount() : 0L)
                    .build());
        }

        return ResponseEntity.ok(tableStats);
    }

    /**
     * 특정 매핑의 처리 로그 조회
     * mappingName 또는 테이블명으로 검색 (Orchestrator는 테이블명을 보내므로 fallback 필요)
     */
    @GetMapping("/{executionId}/tables/{name}")
    public ResponseEntity<SyncLog> getTableLog(
            @PathVariable String executionId,
            @PathVariable String name) {
        // 1차: mappingName 매칭
        Optional<SyncLog> byMapping = syncLogRepository.findByExecutionIdAndMappingName(executionId, name);
        if (byMapping.isPresent()) return ResponseEntity.ok(byMapping.get());

        // 2차: sourceTables/targetTables JSON에서 테이블명 매칭
        return syncLogRepository.findByExecutionId(executionId).stream()
                .filter(l -> containsTable(l, name))
                .findFirst()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 특정 매핑의 실패 정보 조회
     * mappingName 또는 테이블명으로 검색
     */
    @GetMapping("/{executionId}/tables/{name}/failed")
    public ResponseEntity<Map<String, Object>> getTableFailedInfo(
            @PathVariable String executionId,
            @PathVariable String name) {
        // 1차: mappingName 매칭
        Optional<SyncLog> byMapping = syncLogRepository.findByExecutionIdAndMappingName(executionId, name);
        SyncLog syncLog = byMapping.orElseGet(() ->
                // 2차: 테이블명 매칭
                syncLogRepository.findByExecutionId(executionId).stream()
                        .filter(l -> containsTable(l, name))
                        .findFirst()
                        .orElse(null));

        if (syncLog == null || syncLog.getFailedCount() == null || syncLog.getFailedCount() <= 0) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mappingName", syncLog.getMappingName());
        result.put("failedCount", syncLog.getFailedCount());
        result.put("failedKeys", syncLog.getFailedKeys());
        result.put("errorSummary", syncLog.getErrorSummary());
        return ResponseEntity.ok(result);
    }

    /**
     * Source PK로 데이터 추적 (Source → Target)
     * Orchestrator가 Agent의 테이블 매핑 정보를 기반으로 targetTable(구 ifTableName)을 자동 해석
     *
     * 3단계 source_refs 기반 forward trace:
     * Step 1: source_refs에 PK가 임베딩된 경우 (RCV, SND, Internal)
     * Step 2: source_refs 값 일치 — Loader 복사 패턴
     * Step 3: 직접 PK fallback (최후 수단)
     */
    @GetMapping("/{executionId}/trace")
    public ResponseEntity<Map<String, Object>> traceBySourcePk(
            @PathVariable String executionId,
            @RequestParam String pkValue,
            @RequestParam(defaultValue = "id") String pkColumn,
            @RequestParam(required = false) String sourceTable,
            @RequestParam(required = false) String ifTableName) {

        try {
            if (sourceTable == null || sourceTable.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "sourceTable 파라미터가 필요합니다."));
            }

            // Execution에서 targetDatasourceId 조회
            Execution execution = executionService.getExecution(executionId).orElse(null);
            String targetDsId = (execution != null && execution.getTargetDatasourceId() != null)
                    ? execution.getTargetDatasourceId()
                    : dataSourceProvider.getTargetDatasourceId();
            JdbcTemplate targetJdbc = dataSourceProvider.getJdbcTemplate(targetDsId);

            // SyncLog에서 모든 매핑의 target 테이블 목록 추출
            List<SyncLog> allLogs = syncLogRepository.findByExecutionId(executionId);
            List<String> targetTables = allLogs.stream()
                    .map(SyncLog::getTargetTables)
                    .filter(Objects::nonNull)
                    .flatMap(json -> parseJsonArray(json).stream())
                    .distinct()
                    .toList();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("pkColumn", pkColumn);
            result.put("pkValue", pkValue);
            result.put("executionId", executionId);

            List<Map<String, Object>> targetRecords = new ArrayList<>();
            String matchedTable = null;

            // ─── Step 1: source_refs에 PK가 임베딩된 경우 ───
            // RCV, SND, Internal RCV, Internal Loader에서 target이 source_refs를 새로 생성
            for (String candidateTable : targetTables) {
                try {
                    // Pattern A (정밀): sourceTable:pkValue 패턴
                    String patternA = "%:" + sourceTable + ":" + pkValue + "\"]";
                    String sqlA = String.format(
                            "SELECT * FROM %s WHERE source_refs LIKE ? AND execution_id = ?",
                            candidateTable);
                    List<Map<String, Object>> rows = targetJdbc.queryForList(sqlA, patternA, executionId);
                    if (!rows.isEmpty()) {
                        targetRecords.addAll(rows);
                        matchedTable = candidateTable;
                        log.debug("Step 1A matched: {} -> {} ({} rows)", sourceTable, candidateTable, rows.size());
                        break;
                    }

                    // Pattern B (범용): pkValue로 검색
                    String patternB = "%:" + pkValue + "\"]";
                    String sqlB = String.format(
                            "SELECT * FROM %s WHERE source_refs LIKE ? AND execution_id = ?",
                            candidateTable);
                    rows = targetJdbc.queryForList(sqlB, patternB, executionId);
                    if (!rows.isEmpty()) {
                        targetRecords.addAll(rows);
                        matchedTable = candidateTable;
                        log.debug("Step 1B matched: {} -> {} ({} rows)", sourceTable, candidateTable, rows.size());
                        break;
                    }
                } catch (Exception e) {
                    log.debug("Step 1 failed for {}: {}", candidateTable, e.getMessage());
                }
            }

            // ─── Step 2: source_refs 값 일치 (Loader 복사 패턴) ───
            // Loader는 IF의 source_refs를 target에 그대로 복사
            // source 테이블은 source datasource에 있으므로 sourceJdbc 사용
            if (targetRecords.isEmpty()) {
                try {
                    // source datasource에서 source 테이블 조회
                    String sourceDsId = (execution != null && execution.getSourceDatasourceId() != null)
                            ? execution.getSourceDatasourceId() : null;
                    JdbcTemplate sourceJdbc2 = null;
                    if (sourceDsId != null) {
                        try {
                            sourceJdbc2 = dataSourceProvider.getJdbcTemplate(sourceDsId);
                        } catch (Exception e) {
                            log.debug("Source datasource '{}' not found for trace Step 2, falling back to target", sourceDsId);
                        }
                    }
                    // source datasource에서 먼저 시도, 없으면 target에서 시도
                    JdbcTemplate sourceReadJdbc = sourceJdbc2 != null ? sourceJdbc2 : targetJdbc;

                    String actualSourceTable = findActualTableName(sourceReadJdbc, sourceTable);
                    if (actualSourceTable == null && sourceJdbc2 != null) {
                        // source datasource에 없으면 target에서 재시도
                        sourceReadJdbc = targetJdbc;
                        actualSourceTable = findActualTableName(targetJdbc, sourceTable);
                    }
                    if (actualSourceTable != null) {
                        Object pkTyped = typedValue(pkValue);
                        String srcDbType = detectDbType(sourceReadJdbc);
                        List<String> srcPkCols = findPkColumns(sourceReadJdbc, actualSourceTable, srcDbType);
                        String srcPkCol = srcPkCols.isEmpty() ? pkColumn : srcPkCols.get(0);

                        String readSql = String.format("SELECT source_refs FROM %s WHERE %s = ?",
                                qi(actualSourceTable, srcDbType), qi(srcPkCol, srcDbType));
                        List<Map<String, Object>> srcRows = sourceReadJdbc.queryForList(readSql, pkTyped);

                        if (!srcRows.isEmpty()) {
                            Object refsObj = findValueIgnoreCase(srcRows.get(0), "source_refs");
                            if (refsObj != null) {
                                String refsValue = refsObj.toString();
                                log.debug("Step 2: source_refs from {} = {}", actualSourceTable, refsValue);

                                for (String candidateTable : targetTables) {
                                    if (candidateTable.equalsIgnoreCase(actualSourceTable)) continue;
                                    try {
                                        String sql = String.format(
                                                "SELECT * FROM %s WHERE source_refs = ? AND execution_id = ?",
                                                candidateTable);
                                        List<Map<String, Object>> rows =
                                                targetJdbc.queryForList(sql, refsValue, executionId);
                                        if (!rows.isEmpty()) {
                                            targetRecords.addAll(rows);
                                            matchedTable = candidateTable;
                                            log.debug("Step 2 matched: {} -> {} ({} rows)",
                                                    actualSourceTable, candidateTable, rows.size());
                                            break;
                                        }
                                    } catch (Exception e) {
                                        log.debug("Step 2 failed for {}: {}", candidateTable, e.getMessage());
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.debug("Step 2 source read failed: {}", e.getMessage());
                }
            }

            // ─── Step 3: 직접 PK fallback (최후 수단) ───
            if (targetRecords.isEmpty()) {
                Object pkTyped = typedValue(pkValue);
                for (String candidateTable : targetTables) {
                    try {
                        String sql = String.format(
                                "SELECT * FROM %s WHERE %s = ? AND execution_id = ?",
                                candidateTable, pkColumn);
                        List<Map<String, Object>> rows = targetJdbc.queryForList(sql, pkTyped, executionId);
                        if (!rows.isEmpty()) {
                            targetRecords.addAll(rows);
                            matchedTable = candidateTable;
                            log.debug("Step 3 matched: {} ({} rows)", candidateTable, rows.size());
                            break;
                        }
                    } catch (Exception e) {
                        log.debug("Step 3 failed for {} (column '{}' may not exist): {}",
                                candidateTable, pkColumn, e.getMessage());
                    }
                }
            }

            result.put("targetTableName", matchedTable != null ? matchedTable : "");
            result.put("targetRecords", targetRecords);
            result.put("targetCount", targetRecords.size());
            result.put("traceStatus", targetRecords.isEmpty() ? "NOT_SYNCED" : "SYNCED");

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
                    // 정확 매칭 우선, 없으면 contains 매칭
                    List<String> allSourceTables = allLogs.stream()
                            .map(SyncLog::getSourceTables)
                            .filter(Objects::nonNull)
                            .flatMap(json -> parseJsonArray(json).stream())
                            .toList();
                    sourceTable = allSourceTables.stream()
                            .filter(t -> t.toLowerCase().equals(finalBaseName))
                            .findFirst()
                            .or(() -> allSourceTables.stream()
                                    .filter(t -> t.toLowerCase().contains(finalBaseName))
                                    .findFirst())
                            .orElse(sourceTable);  // 못 찾으면 원본 유지
                    log.debug("Resolved sourceTable from IF table: {} -> {}", lowerTable, sourceTable);
                } else {
                    // Loader 대응: sourceTable이 TARGET 테이블(sec_jewon 등)인 경우
                    // sync_log SOURCE에서 해당 base를 포함하는 테이블로 변환
                    // sec_jewon → if_rsv_sec_jewon (SOURCE), source_refs로 매칭
                    List<SyncLog> allLogs = syncLogRepository.findByExecutionId(executionId);
                    String resolvedSource = allLogs.stream()
                            .map(SyncLog::getSourceTables)
                            .filter(Objects::nonNull)
                            .flatMap(json -> parseJsonArray(json).stream())
                            .filter(t -> t.toLowerCase().contains(lowerTable))
                            .findFirst()
                            .orElse(null);
                    if (resolvedSource != null) {
                        log.debug("Loader trace: resolved TARGET {} -> SOURCE {}", sourceTable, resolvedSource);
                        sourceTable = resolvedSource;
                        isLoaderTarget = true;
                    } else {
                        // 이름 매칭 실패 시 source_refs에서 직접 테이블명 추출
                        // I:dsId:tableName:pk 형식 (Internal) → tableName 사용
                        String refsTable = parseSourceRefsTableName(sourceRefs);
                        if (refsTable != null) {
                            log.debug("Resolved sourceTable from source_refs: {} -> {}", sourceTable, refsTable);
                            sourceTable = refsTable;
                            isLoaderTarget = true;
                        }
                    }
                }
            }

            // sourceTable이 아직 없으면 syncLog에서 첫 번째 SOURCE 테이블 사용
            if (sourceTable == null || sourceTable.isBlank()) {
                List<SyncLog> allLogs = syncLogRepository.findByExecutionId(executionId);
                sourceTable = allLogs.stream()
                        .map(SyncLog::getSourceTables)
                        .filter(Objects::nonNull)
                        .flatMap(json -> parseJsonArray(json).stream())
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
                            boolean isNumeric = isNumericPkColumn(sourceJdbc, actualTableName, pkColumns.get(i), sourceDbType);
                            params.add(isNumeric ? typedValue(pkParts[i]) : pkParts[i]);
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
                String sndTargetDsId = (execution != null && execution.getTargetDatasourceId() != null)
                        ? execution.getTargetDatasourceId()
                        : dataSourceProvider.getTargetDatasourceId();
                sourceRecords = traceSourceBySndBusinessKey(sourceJdbc, quotedTableName, sndPkColumn,
                        originalIfTable, pkValues, executionId, sourceDbType, sndTargetDsId);
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
            String sourceDbType, String targetDatasourceId) {

        List<Map<String, Object>> result = new ArrayList<>();

        try {
            JdbcTemplate targetJdbc = dataSourceProvider.getJdbcTemplate(targetDatasourceId);

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
                String sql = limit1Sql(String.format("SELECT * FROM %s", qi(tableName, dbType)), dbType);
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

    /**
     * source_refs에서 테이블명 추출
     * I:dsId:tableName:pk 형식 (Internal) → tableName이 실제 테이블명
     * D/E:dsId:tableId:pk 형식 (External) → tableId가 숫자이므로 null 반환
     */
    private String parseSourceRefsTableName(String sourceRefs) {
        if (sourceRefs == null || sourceRefs.isBlank()) return null;
        try {
            String content = sourceRefs.startsWith("[")
                    ? sourceRefs.substring(1, sourceRefs.length() - 1)
                    : sourceRefs;
            String trimmed = content.split(",")[0].trim().replace("\"", "");
            String[] parts = trimmed.split(":", 4);
            if (parts.length >= 4 && "I".equals(parts[0])) {
                // Internal 형식: tableName이 숫자가 아닌 실제 테이블명
                String tableName = parts[2];
                try {
                    Long.parseLong(tableName);
                    return null; // 숫자면 tableId → 해석 불가
                } catch (NumberFormatException e) {
                    return tableName; // 문자열이면 실제 테이블명
                }
            }
        } catch (Exception e) {
            log.debug("Failed to parse table name from sourceRefs: {}", sourceRefs);
        }
        return null;
    }

    // ==================== DB Dialect Helpers ====================

    private static boolean isMysql(String dbType) {
        return "MYSQL".equalsIgnoreCase(dbType) || "MARIADB".equalsIgnoreCase(dbType);
    }

    private static boolean isOracle(String dbType) {
        return "ORACLE".equalsIgnoreCase(dbType) || "TIBERO".equalsIgnoreCase(dbType);
    }

    /** PK 컬럼이 숫자 타입인지 확인 (source_refs 서브쿼리 캐스팅용) */
    private boolean isNumericPkColumn(JdbcTemplate jdbc, String tableName, String pkColumn, String dbType) {
        try {
            String sql;
            if (isOracle(dbType)) {
                sql = String.format(
                    "SELECT DATA_TYPE FROM USER_TAB_COLUMNS WHERE TABLE_NAME = UPPER('%s') AND COLUMN_NAME = UPPER('%s')",
                    tableName, pkColumn);
            } else {
                sql = String.format(
                    "SELECT data_type FROM information_schema.columns WHERE table_name = '%s' AND column_name = '%s'",
                    tableName.toLowerCase(), pkColumn.toLowerCase());
            }
            String dataType = jdbc.queryForObject(sql, String.class);
            if (dataType == null) return true;
            dataType = dataType.toUpperCase();
            return dataType.contains("INT") || dataType.contains("NUMBER") || dataType.contains("NUMERIC")
                    || dataType.contains("SERIAL") || dataType.contains("DECIMAL");
        } catch (Exception e) {
            log.debug("PK 타입 확인 실패 (기본값 숫자): table={}, pk={}, error={}", tableName, pkColumn, e.getMessage());
            return true;
        }
    }

    /** Oracle 호환 페이징: PG/MySQL=LIMIT+OFFSET, Oracle=OFFSET+FETCH */
    private static String pagingSql(String baseSql, String dbType, int limit, int offset) {
        if (isOracle(dbType)) {
            return baseSql + " OFFSET " + offset + " ROWS FETCH FIRST " + limit + " ROWS ONLY";
        }
        return baseSql + " LIMIT " + limit + " OFFSET " + offset;
    }

    /** Oracle 호환 LIMIT 1: PG/MySQL=LIMIT 1, Oracle=FETCH FIRST 1 ROWS ONLY */
    private static String limit1Sql(String baseSql, String dbType) {
        if (isOracle(dbType)) {
            return baseSql + " FETCH FIRST 1 ROWS ONLY";
        }
        return baseSql + " LIMIT 1";
    }

    /** Quoted Identifier: MySQL → backtick, others → double-quote */
    private static String qi(String name, String dbType) {
        if (isMysql(dbType)) return "`" + name + "`";
        if (isOracle(dbType)) return name;  // Oracle: 인용 없이 (자동 대문자)
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

    /**
     * 대량 PK 배치 분할 실행 — COUNT와 데이터를 1000건씩 나눠서 실행 후 합산
     */
    private ResponseEntity<Map<String, Object>> executeBatchSourceQuery(
            JdbcTemplate sourceJdbc, String actualTableName, String quotedTableName,
            String dbType, String pkColumn, List<Object> allPks,
            int page, int size, String search, String searchColumn,
            String sortColumn, String sortDirection) {

        int batchSize = 1000;
        int totalCount = 0;

        // 1. 배치 COUNT 합산
        for (int i = 0; i < allPks.size(); i += batchSize) {
            List<Object> batch = allPks.subList(i, Math.min(i + batchSize, allPks.size()));
            String placeholders = String.join(",", batch.stream().map(pk -> "?").toList());
            String countSql = String.format("SELECT COUNT(*) FROM %s WHERE %s IN (%s)",
                    quotedTableName, pkColumn, placeholders);
            Integer cnt = sourceJdbc.queryForObject(countSql, Integer.class, batch.toArray());
            if (cnt != null) totalCount += cnt;
        }

        // 2. 페이징된 데이터 조회 — 정렬 후 skip/take
        String orderBy = buildOrderByClause(sourceJdbc, actualTableName, sortColumn, sortDirection);
        int offset = page * size;
        int remaining = size;
        int skipped = 0;
        List<Map<String, Object>> pageData = new ArrayList<>();

        for (int i = 0; i < allPks.size() && remaining > 0; i += batchSize) {
            List<Object> batch = allPks.subList(i, Math.min(i + batchSize, allPks.size()));
            String placeholders = String.join(",", batch.stream().map(pk -> "?").toList());

            // 이 배치의 건수 확인 (skip 최적화)
            String batchCountSql = String.format("SELECT COUNT(*) FROM %s WHERE %s IN (%s)",
                    quotedTableName, pkColumn, placeholders);
            Integer batchCount = sourceJdbc.queryForObject(batchCountSql, Integer.class, batch.toArray());
            if (batchCount == null) batchCount = 0;

            if (skipped + batchCount <= offset) {
                // 이 배치는 전부 skip
                skipped += batchCount;
                continue;
            }

            // 이 배치에서 데이터 가져오기
            int batchOffset = Math.max(0, offset - skipped);
            String baseSql = String.format("SELECT * FROM %s WHERE %s IN (%s) %s",
                    quotedTableName, pkColumn, placeholders, orderBy);
            String dataSql = pagingSql(baseSql, dbType, remaining, batchOffset);
            List<Map<String, Object>> batchData = sourceJdbc.queryForList(dataSql, batch.toArray());
            pageData.addAll(batchData);
            remaining -= batchData.size();
            skipped += batchCount;
        }

        List<String> columns = pageData.isEmpty()
                ? getTableColumns(sourceJdbc, actualTableName)
                : new ArrayList<>(pageData.get(0).keySet());

        return ResponseEntity.ok(buildPageResult(actualTableName, columns, pageData, totalCount, page, size));
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
            String sql = limit1Sql(String.format("SELECT * FROM %s", qi(tableName, dbType)), dbType);
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
            String sql = limit1Sql(String.format("SELECT * FROM %s", qi(tableName, dbType)), dbType);
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
            // 테이블 카탈로그에서 대소문자 무시하고 테이블 찾기
            String sql;
            if (isOracle(dbType)) {
                sql = "SELECT table_name FROM user_tables WHERE LOWER(table_name) = LOWER(?)";
            } else {
                String schemaFilter = isMysql(dbType)
                        ? "TABLE_SCHEMA = DATABASE()"
                        : "table_schema = 'public'";
                sql = "SELECT table_name FROM information_schema.tables " +
                        "WHERE " + schemaFilter + " AND LOWER(table_name) = LOWER(?)";
            }
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
                String sql = limit1Sql(String.format("SELECT 1 FROM %s", qi(tableName, dbType)), dbType);
                jdbcTemplate.queryForList(sql);
                return tableName;
            } catch (Exception e2) {
                // 대문자로도 시도
                try {
                    String upperName = tableName.toUpperCase();
                    String sql = limit1Sql(String.format("SELECT 1 FROM %s", qi(upperName, dbType)), dbType);
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
