package com.sync.agent.bojoint.config;

import lombok.Getter;
import lombok.Setter;

/**
 * 개별 Agent YAML 파일의 파싱 결과를 담는 POJO
 */
@Getter
@Setter
public class AgentDefinition {

    private String agentCode;
    private String type;  // RCV

    // RCV 설정
    private TableConfig jewon;
    private TableConfig obsvdata;

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
}
