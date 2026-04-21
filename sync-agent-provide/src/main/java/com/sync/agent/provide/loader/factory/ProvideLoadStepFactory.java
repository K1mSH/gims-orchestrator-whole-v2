package com.sync.agent.provide.loader.factory;

import com.sync.agent.common.controller.DataSourceProvider;
import com.sync.agent.common.pipeline.StepFactory;
import com.sync.agent.common.repository.SyncLogRepository;
import com.sync.agent.common.step.StepExecutor;
import com.sync.agent.provide.loader.step.ProvideLoadStep;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Provide Agent 범용 Loader Step Factory
 *
 * factory-key: provide-load
 * YAML에서 source-table, target-table, merge-key를 받아 ProvideLoadStep 생성
 */
@Component
@RequiredArgsConstructor
public class ProvideLoadStepFactory implements StepFactory {

    private final DataSourceProvider dataSourceProvider;
    private final SyncLogRepository syncLogRepository;

    @Override
    public String getFactoryKey() {
        return "provide-load";
    }

    @SuppressWarnings("unchecked")
    @Override
    public StepExecutor create(Map<String, Object> config) {
        String stepId = (String) config.get("id");
        String stepName = (String) config.get("name");
        String mergeKey = (String) config.get("merge-key");

        List<String> sourceTables = toList(config.get("source-table"));
        List<String> targetTables = toList(config.get("target-table"));

        String sourceTable = sourceTables.isEmpty() ? "" : sourceTables.get(0);
        String targetTable = targetTables.isEmpty() ? "" : targetTables.get(0);

        return new ProvideLoadStep(
                stepId, stepName,
                sourceTable, targetTable, mergeKey,
                sourceTables, targetTables,
                dataSourceProvider, syncLogRepository
        );
    }

    @SuppressWarnings("unchecked")
    private List<String> toList(Object value) {
        if (value instanceof List) return (List<String>) value;
        if (value instanceof String) return List.of((String) value);
        return Collections.emptyList();
    }
}
