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
import org.springframework.data.domain.Pageable;

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
    private final CredentialEncryptor credentialEncryptor;
    private final RestTemplate restTemplate;

    /**
     * Agent별 실행 이력 조회 (Agent DB에서)
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> findByAgentIdFromAgent(String agentId) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));

        try {
            String url = agent.getEndpointUrl() + "/api/execution-data";
            log.info("Fetching executions from agent: {}", url);

            ResponseEntity<List> response = restTemplate.getForEntity(url, List.class);
            return response.getBody() != null ? response.getBody() : List.of();
        } catch (Exception e) {
            log.error("Failed to fetch executions from agent: {}", agentId, e);
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
                    agent.getAgentId(),
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
        String agentId = extractAgentIdFromExecutionId(executionId);
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));

        try {
            String url = agent.getEndpointUrl() + "/api/execution-data/" + executionId;
            log.info("Fetching execution detail from agent: {}", url);

            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to fetch execution detail from agent: {}", agent.getAgentId(), e);
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * 실행 데이터 조회 (Agent 프록시) - 페이징/검색 지원
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getExecutionData(String executionId, String dataType,
                                                 ExecutionDto.TableDataSearchParams searchParams) {
        String agentId = extractAgentIdFromExecutionId(executionId);
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));

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
            log.error("Failed to fetch execution data from agent: {}", agent.getAgentId(), e);
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * Step 로그 조회 (Agent DB에서)
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getStepLogs(String executionId) {
        String agentId = extractAgentIdFromExecutionId(executionId);
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));

        try {
            String url = agent.getEndpointUrl() + "/api/execution-data/" + executionId + "/steps";
            log.info("Fetching step logs from agent: {}", url);

            ResponseEntity<List> response = restTemplate.getForEntity(url, List.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to fetch step logs from agent: {}", agent.getAgentId(), e);
            return List.of();
        }
    }

    /**
     * 테이블별 통계 조회 (Agent DB에서)
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getTableStats(String executionId) {
        String agentId = extractAgentIdFromExecutionId(executionId);
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));

        try {
            String url = agent.getEndpointUrl() + "/api/execution-data/" + executionId + "/tables";
            log.info("Fetching table stats from agent: {}", url);

            ResponseEntity<List> response = restTemplate.getForEntity(url, List.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to fetch table stats from agent: {}", agent.getAgentId(), e);
            return List.of();
        }
    }

    /**
     * 특정 테이블의 레코드 조회 (Agent DB에서)
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getTableRecords(String executionId, String tableName) {
        String agentId = extractAgentIdFromExecutionId(executionId);
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));

        try {
            String url = agent.getEndpointUrl() + "/api/execution-data/" + executionId + "/tables/" + tableName;
            log.info("Fetching table records from agent: {}", url);

            ResponseEntity<List> response = restTemplate.getForEntity(url, List.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to fetch table records from agent: {}", agent.getAgentId(), e);
            return List.of();
        }
    }

    /**
     * 특정 테이블의 실패 레코드 조회 (Agent DB에서)
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getTableFailedRecords(String executionId, String tableName) {
        String agentId = extractAgentIdFromExecutionId(executionId);
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));

        try {
            String url = agent.getEndpointUrl() + "/api/execution-data/" + executionId + "/tables/" + tableName + "/failed";
            log.info("Fetching table failed records from agent: {}", url);

            ResponseEntity<List> response = restTemplate.getForEntity(url, List.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to fetch table failed records from agent: {}", agent.getAgentId(), e);
            return List.of();
        }
    }

    /**
     * Source PK로 데이터 추적 (Source → IF → Target)
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> traceBySourcePk(String executionId, String pkValue, String pkColumn, String sourceTable, String ifTableName, String targetTableName) {
        String agentId = extractAgentIdFromExecutionId(executionId);
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));

        // ifTableName, targetTableName이 없으면 Agent의 테이블 매핑에서 자동으로 찾기
        String resolvedIfTableName = ifTableName;
        String resolvedTargetTableName = targetTableName;

        if ((resolvedIfTableName == null || resolvedIfTableName.isBlank()) && agent.getAgentTables() != null) {
            // Agent의 TARGET 테이블 중 sourceTable과 관련된 것 찾기
            // TARGET 타입 테이블이 IF 테이블 역할 (relay의 경우)
            for (AgentTable at : agent.getAgentTables()) {
                if (at.getTableType() == AgentTable.TableType.TARGET) {
                    DatasourceTable dt = datasourceTableRepository.findById(at.getDatasourceTableId()).orElse(null);
                    if (dt != null && sourceTable != null) {
                        String tableName = dt.getTableName();
                        // sourceTable 이름이 포함된 TARGET 테이블 찾기 (예: SEC_JEWON_VIEW → sec_jewon)
                        // toLowerCase() 후 _view 제거하면 대소문자 모두 처리됨
                        String sourceBase = sourceTable.toLowerCase().replace("_view", "");
                        if (tableName.toLowerCase().contains(sourceBase)) {
                            resolvedIfTableName = tableName;
                            break;
                        }
                    }
                }
            }
        }
        // targetTableName은 optional - Agent에서 처리

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
            log.error("Failed to trace data from agent: {}", agent.getAgentId(), e);
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * Target에서 Source로 역추적 (sourceRefs 기반)
     * Agent에 프록시하여 Source 테이블에서 원본 레코드 조회
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> traceToSource(String executionId, String sourceRefs, String sourceTable) {
        String agentId = extractAgentIdFromExecutionId(executionId);
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));

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
            log.error("Failed to trace source from agent: {}", agent.getAgentId(), e);
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * 레코드 처리 이력 조회 (Agent 프록시)
     * Agent의 sync_record_history 테이블에서 특정 레코드의 처리 이력 조회
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getRecordHistory(String executionId, String tableName, String recordKey) {
        String agentId = extractAgentIdFromExecutionId(executionId);
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));

        try {
            UriComponentsBuilder builder = UriComponentsBuilder
                    .fromHttpUrl(agent.getEndpointUrl())
                    .path("/api/execution-data/record-history")
                    .queryParam("tableName", tableName)
                    .queryParam("recordKey", recordKey);

            String url = builder.toUriString();
            log.info("Fetching record history from agent: {}", url);

            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to fetch record history from agent: {}", agent.getAgentId(), e);
            return Map.of("error", e.getMessage(), "histories", List.of(), "count", 0);
        }
    }

    /**
     * 실행 ID별 처리 이력 조회 (Agent 프록시)
     * Agent의 sync_record_history에서 해당 실행이 처리한 모든 레코드 이력 조회
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getRecordHistoryByExecution(String executionId) {
        String agentId = extractAgentIdFromExecutionId(executionId);
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));

        try {
            UriComponentsBuilder builder = UriComponentsBuilder
                    .fromHttpUrl(agent.getEndpointUrl())
                    .path("/api/execution-data/record-history/by-execution")
                    .queryParam("executionId", executionId);

            String url = builder.toUriString();
            log.info("Fetching record history by execution from agent: {}", url);

            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to fetch record history by execution from agent: {}", agent.getAgentId(), e);
            return Map.of("error", e.getMessage(), "executionId", executionId, "totalCount", 0, "tables", List.of());
        }
    }

    /**
     * 실행 트리거 - Agent에 실행 요청 (기본 lookback 사용, MANUAL 트리거)
     */
    @Transactional
    public ExecutionDto.TriggerResponse triggerExecution(String agentId) {
        return triggerExecution(agentId, null, null, "MANUAL");
    }

    /**
     * 실행 트리거 - Agent에 실행 요청 (triggeredBy 지정)
     */
    @Transactional
    public ExecutionDto.TriggerResponse triggerExecution(String agentId, String triggeredBy) {
        return triggerExecution(agentId, null, null, triggeredBy);
    }

    /**
     * 실행 트리거 - Agent에 실행 요청 (시간 범위 지정, MANUAL 트리거)
     * executionId 형식: {agentId}_{uuid}
     *
     * @param startTime 동기화 시작 시간 (null이면 기본 lookback 사용)
     * @param endTime   동기화 종료 시간 (null이면 현재 시간)
     */
    @Transactional
    public ExecutionDto.TriggerResponse triggerExecution(String agentId, LocalDateTime startTime, LocalDateTime endTime) {
        return triggerExecution(agentId, startTime, endTime, "MANUAL");
    }

    /**
     * 실행 트리거 - Agent에 실행 요청 (시간 범위, 필터, 트리거 유형 지정)
     */
    @Transactional
    public ExecutionDto.TriggerResponse triggerExecution(String agentId, LocalDateTime startTime, LocalDateTime endTime,
                                                          List<Map<String, Object>> filters, String triggeredBy) {
        return triggerExecutionInternal(agentId, startTime, endTime, filters, triggeredBy);
    }

    /**
     * 실행 트리거 - Agent에 실행 요청 (시간 범위 및 트리거 유형 지정)
     * executionId 형식: {agentId}_{uuid}
     *
     * @param startTime   동기화 시작 시간 (null이면 기본 lookback 사용)
     * @param endTime     동기화 종료 시간 (null이면 현재 시간)
     * @param triggeredBy 트리거 유형 (MANUAL, SCHEDULE, CHAIN)
     */
    @Transactional
    public ExecutionDto.TriggerResponse triggerExecution(String agentId, LocalDateTime startTime, LocalDateTime endTime, String triggeredBy) {
        return triggerExecutionInternal(agentId, startTime, endTime, null, triggeredBy);
    }

    /**
     * 실행 트리거 내부 구현
     */
    @Transactional
    public ExecutionDto.TriggerResponse triggerExecutionInternal(String agentId, LocalDateTime startTime, LocalDateTime endTime,
                                                                  List<Map<String, Object>> filters, String triggeredBy) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));

        if (agent.getStatus() == AgentStatus.OFFLINE) {
            throw new IllegalStateException("Agent is offline: " + agentId);
        }

        if (agent.getStatus() == AgentStatus.RUNNING) {
            throw new IllegalStateException("Agent is already running: " + agentId);
        }

        // executionId 형식: {agentId}_{uuid}
        String executionId = agentId + "_" + UUID.randomUUID().toString();

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
            request.put("agentId", agentId);  // 통합 Agent에서 파이프라인 라우팅에 사용
            request.put("triggeredBy", triggeredBy != null ? triggeredBy : "MANUAL");

            // 시간 범위 파라미터 (지정된 경우에만 전달)
            // ISO_LOCAL_DATE_TIME 형식으로 초까지 포함해서 전달 (초 생략 방지)
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
                // Source DB의 네트워크 Zone (Agent Chain 추적용)
                request.put("sourceZone", sourceDatasource.getZone());
                // sourceRef용 숫자 ID 및 Zone shortCode
                request.put("sourceDatasourceDbId", sourceDatasource.getId());
                String zoneShortCode = zoneConfigRepository.findShortCodeByZone(sourceDatasource.getZone());
                request.put("sourceZoneShortCode", zoneShortCode != null ? zoneShortCode : "U");

                // Source 테이블 ID 목록 (테이블명 -> tableId 매핑)
                // SOURCE 타입 테이블 우선, 없으면 TARGET도 포함 (snd-relay 케이스)
                Map<String, Long> sourceTableIds = new java.util.HashMap<>();

                // 1차: SOURCE 타입 테이블 수집
                agent.getAgentTables().stream()
                        .filter(t -> t.getTableType() == AgentTable.TableType.SOURCE)
                        .forEach(at -> {
                            datasourceTableRepository.findById(at.getDatasourceTableId())
                                    .ifPresent(dt -> sourceTableIds.put(dt.getTableName(), dt.getId()));
                        });

                // 2차: SOURCE가 비어있으면 TARGET도 포함 (snd-relay는 TARGET에서 읽어서 IF로 보냄)
                if (sourceTableIds.isEmpty()) {
                    agent.getAgentTables().stream()
                            .filter(t -> t.getTableType() == AgentTable.TableType.TARGET)
                            .forEach(at -> {
                                datasourceTableRepository.findById(at.getDatasourceTableId())
                                        .ifPresent(dt -> sourceTableIds.put(dt.getTableName(), dt.getId()));
                            });
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

            log.info("Triggering execution on agent: {} with executionId: {}, source: {} (zone={}), target: {}, timeRange: {} ~ {}",
                    agentId, executionId, agent.getSourceDatasourceId(), request.get("sourceZone"), agent.getTargetDatasourceId(),
                    startTime != null ? startTime : "default", endTime != null ? endTime : "now");
            ResponseEntity<Void> response = restTemplate.postForEntity(url, request, Void.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Execution triggered successfully: {}", executionId);
            }

            return ExecutionDto.TriggerResponse.builder()
                    .executionId(executionId)
                    .agentId(agentId)
                    .status("RUNNING")
                    .startTime(startTime)
                    .endTime(endTime)
                    .build();

        } catch (Exception e) {
            log.error("Failed to trigger execution on agent: {}", agentId, e);

            // Agent 상태 복구
            agent.setStatus(AgentStatus.ONLINE);
            agent.setLastExecutionStatus("FAILED");
            agentRepository.save(agent);

            throw new RuntimeException("Failed to trigger execution: " + e.getMessage(), e);
        }
    }

    /**
     * executionId에서 agentId 추출
     * 형식: {agentId}_{uuid}
     */
    public String extractAgentIdFromExecutionId(String executionId) {
        int lastUnderscoreIndex = executionId.lastIndexOf('_');
        if (lastUnderscoreIndex == -1) {
            throw new IllegalArgumentException("Invalid executionId format: " + executionId);
        }
        return executionId.substring(0, lastUnderscoreIndex);
    }

    // ==================== ExecutionHistory 관련 메서드 ====================

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
     * Agent별 실행 이력 조회 (Orchestrator DB에서)
     */
    public List<ExecutionDto.HistoryResponse> getHistoryByAgent(String agentId) {
        return executionHistoryRepository.findByAgentIdOrderByStartedAtDesc(agentId).stream()
                .map(ExecutionDto.HistoryResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 실행 이력 페이징 조회
     */
    public Page<ExecutionDto.HistoryResponse> getHistoryPaged(Pageable pageable) {
        return executionHistoryRepository.findAllByOrderByStartedAtDesc(pageable)
                .map(ExecutionDto.HistoryResponse::from);
    }

    /**
     * Agent별 실행 이력 페이징 조회
     */
    public Page<ExecutionDto.HistoryResponse> getHistoryByAgentPaged(String agentId, Pageable pageable) {
        return executionHistoryRepository.findByAgentIdOrderByStartedAtDesc(agentId, pageable)
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
