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
import java.util.*;
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

    public AgentDto.Response findById(Long id) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + id));
        return AgentDto.Response.from(agent);
    }

    public AgentDto.Response findByAgentCode(String agentCode) {
        Agent agent = agentRepository.findByAgentCode(agentCode)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + agentCode));
        return AgentDto.Response.from(agent);
    }

    @Transactional
    public AgentDto.Response create(AgentDto.CreateRequest request) {
        // agentCode 중복 체크
        String agentCode = request.getAgentCode();
        if (agentRepository.findByAgentCode(agentCode).isPresent()) {
            throw new IllegalArgumentException("이미 등록된 agentCode입니다: " + agentCode);
        }

        Agent agent = Agent.builder()
                .agentCode(agentCode)
                .agentName(request.getAgentName())
                .endpointUrl(request.getEndpointUrl())
                .zone(request.getZone())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .agentType(request.getAgentType())
                .datasourceTag(request.getDatasourceTag())
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

    /**
     * Agent 프로세스의 /health 엔드포인트를 호출하여 사용 가능한 agentCode 목록을 조회
     * 이미 DB에 등록된 코드에는 registered=true 표시
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> discoverAgents(String endpointUrl) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("endpointUrl", endpointUrl);

        try {
            String healthUrl = endpointUrl.endsWith("/") ? endpointUrl + "health" : endpointUrl + "/health";
            log.info("Discovering agents from: {}", healthUrl);

            ResponseEntity<Map> response = restTemplate.getForEntity(healthUrl, Map.class);
            Map<String, Object> healthData = response.getBody();

            if (healthData == null) {
                result.put("error", "Empty response from agent");
                return result;
            }

            String zone = (String) healthData.get("zone");
            result.put("zone", zone);

            // 이미 등록된 agentCode 목록 조회
            Set<String> registeredCodes = agentRepository.findAll().stream()
                    .map(Agent::getAgentCode)
                    .collect(Collectors.toSet());

            List<Map<String, Object>> agents = new ArrayList<>();

            // RCV agents
            Collection<String> rcvAgents = (Collection<String>) healthData.get("rcvAgents");
            if (rcvAgents != null) {
                for (String code : rcvAgents) {
                    agents.add(Map.of(
                            "agentCode", code,
                            "type", "RCV",
                            "registered", registeredCodes.contains(code)
                    ));
                }
            }

            // Loader agents
            Collection<String> loaderAgents = (Collection<String>) healthData.get("loaderAgents");
            if (loaderAgents != null) {
                for (String code : loaderAgents) {
                    agents.add(Map.of(
                            "agentCode", code,
                            "type", "LOADER",
                            "registered", registeredCodes.contains(code)
                    ));
                }
            }

            // SND agents
            Collection<String> sndAgents = (Collection<String>) healthData.get("sndAgents");
            if (sndAgents != null) {
                for (String code : sndAgents) {
                    agents.add(Map.of(
                            "agentCode", code,
                            "type", "SND",
                            "registered", registeredCodes.contains(code)
                    ));
                }
            }

            result.put("agents", agents);
            log.info("Discovered {} agents from {}", agents.size(), endpointUrl);

        } catch (Exception e) {
            log.error("Failed to discover agents from {}: {}", endpointUrl, e.getMessage());
            result.put("error", "Agent 연결 실패: " + e.getMessage());
        }

        return result;
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
    public List<Map<String, Object>> fetchExecutionParamsFromAgent(Long id) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + id));

        try {
            String url = agent.getEndpointUrl() + "/api/pipeline/execution-params";
            log.info("Fetching execution params from agent: {}", url);

            ResponseEntity<List> response = restTemplate.getForEntity(url, List.class);
            return response.getBody() != null ? response.getBody() : List.of();
        } catch (Exception e) {
            log.error("Failed to fetch execution params from agent: {}", agent.getAgentCode(), e);
            return List.of();
        }
    }

    /**
     * DB에 저장된 Agent 실행 파라미터 조회
     */
    public List<AgentDto.ExecutionParamResponse> getExecutionParams(Long id) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + id));

        return agent.getExecutionParams().stream()
                .map(AgentDto.ExecutionParamResponse::from)
                .toList();
    }

    /**
     * Agent API에서 가져온 실행 파라미터를 DB에 저장 (기존 데이터 교체)
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public List<AgentDto.ExecutionParamResponse> refreshExecutionParams(Long id) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + id));

        // Agent API에서 가져오기
        List<Map<String, Object>> fetched = fetchExecutionParamsFromAgent(id);

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
    public AgentDto.Response update(Long id, AgentDto.UpdateRequest request) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + id));

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
        if (request.getDatasourceTag() != null) {
            agent.setDatasourceTag(request.getDatasourceTag());
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
    public void delete(Long id) {
        if (!agentRepository.existsById(id)) {
            throw new IllegalArgumentException("Agent not found: " + id);
        }
        agentRepository.deleteById(id);
    }

    @Transactional
    public AgentDto.HealthCheckResponse healthCheck(Long id) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + id));

        AgentStatus previousStatus = agent.getStatus();
        AgentStatus newStatus = AgentStatus.OFFLINE;
        String message = "";

        try {
            String healthUrl = agent.getEndpointUrl() + "/health";
            log.info("Health check for agent {} ({}): {}", agent.getAgentCode(), id, healthUrl);

            ResponseEntity<String> response = restTemplate.getForEntity(healthUrl, String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                newStatus = AgentStatus.ONLINE;
                if (previousStatus == AgentStatus.RUNNING) {
                    message = "Agent is online (recovered from RUNNING state)";
                } else {
                    message = "Agent is online";
                }
            } else {
                message = "Agent returned non-2xx status";
            }
            log.info("Health check result for agent {}: {} (was {})", agent.getAgentCode(), newStatus, previousStatus);
        } catch (Exception e) {
            log.warn("Health check failed for agent {}: {}", agent.getAgentCode(), e.getMessage());
            message = "Connection failed: " + e.getMessage();
            newStatus = AgentStatus.OFFLINE;
        }

        agent.setStatus(newStatus);
        agentRepository.save(agent);

        return AgentDto.HealthCheckResponse.builder()
                .id(id)
                .agentCode(agent.getAgentCode())
                .status(newStatus)
                .message(message)
                .build();
    }

    @Transactional
    public void updateStatus(Long id, AgentStatus status) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + id));
        agent.setStatus(status);
        agentRepository.save(agent);
    }

    /**
     * Agent의 source DB에 테스트 데이터 생성 요청
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> generateTestData(Long id, int count) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + id));

        if (agent.getSourceDatasourceId() == null) {
            return Map.of("error", "Source datasource not configured for agent: " + agent.getAgentCode());
        }

        Datasource datasource = datasourceRepository.findById(agent.getSourceDatasourceId())
                .orElseThrow(() -> new IllegalArgumentException("Datasource not found: " + agent.getSourceDatasourceId()));

        try {
            String url = agent.getEndpointUrl() + "/api/test/generate-data?count=" + count;
            log.info("Requesting test data generation for agent {}: {}", agent.getAgentCode(), url);

            Map<String, Object> request = buildDatasourceRequest(datasource);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            log.info("Test data generation result for agent {}: {}", agent.getAgentCode(), response.getBody());

            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to generate test data for agent {}: {}", agent.getAgentCode(), e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * Agent의 source DB 테스트 데이터 정리 요청
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> clearTestData(Long id) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Agent not found: " + id));

        if (agent.getSourceDatasourceId() == null) {
            return Map.of("error", "Source datasource not configured for agent: " + agent.getAgentCode());
        }

        Datasource datasource = datasourceRepository.findById(agent.getSourceDatasourceId())
                .orElseThrow(() -> new IllegalArgumentException("Datasource not found: " + agent.getSourceDatasourceId()));

        try {
            String url = agent.getEndpointUrl() + "/api/test/clear-data";
            log.info("Requesting test data clear for agent {}: {}", agent.getAgentCode(), url);

            Map<String, Object> request = buildDatasourceRequest(datasource);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            return response.getBody() != null ? response.getBody() : Map.of("message", "Test data cleared");
        } catch (Exception e) {
            log.error("Failed to clear test data for agent {}: {}", agent.getAgentCode(), e.getMessage());
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
