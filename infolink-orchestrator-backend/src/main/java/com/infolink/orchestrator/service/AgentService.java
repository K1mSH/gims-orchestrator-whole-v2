package com.infolink.orchestrator.service;

import com.infolink.orchestrator.dto.AgentDto;
import com.infolink.orchestrator.dto.DatasourceDto;
import com.infolink.orchestrator.entity.Agent;
import com.infolink.orchestrator.entity.AgentTable;
import com.infolink.orchestrator.entity.AgentStatus;
import com.infolink.orchestrator.entity.AgentType;
import com.infolink.orchestrator.entity.DatasourceTable;
import com.infolink.orchestrator.repository.AgentRepository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infolink.agent.common.datasource.PasswordEncryptor;
import com.infolink.orchestrator.entity.Datasource;
import com.infolink.orchestrator.repository.DatasourceRepository;
import com.infolink.orchestrator.repository.DatasourceTableRepository;
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
    private final DatasourceTableRepository tableRepository;
    private final PasswordEncryptor passwordEncryptor;
    private final RestTemplate restTemplate;
    private final EntityManager entityManager;
    private final ObjectMapper objectMapper;

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
                .orElseThrow(() -> new IllegalArgumentException("Agent를 찾을 수 없습니다: " + id));
        return AgentDto.Response.from(agent);
    }

    public AgentDto.Response findByAgentCode(String agentCode) {
        Agent agent = agentRepository.findByAgentCode(agentCode)
                .orElseThrow(() -> new IllegalArgumentException("Agent를 찾을 수 없습니다: " + agentCode));
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

        // 테이블 연결: 테이블명 기반 자동 연결 우선, 없으면 기존 ID 방식
        if (request.getSourceTableNames() != null && !request.getSourceTableNames().isEmpty()) {
            List<Long> ids = resolveTableIds(request.getSourceDatasourceId(), request.getSourceTableNames());
            addAgentTables(agent, ids, AgentTable.TableType.SOURCE);
        } else {
            addAgentTables(agent, request.getSourceTableIds(), AgentTable.TableType.SOURCE);
        }

        if (request.getTargetTableNames() != null && !request.getTargetTableNames().isEmpty()) {
            List<Long> ids = resolveTableIds(request.getTargetDatasourceId(), request.getTargetTableNames());
            addAgentTables(agent, ids, AgentTable.TableType.TARGET);
        } else {
            addAgentTables(agent, request.getTargetTableIds(), AgentTable.TableType.TARGET);
        }

        Agent saved = agentRepository.save(agent);
        return AgentDto.Response.from(saved);
    }

    /**
     * 테이블명 → datasource_table ID 변환 (없으면 자동 등록)
     */
    private List<Long> resolveTableIds(String datasourceId, List<String> tableNames) {
        List<Long> ids = new ArrayList<>();
        if (datasourceId == null || tableNames == null) return ids;

        for (String tableName : tableNames) {
            DatasourceTable dt = tableRepository.findByDatasourceIdAndTableName(datasourceId, tableName)
                    .orElseGet(() -> {
                        // 자동 등록
                        DatasourceTable newTable = DatasourceTable.builder()
                                .datasourceId(datasourceId)
                                .tableName(tableName)
                                .build();
                        return tableRepository.save(newTable);
                    });
            ids.add(dt.getId());
        }
        return ids;
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
            log.info("Agent 탐색 중: {}", healthUrl);

            ResponseEntity<Map> response = restTemplate.getForEntity(healthUrl, Map.class);
            Map<String, Object> healthData = response.getBody();

            if (healthData == null) {
                result.put("error", "Agent로부터 빈 응답을 받았습니다");
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

            // DB_CON_PROXY (프록시 Agent는 파이프라인 없이 단일 Agent)
            String agentType = (String) healthData.get("type");
            if ("DB_CON_PROXY".equals(agentType)) {
                String appName = (String) healthData.get("appName");
                if (appName != null) {
                    agents.add(Map.of(
                            "agentCode", appName,
                            "type", "DB_CON_PROXY",
                            "registered", registeredCodes.contains(appName)
                    ));
                }
            }

            result.put("agents", agents);

            // step/테이블 정보 조회 (/api/pipeline/info)
            try {
                String infoUrl = endpointUrl.endsWith("/") ? endpointUrl + "api/pipeline/info" : endpointUrl + "/api/pipeline/info";
                ResponseEntity<List> infoResponse = restTemplate.getForEntity(infoUrl, List.class);
                if (infoResponse.getBody() != null) {
                    result.put("agentInfo", infoResponse.getBody());
                }
            } catch (Exception infoEx) {
                log.warn("Agent info 조회 실패 (등록은 가능): {}", infoEx.getMessage());
            }

            log.info("{}개의 Agent를 탐색했습니다: {}", agents.size(), endpointUrl);

        } catch (Exception e) {
            log.error("Agent 탐색 실패 {}: {}", endpointUrl, e.getMessage());
            result.put("error", "Agent 연결 실패: " + e.getMessage());
        }

        return result;
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
                .orElseThrow(() -> new IllegalArgumentException("Agent를 찾을 수 없습니다: " + id));

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

        return AgentDto.Response.from(agent);
    }

    /**
     * pipeline/info 기반 agent_table 동기화 (추가만, 삭제 안 함)
     * YAML의 테이블 중 datasource_table에 등록된 것만 agent_table에 연결
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> syncAgentTables(Long agentId) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent를 찾을 수 없습니다: " + agentId));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("agentCode", agent.getAgentCode());

        try {
            String infoUrl = agent.getEndpointUrl() + "/api/pipeline/info";
            ResponseEntity<List> infoResponse = restTemplate.getForEntity(infoUrl, List.class);
            List<Map<String, Object>> infoList = infoResponse.getBody();
            if (infoList == null) {
                result.put("status", "NO_DATA");
                return result;
            }

            // 해당 agentCode의 steps 찾기
            Map<String, Object> agentInfo = infoList.stream()
                    .filter(info -> agent.getAgentCode().equals(((Map<String, Object>) info).get("agentCode")))
                    .map(info -> (Map<String, Object>) info)
                    .findFirst().orElse(null);

            if (agentInfo == null) {
                result.put("status", "AGENT_NOT_FOUND");
                return result;
            }

            List<Map<String, Object>> steps = (List<Map<String, Object>>) agentInfo.get("steps");
            if (steps == null) {
                result.put("status", "NO_STEPS");
                return result;
            }

            // YAML에서 source/target 테이블명 수집
            Set<String> yamlSourceTables = new LinkedHashSet<>();
            Set<String> yamlTargetTables = new LinkedHashSet<>();
            for (Map<String, Object> step : steps) {
                List<String> src = (List<String>) step.get("sourceTables");
                List<String> tgt = (List<String>) step.get("targetTables");
                if (src != null) yamlSourceTables.addAll(src);
                if (tgt != null) yamlTargetTables.addAll(tgt);
            }

            // 기존 agent_table에 연결된 datasource_table ID
            Set<Long> existingTableIds = agent.getAgentTables().stream()
                    .map(AgentTable::getDatasourceTableId)
                    .collect(Collectors.toSet());

            int added = 0;

            // Source 테이블 매칭 + 연결
            if (agent.getSourceDatasourceId() != null) {
                for (String tableName : yamlSourceTables) {
                    Optional<DatasourceTable> dt = tableRepository.findByDatasourceIdAndTableName(
                            agent.getSourceDatasourceId(), tableName);
                    if (dt.isPresent() && !existingTableIds.contains(dt.get().getId())) {
                        AgentTable at = AgentTable.builder()
                                .agent(agent)
                                .datasourceTableId(dt.get().getId())
                                .tableType(AgentTable.TableType.SOURCE)
                                .build();
                        agent.getAgentTables().add(at);
                        existingTableIds.add(dt.get().getId());
                        added++;
                        log.info("[테이블 갱신] {} SOURCE 추가: {}", agent.getAgentCode(), tableName);
                    }
                }
            }

            // Target 테이블 매칭 + 연결
            if (agent.getTargetDatasourceId() != null) {
                for (String tableName : yamlTargetTables) {
                    Optional<DatasourceTable> dt = tableRepository.findByDatasourceIdAndTableName(
                            agent.getTargetDatasourceId(), tableName);
                    if (dt.isPresent() && !existingTableIds.contains(dt.get().getId())) {
                        AgentTable at = AgentTable.builder()
                                .agent(agent)
                                .datasourceTableId(dt.get().getId())
                                .tableType(AgentTable.TableType.TARGET)
                                .build();
                        agent.getAgentTables().add(at);
                        existingTableIds.add(dt.get().getId());
                        added++;
                        log.info("[테이블 갱신] {} TARGET 추가: {}", agent.getAgentCode(), tableName);
                    }
                }
            }

            agentRepository.save(agent);
            result.put("status", "OK");
            result.put("added", added);
            result.put("yamlSourceTables", yamlSourceTables);
            result.put("yamlTargetTables", yamlTargetTables);

        } catch (Exception e) {
            log.warn("[테이블 갱신] {} 실패: {}", agent.getAgentCode(), e.getMessage());
            result.put("status", "ERROR");
            result.put("error", e.getMessage());
        }

        return result;
    }

    @Transactional
    public void delete(Long id) {
        if (!agentRepository.existsById(id)) {
            throw new IllegalArgumentException("Agent를 찾을 수 없습니다: " + id);
        }
        agentRepository.deleteById(id);
    }

    @Transactional
    public AgentDto.HealthCheckResponse healthCheck(Long id) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Agent를 찾을 수 없습니다: " + id));

        AgentStatus previousStatus = agent.getStatus();
        AgentStatus newStatus = AgentStatus.OFFLINE;
        String message = "";

        try {
            String healthUrl = agent.getEndpointUrl() + "/health";
            log.info("Agent {} ({}) 상태 확인 중: {}", agent.getAgentCode(), id, healthUrl);

            ResponseEntity<String> response = restTemplate.getForEntity(healthUrl, String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                newStatus = AgentStatus.ONLINE;
                if (previousStatus == AgentStatus.RUNNING) {
                    message = "Agent 온라인 (RUNNING 상태에서 복구됨)";
                } else {
                    message = "Agent 온라인";
                }
            } else {
                message = "Agent가 비정상 상태 코드를 반환했습니다";
            }
            log.info("Agent {} 상태 확인 결과: {} (이전: {})", agent.getAgentCode(), newStatus, previousStatus);
        } catch (Exception e) {
            log.warn("Agent {} 상태 확인 실패: {}", agent.getAgentCode(), e.getMessage());
            message = "연결 실패: " + e.getMessage();
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
                .orElseThrow(() -> new IllegalArgumentException("Agent를 찾을 수 없습니다: " + id));
        agent.setStatus(status);
        agentRepository.save(agent);
    }

    /**
     * Agent의 retention(자동삭제) 설정 조회 (DB)
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getRetentionConfig(Long id) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Agent를 찾을 수 없습니다: " + id));
        String json = agent.getRetentionConfig();
        if (json == null || json.isBlank()) {
            return Map.of("enabled", false, "targets", java.util.Collections.emptyList());
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            log.warn("Retention config 파싱 실패: agent={}", agent.getAgentCode(), e);
            return Map.of("enabled", false, "targets", java.util.Collections.emptyList());
        }
    }

    /**
     * Agent의 retention(자동삭제) 설정 저장
     */
    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> updateRetentionConfig(Long id, String configJson) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Agent를 찾을 수 없습니다: " + id));

        // retentionDays 음수/0 방어 + retention-candidates 화이트리스트 검증
        // 룰: targets 가 있으면 적용 (enabled 필드 deprecate)
        // dev_plan/2026_05/08/retention-candidates-safety.md §3-3 layer C
        try {
            Map<String, Object> parsed = objectMapper.readValue(configJson, Map.class);
            List<Map<String, Object>> targets = (List<Map<String, Object>>) parsed.get("targets");

            if (targets != null && !targets.isEmpty()) {
                List<Map<String, Object>> candidates = getRetentionCandidates(id);
                java.util.Set<String> allowed = candidates.stream()
                        .map(c -> c.get("table") + ":" + c.get("dateColumn"))
                        .collect(java.util.stream.Collectors.toSet());

                if (allowed.isEmpty()) {
                    throw new IllegalArgumentException(
                            "이 Agent 는 retention 비대상입니다 (yml retention-candidates 비어있음). agent=" + agent.getAgentCode());
                }
                for (Map<String, Object> t : targets) {
                    String key = t.get("table") + ":" + t.get("dateColumn");
                    if (!allowed.contains(key)) {
                        throw new IllegalArgumentException(
                                "retention-candidates 외 (table, dateColumn) — yml 정합 필요. agent=" + agent.getAgentCode() + ", 입력=" + key);
                    }
                    int days = t.get("retentionDays") != null ? ((Number) t.get("retentionDays")).intValue() : 365;
                    if (days < 1) {
                        throw new IllegalArgumentException(
                                "retentionDays는 1 이상이어야 합니다. (입력값: " + days + ", 테이블: " + t.get("table") + ")");
                    }
                }
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException("Retention config JSON 파싱 실패: " + e.getMessage());
        }

        agent.setRetentionConfig(configJson);
        agentRepository.save(agent);
        log.info("Retention config 저장: agent={}", agent.getAgentCode());
        try {
            return objectMapper.readValue(configJson, Map.class);
        } catch (Exception e) {
            return Map.of("saved", true);
        }
    }

    /**
     * Agent의 source DB에 테스트 데이터 생성 요청
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> generateTestData(Long id, int count) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Agent를 찾을 수 없습니다: " + id));

        if (agent.getSourceDatasourceId() == null) {
            return Map.of("error", "Agent에 Source 데이터소스가 설정되지 않았습니다: " + agent.getAgentCode());
        }

        Datasource datasource = datasourceRepository.findById(agent.getSourceDatasourceId())
                .orElseThrow(() -> new IllegalArgumentException("데이터소스를 찾을 수 없습니다: " + agent.getSourceDatasourceId()));

        try {
            String url = agent.getEndpointUrl() + "/api/test/generate-data?count=" + count;
            log.info("Agent {} 테스트 데이터 생성 요청 중: {}", agent.getAgentCode(), url);

            Map<String, Object> request = buildDatasourceRequest(datasource);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            log.info("Agent {} 테스트 데이터 생성 결과: {}", agent.getAgentCode(), response.getBody());

            return response.getBody();
        } catch (Exception e) {
            log.error("Agent {} 테스트 데이터 생성 실패: {}", agent.getAgentCode(), e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * Agent의 source DB 테스트 데이터 정리 요청
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> clearTestData(Long id) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Agent를 찾을 수 없습니다: " + id));

        if (agent.getSourceDatasourceId() == null) {
            return Map.of("error", "Agent에 Source 데이터소스가 설정되지 않았습니다: " + agent.getAgentCode());
        }

        Datasource datasource = datasourceRepository.findById(agent.getSourceDatasourceId())
                .orElseThrow(() -> new IllegalArgumentException("데이터소스를 찾을 수 없습니다: " + agent.getSourceDatasourceId()));

        try {
            String url = agent.getEndpointUrl() + "/api/test/clear-data";
            log.info("Agent {} 테스트 데이터 정리 요청 중: {}", agent.getAgentCode(), url);

            Map<String, Object> request = buildDatasourceRequest(datasource);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            return response.getBody() != null ? response.getBody() : Map.of("message", "테스트 데이터가 정리되었습니다");
        } catch (Exception e) {
            log.error("Agent {} 테스트 데이터 정리 실패: {}", agent.getAgentCode(), e.getMessage());
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * Agent YML에 정의된 select-tables(WHERE 조건 대상 테이블) 목록 조회.
     * Agent API를 호출하여 테이블명 목록을 받고, Orchestrator에 등록된 DatasourceTable과 매칭하여 컬럼 정보를 포함해 반환.
     */
    /**
     * Agent yml 의 retention-candidates 조회 (보존 정책 dropdown 단일 진실원).
     * Agent 가 자기 yml 의 retention-candidates 를 그대로 응답. 빈 배열 = retention 비대상.
     * dev_plan/2026_05/08/retention-candidates-safety.md
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getRetentionCandidates(Long agentId) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent를 찾을 수 없습니다: " + agentId));
        try {
            String url = agent.getEndpointUrl() + "/api/pipeline/" + agent.getAgentCode() + "/retention-candidates";
            log.info("Agent에서 retention-candidates 조회 중: {}", url);
            ResponseEntity<List> response = restTemplate.getForEntity(url, List.class);
            return response.getBody() != null ? response.getBody() : List.of();
        } catch (Exception e) {
            log.warn("Agent {} retention-candidates 조회 실패: {}", agent.getAgentCode(), e.getMessage());
            return List.of();
        }
    }

    public List<DatasourceDto.TableResponse> getSelectTables(Long agentId) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("Agent를 찾을 수 없습니다: " + agentId));

        // 1. Agent에 select-tables 요청
        List<String> tableNames;
        try {
            String url = agent.getEndpointUrl() + "/api/pipeline/" + agent.getAgentCode() + "/select-tables";
            log.info("Agent에서 select-tables 조회 중: {}", url);
            ResponseEntity<List> response = restTemplate.getForEntity(url, List.class);
            tableNames = response.getBody() != null ? response.getBody() : List.of();
        } catch (Exception e) {
            log.warn("Agent {} select-tables 조회 실패: {}", agent.getAgentCode(), e.getMessage());
            return List.of();
        }

        if (tableNames.isEmpty()) return List.of();

        // 2. 등록된 DatasourceTable과 매칭 (source + target 양쪽 datasource에서 검색)
        List<DatasourceDto.TableResponse> result = new ArrayList<>();
        List<String> datasourceIds = new ArrayList<>();
        if (agent.getSourceDatasourceId() != null) datasourceIds.add(agent.getSourceDatasourceId());
        if (agent.getTargetDatasourceId() != null) datasourceIds.add(agent.getTargetDatasourceId());

        for (String tableName : tableNames) {
            boolean found = false;
            for (String dsId : datasourceIds) {
                var tableOpt = tableRepository.findByDatasourceIdAndTableName(dsId, tableName);
                if (tableOpt.isPresent()) {
                    result.add(DatasourceDto.TableResponse.from(tableOpt.get()));
                    found = true;
                    break;
                }
            }
            if (!found) {
                log.debug("select-table '{}'이(가) 등록된 테이블에서 발견되지 않았습니다", tableName);
            }
        }

        return result;
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
        request.put("username", datasource.getUsername());
        request.put("password", datasource.getPassword());
        return request;
    }
}
