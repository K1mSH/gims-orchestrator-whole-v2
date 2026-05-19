package com.infolink.orchestrator.service;

import com.infolink.orchestrator.dto.ExecutionDto;
import com.infolink.orchestrator.entity.ExecutionHistory;
import com.infolink.orchestrator.entity.ExecutionStatus;
import com.infolink.orchestrator.entity.ExecutionStepHistory;
import com.infolink.orchestrator.repository.ExecutionHistoryRepository;
import com.infolink.orchestrator.repository.ExecutionStepHistoryRepository;

import com.infolink.agent.common.datasource.PasswordEncryptor;
import com.infolink.orchestrator.entity.Agent;
import com.infolink.orchestrator.repository.AgentRepository;
import com.infolink.orchestrator.entity.AgentStatus;
import com.infolink.orchestrator.entity.AgentTable;
import com.infolink.orchestrator.entity.Datasource;
import com.infolink.orchestrator.repository.DatasourceRepository;
import com.infolink.orchestrator.entity.DatasourceTable;
import com.infolink.orchestrator.repository.DatasourceTableRepository;
import com.infolink.orchestrator.entity.ZoneConfig;
import com.infolink.orchestrator.repository.ZoneConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
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
    private final PasswordEncryptor passwordEncryptor;
    private final RestTemplate restTemplate;

    /**
     * Agent별 실행 이력 조회 (프록시 Agent 경유)
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> findByAgentIdFromAgent(Long id) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Agent를 찾을 수 없습니다: " + id));

        try {
            String proxyUrl = getProxyUrlForAgent(agent);
            String url = proxyUrl + "/api/execution-data";
            log.info("프록시에서 실행 이력 조회 중: {}", url);

            ResponseEntity<List> response = restTemplate.exchange(url, HttpMethod.GET, buildHeaders(agent), List.class);
            return response.getBody() != null ? response.getBody() : List.of();
        } catch (Exception e) {
            log.error("Agent {} 실행 이력 조회 실패: {}", agent.getAgentCode(), e);
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
     * 실행 상세 정보 조회 (프록시 Agent 경유)
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getExecutionDetail(String executionId) {
        Agent agent = findAgentByExecutionId(executionId);

        try {
            String proxyUrl = getProxyUrlForAgent(agent);
            String url = proxyUrl + "/api/execution-data/" + executionId;
            log.info("프록시에서 실행 상세 조회 중: {}", url);

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, buildHeaders(agent), Map.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Agent {} 실행 상세 조회 실패: {}", agent.getAgentCode(), e);
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * 실행 데이터 조회 (프록시 Agent 경유) - 페이징/검색 지원
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
                default -> throw new IllegalArgumentException("알 수 없는 데이터 타입: " + dataType);
            }

            // 페이징/검색 파라미터 추가 (source, target-if, target만 해당)
            if (searchParams != null && (dataType.equals("source") || dataType.equals("target-if") || dataType.equals("target"))) {
                endpoint.append("?").append(searchParams.toQueryString());
            }

            String proxyUrl = getProxyUrlForAgent(agent);
            String url = proxyUrl + endpoint;
            log.info("프록시에서 실행 데이터 조회 중: {}", url);

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, buildHeaders(agent), Map.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Agent {} 실행 데이터 조회 실패: {}", agent.getAgentCode(), e);
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * 테이블별 통계 조회 (프록시 Agent 경유)
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getTableStats(String executionId) {
        Agent agent = findAgentByExecutionId(executionId);

        try {
            String proxyUrl = getProxyUrlForAgent(agent);
            String url = proxyUrl + "/api/execution-data/" + executionId + "/tables";
            log.info("프록시에서 테이블 통계 조회 중: {}", url);

            ResponseEntity<List> response = restTemplate.exchange(url, HttpMethod.GET, buildHeaders(agent), List.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Agent {} 테이블 통계 조회 실패: {}", agent.getAgentCode(), e);
            return List.of();
        }
    }

    /**
     * 특정 테이블의 레코드 조회 (프록시 Agent 경유)
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getTableRecords(String executionId, String tableName) {
        Agent agent = findAgentByExecutionId(executionId);

        try {
            String proxyUrl = getProxyUrlForAgent(agent);
            String url = proxyUrl + "/api/execution-data/" + executionId + "/tables/" + tableName;
            log.info("프록시에서 테이블 레코드 조회 중: {}", url);

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, buildHeaders(agent), Map.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Agent {} 테이블 레코드 조회 실패: {}", agent.getAgentCode(), e);
            return Map.of();
        }
    }

    /**
     * 특정 테이블의 실패 레코드 조회 (프록시 Agent 경유)
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getTableFailedRecords(String executionId, String tableName) {
        Agent agent = findAgentByExecutionId(executionId);

        try {
            String proxyUrl = getProxyUrlForAgent(agent);
            String url = proxyUrl + "/api/execution-data/" + executionId + "/tables/" + tableName + "/failed";
            log.info("프록시에서 테이블 실패 레코드 조회 중: {}", url);

            ResponseEntity<List> response = restTemplate.exchange(url, HttpMethod.GET, buildHeaders(agent), List.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Agent {} 테이블 실패 레코드 조회 실패: {}", agent.getAgentCode(), e);
            return List.of();
        }
    }

    /**
     * Source PK로 데이터 추적 (Source 행 → Target 행, 프록시 Agent 경유).
     * **정의기반 정확 동등성** 매칭만 (cf. feedback_trace_definition_only). LIKE/contains 일체 없음.
     *
     * 흐름:
     *   1. agent.agentTables 에서 sourceTable 정확매칭(equalsIgnoreCase) SOURCE DatasourceTable 검색
     *   2. Datasource → zone shortCode, datasource.id, datasource_table.id 해석
     *   3. exactSourceRefs = ["{zone}:{dsId}:{tableId}:{pk}"] 빌드 (writer 가 작성한 형식과 문자 단위 동일)
     *      - pk = pkValue 콤마구분 split → "|" join (frontend 가 DB 제약조건 순으로 보냄)
     *   4. proxy 로 exactSourceRefs 전달 → proxy 가 `WHERE source_refs = ?` 정확 동등 매칭
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> traceBySourcePk(String executionId, String pkValue, String pkColumn, String sourceTable, String ifTableName, String targetTableName) {
        Agent agent = findAgentByExecutionId(executionId);

        if (sourceTable == null || sourceTable.isBlank()) {
            return Map.of("error", "sourceTable 파라미터가 필요합니다.");
        }

        // 1. agent SOURCE DatasourceTable 정확매칭
        DatasourceTable srcDt = null;
        if (agent.getAgentTables() != null) {
            for (AgentTable at : agent.getAgentTables()) {
                if (at.getTableType() != AgentTable.TableType.SOURCE) continue;
                DatasourceTable dt = datasourceTableRepository.findById(at.getDatasourceTableId()).orElse(null);
                if (dt != null && sourceTable.equalsIgnoreCase(dt.getTableName())) {
                    srcDt = dt;
                    break;
                }
            }
        }
        if (srcDt == null) {
            return Map.of("error", "agent '" + agent.getAgentCode() + "' 의 SOURCE 테이블에 '" + sourceTable + "' 등록 없음 (정의기반 추적 — agent_tables 정의 확인 필요)");
        }

        // 2. Datasource → zone shortCode + dsId
        Datasource srcDs = datasourceRepository.findById(srcDt.getDatasourceId()).orElse(null);
        if (srcDs == null) {
            return Map.of("error", "Source datasource(id=" + srcDt.getDatasourceId() + ") 등록 없음");
        }
        String zoneShortCode = zoneConfigRepository.findShortCodeByZone(srcDs.getZone());
        if (zoneShortCode == null || zoneShortCode.isBlank()) {
            return Map.of("error", "Source datasource zone '" + srcDs.getZone() + "' 의 shortCode 등록 없음 (zone_config)");
        }
        Long dsId = srcDs.getId();
        Long tableId = srcDt.getId();

        // 3. exactSourceRefs 빌드 — SourceRefUtils.build 와 동일 형식
        //    pkValue 콤마구분 → "|" join (frontend 의 findPkColumns 순서 = DB 제약조건 순서)
        String pk = pkValue == null ? "" : pkValue.replace(",", "|");
        String exactSourceRefs = "[\"" + zoneShortCode + ":" + dsId + ":" + tableId + ":" + pk + "\"]";

        try {
            String proxyUrl = getProxyUrlForAgent(agent);
            UriComponentsBuilder builder = UriComponentsBuilder
                    .fromHttpUrl(proxyUrl)
                    .path("/api/execution-data/{executionId}/trace")
                    .queryParam("exactSourceRefs", exactSourceRefs)
                    .queryParam("sourceTable", sourceTable)
                    // 진단·표시용 (proxy 의 결정 로직엔 안 쓰임)
                    .queryParam("pkValue", pkValue)
                    .queryParam("pkColumn", pkColumn);

            String url = builder.buildAndExpand(executionId).toUriString();
            log.info("Agent에서 데이터 추적 중: {} (exactSourceRefs={})", url, exactSourceRefs);

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, buildHeaders(agent), Map.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Agent {} 데이터 추적 실패: {}", agent.getAgentCode(), e);
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * Target에서 Source로 역추적 (sourceRefs 기반, 프록시 Agent 경유)
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> traceToSource(String executionId, String sourceRefs, String sourceTable) {
        Agent agent = findAgentByExecutionId(executionId);

        try {
            String proxyUrl = getProxyUrlForAgent(agent);
            UriComponentsBuilder builder = UriComponentsBuilder
                    .fromHttpUrl(proxyUrl)
                    .path("/api/execution-data/{executionId}/trace-source")
                    .queryParam("sourceRefs", sourceRefs);

            if (sourceTable != null && !sourceTable.isBlank()) {
                builder.queryParam("sourceTable", sourceTable);
            }

            String url = builder.buildAndExpand(executionId).toUriString();
            log.info("Agent에서 Source 역추적 중: {}", url);

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, buildHeaders(agent), Map.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("Agent {} Source 역추적 실패: {}", agent.getAgentCode(), e);
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
     * 실행 트리거 (시간 범위 + 필터 + Step 선택 + 동적 조건)
     */
    public ExecutionDto.TriggerResponse triggerExecution(Long id, LocalDateTime startTime, LocalDateTime endTime,
                                                          List<Map<String, Object>> filters,
                                                          List<String> selectedStepIds,
                                                          List<Map<String, Object>> conditions, String triggeredBy) {
        return triggerExecutionInternal(id, startTime, endTime, filters, selectedStepIds, conditions, triggeredBy);
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
                                                                  List<Map<String, Object>> conditions,
                                                                  String triggeredBy) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Agent를 찾을 수 없습니다: " + id));

        String agentCode = agent.getAgentCode();

        if (agent.getStatus() == AgentStatus.OFFLINE) {
            throw new IllegalStateException("Agent가 오프라인 상태입니다: " + agentCode);
        }

        if (agent.getStatus() == AgentStatus.RUNNING) {
            throw new IllegalStateException("Agent가 이미 실행 중입니다: " + agentCode);
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
                        .orElseThrow(() -> new IllegalArgumentException("Source 데이터소스를 찾을 수 없습니다: " + agent.getSourceDatasourceId()));
                request.put("sourceDatasourceId", sourceDatasource.getDatasourceId());
                request.put("sourceDbType", sourceDatasource.getDbType().name());
                // credentials는 Agent가 Proxy에서 자체 해석 (보안상 평문 전달 제거)
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
                    log.info("Agent {} Source 테이블 자동 탐색 중", agentCode);
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
                                log.info("Agent {} Source 테이블 {}개 자동 등록 완료", agentCode, sourceTableIds.size());
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Agent {} 테이블 자동 탐색 실패: {}", agentCode, e.getMessage());
                    }
                }

                request.put("sourceTableIds", sourceTableIds);
            }

            // Target Datasource 연결 정보
            if (agent.getTargetDatasourceId() != null) {
                Datasource targetDatasource = datasourceRepository.findById(agent.getTargetDatasourceId())
                        .orElseThrow(() -> new IllegalArgumentException("Target 데이터소스를 찾을 수 없습니다: " + agent.getTargetDatasourceId()));
                request.put("targetDatasourceId", targetDatasource.getDatasourceId());
                request.put("targetDbType", targetDatasource.getDbType().name());
                // credentials는 Agent가 Proxy에서 자체 해석 (보안상 평문 전달 제거)
            }

            // 실행 필터 전달
            if (filters != null && !filters.isEmpty()) {
                request.put("filters", filters);
                log.info("{}개의 필터를 포함하여 실행 요청", filters.size());
            }

            // 선택적 Step 실행
            if (selectedStepIds != null && !selectedStepIds.isEmpty()) {
                request.put("selectedStepIds", selectedStepIds);
                log.info("선택된 Step으로 실행 요청: {}", selectedStepIds);
            }

            // 동적 WHERE 조건
            if (conditions != null && !conditions.isEmpty()) {
                request.put("conditions", conditions);
                log.info("{}개의 조건을 포함하여 실행 요청", conditions.size());
            }

            log.info("Agent {} 실행 요청 - executionId: {}, source: {} (zone={}), target: {}, 시간범위: {} ~ {}",
                    agentCode, executionId, agent.getSourceDatasourceId(), request.get("sourceZone"), agent.getTargetDatasourceId(),
                    startTime != null ? startTime : "default", endTime != null ? endTime : "now");
            ResponseEntity<Void> response = restTemplate.postForEntity(url, request, Void.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("실행 요청 성공: {}", executionId);
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
            log.error("Agent {} 실행 요청 실패", agentCode, e);

            // Agent 상태 복구
            agent.setStatus(AgentStatus.ONLINE);
            agent.setLastExecutionStatus("FAILED");
            agentRepository.save(agent);

            throw new RuntimeException("실행 요청 실패: " + e.getMessage(), e);
        }
    }

    /**
     * executionId에서 agentCode 추출
     * 형식: {agentCode}_{uuid}
     */
    public String extractAgentCodeFromExecutionId(String executionId) {
        int lastUnderscoreIndex = executionId.lastIndexOf('_');
        if (lastUnderscoreIndex == -1) {
            throw new IllegalArgumentException("잘못된 executionId 형식: " + executionId);
        }
        return executionId.substring(0, lastUnderscoreIndex);
    }

    /**
     * executionId에서 Agent 조회 (agentCode 기반)
     */
    private Agent findAgentByExecutionId(String executionId) {
        String agentCode = extractAgentCodeFromExecutionId(executionId);
        return agentRepository.findByAgentCode(agentCode)
                .orElseThrow(() -> new IllegalArgumentException("Agent를 찾을 수 없습니다: " + agentCode));
    }

    /**
     * Agent의 zone에 해당하는 프록시 Agent URL 조회
     * 실행 데이터 조회는 프록시 Agent를 통해 수행
     */
    private String getProxyUrlForAgent(Agent agent) {
        return zoneConfigRepository.findByZoneAndIsActiveTrue(agent.getZone())
                .map(ZoneConfig::getProxyAgentUrl)
                .orElseThrow(() -> new IllegalStateException(
                        "해당 Zone에 프록시 Agent URL이 설정되지 않았습니다: " + agent.getZone()));
    }

    /**
     * Proxy 경유 관리 테이블 조회용 헤더 빌더
     * Agent의 target DB = 관리 DB 규약에 따라 targetDatasourceId를 헤더로 전달
     * (feedback_agent_at_target.md 참조)
     *
     * 기존 Agent(target = Proxy 기본 DB)는 풀 중복이 생기지만 Proxy는 경량 조회라 실질 영향 없음
     */
    private HttpEntity<Void> buildHeaders(Agent agent) {
        HttpHeaders headers = new HttpHeaders();
        String mgmtDs = resolveManagementDatasource(agent);
        if (mgmtDs != null) {
            headers.set("X-Manage-Datasource-Id", mgmtDs);
        }
        return new HttpEntity<>(headers);
    }

    /**
     * Agent 의 management DB(sync_log/execution 적재 위치) 결정.
     * 룰: sync_log 적재 위치 = agent.target_datasource_id ([[feedback_agent_at_target]]).
     * agent 측 SyncLogWriter 도 같은 룰로 target 에 적재하므로 36 agent 일관 통용.
     */
    private String resolveManagementDatasource(Agent agent) {
        return agent != null ? agent.getTargetDatasourceId() : null;
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
                .orElseThrow(() -> new IllegalArgumentException("Agent를 찾을 수 없습니다: " + id));
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
                .orElseThrow(() -> new IllegalArgumentException("Agent를 찾을 수 없습니다: " + id));
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
