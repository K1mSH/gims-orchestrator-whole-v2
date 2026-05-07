package com.infolink.agent.others.config.pipeline;

import com.infolink.agent.common.model.TableMapping;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class AgentDefinition {

    private String agentCode;
    private String type;  // SND

    private List<Map<String, Object>> steps = new ArrayList<>();
    private List<String> selectTables = new ArrayList<>();
    private List<TableMapping> tableMappings = new ArrayList<>();
}
