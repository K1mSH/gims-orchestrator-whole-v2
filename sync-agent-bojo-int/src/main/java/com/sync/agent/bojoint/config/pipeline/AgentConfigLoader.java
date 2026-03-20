package com.sync.agent.bojoint.config.pipeline;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import com.sync.agent.common.model.TableMapping;

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
                        log.info("Loaded agent config: {} (type={}, steps={})",
                                def.getAgentCode(), def.getType(), def.getSteps().size());
                    }
                } catch (Exception e) {
                    log.error("Failed to load agent config: {}", resource.getFilename(), e);
                }
            }

            log.info("Total {} agent configs loaded", agentDefinitions.size());
        } catch (Exception e) {
            log.error("Failed to scan agent config files", e);
        }
    }

    @SuppressWarnings("unchecked")
    private AgentDefinition parseAgentDefinition(Map<String, Object> data) {
        AgentDefinition def = new AgentDefinition();
        def.setAgentCode((String) data.get("agent-code"));
        def.setType((String) data.get("type"));

        // steps — YAML 배열을 그대로 보관 (각 Factory가 자기 필드를 파싱)
        List<Map<String, Object>> stepsList = (List<Map<String, Object>>) data.get("steps");
        if (stepsList != null) {
            def.setSteps(stepsList);
        }

        // select-tables
        List<String> selectTables = (List<String>) data.get("select-tables");
        if (selectTables != null) {
            def.setSelectTables(selectTables);
        }

        // table-mappings
        List<Map<String, Object>> mappingsList = (List<Map<String, Object>>) data.get("table-mappings");
        if (mappingsList != null) {
            for (Map<String, Object> mappingMap : mappingsList) {
                TableMapping mapping = new TableMapping();
                mapping.setName((String) mappingMap.get("name"));
                mapping.setSource((List<String>) mappingMap.get("source"));
                mapping.setTarget((List<String>) mappingMap.get("target"));
                def.getTableMappings().add(mapping);
            }
        }

        return def;
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
