package com.infolink.agent.common.config;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Retention(자동삭제) 설정 POJO
 * Orchestrator DB에서 JSON으로 관리, POST body로 Agent에 전달됨
 */
@Getter
@Setter
public class RetentionConfig {

    private boolean enabled;
    private String targetDatasourceId;  // cleanup 대상 datasource ID
    private List<TableRetention> targets = new ArrayList<>();

    @Getter
    @Setter
    public static class TableRetention {
        private String table;
        private String dateColumn;
        private int retentionDays;
    }
}
