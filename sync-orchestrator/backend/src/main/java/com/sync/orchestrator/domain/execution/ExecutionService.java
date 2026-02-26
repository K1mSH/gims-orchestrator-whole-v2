package com.sync.orchestrator.domain.execution;

import com.sync.orchestrator.common.CredentialEncryptor;
import com.sync.orchestrator.domain.agent.Agent;
import com.sync.orchestrator.domain.agent.AgentRepository;
import com.sync.orchestrator.domain.agent.AgentStatus;
import com.sync.orchestrator.domain.agent.AgentTable;
import com.sync.orchestrator.domain.datasource.Datasource;
import com.sync.orchestrator.domain.datasource.DatasourceRepository;
import com.sync.orchestrator.domain.datasource.DatasourceTable;
import com.sync.orchestrator.domain.datasource.DatasourceTableRepository;
import com.sync.orchestrator.domain.zone.ZoneConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

/**
 * 실행 관리 서비스
 * - 실행 데이터는 Agent DB에 저장됨
 * - Orchestrator는 Agent 상태 관리 및 Agent API 프록시 역할
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExecutionService {

    private final AgentRepository agentRepository;
    private final DatasourceRepository datasourceRepository;
    private final DatasourceTableRepository datasourceTableRepository;
    private final ZoneConfigRepository zoneConfigRepository;
    private final ExecutionHistoryRepository executionHistoryRepository;
    private final ExecutionStepHistoryRepository executionStepHistoryRepository;
    private final CredentialEncryptor credentialEncryptor;
    private final RestTemplate restTemplate;

    /**
     * Agent별 실행 이력 조회 (Agent DB에서)
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> findByAgentIdFromAgent(Long id) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + id));

        try {
            String url = agent.getEndpointUrl() + "/api/execution-data";
            log.info("Fetching executions from agent: {}", url);

            ResponseEntity<List> response = restTemplate.getForEntity(url, List.class);
            return response.getBody() != null ? response.getBody() : List.of();
        } catch (Exception e) {
            log.error("Failed to fetch executions from agent: {}", agent.getAgentCode(), e);
            return List.of();
        }
    }

    /**
     * 전체 Agent 상태 조회
     */
    public List<ExecutionDto.AgentExecutionSummary> getAgentStatuses() {
        List<Agent> agents = agentRepository.findAll();

        return agents.stream().map(agent -> {
            ExecutionStatus lastStatus = null;
            if (agent.getLastExecutionStatus() != null) {
                try {
                    lastStatus = ExecutionStatus.valueOf(agent.getLastExecutionStatus());
                } catch (IllegalArgumentException ignored) {
                }
            }

            return ExecutionDto.AgentExecutionSummary.of(
                    agent.getId(),
                    agent.getAgentCode(),
                    agent.getAgentName(),
                    agent.getZone(),
                    lastStatus,
                    agent.getLastExecutedAt(),
                    agent.getStatus()
            );
        }).collect(Collectors.toList());
    }

    /**
     * 실행 상세 정보 조회 (Agent DB에서)
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getExecutionDetail(String executionId) {
        Agent agent = findAgentByExecutionId(executionId);

        try {
            String url = agent.getEndpointUrl() + "/api/execution-data/" + executionId;
            log.info("Fetching execution detail from agent: {}", url);

            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to fetch execution detail from agent: {}", agent.getAgentCode(), e);
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * 실행 데이터 조회 (Agent 프록시) - 페이징/검색 지원
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getExecutionData(String executionId, String dataType,
                                                 ExecutionDto.TableDataSearchParams searchParams) {
        Agent agent = findAgentByExecutionId(executionId);

        try {
            StringBuilder endpoint = new StringBuilder("/api/execution-data/").append(executionId);
            switch (dataType) {
                case "summary" -> endpoint.append("/summary");
                case "source" -> endpoint.append("/source");
                case "target-if" -> endpoint.append("/target-if");
                case "target" -> endpoint.append("/target");
                case "failed" -> endpoint.append("/failed");
                default -> throw new IllegalArgumentException("Unknown data type: " + dataType);
            }

            // 페이징/검색 파라미터 추가 (source, target-if, target만 해당)
            if (searchParams != null && (dataType.equals("source") || dataType.equals("target-if") || dataType.equals("target"))) {
                endpoint.append("?").append(searchParams.toQueryString());
            }

            String url = agent.getEndpointUrl() + endpoint;
            log.info("Fetching execution data from agent: {}", url);

            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to fetch execution data from agent: {}", agent.getAgentCode(), e);
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * 테이블별 통계 조회 (Agent DB에서)
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getTableStats(String executionId) {
        Agent agent = findAgentByExecutionId(executionId);

        try {
            String url = agent.getEndpointUrl() + "/api/execution-data/" + executionId + "/tables";
            log.info("Fetching table stats from agent: {}", url);

            ResponseEntity<List> response = restTemplate.getForEntity(url, List.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to fetch table stats from agent: {}", agent.getAgentCode(), e);
            return List.of();
        }
    }

    /**
     * 특정 테이블의 레코드 조회 (Agent DB에서)
     * Agent의 getTableLog()는 SyncLog 단일 객체를 반환하므로 Map으로 역직렬화
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getTableRecords(String executionId, String tableName) {
        Agent agent = findAgentByExecutionId(executionId);

        try {
            String url = agent.getEndpointUrl() + "/api/execution-data/" + executionId + "/tables/" + tableName;
            log.info("Fetching table records from agent: {}", url);

            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to fetch table records from agent: {}", agent.getAgentCode(), e);
            return Map.of();
        }
    }

    /**
     * 특정 테이블의 실패 레코드 조회 (Agent DB에서)
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getTableFailedRecords(String executionId, String tableName) {
        Agent agent = findAgentByExecutionId(executionId);

        try {
            String url = agent.getEndpointUrl() + "/api/execution-data/" + executionId + "/tables/" + tableName + "/failed";
            log.info("Fetching table failed records from agent: {}", url);

            ResponseEntity<List> response = restTemplate.getForEntity(url, List.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to fetch table failed records from agent: {}", agent.getAgentCode(), e);
            return List.of();
        }
    }

    /**
     * Source PK로 데이터 추적 (Source → IF → Target)
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> traceBySourcePk(String executionId, String pkValue, String pkColumn, String sourceTable, String ifTableName, String targetTableName) {
        Agent agent = findAgentByExecutionId(executionId);

        // ifTableName, targetTableName이 없으면 Agent의 테이블 매핑에서 자동으로 찾기
        String resolvedIfTableName = ifTableName;
        String resolvedTargetTableName = targetTableName;

        if ((resolvedIfTableName == null || resolvedIfTableName.isBlank()) && agent.getAgentTables() != null) {
            for (AgentTable at : agent.getAgentTables()) {
                if (at.getTableType() == AgentTable.TableType.TARGET) {
                    DatasourceTable dt = datasourceTableRepository.findById(at.getDatasourceTableId()).orElse(null);
                    if (dt != null && sourceTable != null) {
                        String tableName2 = dt.getTableName();
                        String sourceBase = sourceTable.toLowerCase().replace("_view", "");
                        if (tableName2.toLowerCase().contains(sourceBase)) {
                            resolvedIfTableName = tableName2;
                            break;
                        }
                    }
                }
            }
        }

        try {
            UriComponentsBuilder builder = UriComponentsBuilder
                    .fromHttpUrl(agent.getEndpointUrl())
                    .path("/api/execution-data/{executionId}/trace")
                    .queryParam("pkValue", pkValue)
                    .queryParam("pkColumn", pkColumn)
                    .queryParam("sourceTable", sourceTable);

            if (resolvedIfTableName != null && !resolvedIfTableName.isBlank()) {
                builder.queryParam("ifTableName", resolvedIfTableName);
            }
            if (resolvedTargetTableName != null && !resolvedTargetTableName.isBlank()) {
                builder.queryParam("targetTableName", resolvedTargetTableName);
            }

            String url = builder.buildAndExpand(executionId).toUriString();
            log.info("Tracing data from agent: {}", url);

            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to trace data from agent: {}", agent.getAgentCode(), e);
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * Target에서 Source로 역추적 (sourceRefs 기반)
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> traceToSource(String executionId, String sourceRefs, String sourceTable) {
        Agent agent = findAgentByExecutionId(executionId);

        try {
            UriComponentsBuilder builder = UriComponentsBuilder
                    .fromHttpUrl(agent.getEndpointUrl())
                    .path("/api/execution-data/{executionId}/trace-source")
                    .queryParam("sourceRefs", sourceRefs);

            if (sourceTable != null && !sourceTable.isBlank()) {
                builder.queryParam("sourceTable", sourceTable);
            }

            String url = builder.buildAndExpand(executionId).toUriString();
            log.info("Tracing source from agent: {}", url);

            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to trace source from agent: {}", agent.getAgentCode(), e);
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * 실행 트리거 - Agent에 실행 요청 (증분 동기화, MANUAL 트리거)
     */
    @Transactional
    public ExecutionDto.TriggerResponse triggerExecution(Long id) {
        return triggerExecution(id, null, null, "MANUAL");
    }

    /**
     * 실행 트리거 - Agent에 실행 요청 (triggeredBy 지정)
     */
    @Transactional
    public ExecutionDto.TriggerResponse triggerExecution(Long id, String triggeredBy) {
        return triggerExecution(id, null, null, triggeredBy);
    }

    /**
     * 실행 트리거 - Agent에 실행 요청 (시간 범위 지정, MANUAL 트리거)
     * executionId 형식: {agentCode}_{uuid}
     */
    @Transactional
    public ExecutionDto.TriggerResponse triggerExecution(Long id, LocalDateTime startTime, LocalDateTime endTime) {
        return triggerExecution(id, startTime, endTime, "MANUAL");
    }

    /**
     * 실행 트리거 - Agent에 실행 요청 (시간 범위, 필터, 트리거 유형 지정)
     */
    @Transactional
    public ExecutionDto.TriggerResponse triggerExecution(Long id, LocalDateTime startTime, LocalDateTime endTime,
                                                          List<Map<String, Object>> filters, String triggeredBy) {
        return triggerExecutionInternal(id, startTime, endTime, filters, null, null, triggeredBy);
    }

    /**
     * 실행 트리거 - Agent에 실행 요청 (시간 범위, 필터, Step 선택, 트리거 유형 지정)
     */
    @Transactional
    public ExecutionDto.TriggerResponse triggerExecution(Long id, LocalDateTime startTime, LocalDateTime endTime,
                                                          List<Map<String, Object>> filters,
                                                          List<String> selectedStepIds, String triggeredBy) {
        return triggerExecutionInternal(id, startTime, endTime, filters, selectedStepIds, null, triggeredBy);
    }

    /**
     * 실행 트리거 - Agent에 실행 요청 (시간 범위, 필터, Step 선택, 실행 모드, 트리거 유형 지정)
     */
    @Transactional
    public ExecutionDto.TriggerResponse triggerExecution(Long id, LocalDateTime startTime, LocalDateTime endTime,
                                                          List<Map<String, Object>> filters,
                                                          List<String> selectedStepIds,
                                                          String executionModeId, String triggeredBy) {
        return triggerExecutionInternal(id, startTime, endTime, filters, selectedStepIds, executionModeId, triggeredBy);
    }

    /**
     * 실행 트리거 - Agent에 실행 요청 (시간 범위 및 트리거 유형 지정)
     * executionId 형식: {agentCode}_{uuid}
     */
    @Transactional
    public ExecutionDto.TriggerResponse triggerExecution(Long id, LocalDateTime startTime, LocalDateTime endTime, String triggeredBy) {
        return triggerExecutionInternal(id, startTime, endTime, null, null, null, triggeredBy);
    }

    /**
     * 실행 트리거 내부 구현
     */
    @Transactional
    public ExecutionDto.TriggerResponse triggerExecutionInternal(Long id, LocalDateTime startTime, LocalDateTime endTime,
                                                                  List<Map<String, Object>> filters,
                                                                  List<String> selectedStepIds,
                                                                  String executionModeId, String triggeredBy) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + id));

        String agentCode = agent.getAgentCode();

        if (agent.getStatus() == AgentStatus.OFFLINE) {
            throw new IllegalStateException("Agent is offline: " + agentCode);
        }

        if (agent.getStatus() == AgentStatus.RUNNING) {
            throw new IllegalStateException("Agent is already running: " + agentCode);
        }

        // executionId 형식: {agentCode}_{uuid}
        String executionId = agentCode + "_" + UUID.randomUUID().toString();

        // Agent 상태를 RUNNING으로 변경
        agent.setStatus(AgentStatus.RUNNING);
        agent.setLastExecutedAt(LocalDateTime.now());
        agentRepository.save(agent);

        // Agent에 실행 요청
        try {
            String url = agent.getEndpointUrl() + "/api/pipeline/execute";

            // Datasource 연결 정보 전체를 조회해서 Agent에 전달
            Map<String, Object> request = new java.util.HashMap<>();
            request.put("executionId", executionId);
            request.put("agentCode", agentCode);
            request.put("agentType", agent.getAgentType().name());
            request.put("triggeredBy", triggeredBy != null ? triggeredBy : "MANUAL");

            // 시간 범위 파라미터 (지정된 경우에만 전달)
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
            if (startTime != null) {
                request.put("startTime", startTime.format(formatter));
            }
            if (endTime != null) {
                request.put("endTime", endTime.format(formatter));
            }

            // Source Datasource 연결 정보
            if (agent.getSourceDatasourceId() != null) {
                Datasource sourceDatasource = datasourceRepository.findById(agent.getSourceDatasourceId())
                        .orElseThrow(() -> new IllegalArgumentException("Source datasource not found: " + agent.getSourceDatasourceId()));
                request.put("sourceDatasourceId", sourceDatasource.getDatasourceId());
                request.put("sourceDbType", sourceDatasource.getDbType().name());
                request.put("sourceHost", sourceDatasource.getHost());
                request.put("sourcePort", sourceDatasource.getPort());
                request.put("sourceDatabaseName", sourceDatasource.getDatabaseName());
                request.put("sourceUsername", credentialEncryptor.decrypt(sourceDatasource.getUsername()));
                request.put("sourcePassword", credentialEncryptor.decrypt(sourceDatasource.getPassword()));
                request.put("sourceZone", sourceDatasource.getZone());
                request.put("sourceDatasourceDbId", sourceDatasource.getId());
                String zoneShortCode = zoneConfigRepository.findShortCodeByZone(sourceDatasource.getZone());
                request.put("sourceZoneShortCode", zoneShortCode != null ? zoneShortCode : "U");

                // Source 테이블 ID 목록
                Map<String, Long> sourceTableIds = new java.util.HashMap<>();
                agent.getAgentTables().stream()
                        .filter(t -> t.getTableType() == AgentTable.TableType.SOURCE)
                        .forEach(at -> {
                            datasourceTableRepository.findById(at.getDatasourceTableId())
                                    .ifPresent(dt -> sourceTableIds.put(dt.getTableName(), dt.getId()));
                        });

                if (sourceTableIds.isEmpty()) {
                    agent.getAgentTables().stream()
                            .filter(t -> t.getTableType() == AgentTable.TableType.TARGET)
                            .forEach(at -> {
                                datasourceTableRepository.findById(at.getDatasourceTableId())
                                        .ifPresent(dt -> sourceTableIds.put(dt.getTableName(), dt.getId()));
                            });
                }

                // sourceTableIds가 비었을 때 Agent에서 자동 발견 & 등록
                if (sourceTableIds.isEmpty()) {
                    log.info("Auto-discovering source tables for agent: {}", agentCode);
                    try {
                        String tablesUrl = agent.getEndpointUrl() + "/api/pipeline/" + agentCode + "/tables";
                        @SuppressWarnings("unchecked")
                        ResponseEntity<Map> tablesResponse = restTemplate.getForEntity(tablesUrl, Map.class);
                        @SuppressWarnings("unchecked")
                        List<Map<String, String>> tables = (List<Map<String, String>>) tablesResponse.getBody().get("tables");

                        if (tables != null) {
                            for (Map<String, String> tableInfo : tables) {
                                if ("SOURCE".equals(tableInfo.get("type"))) {
                                    String tableName = tableInfo.get("tableName");
                                    String dsId = agent.getSourceDatasourceId();

                                    // DatasourceTable 등록 (없으면 생성)
                                    DatasourceTable dt = datasourceTableRepository
                                            .findByDatasourceIdAndTableName(dsId, tableName)
                                            .orElseGet(() -> datasourceTableRepository.save(
                                                    DatasourceTable.builder()
                                                            .datasourceId(dsId)
                                                            .tableName(tableName)
                                                            .build()));

                                    // AgentTable 매핑 (중복 방지)
                                    boolean alreadyMapped = agent.getAgentTables().stream()
                                            .anyMatch(at -> at.getDatasourceTableId().equals(dt.getId()));
                                    if (!alreadyMapped) {
                                        agent.getAgentTables().add(AgentTable.builder()
                                                .agent(agent)
                                                .datasourceTableId(dt.getId())
                                                .tableType(AgentTable.TableType.SOURCE)
                                                .build());
                                    }

                                    sourceTableIds.put(tableName, dt.getId());
                                }
                            }
                            if (!sourceTableIds.isEmpty()) {
                                agentRepository.save(agent);
                                log.info("Auto-registered {} source tables for agent: {}", sourceTableIds.size(), agentCode);
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Failed to auto-discover tables for agent {}: {}", agentCode, e.getMessage());
                    }
                }

                request.put("sourceTableIds", sourceTableIds);
            }

            // Target Datasource 연결 정보
            if (agent.getTargetDatasourceId() != null) {
                Datasource targetDatasource = datasourceRepository.findById(agent.getTargetDatasourceId())
                        .orElseThrow(() -> new IllegalArgumentException("Target datasource not found: " + agent.getTargetDatasourceId()));
                request.put("targetDatasourceId", targetDatasource.getDatasourceId());
                request.put("targetDbType", targetDatasource.getDbType().name());
                request.put("targetHost", targetDatasource.getHost());
                request.put("targetPort", targetDatasource.getPort());
                request.put("targetDatabaseName", targetDatasource.getDatabaseName());
                request.put("targetUsername", credentialEncryptor.decrypt(targetDatasource.getUsername()));
                request.put("targetPassword", credentialEncryptor.decrypt(targetDatasource.getPassword()));
            }

            // 실행 필터 전달
            if (filters != null && !filters.isEmpty()) {
                request.put("filters", filters);
                log.info("Triggering execution with {} filters", filters.size());
            }

            // 선택적 Step 실행
            if (selectedStepIds != null && !selectedStepIds.isEmpty()) {
                request.put("selectedStepIds", selectedStepIds);
                log.info("Triggering execution with selectedStepIds: {}", selectedStepIds);
            }

            // 실행 모드
            if (executionModeId != null && !executionModeId.isBlank()) {
                request.put("executionModeId", executionModeId);
                log.info("Triggering execution with executionModeId: {}", executionModeId);
            }

            log.info("Triggering execution on agent: {} with executionId: {}, source: {} (zone={}), target: {}, timeRange: {} ~ {}",
                    agentCode, executionId, agent.getSourceDatasourceId(), request.get("sourceZone"), agent.getTargetDatasourceId(),
                    startTime != null ? startTime : "default", endTime != null ? endTime : "now");
            ResponseEntity<Void> response = restTemplate.postForEntity(url, request, Void.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Execution triggered successfully: {}", executionId);
            }

            return ExecutionDto.TriggerResponse.builder()
                    .executionId(executionId)
                    .agentId(id)
                    .agentCode(agentCode)
                    .status("RUNNING")
                    .startTime(startTime)
                    .endTime(endTime)
                    .build();

        } catch (Exception e) {
            log.error("Failed to trigger execution on agent: {}", agentCode, e);

            // Agent 상태 복구
            agent.setStatus(AgentStatus.ONLINE);
            agent.setLastExecutionStatus("FAILED");
            agentRepository.save(agent);

            throw new RuntimeException("Failed to trigger execution: " + e.getMessage(), e);
        }
    }

    /**
     * executionId에서 agentCode 추출
     * 형식: {agentCode}_{uuid}
     */
    public String extractAgentCodeFromExecutionId(String executionId) {
        int lastUnderscoreIndex = executionId.lastIndexOf('_');
        if (lastUnderscoreIndex == -1) {
            throw new IllegalArgumentException("Invalid executionId format: " + executionId);
        }
        return executionId.substring(0, lastUnderscoreIndex);
    }

    /**
     * executionId에서 Agent 조회 (agentCode 기반)
     */
    private Agent findAgentByExecutionId(String executionId) {
        String agentCode = extractAgentCodeFromExecutionId(executionId);
        return agentRepository.findByAgentCode(agentCode)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentCode));
    }

    // ==================== ExecutionHistory 관련 메서드 ====================

    /**
     * 실행의 Step별 결과 조회
     */
    public List<ExecutionStepHistory> getExecutionSteps(String executionId) {
        return executionStepHistoryRepository.findByExecutionIdOrderByStepOrder(executionId);
    }

    /**
     * 최근 실행 이력 조회 (대시보드용)
     */
    public List<ExecutionDto.HistoryResponse> getRecentHistory() {
        return executionHistoryRepository.findTop50ByOrderByStartedAtDesc().stream()
                .map(ExecutionDto.HistoryResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 현재 실행 중인 이력 조회
     */
    public List<ExecutionDto.HistoryResponse> getRunningHistory() {
        return executionHistoryRepository.findByStatusOrderByStartedAtAsc(ExecutionStatus.RUNNING).stream()
                .map(ExecutionDto.HistoryResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * Agent별 실행 이력 조회 (Orchestrator DB에서, agentCode 기반)
     */
    public List<ExecutionDto.HistoryResponse> getHistoryByAgentCode(String agentCode) {
        return executionHistoryRepository.findByAgentCodeOrderByStartedAtDesc(agentCode).stream()
                .map(ExecutionDto.HistoryResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * Agent별 실행 이력 조회 (Agent Long ID 기반)
     */
    public List<ExecutionDto.HistoryResponse> getHistoryByAgent(Long id) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + id));
        return getHistoryByAgentCode(agent.getAgentCode());
    }

    /**
     * 실행 이력 페이징 조회
     */
    public Page<ExecutionDto.HistoryResponse> getHistoryPaged(Pageable pageable) {
        return executionHistoryRepository.findAllByOrderByStartedAtDesc(pageable)
                .map(ExecutionDto.HistoryResponse::from);
    }

    /**
     * 실행 이력 필터/검색 페이징 조회
     */
    public Page<ExecutionDto.HistoryResponse> getHistoryPaged(int page, int size,
            String status, String agentCode, String agentType,
            String zone, String startDate, String endDate, String search) {
        Specification<ExecutionHistory> spec = Specification.where(null);
        if (status != null && !status.isBlank()) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("status"), ExecutionStatus.valueOf(status)));
        }
        if (agentCode != null && !agentCode.isBlank()) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("agentCode"), agentCode));
        }
        if (agentType != null && !agentType.isBlank()) {
            spec = spec.and((r, q, cb) -> cb.equal(r.get("agentType"), agentType));
        }
        // 망구분 필터: Agent 테이블에서 해당 zone의 agentCode 목록 조회 후 in 조건
        if (zone != null && !zone.isBlank()) {
            List<String> agentCodes = agentRepository.findByZone(zone).stream()
                    .map(Agent::getAgentCode)
                    .collect(Collectors.toList());
            if (agentCodes.isEmpty()) {
                // 해당 zone에 Agent가 없으면 빈 결과 반환
                spec = spec.and((r, q, cb) -> cb.isNull(r.get("executionId")));
            } else {
                spec = spec.and((r, q, cb) -> r.get("agentCode").in(agentCodes));
            }
        }
        // 날짜 필터
        if (startDate != null && !startDate.isBlank()) {
            LocalDateTime start = LocalDate.parse(startDate).atStartOfDay();
            spec = spec.and((r, q, cb) -> cb.greaterThanOrEqualTo(r.get("startedAt"), start));
        }
        if (endDate != null && !endDate.isBlank()) {
            LocalDateTime end = LocalDate.parse(endDate).plusDays(1).atStartOfDay();
            spec = spec.and((r, q, cb) -> cb.lessThan(r.get("startedAt"), end));
        }
        if (search != null && !search.isBlank()) {
            spec = spec.and((r, q, cb) -> cb.like(cb.lower(r.get("agentName")),
                    "%" + search.toLowerCase() + "%"));
        }
        Pageable pageable = PageRequest.of(page, size, Sort.by("startedAt").descending());
        return executionHistoryRepository.findAll(spec, pageable)
                .map(ExecutionDto.HistoryResponse::from);
    }

    /**
     * Agent별 실행 이력 페이징 조회
     */
    public Page<ExecutionDto.HistoryResponse> getHistoryByAgentPaged(Long id, Pageable pageable) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + id));
        return executionHistoryRepository.findByAgentCodeOrderByStartedAtDesc(agent.getAgentCode(), pageable)
                .map(ExecutionDto.HistoryResponse::from);
    }

    /**
     * 대시보드 통계 조회
     */
    public ExecutionDto.DashboardStats getDashboardStats() {
        LocalDateTime todayStart = LocalDate.now().atStartOfDay();

        long todayExecutions = executionHistoryRepository.countTodayExecutions(todayStart);
        long todayFailed = executionHistoryRepository.countTodayFailedExecutions(todayStart);
        long currentlyRunning = executionHistoryRepository.countByStatus(ExecutionStatus.RUNNING);

        long totalAgents = agentRepository.count();
        long onlineAgents = agentRepository.countByStatus(AgentStatus.ONLINE)
                         + agentRepository.countByStatus(AgentStatus.RUNNING);

        return ExecutionDto.DashboardStats.builder()
                .todayExecutions(todayExecutions)
                .todayFailed(todayFailed)
                .currentlyRunning(currentlyRunning)
                .totalAgents(totalAgents)
                .onlineAgents(onlineAgents)
                .build();
    }
}
