package com.sync.agent.bojo.config;

import com.sync.agent.common.model.TableMapping;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
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

    // Loader 전용 설정
    private Map<String, String> ifTable;      // jewon, obsvdata
    private Map<String, String> targetTable;  // jewon, obsvdata

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
        private String conflictKey;  // UPSERT 충돌 기준 (선택적, 기본값: primaryKey)
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
    public static class StepConfig {
        private String id;
        private String name;
    }
}
