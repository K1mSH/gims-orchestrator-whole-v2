package com.sync.agent.bojo.config.pipeline;

import com.sync.agent.common.model.TableMapping;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 개별 Agent YAML 파일의 파싱 결과를 담는 POJO
 *
 * config/agents/*.yml의 내용을 파싱.
 * steps 배열이 핵심 — factory-key로 StepFactory를 찾아 Step을 생성한다.
 */
@Getter
@Setter
public class AgentDefinition {

    private String agentCode;
    private String type;  // RCV, LOADER, SND

    // Step 정의 목록 — YAML steps 배열을 그대로 보관
    private List<Map<String, Object>> steps = new ArrayList<>();

    // select-tables: 프론트 WHERE 조건 드롭다운에 노출할 테이블 목록
    private List<String> selectTables = new ArrayList<>();

    // table-mappings (Source→Target 관계 명시, 프론트 모니터링용)
    private List<TableMapping> tableMappings = new ArrayList<>();
}
