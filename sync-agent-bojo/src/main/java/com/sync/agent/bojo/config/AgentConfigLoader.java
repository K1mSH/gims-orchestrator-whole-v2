package com.sync.agent.bojo.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

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
                        log.info("Loaded agent config: {} (type={})", def.getAgentCode(), def.getType());
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

        // jewon
        Map<String, Object> jewonMap = (Map<String, Object>) data.get("jewon");
        if (jewonMap != null) {
            AgentDefinition.TableConfig jewon = new AgentDefinition.TableConfig();
            jewon.setSourceTable((String) jewonMap.get("source-table"));
            jewon.setTargetTable((String) jewonMap.get("target-table"));
            jewon.setPrimaryKey((String) jewonMap.get("primary-key"));
            jewon.setConflictKey((String) jewonMap.get("conflict-key"));
            jewon.setFullCopy(Boolean.TRUE.equals(jewonMap.get("full-copy")));
            jewon.setSkipSourceStatusUpdate(Boolean.TRUE.equals(jewonMap.get("skip-source-status-update")));
            jewon.setDateColumn((String) jewonMap.get("date-column"));
            jewon.setTimeColumn((String) jewonMap.get("time-column"));
            def.setJewon(jewon);
        }

        // obsvdata
        Map<String, Object> obsvMap = (Map<String, Object>) data.get("obsvdata");
        if (obsvMap != null) {
            AgentDefinition.TableConfig obsv = new AgentDefinition.TableConfig();
            obsv.setSourceTable((String) obsvMap.get("source-table"));
            obsv.setTargetTable((String) obsvMap.get("target-table"));
            obsv.setPrimaryKey((String) obsvMap.get("primary-key"));
            obsv.setConflictKey((String) obsvMap.get("conflict-key"));
            obsv.setFullCopy(Boolean.TRUE.equals(obsvMap.get("full-copy")));
            obsv.setDateColumn((String) obsvMap.get("date-column"));
            obsv.setTimeColumn((String) obsvMap.get("time-column"));
            def.setObsvdata(obsv);
        }

        // link
        Map<String, Object> linkMap = (Map<String, Object>) data.get("link");
        if (linkMap != null) {
            AgentDefinition.LinkConfig link = new AgentDefinition.LinkConfig();
            link.setUseLinkTable(Boolean.TRUE.equals(linkMap.get("use-link-table")));
            link.setTableName((String) linkMap.get("table-name"));
            def.setLink(link);
        }

        // if-table (Loader)
        Map<String, Object> ifTableMap = (Map<String, Object>) data.get("if-table");
        if (ifTableMap != null) {
            java.util.HashMap<String, String> ifTable = new java.util.HashMap<>();
            ifTableMap.forEach((k, v) -> ifTable.put(k, v.toString()));
            def.setIfTable(ifTable);
        }

        // target-table (Loader)
        Map<String, Object> targetTableMap = (Map<String, Object>) data.get("target-table");
        if (targetTableMap != null) {
            java.util.HashMap<String, String> targetTable = new java.util.HashMap<>();
            targetTableMap.forEach((k, v) -> targetTable.put(k, v.toString()));
            def.setTargetTable(targetTable);
        }

        // step (Loader)
        Map<String, Object> stepMap = (Map<String, Object>) data.get("step");
        if (stepMap != null) {
            AgentDefinition.StepConfig step = new AgentDefinition.StepConfig();
            step.setId((String) stepMap.get("id"));
            step.setName((String) stepMap.get("name"));
            def.setStep(step);
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
