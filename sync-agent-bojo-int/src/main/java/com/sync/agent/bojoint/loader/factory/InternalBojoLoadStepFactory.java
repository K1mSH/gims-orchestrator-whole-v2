package com.sync.agent.bojoint.loader.factory;

import com.sync.agent.bojoint.config.DynamicEntityManagerService;
import com.sync.agent.bojoint.loader.step.InternalBojoLoadStep;
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
 * internal-bojo-load Factory (bojo-int 전용)
 *
 * Internal Loader Step 생성. 테이블명은 YAML step config에서 읽음.
 */
@Component
@RequiredArgsConstructor
public class InternalBojoLoadStepFactory implements StepFactory {

    private final DataSourceProvider dataSourceProvider;
    private final SyncLogRepository syncLogRepository;
    private final IfTableService ifTableService;
    private final DynamicEntityManagerService dynamicEmService;

    @Override
    public String getFactoryKey() {
        return "internal-bojo-load";
    }

    @SuppressWarnings("unchecked")
    @Override
    public StepExecutor create(Map<String, Object> config) {
        String stepId = (String) config.getOrDefault("id", "internal-bojo-load");
        String stepName = (String) config.getOrDefault("name", "내부 적재");

        List<String> sourceTables = (List<String>) config.get("source-table");
        List<String> targetTables = (List<String>) config.get("target-table");

        // source: IF_RSV_SEC_OBSVDATA (1개)
        String ifObsvdataTable = sourceTables != null && !sourceTables.isEmpty()
                ? sourceTables.get(0) : "IF_RSV_SEC_OBSVDATA";

        // target에서 각 테이블 역할 매핑 (테이블명 패턴으로 판별)
        String targetJewonTable = "TM_GD970001";
        String targetObsvdataTable = "PM_GD970201";
        String targetLinkTable = "TM_GD980002";
        String targetResultTable = "TM_GD970101";
        if (targetTables != null) {
            for (String t : targetTables) {
                if (t.contains("970001")) targetJewonTable = t;
                else if (t.contains("970201")) targetObsvdataTable = t;
                else if (t.contains("980002")) targetLinkTable = t;
                else if (t.contains("970101")) targetResultTable = t;
            }
        }

        return new InternalBojoLoadStep(
                stepId, stepName,
                ifObsvdataTable,
                targetJewonTable, targetObsvdataTable,
                targetLinkTable, targetResultTable,
                sourceTables, targetTables,
                dataSourceProvider, syncLogRepository, ifTableService,
                dynamicEmService
        );
    }
}
