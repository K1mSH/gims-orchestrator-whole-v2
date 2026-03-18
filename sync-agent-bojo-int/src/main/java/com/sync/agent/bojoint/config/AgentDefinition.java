package com.sync.agent.bojoint.config;

import com.sync.agent.common.model.TableMapping;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 개별 Agent YAML 파일의 파싱 결과를 담는 POJO
 */
@Getter
@Setter
public class AgentDefinition {

    private String agentCode;
    private String type;  // RCV, LOADER

    // RCV 설정
    private TableConfig jewon;
    private TableConfig obsvdata;

    // Loader 전용 설정
    private Map<String, String> ifTable;      // jewon, obsvdata
    private Map<String, String> targetTable;  // jewon, obsvdata, link, result

    // Step 설정 (Loader)
    private StepConfig step;

    // select-tables: 프론트 WHERE 조건 드롭다운에 노출할 테이블 목록
    private List<String> selectTables = new ArrayList<>();

    // table-mappings (Source→Target 관계 명시)
    private List<TableMapping> tableMappings = new ArrayList<>();

    @Getter
    @Setter
    public static class TableConfig {
        private String sourceTable;
        private String targetTable;
        private String primaryKey;
        private String conflictKey;
        private boolean fullCopy;
        private boolean skipSourceStatusUpdate;
        private String dateColumn;
        private String timeColumn;
    }

    @Getter
    @Setter
    public static class StepConfig {
        private String id;
        private String name;
    }

}
