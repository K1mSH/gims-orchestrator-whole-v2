package com.sync.orchestrator.domain.agent;

import com.sync.orchestrator.common.CredentialEncryptor;
import com.sync.orchestrator.domain.datasource.Datasource;
import com.sync.orchestrator.domain.datasource.DatasourceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import javax.persistence.EntityManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AgentService {

    private final AgentRepository agentRepository;
    private final DatasourceRepository datasourceRepository;
    private final CredentialEncryptor credentialEncryptor;
    private final RestTemplate restTemplate;
    private final EntityManager entityManager;

    public List<AgentDto.Response> findAll() {
        return agentRepository.findAll().stream()
                .map(AgentDto.Response::from)
                .collect(Collectors.toList());
    }

    public List<AgentDto.Response> findOnlineAgents() {
        return agentRepository.findByStatusNot(AgentStatus.OFFLINE).stream()
                .map(AgentDto.Response::from)
                .collect(Collectors.toList());
    }

    public AgentDto.Response findById(String agentId) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));
        return AgentDto.Response.from(agent);
    }

    @Transactional
    public AgentDto.Response create(AgentDto.CreateRequest request) {
        if (agentRepository.existsById(request.getAgentId())) {
            throw new IllegalArgumentException("Agent already exists: " + request.getAgentId());
        }

        Agent agent = Agent.builder()
                .agentId(request.getAgentId())
                .agentName(request.getAgentName())
                .endpointUrl(request.getEndpointUrl())
                .zone(request.getZone())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .agentType(request.getAgentType() != null ? request.getAgentType() : AgentType.LOADER_CUSTOM)
                .sourceDatasourceId(request.getSourceDatasourceId())
                .targetDatasourceId(request.getTargetDatasourceId())
                .description(request.getDescription())
                .build();

        // 선택된 테이블 추가
        addAgentTables(agent, request.getSourceTableIds(), AgentTable.TableType.SOURCE);
        addAgentTables(agent, request.getTargetTableIds(), AgentTable.TableType.TARGET);

        // 실행 파라미터 추가
        addExecutionParams(agent, request.getExecutionParams());

        Agent saved = agentRepository.save(agent);
        return AgentDto.Response.from(saved);
    }

    private void addExecutionParams(Agent agent, List<AgentDto.ExecutionParamInput> params) {
        if (params == null || params.isEmpty()) return;
        for (AgentDto.ExecutionParamInput input : params) {
            AgentExecutionParam param = AgentExecutionParam.builder()
                    .agent(agent)
                    .paramId(input.getParamId())
                    .label(input.getLabel())
                    .description(input.getDescription())
                    .dataType(input.getDataType() != null ? input.getDataType() : "STRING")
                    .defaultValue(input.getDefaultValue())
                    .isEnabled(input.getIsEnabled() != null ? input.getIsEnabled() : true)
                    .displayOrder(input.getDisplayOrder() != null ? input.getDisplayOrder() : 0)
                    .build();
            agent.getExecutionParams().add(param);
        }
    }

    /**
     * Agent API에서 실행 파라미터 메타데이터 가져오기 (프록시)
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> fetchExecutionParamsFromAgent(String agentId) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));

        try {
            String url = agent.getEndpointUrl() + "/api/pipeline/execution-params";
            log.info("Fetching execution params from agent: {}", url);

            ResponseEntity<List> response = restTemplate.getForEntity(url, List.class);
            return response.getBody() != null ? response.getBody() : List.of();
        } catch (Exception e) {
            log.error("Failed to fetch execution params from agent: {}", agentId, e);
            return List.of();
        }
    }

    /**
     * DB에 저장된 Agent 실행 파라미터 조회
     */
    public List<AgentDto.ExecutionParamResponse> getExecutionParams(String agentId) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));

        return agent.getExecutionParams().stream()
                .map(AgentDto.ExecutionParamResponse::from)
                .toList();
    }

    /**
     * Agent API에서 가져온 실행 파라미터를 DB에 저장 (기존 데이터 교체)
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public List<AgentDto.ExecutionParamResponse> refreshExecutionParams(String agentId) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));

        // Agent API에서 가져오기
        List<Map<String, Object>> fetched = fetchExecutionParamsFromAgent(agentId);

        // 기존 파라미터 제거
        agent.getExecutionParams().clear();
        entityManager.flush();

        // 새로 추가
        for (Map<String, Object> fm : fetched) {
            AgentExecutionParam param = AgentExecutionParam.builder()
                    .agent(agent)
                    .paramId((String) fm.get("paramId"))
                    .label((String) fm.get("label"))
                    .description((String) fm.get("description"))
                    .dataType(fm.get("dataType") != null ? (String) fm.get("dataType") : "STRING")
                    .defaultValue((String) fm.get("defaultValue"))
                    .isEnabled(true)
                    .displayOrder(fm.get("displayOrder") != null ? ((Number) fm.get("displayOrder")).intValue() : 0)
                    .build();
            agent.getExecutionParams().add(param);
        }

        return agent.getExecutionParams().stream()
                .map(AgentDto.ExecutionParamResponse::from)
                .toList();
    }

    private void addAgentTables(Agent agent, List<Long> tableIds, AgentTable.TableType tableType) {
        if (tableIds == null || tableIds.isEmpty()) return;

        for (Long tableId : tableIds) {
            AgentTable agentTable = AgentTable.builder()
                    .agent(agent)
                    .datasourceTableId(tableId)
                    .tableType(tableType)
                    .build();
            agent.getAgentTables().add(agentTable);
        }
    }

    @Transactional
    public AgentDto.Response update(String agentId, AgentDto.UpdateRequest request) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));

        if (request.getAgentName() != null) {
            agent.setAgentName(request.getAgentName());
        }
        if (request.getEndpointUrl() != null) {
            agent.setEndpointUrl(request.getEndpointUrl());
        }
        if (request.getZone() != null) {
            agent.setZone(request.getZone());
        }
        if (request.getIsActive() != null) {
            agent.setIsActive(request.getIsActive());
        }
        if (request.getAgentType() != null) {
            agent.setAgentType(request.getAgentType());
        }
        if (request.getSourceDatasourceId() != null) {
            agent.setSourceDatasourceId(request.getSourceDatasourceId());
        }
        if (request.getTargetDatasourceId() != null) {
            agent.setTargetDatasourceId(request.getTargetDatasourceId());
        }
        if (request.getDescription() != null) {
            agent.setDescription(request.getDescription());
        }
        if (request.getStatus() != null) {
            agent.setStatus(request.getStatus());
        }

        // 테이블 목록 업데이트 (전달된 경우에만)
        if (request.getSourceTableIds() != null || request.getTargetTableIds() != null) {
            agent.getAgentTables().clear();
            // orphanRemoval로 인한 DELETE가 INSERT보다 먼저 실행되도록 flush
            entityManager.flush();
            addAgentTables(agent, request.getSourceTableIds(), AgentTable.TableType.SOURCE);
            addAgentTables(agent, request.getTargetTableIds(), AgentTable.TableType.TARGET);
        }

        // 실행 파라미터 업데이트 (전달된 경우에만)
        if (request.getExecutionParams() != null) {
            agent.getExecutionParams().clear();
            entityManager.flush();
            addExecutionParams(agent, request.getExecutionParams());
        }

        return AgentDto.Response.from(agent);
    }

    @Transactional
    public void delete(String agentId) {
        if (!agentRepository.existsById(agentId)) {
            throw new IllegalArgumentException("Agent not found: " + agentId);
        }
        agentRepository.deleteById(agentId);
    }

    @Transactional
    public AgentDto.HealthCheckResponse healthCheck(String agentId) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));

        AgentStatus previousStatus = agent.getStatus();
        AgentStatus newStatus = AgentStatus.OFFLINE;
        String message = "";

        try {
            String healthUrl = agent.getEndpointUrl() + "/health";
            log.info("Health check for agent {}: {}", agentId, healthUrl);

            ResponseEntity<String> response = restTemplate.getForEntity(healthUrl, String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                // Agent가 응답하면 ONLINE (RUNNING 상태였어도 실제로는 실행 완료된 것)
                newStatus = AgentStatus.ONLINE;
                if (previousStatus == AgentStatus.RUNNING) {
                    message = "Agent is online (recovered from RUNNING state)";
                } else {
                    message = "Agent is online";
                }
            } else {
                message = "Agent returned non-2xx status";
            }
            log.info("Health check result for agent {}: {} (was {})", agentId, newStatus, previousStatus);
        } catch (Exception e) {
            log.warn("Health check failed for agent {}: {}", agentId, e.getMessage());
            message = "Connection failed: " + e.getMessage();
            newStatus = AgentStatus.OFFLINE;
        }

        // 상태 업데이트
        agent.setStatus(newStatus);
        agentRepository.save(agent);

        return AgentDto.HealthCheckResponse.builder()
                .agentId(agentId)
                .status(newStatus)
                .message(message)
                .build();
    }

    @Transactional
    public void updateStatus(String agentId, AgentStatus status) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));
        agent.setStatus(status);
        agentRepository.save(agent);
    }

    /**
     * Agent의 source DB에 테스트 데이터 생성 요청
     * Datasource 연결 정보 전체를 전달
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> generateTestData(String agentId, int count) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));

        if (agent.getSourceDatasourceId() == null) {
            return Map.of("error", "Source datasource not configured for agent: " + agentId);
        }

        Datasource datasource = datasourceRepository.findById(agent.getSourceDatasourceId())
                .orElseThrow(() -> new IllegalArgumentException("Datasource not found: " + agent.getSourceDatasourceId()));

        try {
            String url = agent.getEndpointUrl() + "/api/test/generate-data?count=" + count;
            log.info("Requesting test data generation for agent {}: {}", agentId, url);

            // 연결 정보 전체를 body로 전달
            Map<String, Object> request = buildDatasourceRequest(datasource);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            log.info("Test data generation result for agent {}: {}", agentId, response.getBody());

            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to generate test data for agent {}: {}", agentId, e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * Agent의 source DB 테스트 데이터 정리 요청
     * Datasource 연결 정보 전체를 전달
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> clearTestData(String agentId) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentId));

        if (agent.getSourceDatasourceId() == null) {
            return Map.of("error", "Source datasource not configured for agent: " + agentId);
        }

        Datasource datasource = datasourceRepository.findById(agent.getSourceDatasourceId())
                .orElseThrow(() -> new IllegalArgumentException("Datasource not found: " + agent.getSourceDatasourceId()));

        try {
            String url = agent.getEndpointUrl() + "/api/test/clear-data";
            log.info("Requesting test data clear for agent {}: {}", agentId, url);

            // 연결 정보 전체를 body로 전달 (POST로 변경)
            Map<String, Object> request = buildDatasourceRequest(datasource);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            return response.getBody() != null ? response.getBody() : Map.of("message", "Test data cleared");
        } catch (Exception e) {
            log.error("Failed to clear test data for agent {}: {}", agentId, e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * Datasource 연결 정보를 Map으로 변환 (Agent에 전달용)
     */
    private Map<String, Object> buildDatasourceRequest(Datasource datasource) {
        Map<String, Object> request = new HashMap<>();
        request.put("datasourceId", datasource.getDatasourceId());
        request.put("dbType", datasource.getDbType().name());
        request.put("host", datasource.getHost());
        request.put("port", datasource.getPort());
        request.put("databaseName", datasource.getDatabaseName());
        request.put("username", credentialEncryptor.decrypt(datasource.getUsername()));
        request.put("password", credentialEncryptor.decrypt(datasource.getPassword()));
        return request;
    }
}
