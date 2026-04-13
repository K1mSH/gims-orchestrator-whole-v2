package com.sync.agent.bojoint.loader.factory;

import com.sync.agent.bojoint.config.DynamicEntityManagerService;
import com.sync.agent.bojoint.loader.step.JejuFacilityLoadStep;
import com.sync.agent.bojoint.loader.step.JejuJewonLoadStep;
import com.sync.agent.bojoint.loader.step.JejuObsvdataLoadStep;
import com.sync.agent.common.controller.DataSourceProvider;
import com.sync.agent.common.pipeline.StepFactory;
import com.sync.agent.common.repository.SyncLogRepository;
import com.sync.agent.common.service.IfTableService;
import com.sync.agent.common.step.StepExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 제주 Loader Step Factory
 * - jeju-jewon-load: 제원 1→5 분산 적재
 * - jeju-obsvdata-load: 관측데이터 적재 (단일심도/다심도)
 * - jeju-facility-load: 이용시설 적재 (시설 + 일자료)
 */
@Component
@RequiredArgsConstructor
public class JejuLoadStepFactory implements StepFactory {

    private static final List<String> FACTORY_KEYS = Arrays.asList("jeju-jewon-load", "jeju-obsvdata-load", "jeju-facility-load");

    private final DataSourceProvider dataSourceProvider;
    private final SyncLogRepository syncLogRepository;
    private final IfTableService ifTableService;
    private final DynamicEntityManagerService dynamicEmService;

    @Override
    public String getFactoryKey() {
        return "jeju-jewon-load";
    }

    @Override
    public List<String> getFactoryKeys() {
        return FACTORY_KEYS;
    }

    @SuppressWarnings("unchecked")
    @Override
    public StepExecutor create(Map<String, Object> config) {
        String factoryKey = (String) config.getOrDefault("factory-key", "jeju-jewon-load");
        String stepId = (String) config.getOrDefault("id", factoryKey);
        String stepName = (String) config.getOrDefault("name", "제주 적재");

        List<String> sourceTables = (List<String>) config.get("source-table");
        List<String> targetTables = (List<String>) config.get("target-table");

        String ifTable = sourceTables != null && !sourceTables.isEmpty()
                ? sourceTables.get(0) : "";

        if ("jeju-obsvdata-load".equals(factoryKey)) {
            if (ifTable.isEmpty()) ifTable = "IF_RSV_TB_JEJU";
            return new JejuObsvdataLoadStep(
                    stepId, stepName, ifTable,
                    sourceTables, targetTables,
                    dataSourceProvider, syncLogRepository, ifTableService,
                    dynamicEmService
            );
        }

        if ("jeju-facility-load".equals(factoryKey)) {
            if (ifTable.isEmpty()) ifTable = "IF_RSV_RGETSTGMS01";
            return new JejuFacilityLoadStep(
                    stepId, stepName, ifTable,
                    sourceTables, targetTables,
                    dataSourceProvider, syncLogRepository, ifTableService,
                    dynamicEmService
            );
        }

        // 기본: jeju-jewon-load
        if (ifTable.isEmpty()) ifTable = "IF_RSV_TB_JEJU_JEWON";
        return new JejuJewonLoadStep(
                stepId, stepName, ifTable,
                sourceTables, targetTables,
                dataSourceProvider, syncLogRepository, ifTableService,
                dynamicEmService
        );
    }
}
