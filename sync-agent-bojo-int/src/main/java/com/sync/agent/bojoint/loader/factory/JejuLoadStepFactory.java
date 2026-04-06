package com.sync.agent.bojoint.loader.factory;

import com.sync.agent.bojoint.loader.step.JejuJewonLoadStep;
import com.sync.agent.common.controller.DataSourceProvider;
import com.sync.agent.common.pipeline.StepFactory;
import com.sync.agent.common.repository.SyncLogRepository;
import com.sync.agent.common.service.IfTableService;
import com.sync.agent.common.step.StepExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 제주 Loader Step Factory
 * - jeju-jewon-load: 제원 1→5 분산 적재
 */
@Component
@RequiredArgsConstructor
public class JejuLoadStepFactory implements StepFactory {

    private final DataSourceProvider dataSourceProvider;
    private final SyncLogRepository syncLogRepository;
    private final IfTableService ifTableService;

    @Override
    public String getFactoryKey() {
        return "jeju-jewon-load";
    }

    @SuppressWarnings("unchecked")
    @Override
    public StepExecutor create(Map<String, Object> config) {
        String stepId = (String) config.getOrDefault("id", "jeju-jewon-load");
        String stepName = (String) config.getOrDefault("name", "제주 제원 적재");

        List<String> sourceTables = (List<String>) config.get("source-table");
        List<String> targetTables = (List<String>) config.get("target-table");

        String ifTable = sourceTables != null && !sourceTables.isEmpty()
                ? sourceTables.get(0) : "IF_RSV_TB_JEJU_JEWON";

        return new JejuJewonLoadStep(
                stepId, stepName, ifTable,
                sourceTables, targetTables,
                dataSourceProvider, syncLogRepository, ifTableService
        );
    }
}
