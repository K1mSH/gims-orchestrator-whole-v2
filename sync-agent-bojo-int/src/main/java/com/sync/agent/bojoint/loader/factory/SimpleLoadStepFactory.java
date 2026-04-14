package com.sync.agent.bojoint.loader.factory;

import com.sync.agent.bojoint.config.DynamicEntityManagerService;
import com.sync.agent.bojoint.loader.step.SimpleLoadStep;
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
 * 범용 1:1 매핑 Loader Step Factory
 *
 * factory-key: simple-load
 * YAML에서 merge-key만 지정하면 IF_RSV → Target MERGE 자동 처리
 */
@Component
@RequiredArgsConstructor
public class SimpleLoadStepFactory implements StepFactory {

    private final DataSourceProvider dataSourceProvider;
    private final SyncLogRepository syncLogRepository;
    private final IfTableService ifTableService;
    private final DynamicEntityManagerService dynamicEmService;

    @Override
    public String getFactoryKey() {
        return "simple-load";
    }

    @SuppressWarnings("unchecked")
    @Override
    public StepExecutor create(Map<String, Object> config) {
        String stepId = (String) config.get("id");
        String stepName = (String) config.get("name");
        String mergeKey = (String) config.get("merge-key");

        Object sourceTableRaw = config.get("source-table");
        Object targetTableRaw = config.get("target-table");

        List<String> sourceTables = toList(sourceTableRaw);
        List<String> targetTables = toList(targetTableRaw);

        String ifTable = sourceTables.isEmpty() ? "" : sourceTables.get(0);
        String targetTable = targetTables.isEmpty() ? "" : targetTables.get(0);

        return new SimpleLoadStep(
                stepId, stepName,
                ifTable, targetTable, mergeKey,
                sourceTables, targetTables,
                dataSourceProvider, syncLogRepository, ifTableService,
                dynamicEmService
        );
    }

    @SuppressWarnings("unchecked")
    private List<String> toList(Object value) {
        if (value instanceof List) return (List<String>) value;
        if (value instanceof String) return List.of((String) value);
        return List.of();
    }
}
