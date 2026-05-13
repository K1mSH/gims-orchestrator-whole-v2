package com.infolink.agent.common.controller;

import com.infolink.agent.common.dto.TableStatsDto;
import com.infolink.agent.common.entity.Execution;
import com.infolink.agent.common.entity.SyncLog;
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
 * GET /{executionId}/target   — Target 테이블 데이터 (IF 포함 — 구 /target-if 통합)
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
 * - infolink-agent-bojo-internal, infolink-proxy-dmz, infolink-proxy-internal: 그대로 사용
 * - infolink-agent-bojo-dmz: excludeFilters로 제외 (bojo는 Proxy 경유만 허용)
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

    /**
     * 실행 데이터 요약 (SyncLog 요약 기반)
     */
    @GetMapping("/{executionId}/summary")
    public ResponseEntity<Map<String, Object>> getSummary(
            @PathVariable String executionId,
            @RequestHeader(value = "X-Manage-Datasource-Id", required = false) String manageDsId) {
        JdbcTemplate mgmtJdbc = dataSourceProvider.getJdbcTemplate(manageDsId);

        long[] sums = ExecutionDataReader.sumCountsByExecutionId(mgmtJdbc, executionId);
        long readCount = sums[0];
        long writeCount = sums[1];
        long failedCount = sums[2];
        long skipCount = sums[3];

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
    public ResponseEntity<?> getExecution(
            @PathVariable String executionId,
            @RequestHeader(value = "X-Manage-Datasource-Id", required = false) String manageDsId) {
        JdbcTemplate mgmtJdbc = dataSourceProvider.getJdbcTemplate(manageDsId);
        return ExecutionDataReader.findExecutionById(mgmtJdbc, executionId)
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
    public ResponseEntity<List<Execution>> getRecentExecutions(
            @RequestHeader(value = "X-Manage-Datasource-Id", required = false) String manageDsId) {
        JdbcTemplate mgmtJdbc = dataSourceProvider.getJdbcTemplate(manageDsId);
        return ResponseEntity.ok(ExecutionDataReader.findRecentExecutions(mgmtJdbc, 10));
    }

    /**
     * 전체 실행 목록 조회
     */
    @GetMapping("")
    public ResponseEntity<List<Execution>> getAllExecutions(
            @RequestHeader(value = "X-Manage-Datasource-Id", required = false) String manageDsId) {
        JdbcTemplate mgmtJdbc = dataSourceProvider.getJdbcTemplate(manageDsId);
        return ResponseEntity.ok(ExecutionDataReader.findAllExecutions(mgmtJdbc));
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
            @RequestParam(defaultValue = "asc") String sortDirection,
            @RequestHeader(value = "X-Manage-Datasource-Id", required = false) String manageDsId) {

        try {
            // tableName 필수 체크
            if (tableName == null || tableName.isBlank()) {
                return ResponseEntity.ok(buildEmptyPageResultWithMessage("unknown", page, size,
                        "테이블명이 지정되지 않았습니다. tableName 파라미터를 전달해주세요."));
            }

            JdbcTemplate mgmtJdbc = dataSourceProvider.getJdbcTemplate(manageDsId);

            // Execution에서 sourceDatasourceId 조회
            Execution execution = ExecutionDataReader.findExecutionById(mgmtJdbc, executionId).orElse(null);
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
            SourceFilterResult sourceFilter = buildSourceFilter(mgmtJdbc, executionId, tableName, targetDsId, sourceJdbc, actualTableName, srcDbType);

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
                Map<String, Object> emptyRes = buildPageResult(tableName, getTableColumns(sourceJdbc, actualTableName),
                        List.of(), 0, page, size);
                emptyRes.put("pkColumns", findPkColumns(sourceJdbc, actualTableName, srcDbType));
                return ResponseEntity.ok(emptyRes);
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

            Map<String, Object> pageRes = buildPageResult(tableName, columns, data, totalCount, page, size);
            pageRes.put("pkColumns", findPkColumns(sourceJdbc, actualTableName, srcDbType));
            return ResponseEntity.ok(pageRes);
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
    private SourceFilterResult buildSourceFilter(JdbcTemplate mgmtJdbc, String executionId, String sourceTableName,
                                                  String targetDatasourceId,
                                                  JdbcTemplate sourceJdbc, String actualSourceTable,
                                                  String srcDbType) {
        try {
            // SyncLog에서 source_tables에 요청된 sourceTable이 포함된 매핑 찾기 (정확매칭)
            List<SyncLog> syncLogs = ExecutionDataReader.findSyncLogsByExecutionId(mgmtJdbc, executionId);
            SyncLog matchedMapping = syncLogs.stream()
                    .filter(l -> containsSourceTable(l, sourceTableName))
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
                            .filter(l -> containsSourceTable(l, sourceTableName))
                            .map(SyncLog::getSourcePkColumn)
                            .filter(Objects::nonNull)
                            .findFirst().orElse(null);
                    if (fallbackPkCol != null) {
                        actualPkCol = findActualColumnName(sourceJdbc, actualSourceTable, fallbackPkCol);
                    }
                }
                String pkColumn = qi(actualPkCol != null ? actualPkCol : "id", srcDbType);

                // 같은 DB + 소량이면 서브쿼리, 그 외(대량 또는 cross-DB)는 배치 분할
                Execution exec = ExecutionDataReader.findExecutionById(mgmtJdbc, executionId).orElse(null);
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
     * SyncLog 의 source_tables / target_tables JSON 에 테이블명이 정확매칭(equalsIgnoreCase) 으로 포함되는지.
     * 정의 진실원 매칭만 — substring/contains 금지 (cf. feedback_trace_definition_only).
     */
    private boolean containsTable(SyncLog syncLog, String tableName) {
        return containsSourceTable(syncLog, tableName) || containsTargetTable(syncLog, tableName);
    }

    /** source_tables 만 정확매칭. */
    private boolean containsSourceTable(SyncLog syncLog, String tableName) {
        return parseJsonArray(syncLog.getSourceTables()).stream()
                .anyMatch(t -> t.equalsIgnoreCase(tableName));
    }

    /** target_tables 만 정확매칭. */
    private boolean containsTargetTable(SyncLog syncLog, String tableName) {
        return parseJsonArray(syncLog.getTargetTables()).stream()
                .anyMatch(t -> t.equalsIgnoreCase(tableName));
    }

    /**
     * JSON 배열 문자열 파싱 (간이)
     * 예: ["if_rsv_sec_obsvdata","pm_gd970201"] → List.of("if_rsv_sec_obsvdata", "pm_gd970201")
     */
    private List<String> parseJsonArray(String json) {
        if (json == null || json.isBlank()) return List.of();
        List<String> result = new ArrayList<>();
        try {
            // JSON 배열 파싱: [{"name":"RGETNPMMS01"}] 또는 ["sec_jewon"]
            com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
            if (root.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode node : root) {
                    if (node.isObject() && node.has("name")) {
                        result.add(node.get("name").asText());
                    } else if (node.isTextual()) {
                        result.add(node.asText());
                    }
                }
            }
        } catch (Exception e) {
            // fallback: 정규식으로 "name":"값" 패턴 추출
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
            while (m.find()) {
                result.add(m.group(1));
            }
            // 매칭 없으면 기존 방식 (단순 문자열 배열)
            if (result.isEmpty()) {
                m = java.util.regex.Pattern.compile("\"([^\"]+)\"").matcher(json);
                while (m.find()) {
                    result.add(m.group(1));
                }
            }
        }
        return result;
    }

    /**
     * Per-target count 메타 파싱.
     * sync_log.target_tables JSON 이 [{"name":"...","count":N}] 형식일 때 Map 반환.
     * 단순 문자열 배열 ["..."] 또는 count 없는 객체일 때 null 반환 (TableStatsDto.targetCounts 도 null).
     */
    private Map<String, Long> parseTargetCounts(String json) {
        if (json == null || json.isBlank()) return null;
        Map<String, Long> result = new LinkedHashMap<>();
        try {
            com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
            if (root.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode node : root) {
                    if (node.isObject() && node.has("name") && node.has("count")) {
                        result.put(node.get("name").asText(), node.get("count").asLong());
                    }
                }
            }
        } catch (Exception e) {
            // 파싱 실패 시 null 반환 (frontend 가 fallback 으로 mapping write 사용)
            return null;
        }
        return result.isEmpty() ? null : result;
    }

    /**
     * Target 테이블 데이터 조회 (구 /target-if와 통합)
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
            @RequestParam(defaultValue = "asc") String sortDirection,
            @RequestHeader(value = "X-Manage-Datasource-Id", required = false) String manageDsId) {

        try {
            // tableName 필수 체크
            if (tableName == null || tableName.isBlank()) {
                return ResponseEntity.ok(buildEmptyPageResultWithMessage("unknown", page, size,
                        "테이블명이 지정되지 않았습니다. tableName 파라미터를 전달해주세요."));
            }

            JdbcTemplate mgmtJdbc = dataSourceProvider.getJdbcTemplate(manageDsId);

            // Execution에서 targetDatasourceId 조회
            Execution execution = ExecutionDataReader.findExecutionById(mgmtJdbc, executionId).orElse(null);
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
    public ResponseEntity<List<Map<String, Object>>> getFailedRecords(
            @PathVariable String executionId,
            @RequestHeader(value = "X-Manage-Datasource-Id", required = false) String manageDsId) {
        JdbcTemplate mgmtJdbc = dataSourceProvider.getJdbcTemplate(manageDsId);
        List<SyncLog> failedLogs = ExecutionDataReader.findFailedSyncLogsByExecutionId(mgmtJdbc, executionId);

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
    public ResponseEntity<List<TableStatsDto>> getTableStats(
            @PathVariable String executionId,
            @RequestHeader(value = "X-Manage-Datasource-Id", required = false) String manageDsId) {
        JdbcTemplate mgmtJdbc = dataSourceProvider.getJdbcTemplate(manageDsId);
        List<SyncLog> syncLogs = ExecutionDataReader.findSyncLogsByExecutionId(mgmtJdbc, executionId);

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
                    .targetCounts(parseTargetCounts(syncLog.getTargetTables()))
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
            @PathVariable String name,
            @RequestHeader(value = "X-Manage-Datasource-Id", required = false) String manageDsId) {
        JdbcTemplate mgmtJdbc = dataSourceProvider.getJdbcTemplate(manageDsId);
        // 1차: mappingName 매칭
        Optional<SyncLog> byMapping = ExecutionDataReader.findByExecutionIdAndMappingName(mgmtJdbc, executionId, name);
        if (byMapping.isPresent()) return ResponseEntity.ok(byMapping.get());

        // 2차: sourceTables/targetTables JSON에서 테이블명 매칭
        return ExecutionDataReader.findSyncLogsByExecutionId(mgmtJdbc, executionId).stream()
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
            @PathVariable String name,
            @RequestHeader(value = "X-Manage-Datasource-Id", required = false) String manageDsId) {
        JdbcTemplate mgmtJdbc = dataSourceProvider.getJdbcTemplate(manageDsId);
        // 1차: mappingName 매칭
        Optional<SyncLog> byMapping = ExecutionDataReader.findByExecutionIdAndMappingName(mgmtJdbc, executionId, name);
        SyncLog syncLog = byMapping.orElseGet(() ->
                // 2차: 테이블명 매칭
                ExecutionDataReader.findSyncLogsByExecutionId(mgmtJdbc, executionId).stream()
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
     * Source 행 → Target 행 정방향 추적.
     * **정의기반 정확 동등성 매칭만** — LIKE/contains/substring 일체 없음 (cf. feedback_trace_definition_only).
     *
     * backend (orchestrator) 가 `SourceRefUtils.buildJson` 과 동일 로직으로 exactSourceRefs 를 빌드해서 전달.
     * 우리 모든 writer (`SourceToTargetStep`/`LoaderStepHelper`/jeju·use·simple·internal 커스텀 step/`SaeolLinkPlanSndStep`)
     * 가 같은 함수로 source_refs 를 작성하므로, exactSourceRefs == target.source_refs 가 보장됨.
     *
     * Proxy 동작:
     *   1. sync_log 매핑 중 source_tables 에 sourceTable 정확매칭 → 그 매핑의 target_tables 가 candidates
     *   2. for candidate: `WHERE source_refs = ? AND execution_id = ?` (exactSourceRefs)
     *   3. 매칭 0 = NOT_SYNCED (메시지에 exactSourceRefs + 시도한 candidates 노출)
     */
    @GetMapping("/{executionId}/trace")
    public ResponseEntity<Map<String, Object>> traceBySourcePk(
            @PathVariable String executionId,
            @RequestParam String exactSourceRefs,
            @RequestParam(required = false) String sourceTable,
            // 진단·표시용 (결정 로직엔 안 쓰임)
            @RequestParam(required = false) String pkValue,
            @RequestParam(required = false) String pkColumn,
            @RequestHeader(value = "X-Manage-Datasource-Id", required = false) String manageDsId) {

        try {
            if (sourceTable == null || sourceTable.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "sourceTable 파라미터가 필요합니다."));
            }
            if (exactSourceRefs == null || exactSourceRefs.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "exactSourceRefs 파라미터가 필요합니다 (backend 에서 빌드 — 정의기반 매칭용)."));
            }

            JdbcTemplate mgmtJdbc = dataSourceProvider.getJdbcTemplate(manageDsId);
            Execution execution = ExecutionDataReader.findExecutionById(mgmtJdbc, executionId).orElse(null);
            String targetDsId = (execution != null && execution.getTargetDatasourceId() != null)
                    ? execution.getTargetDatasourceId()
                    : dataSourceProvider.getTargetDatasourceId();
            JdbcTemplate targetJdbc = dataSourceProvider.getJdbcTemplate(targetDsId);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("pkColumn", pkColumn);
            result.put("pkValue", pkValue);
            result.put("executionId", executionId);
            result.put("exactSourceRefs", exactSourceRefs);

            // 1. sync_log 매핑 중 source_tables 에 sourceTable 정확매칭 → candidates = 그 매핑들의 target_tables
            List<SyncLog> allLogs = ExecutionDataReader.findSyncLogsByExecutionId(mgmtJdbc, executionId);
            List<String> candidates = allLogs.stream()
                    .filter(s -> containsSourceTable(s, sourceTable))
                    .map(SyncLog::getTargetTables)
                    .filter(Objects::nonNull)
                    .flatMap(json -> parseJsonArray(json).stream())
                    .distinct()
                    .toList();

            if (candidates.isEmpty()) {
                result.put("targetTableName", "");
                result.put("targetRecords", List.of());
                result.put("targetCount", 0);
                result.put("traceStatus", "NOT_SYNCED");
                result.put("error", "sync_log 에 source_tables=" + sourceTable + " 매핑 없음 (이 실행에서 추적 불가)");
                return ResponseEntity.ok(result);
            }

            // 2. for candidate: WHERE source_refs = ? AND execution_id = ?  (정확 동등성)
            List<Map<String, Object>> targetRecords = new ArrayList<>();
            String matchedTable = null;
            for (String candidateTable : candidates) {
                try {
                    String sql = String.format(
                            "SELECT * FROM %s WHERE source_refs = ? AND execution_id = ?",
                            candidateTable);
                    List<Map<String, Object>> rows = targetJdbc.queryForList(sql, exactSourceRefs, executionId);
                    if (!rows.isEmpty()) {
                        targetRecords.addAll(rows);
                        matchedTable = candidateTable;
                        log.debug("trace matched: {} -> {} ({} rows, exactRef={})",
                                sourceTable, candidateTable, rows.size(), exactSourceRefs);
                        break;
                    }
                } catch (Exception e) {
                    log.debug("trace query failed for {}: {}", candidateTable, e.getMessage());
                }
            }

            result.put("targetTableName", matchedTable != null ? matchedTable : "");
            result.put("targetRecords", targetRecords);
            result.put("targetCount", targetRecords.size());
            result.put("traceStatus", targetRecords.isEmpty() ? "NOT_SYNCED" : "SYNCED");
            if (targetRecords.isEmpty()) {
                result.put("triedCandidates", candidates);
            }

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Failed to trace data for sourceTable={}, exactSourceRefs={}", sourceTable, exactSourceRefs, e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 클릭한 TARGET/IF 테이블명 → 실제 SOURCE 테이블명 변환 (역추적 진입 단계).
     * **이름 정의 기반 정확매칭만** — prefix/contains/baseName 휴리스틱 없음 (cf. feedback_trace_definition_only).
     *
     * 진실원 우선순위:
     *   ① sync_log.target_tables 에 clickedTable 정확매칭(equalsIgnoreCase) → 그 매핑의 source_tables[0]
     *   ② source_refs.tableId → datasource_table.id 정확조회 → table_name
     *      (mgmtJdbc 가 orchestrator DB 일 때만 동작. proxy 의 mgmtJdbc 는 보통 자기 datasource 라 null 반환)
     *   ③ 옛 `I:dsId:tableName:pk` 형식 source_refs 에서 박힌 tableName 직접 추출
     *   ④ clickedTable 이 빈/null 일 때 — sync_log 첫 매핑의 source_tables[0] (단일매핑 케이스)
     *   ⑤ 다 실패 → null (호출부가 명확한 에러 처리)
     */
    private String resolveTraceSourceTable(JdbcTemplate mgmtJdbc, String executionId, String clickedTable, String sourceRefs) {
        List<SyncLog> allLogs = ExecutionDataReader.findSyncLogsByExecutionId(mgmtJdbc, executionId);

        if (clickedTable != null && !clickedTable.isBlank()) {
            // ① target_tables 정확매칭
            for (SyncLog s : allLogs) {
                if (containsTargetTable(s, clickedTable)) {
                    List<String> srcs = parseJsonArray(s.getSourceTables());
                    if (!srcs.isEmpty()) {
                        log.debug("trace-source: matched target_tables → source '{}' for clicked '{}'", srcs.get(0), clickedTable);
                        return srcs.get(0);
                    }
                }
            }
        }

        // ② source_refs.tableId 조회 (orchestrator DB 가 mgmtJdbc 일 때만 동작)
        String byId = resolveSourceTableByRefsTableId(mgmtJdbc, sourceRefs);
        if (byId != null) {
            log.debug("trace-source: matched source_refs.tableId → '{}'", byId);
            return byId;
        }

        // ③ 옛 `I:dsId:tableName:pk` 형식 호환 — source_refs 에 박힌 tableName 직접
        String byRefsName = parseSourceRefsTableName(sourceRefs);
        if (byRefsName != null) {
            log.debug("trace-source: matched source_refs.tableName(legacy I:* format) → '{}'", byRefsName);
            return byRefsName;
        }

        // ④ clickedTable 없는 케이스 (sync_log 첫 매핑 source 사용)
        if (clickedTable == null || clickedTable.isBlank()) {
            return allLogs.stream()
                    .map(SyncLog::getSourceTables).filter(Objects::nonNull)
                    .flatMap(j -> parseJsonArray(j).stream())
                    .findFirst().orElse(null);
        }

        return null;
    }

    /**
     * Target/IF 행 클릭 시 원본 SOURCE 레코드 역추적 (sourceRefs 기반).
     * 모든 매칭은 **이름 정의 기반** 만 — sync_log target_tables 정확매칭 / source_refs.tableId 조회 / source_refs 박힌 값.
     * prefix 검사·차감, contains/substring, baseName 추출 같은 휴리스틱 없음 (cf. feedback_trace_definition_only).
     * sourceRefs 형식: ["E:dsId:tableId:pk"] / ["D:dsId:tableId:pk"] (표준) 또는 ["I:dsId:tableName:pk"] (옛 형식 호환).
     */
    @GetMapping("/{executionId}/trace-source")
    public ResponseEntity<Map<String, Object>> traceToSource(
            @PathVariable String executionId,
            @RequestParam String sourceRefs,
            @RequestParam(required = false) String sourceTable,
            @RequestHeader(value = "X-Manage-Datasource-Id", required = false) String manageDsId) {

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("executionId", executionId);
        result.put("sourceRefs", sourceRefs);

        try {
            JdbcTemplate mgmtJdbc = dataSourceProvider.getJdbcTemplate(manageDsId);

            // sourceRefs 파싱: ["E:1:4:26"] 또는 ["D:1:4:26", "D:1:4:27"]
            List<String> pkValues = parseSourceRefsPks(sourceRefs);
            if (pkValues.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "sourceRefs에서 PK를 추출할 수 없습니다.", "sourceRefs", sourceRefs));
            }

            result.put("pkValues", pkValues);

            // sourceTable 파라미터(= 사용자가 클릭한 TARGET/IF 화면 테이블) → 실제 SOURCE 테이블명 변환.
            // **원칙: 이름 정의 기반만** (cf. feedback_trace_definition_only) — prefix/contains/baseName 휴리스틱 일체 금지.
            String clickedTable = sourceTable;
            sourceTable = resolveTraceSourceTable(mgmtJdbc, executionId, sourceTable, sourceRefs);

            if (sourceTable == null || sourceTable.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "역추적 매핑 해석 실패: 입력 TARGET/IF 테이블 '" + clickedTable
                                + "' 이 이 실행(executionId=" + executionId + ") 의 sync_log target_tables 어디에도 정확매칭되지 않고, "
                                + "source_refs.tableId 도 해석 불가. 매핑이 sync_log 에 누락됐거나 입력 테이블명이 정의와 다릅니다.",
                        "executionId", executionId,
                        "input", clickedTable == null ? "" : clickedTable,
                        "sourceRefs", sourceRefs));
            }

            result.put("sourceTableName", sourceTable);

            // Execution에서 sourceDatasourceId 조회 (/source 엔드포인트와 동일한 방식)
            Execution execution = ExecutionDataReader.findExecutionById(mgmtJdbc, executionId).orElse(null);
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
                        // 단일 PK — 컬럼 실제 타입에 따라 typedValue 적용 여부 결정
                        // (예: VARCHAR 컬럼에 "0000000001" 같은 zero-padded 문자열이 저장된 경우
                        //  무조건 Long.parseLong 하면 leading zero 손실 → 매칭 실패)
                        // 복합 PK 분기와 동일하게 isNumericPkColumn 으로 분기
                        String pkColumn = pkColumns.isEmpty() ? "id" : pkColumns.get(0);
                        boolean isNumericPk = isNumericPkColumn(sourceJdbc, actualTableName, pkColumn, sourceDbType);
                        Object pkTyped = isNumericPk ? typedValue(pk) : pk;

                        String sql = String.format("SELECT * FROM %s WHERE %s = ?", quotedTableName, qi(pkColumn, sourceDbType));
                        log.debug("Executing trace-source query: {} with pk={} (isNumericPk={})", sql, pkTyped, isNumericPk);

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

            // Universal fallback: Loader passthrough 패턴 — source 행의 source_refs 가 target 행의 source_refs 와 동일한 케이스.
            // 정의 동등성 (`source_refs = ?`) 만 시도. LIKE/substring 안 함 (휴리스틱 금지 — cf. feedback_trace_definition_only).
            // PK 매칭(앞 본 흐름) 이 실패해도 이 매칭이 잡으면 OK.
            if (sourceRecords.isEmpty()) {
                try {
                    String refsSql = String.format(
                            "SELECT * FROM %s WHERE source_refs = ?", quotedTableName);
                    List<Map<String, Object>> records = sourceJdbc.queryForList(refsSql, sourceRefs);
                    if (!records.isEmpty()) {
                        log.debug("trace-source: matched via source_refs equality in {} ({} rows)", quotedTableName, records.size());
                        sourceRecords.addAll(records);
                    }
                } catch (Exception e) {
                    log.debug("trace-source: source_refs equality query failed (column may not exist): {}", e.getMessage());
                }
            }

            // 휴리스틱 fallback (LIKE %pk%, SND Relay business-key 재조회 등) 제거됨 — cf. feedback_trace_definition_only.
            // 여기서도 못 찾으면 진실원에 매핑이 없는 것 = SOURCE_NOT_FOUND 로 그대로 반환 (사용자에게 명확히 노출).

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
     * SourceToTargetStep.detectSourcePrimaryKey() 패턴 재사용
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

    // (제거됨) splitCsv / queryByCompositePkTokens — 정방향 추적이 LIKE 토큰매칭 대신
    // backend 에서 빌드한 exactSourceRefs 로 `=` 정확 동등성 매칭만 수행 (cf. feedback_trace_definition_only).

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
                // 단일 값: zone:dsId:tbId:pk 형식일 수 있음
                String[] parts = sourceRefs.split(":", 4);
                if (parts.length >= 4) {
                    pks.add(parts[3]); // pk 부분만 추출
                } else {
                    pks.add(sourceRefs); // 파싱 불가하면 그대로 사용
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse sourceRefs: {}", sourceRefs, e);
        }

        return pks;
    }

    /**
     * source_refs에서 tableId 추출 (datasource_table.id).
     * D/E:dsId:tableId:pk 형식 → tableId(Long). I: 형식이거나 숫자 아니면 null.
     */
    private Long parseSourceRefsTableId(String sourceRefs) {
        if (sourceRefs == null || sourceRefs.isBlank()) return null;
        try {
            String content = sourceRefs.startsWith("[")
                    ? sourceRefs.substring(1, sourceRefs.length() - 1)
                    : sourceRefs;
            String trimmed = content.split(",")[0].trim().replace("\"", "");
            String[] parts = trimmed.split(":", 4);
            if (parts.length >= 4 && ("D".equals(parts[0]) || "E".equals(parts[0]))) {
                try { return Long.parseLong(parts[2]); } catch (NumberFormatException e) { return null; }
            }
        } catch (Exception e) {
            log.debug("Failed to parse tableId from sourceRefs: {}", sourceRefs);
        }
        return null;
    }

    /**
     * source_refs 의 tableId 로 그 소스 테이블명을 관리 DB(datasource_table) 에서 정확히 조회.
     * 추적 시 IF 테이블명에서 접두사 떼고 substring/contains 로 추측하던 것을 대체 — tableId 가 그 테이블을 유일하게 특정함.
     * tableId 가 없거나(0/숫자아님) 조회 실패면 null.
     */
    private String resolveSourceTableByRefsTableId(JdbcTemplate mgmtJdbc, String sourceRefs) {
        Long tableId = parseSourceRefsTableId(sourceRefs);
        if (tableId == null || tableId == 0L) return null;
        return ExecutionDataReader.findTableNameById(mgmtJdbc, tableId);
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

        // 검색 절 1회 생성 (각 batch 에 동일 적용)
        List<Object> searchParams = new ArrayList<>();
        String searchClause = buildSearchClause(sourceJdbc, actualTableName, search, searchColumn, searchParams);

        // 1. 배치 COUNT 합산
        for (int i = 0; i < allPks.size(); i += batchSize) {
            List<Object> batch = allPks.subList(i, Math.min(i + batchSize, allPks.size()));
            String placeholders = String.join(",", batch.stream().map(pk -> "?").toList());
            String countSql = String.format("SELECT COUNT(*) FROM %s WHERE %s IN (%s)%s",
                    quotedTableName, pkColumn, placeholders, searchClause);
            List<Object> queryParams = new ArrayList<>(batch);
            queryParams.addAll(searchParams);
            Integer cnt = sourceJdbc.queryForObject(countSql, Integer.class, queryParams.toArray());
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
            List<Object> queryParams = new ArrayList<>(batch);
            queryParams.addAll(searchParams);

            // 이 배치의 (검색 적용된) 건수 확인 (skip 최적화)
            String batchCountSql = String.format("SELECT COUNT(*) FROM %s WHERE %s IN (%s)%s",
                    quotedTableName, pkColumn, placeholders, searchClause);
            Integer batchCount = sourceJdbc.queryForObject(batchCountSql, Integer.class, queryParams.toArray());
            if (batchCount == null) batchCount = 0;

            if (skipped + batchCount <= offset) {
                // 이 배치는 전부 skip
                skipped += batchCount;
                continue;
            }

            // 이 배치에서 데이터 가져오기
            int batchOffset = Math.max(0, offset - skipped);
            String baseSql = String.format("SELECT * FROM %s WHERE %s IN (%s)%s %s",
                    quotedTableName, pkColumn, placeholders, searchClause, orderBy);
            String dataSql = pagingSql(baseSql, dbType, remaining, batchOffset);
            List<Map<String, Object>> batchData = sourceJdbc.queryForList(dataSql, queryParams.toArray());
            pageData.addAll(batchData);
            remaining -= batchData.size();
            skipped += batchCount;
        }

        List<String> columns = pageData.isEmpty()
                ? getTableColumns(sourceJdbc, actualTableName)
                : new ArrayList<>(pageData.get(0).keySet());

        Map<String, Object> res = buildPageResult(actualTableName, columns, pageData, totalCount, page, size);
        res.put("pkColumns", findPkColumns(sourceJdbc, actualTableName, dbType));
        return ResponseEntity.ok(res);
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
        boolean oracleDb = isOracle(dbType);
        boolean mysqlDb = isMysql(dbType);
        String castType = mysqlDb ? "CHAR" : (oracleDb ? "VARCHAR2(4000)" : "TEXT");
        String likeOp = mysqlDb || oracleDb ? "LIKE" : "ILIKE";

        // Oracle: UPPER로 대소문자 무시 검색
        String searchVal = oracleDb ? "%" + search.toUpperCase() + "%" : "%" + search + "%";

        // 특정 컬럼 검색
        if (searchColumn != null && !searchColumn.isBlank()) {
            String actualColumn = findActualColumnName(jdbcTemplate, tableName, searchColumn);
            if (actualColumn != null) {
                params.add(searchVal);
                String castExpr = "CAST(" + qi(actualColumn, dbType) + " AS " + castType + ")";
                if (oracleDb) castExpr = "UPPER(" + castExpr + ")";
                return " AND " + castExpr + " " + likeOp + " ?";
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
            String castExpr = "CAST(" + qi(col, dbType) + " AS " + castType + ")";
            if (oracleDb) castExpr = "UPPER(" + castExpr + ")";
            searchClause.append(castExpr).append(" ").append(likeOp).append(" ?");
            params.add(searchVal);
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

        // "SCHEMA.TABLE" 지원 — 이 경우 카탈로그 쿼리에 schema 필터 추가
        com.infolink.agent.common.config.JdbcTableNameResolver.TableRef ref =
                com.infolink.agent.common.config.JdbcTableNameResolver.parse(tableName);

        try {
            String sql;
            Object[] params;
            if (isOracle(dbType)) {
                if (ref.schema != null) {
                    // cross-schema: ALL_TABLES 에서 owner+table_name 으로 조회
                    sql = "SELECT owner, table_name FROM all_tables " +
                          "WHERE LOWER(owner) = LOWER(?) AND LOWER(table_name) = LOWER(?)";
                    params = new Object[]{ref.schema, ref.table};
                } else {
                    sql = "SELECT table_name FROM user_tables WHERE LOWER(table_name) = LOWER(?)";
                    params = new Object[]{ref.table};
                }
            } else {
                String schemaFilter;
                if (ref.schema != null) {
                    schemaFilter = "LOWER(table_schema) = LOWER(?)";
                } else if (isMysql(dbType)) {
                    schemaFilter = "TABLE_SCHEMA = DATABASE()";
                } else {
                    schemaFilter = "table_schema = 'public'";
                }
                sql = "SELECT " + (ref.schema != null ? "table_schema, " : "") + "table_name " +
                      "FROM information_schema.tables WHERE " + schemaFilter + " AND LOWER(table_name) = LOWER(?)";
                params = (ref.schema != null)
                        ? new Object[]{ref.schema, ref.table}
                        : new Object[]{ref.table};
            }

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params);
            if (!rows.isEmpty()) {
                Map<String, Object> row = rows.get(0);
                String actualTable = (String) row.getOrDefault("table_name",
                        row.getOrDefault("TABLE_NAME", null));
                String actualSchema = null;
                if (ref.schema != null) {
                    actualSchema = (String) row.getOrDefault("owner",
                            row.getOrDefault("OWNER",
                            row.getOrDefault("table_schema",
                            row.getOrDefault("TABLE_SCHEMA", ref.schema))));
                }
                String result = (actualSchema != null) ? actualSchema + "." + actualTable : actualTable;
                if (!result.equals(tableName)) {
                    log.debug("Table name case mismatch: requested='{}', actual='{}'", tableName, result);
                }
                return result;
            }
        } catch (Exception e) {
            log.warn("Failed to find table name in information_schema: {}", e.getMessage());
            // 대체: 직접 SELECT 시도 (schema 포함한 이름 그대로)
            try {
                String sql = limit1Sql(String.format("SELECT 1 FROM %s", qi(tableName, dbType)), dbType);
                jdbcTemplate.queryForList(sql);
                return tableName;
            } catch (Exception e2) {
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
