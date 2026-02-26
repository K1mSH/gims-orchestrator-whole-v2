package com.sync.agent.bojoint.config;

import com.sync.agent.bojoint.loader.step.InternalLoadStep;
import com.sync.agent.common.controller.DataSourceProvider;
import com.sync.agent.common.pipeline.PipelineRunner;
import com.sync.agent.common.repository.SyncLogRepository;
import com.sync.agent.common.service.IfTableService;
import com.sync.agent.common.step.ExecutionModeDefinition;
import com.sync.agent.common.step.StepDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Loader 파이프라인 설정 (내부망 전용)
 *
 * IF_RSV → GIMS Target 적재
 * - 제원(tm_gd970001): READ ONLY (spot_id 매핑용, GIMS 자체 마스터)
 * - 관측데이터: EAV 확장 (1→3행) UPSERT
 * - Link: UPSERT
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class LoaderPipelineConfig {

    private final AgentConfigLoader agentConfigLoader;
    private final PipelineRegistry pipelineRegistry;
    private final DataSourceProvider dataSourceProvider;
    private final SyncLogRepository syncLogRepository;
    private final IfTableService ifTableService;

    @PostConstruct
    public void registerLoaderPipelines() {
        List<AgentDefinition> loaderDefs = agentConfigLoader.getAgentsByType("LOADER");
        log.info("Registering {} Loader pipelines", loaderDefs.size());

        for (AgentDefinition def : loaderDefs) {
            try {
                // YAML에서 설정 읽기
                Map<String, String> ifTable = def.getIfTable();
                Map<String, String> targetTable = def.getTargetTable();
                AgentDefinition.StepConfig stepCfg = def.getStep();

                if (ifTable == null || targetTable == null || stepCfg == null) {
                    log.error("Loader config incomplete for {}: ifTable={}, targetTable={}, step={}",
                            def.getAgentCode(), ifTable, targetTable, stepCfg);
                    continue;
                }

                InternalLoadStep loadStep = new InternalLoadStep(
                        stepCfg.getId(),
                        stepCfg.getName(),
                        ifTable.get("obsvdata"),
                        targetTable.get("jewon"),
                        targetTable.get("obsvdata"),
                        targetTable.get("link"),
                        targetTable.get("result"),
                        dataSourceProvider,
                        syncLogRepository,
                        ifTableService
                );

                PipelineRunner runner = new PipelineRunner(def.getAgentCode(), List.of(loadStep));
                List<StepDefinition> stepDefs = buildStepDefinitions(def);
                List<ExecutionModeDefinition> modes = buildExecutionModes(def);
                pipelineRegistry.register(def.getAgentCode(), "LOADER", runner, stepDefs, modes);
                log.info("Registered Loader pipeline: {} (step={}, modes={})", def.getAgentCode(), stepCfg.getId(), modes.size());

            } catch (Exception e) {
                log.error("Failed to register Loader pipeline: {}", def.getAgentCode(), e);
            }
        }
    }

    private List<StepDefinition> buildStepDefinitions(AgentDefinition def) {
        List<StepDefinition> defs = new ArrayList<>();
        AgentDefinition.StepConfig stepCfg = def.getStep();

        defs.add(StepDefinition.builder()
                .stepId(stepCfg.getId())
                .stepName(stepCfg.getName())
                .description("IF_RSV → GIMS Target (관측데이터 EAV 확장 UPSERT + Link UPSERT)")
                .displayOrder(1)
                .enabledByDefault(true)
                .build());

        return defs;
    }

    private List<ExecutionModeDefinition> buildExecutionModes(AgentDefinition def) {
        List<ExecutionModeDefinition> modes = new ArrayList<>();
        for (AgentDefinition.ExecutionModeConfig cfg : def.getExecutionModes()) {
            modes.add(ExecutionModeDefinition.builder()
                    .modeId(cfg.getModeId())
                    .modeName(cfg.getModeName())
                    .description(cfg.getDescription())
                    .displayOrder(cfg.getDisplayOrder())
                    .isDefault(cfg.isDefault())
                    .build());
        }
        return modes;
    }
}
