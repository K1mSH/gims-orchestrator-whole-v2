package com.sync.agent.bojo.config;

import com.sync.agent.common.controller.DataSourceProvider;
import com.sync.agent.common.pipeline.PipelineRunner;
import com.sync.agent.common.repository.SyncLogRepository;
import com.sync.agent.common.step.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.util.List;

/**
 * SND 파이프라인 설정
 *
 * Target → IF_SND 추출
 */
@Slf4j

@Configuration
@RequiredArgsConstructor
public class SndPipelineConfig {

    private final AgentConfigLoader agentConfigLoader;
    private final PipelineRegistry pipelineRegistry;
    private final DataSourceProvider dataSourceProvider;
    private final SyncLogRepository syncLogRepository;

    @PostConstruct
    public void registerSndPipelines() {
        List<AgentDefinition> sndDefs = agentConfigLoader.getAgentsByType("SND");
        log.info("Registering {} SND pipelines", sndDefs.size());

        for (AgentDefinition def : sndDefs) {
            try {

                PipelineRunner runner = createSndRunner(def);
                pipelineRegistry.register(def.getAgentCode(), "SND", runner);
                log.info("Registered SND pipeline: {}", def.getAgentCode());
            } catch (Exception e) {
                log.error("Failed to register SND pipeline: {}", def.getAgentCode(), e);
            }
        }
    }

    private PipelineRunner createSndRunner(AgentDefinition def) {
        AgentDefinition.TableConfig jewonCfg = def.getJewon();
        AgentDefinition.TableConfig obsvCfg = def.getObsvdata();

        // Step 1: 제원 추출
        ExtractStepConfig jewonConfig = ExtractStepConfig.builder()
                .stepId("jewon-snd-extract")
                .stepName("제원 데이터 송신 추출")
                .extractType(ExtractType.SIMPLE_COPY)
                .sourceTable(jewonCfg.getSourceTable())
                .targetIfTable(jewonCfg.getTargetTable())
                .primaryKeyColumn(jewonCfg.getPrimaryKey())
                .conflictKey(jewonCfg.getConflictKey())
                .fullCopy(jewonCfg.isFullCopy())
                .build();
        SourceToIfStep jewonStep = new SourceToIfStep(
                jewonConfig, dataSourceProvider, syncLogRepository);
        jewonStep.setMappingName("jewon");

        // Step 2: 관측데이터 추출
        ExtractStepConfig obsvConfig = ExtractStepConfig.builder()
                .stepId("obsvdata-snd-extract")
                .stepName("관측데이터 송신 추출")
                .extractType(ExtractType.SIMPLE_COPY)
                .sourceTable(obsvCfg.getSourceTable())
                .targetIfTable(obsvCfg.getTargetTable())
                .primaryKeyColumn(obsvCfg.getPrimaryKey())
                .conflictKey(obsvCfg.getConflictKey())
                .dateColumn(obsvCfg.getDateColumn())
                .timeColumn(obsvCfg.getTimeColumn())
                .build();
        SourceToIfStep obsvStep = new SourceToIfStep(
                obsvConfig, dataSourceProvider, syncLogRepository);
        obsvStep.setMappingName("obsvdata");

        return new PipelineRunner(def.getAgentCode(), List.of(jewonStep, obsvStep));
    }
}
