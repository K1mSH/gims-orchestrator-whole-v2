package com.infolink.agent.bojo.config.pipeline;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import com.infolink.agent.common.model.TableMapping;

import javax.annotation.PostConstruct;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * config/agents/*.yml 파일을 스캔하여 AgentDefinition 목록으로 변환
 */
@Slf4j
@Component
public class AgentConfigLoader {

    private final List<AgentDefinition> agentDefinitions = new ArrayList<>();

    @PostConstruct
    public void loadAgentConfigs() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:config/agents/*.yml");

            Yaml yaml = new Yaml();

            for (Resource resource : resources) {
                try (InputStream is = resource.getInputStream()) {
                    Map<String, Object> data = yaml.load(is);
                    AgentDefinition def = parseAgentDefinition(data);
                    if (def != null && def.getAgentCode() != null) {
                        agentDefinitions.add(def);
                        log.info("[BojoInt] Agent 설정 로드: {} (type={}, steps={})",
                                def.getAgentCode(), def.getType(), def.getSteps().size());
                    }
                } catch (Exception e) {
                    log.error("[BojoInt] Agent 설정 로드 실패: {}", resource.getFilename(), e);
                }
            }

            log.info("[BojoInt] 총 {} 개 Agent 설정 로드 완료", agentDefinitions.size());
        } catch (Exception e) {
            log.error("[BojoInt] Agent 설정 파일 스캔 실패", e);
        }
    }

    @SuppressWarnings("unchecked")
    private AgentDefinition parseAgentDefinition(Map<String, Object> data) {
        AgentDefinition def = new AgentDefinition();
        def.setAgentCode((String) data.get("agent-code"));
        def.setType((String) data.get("type"));

        // steps — YAML 배열을 그대로 보관 (각 Factory가 자기 필드를 파싱)
        // source-table/target-table은 단일값/리스트 양쪽 지원하여 리스트로 정규화
        List<Map<String, Object>> stepsList = (List<Map<String, Object>>) data.get("steps");
        if (stepsList != null) {
            for (Map<String, Object> step : stepsList) {
                step.put("source-table", normalizeToList(step.get("source-table")));
                step.put("target-table", normalizeToList(step.get("target-table")));
            }
            def.setSteps(stepsList);
        }

        // select-tables: 수동 정의 우선, 없으면 steps의 source-table 자동 수집
        List<String> selectTables = (List<String>) data.get("select-tables");
        if (selectTables != null && !selectTables.isEmpty()) {
            def.setSelectTables(selectTables);
        } else {
            def.setSelectTables(collectSourceTables(def.getSteps()));
        }

        // table-mappings: 수동 정의 우선, 없으면 steps에서 자동 생성
        List<Map<String, Object>> mappingsList = (List<Map<String, Object>>) data.get("table-mappings");
        if (mappingsList != null && !mappingsList.isEmpty()) {
            for (Map<String, Object> mappingMap : mappingsList) {
                TableMapping mapping = new TableMapping();
                mapping.setName((String) mappingMap.get("name"));
                mapping.setSource((List<String>) mappingMap.get("source"));
                mapping.setTarget((List<String>) mappingMap.get("target"));
                def.getTableMappings().add(mapping);
            }
        } else {
            def.setTableMappings(generateTableMappings(def.getSteps()));
        }

        return def;
    }

    /**
     * 단일값 또는 리스트를 리스트로 정규화
     * "TABLE_A" → ["TABLE_A"], ["TABLE_A", "TABLE_B"] → 그대로
     */
    @SuppressWarnings("unchecked")
    private List<String> normalizeToList(Object value) {
        if (value == null) return new ArrayList<>();
        if (value instanceof List) return (List<String>) value;
        return new ArrayList<>(List.of(value.toString()));
    }

    /**
     * steps의 source-table을 합쳐서 select-tables 생성 (중복 제거, 순서 유지)
     */
    @SuppressWarnings("unchecked")
    private List<String> collectSourceTables(List<Map<String, Object>> steps) {
        List<String> result = new ArrayList<>();
        for (Map<String, Object> step : steps) {
            List<String> sources = (List<String>) step.get("source-table");
            if (sources != null) {
                for (String s : sources) {
                    if (!result.contains(s)) result.add(s);
                }
            }
        }
        return result;
    }

    /**
     * steps에서 table-mappings 자동 생성 (step.id를 매핑 이름으로)
     */
    @SuppressWarnings("unchecked")
    private List<TableMapping> generateTableMappings(List<Map<String, Object>> steps) {
        List<TableMapping> mappings = new ArrayList<>();
        for (Map<String, Object> step : steps) {
            List<String> sources = (List<String>) step.get("source-table");
            List<String> targets = (List<String>) step.get("target-table");
            if (sources != null && !sources.isEmpty() && targets != null && !targets.isEmpty()) {
                TableMapping mapping = new TableMapping();
                mapping.setName((String) step.get("id"));
                mapping.setSource(sources);
                mapping.setTarget(targets);
                mappings.add(mapping);
            }
        }
        return mappings;
    }

    public List<AgentDefinition> getAgentDefinitions() {
        return agentDefinitions;
    }

    public List<AgentDefinition> getAgentsByType(String type) {
        return agentDefinitions.stream()
                .filter(d -> type.equals(d.getType()))
                .toList();
    }
}
