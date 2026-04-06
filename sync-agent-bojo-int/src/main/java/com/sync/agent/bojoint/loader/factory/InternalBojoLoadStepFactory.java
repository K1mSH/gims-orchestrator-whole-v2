package com.sync.agent.bojoint.loader.factory;

import com.sync.agent.bojoint.loader.step.InternalBojoLoadStep;
import com.sync.agent.common.controller.DataSourceProvider;
import com.sync.agent.common.pipeline.StepFactory;
import com.sync.agent.common.repository.SyncLogRepository;
import com.sync.agent.common.service.IfTableService;
import com.sync.agent.common.step.StepExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * internal-bojo-load Factory (bojo-int 전용)
 *
 * Internal Loader Step 생성. 세부 설정은 application.yml @Value로 처리.
 */
@Component
@RequiredArgsConstructor
public class InternalBojoLoadStepFactory implements StepFactory {

    private final DataSourceProvider dataSourceProvider;
    private final SyncLogRepository syncLogRepository;
    private final IfTableService ifTableService;

    @Value("${loader.if-table.obsvdata:IF_RSV_SEC_OBSVDATA}")
    private String ifObsvdataTable;

    @Value("${loader.target-table.jewon:TM_GD970001}")
    private String targetJewonTable;

    @Value("${loader.target-table.obsvdata:PM_GD970201}")
    private String targetObsvdataTable;

    @Value("${loader.target-table.link:TM_GD980002}")
    private String targetLinkTable;

    @Value("${loader.target-table.result:TM_GD970101}")
    private String targetResultTable;

    @Override
    public String getFactoryKey() {
        return "internal-bojo-load";
    }

    @Override
    public StepExecutor create(Map<String, Object> config) {
        String stepId = (String) config.getOrDefault("id", "internal-bojo-load");
        String stepName = (String) config.getOrDefault("name", "내부 적재");

        return new InternalBojoLoadStep(
                stepId, stepName,
                ifObsvdataTable,
                targetJewonTable, targetObsvdataTable,
                targetLinkTable, targetResultTable,
                dataSourceProvider, syncLogRepository, ifTableService
        );
    }
}
