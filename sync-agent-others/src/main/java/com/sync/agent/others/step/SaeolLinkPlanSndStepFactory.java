package com.sync.agent.others.step;

import com.sync.agent.common.controller.DataSourceProvider;
import com.sync.agent.common.pipeline.StepFactory;
import com.sync.agent.common.repository.SyncLogRepository;
import com.sync.agent.common.step.StepExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 새올 LINK_PLAN SND Step 팩토리
 *
 * YAML factory-key: "saeol-link-plan-snd"
 * YAML의 table-mappings 배열에서 SaeolTableMapping 목록을 구성하여 Step에 전달.
 */
@Component
@RequiredArgsConstructor
public class SaeolLinkPlanSndStepFactory implements StepFactory {

    private final DataSourceProvider dataSourceProvider;
    private final SyncLogRepository syncLogRepository;

    @Override
    public String getFactoryKey() {
        return "saeol-link-plan-snd";
    }

    @Override
    @SuppressWarnings("unchecked")
    public StepExecutor create(Map<String, Object> config) {
        String stepId = (String) config.get("id");
        String stepName = (String) config.get("name");

        // table-mappings 파싱
        List<Map<String, Object>> mappingsRaw =
                (List<Map<String, Object>>) config.get("table-mappings");

        List<SaeolTableMapping> tableMappings = new ArrayList<>();
        if (mappingsRaw != null) {
            for (Map<String, Object> m : mappingsRaw) {
                Map<String, String> linkPlanKeys = new LinkedHashMap<>();
                Map<String, Object> keysRaw = (Map<String, Object>) m.get("link-plan-keys");
                if (keysRaw != null) {
                    keysRaw.forEach((k, v) -> linkPlanKeys.put(k, v.toString()));
                }

                tableMappings.add(SaeolTableMapping.builder()
                        .sourceTable(((String) m.get("source-table")).toUpperCase())
                        .targetTable(((String) m.get("target-table")).toUpperCase())
                        .primaryKey(((String) m.get("primary-key")).toUpperCase())
                        .linkPlanKeys(linkPlanKeys)
                        .build());
            }
        }

        return new SaeolLinkPlanSndStep(
                stepId, stepName, tableMappings, dataSourceProvider, syncLogRepository);
    }
}
