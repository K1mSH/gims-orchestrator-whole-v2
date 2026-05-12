package com.infolink.agent.others.config.pipeline;

import com.infolink.agent.common.model.RetentionCandidate;
import com.infolink.agent.common.model.TableMapping;
import com.infolink.agent.common.model.WhereFilterDef;
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

    // retention-candidates: 운영자가 retention 설정 시 선택 가능한 (table, dateColumn) 후보
    // 빈 배열 = retention 비대상 Agent (마스터 / Link / 메타 데이터)
    // dev_plan/2026_05/08/retention-candidates-safety.md
    private List<RetentionCandidate> retentionCandidates = new ArrayList<>();

    // where-filters: 수동 실행 WHERE 조건으로 노출/허용할 필터 (테이블+컬럼 큐레이션)
    // 미선언(빈 배열) = 기존 동작 (select-tables 기반 범용 드롭다운). column:"*" = 그 테이블 전체 컬럼 허용
    // dev_plan/2026_05/12/yml-declared-where-filters.md
    private List<WhereFilterDef> whereFilters = new ArrayList<>();
}
