package com.infolink.agent.provide.config.pipeline;

import com.infolink.agent.common.model.RetentionCandidate;
import com.infolink.agent.common.model.TableMapping;
import com.infolink.agent.common.model.WhereFilterDef;
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
    private String type;  // RCV, LOADER

    // Step 정의 목록 — YAML steps 배열을 그대로 보관
    private List<Map<String, Object>> steps = new ArrayList<>();

    // select-tables: 프론트 WHERE 조건 드롭다운에 노출할 테이블 목록
    private List<String> selectTables = new ArrayList<>();

    // table-mappings (Source→Target 관계 명시, 프론트 모니터링용)
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
