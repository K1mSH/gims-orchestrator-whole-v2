package com.sync.agent.bojo.config;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * 개별 Agent YAML 파일의 파싱 결과를 담는 POJO
 *
 * config/agents/rcv-bojo-01.yml 등의 내용을 파싱
 */
@Getter
@Setter
public class AgentDefinition {

    private String agentCode;
    private String type;  // RCV, LOADER, SND

    // RCV/SND 공통 설정
    private TableConfig jewon;
    private TableConfig obsvdata;
    private LinkConfig link;
    private LookbackConfig lookback;

    // Loader 전용 설정
    private Map<String, String> ifTable;      // jewon, obsvdata
    private Map<String, String> targetTable;  // jewon, obsvdata

    // Step 설정 (Loader)
    private StepConfig step;

    @Getter
    @Setter
    public static class TableConfig {
        private String sourceTable;
        private String targetTable;
        private String primaryKey;
        private boolean fullCopy;
        private boolean skipSourceStatusUpdate;
        private String dateColumn;
        private String timeColumn;
    }

    @Getter
    @Setter
    public static class LinkConfig {
        private boolean useLinkTable;
        private String tableName;
    }

    @Getter
    @Setter
    public static class LookbackConfig {
        private int value = 3;
        private String unit = "HOURS";
    }

    @Getter
    @Setter
    public static class StepConfig {
        private String id;
        private String name;
    }
}
