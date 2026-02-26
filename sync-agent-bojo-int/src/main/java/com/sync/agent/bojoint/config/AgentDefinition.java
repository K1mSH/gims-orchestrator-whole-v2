package com.sync.agent.bojoint.config;

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

    // 실행 모드 목록
    private List<ExecutionModeConfig> executionModes = new ArrayList<>();

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

    @Getter
    @Setter
    public static class ExecutionModeConfig {
        private String modeId;
        private String modeName;
        private String description;
        private int displayOrder;
        private boolean isDefault;
    }
}
