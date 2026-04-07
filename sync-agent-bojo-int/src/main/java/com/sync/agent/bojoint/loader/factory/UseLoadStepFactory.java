package com.sync.agent.bojoint.loader.factory;

import com.sync.agent.bojoint.loader.step.UseLoadStep;
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
 * 이용량 Loader Step Factory
 * - use-load: 이용량 2개 IF → 4개 타겟 적재
 */
@Component
@RequiredArgsConstructor
public class UseLoadStepFactory implements StepFactory {

    private final DataSourceProvider dataSourceProvider;
    private final SyncLogRepository syncLogRepository;
    private final IfTableService ifTableService;

    @Override
    public String getFactoryKey() {
        return "use-load";
    }

    @SuppressWarnings("unchecked")
    @Override
    public StepExecutor create(Map<String, Object> config) {
        String stepId = (String) config.getOrDefault("id", "use-load");
        String stepName = (String) config.getOrDefault("name", "이용량 적재");

        List<String> sourceTables = (List<String>) config.get("source-table");
        List<String> targetTables = (List<String>) config.get("target-table");

        return new UseLoadStep(
                stepId, stepName,
                sourceTables, targetTables,
                dataSourceProvider, syncLogRepository, ifTableService
        );
    }
}
