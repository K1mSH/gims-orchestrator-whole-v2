package com.sync.agent.bojoint.config;

import com.sync.agent.common.controller.DataSourceProvider;
import com.sync.agent.common.pipeline.PipelineRunner;
import com.sync.agent.common.repository.SyncLogRepository;
import com.sync.agent.common.step.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

/**
 * RCV 파이프라인 설정 (내부망 전용)
 *
 * DMZ SND IF → 내부 RSV IF 단순 복사 (SIMPLE_COPY only)
 * Link 테이블 로직 불필요
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class RcvPipelineConfig {

    private final AgentConfigLoader agentConfigLoader;
    private final PipelineRegistry pipelineRegistry;
    private final DataSourceProvider dataSourceProvider;
    private final SyncLogRepository syncLogRepository;

    @PostConstruct
    public void registerRcvPipelines() {
        List<AgentDefinition> rcvDefs = agentConfigLoader.getAgentsByType("RCV");
        log.info("Registering {} RCV pipelines", rcvDefs.size());

        for (AgentDefinition def : rcvDefs) {
            try {
                PipelineRunner runner = createRcvRunner(def);
                List<StepDefinition> stepDefs = buildStepDefinitions(def);
                pipelineRegistry.register(def.getAgentCode(), "RCV", runner, stepDefs);
                log.info("Registered RCV pipeline: {} (steps={})", def.getAgentCode(), stepDefs.size());
            } catch (Exception e) {
                log.error("Failed to register RCV pipeline: {}", def.getAgentCode(), e);
            }
        }
    }

    private PipelineRunner createRcvRunner(AgentDefinition def) {
        AgentDefinition.TableConfig jewonCfg = def.getJewon();
        AgentDefinition.TableConfig obsvCfg = def.getObsvdata();

        // Step 1: 제원 추출 (SIMPLE_COPY)
        ExtractStepConfig jewonConfig = ExtractStepConfig.builder()
                .stepId("jewon-extract")
                .stepName("제원 데이터 추출")
                .extractType(ExtractType.SIMPLE_COPY)
                .sourceTable(jewonCfg.getSourceTable())
                .targetIfTable(jewonCfg.getTargetTable())
                .primaryKeyColumn(jewonCfg.getPrimaryKey())
                .conflictKey(jewonCfg.getConflictKey())
                .fullCopy(jewonCfg.isFullCopy())
                .skipSourceStatusUpdate(jewonCfg.isSkipSourceStatusUpdate())
                .build();
        SourceToIfStep jewonStep = new SourceToIfStep(
                jewonConfig, dataSourceProvider, syncLogRepository);
        jewonStep.setMappingName("jewon");

        // Step 2: 관측데이터 추출 (SIMPLE_COPY)
        ExtractStepConfig obsvConfig = ExtractStepConfig.builder()
                .stepId("obsvdata-extract")
                .stepName("관측데이터 추출")
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

    /**
     * RCV Agent의 Step 메타데이터 생성
     */
    private List<StepDefinition> buildStepDefinitions(AgentDefinition def) {
        List<StepDefinition> defs = new ArrayList<>();
        if (def.getJewon() != null) {
            defs.add(StepDefinition.builder()
                    .stepId("jewon-extract")
                    .stepName("제원 데이터 추출")
                    .description(def.getJewon().getSourceTable() + " → " + def.getJewon().getTargetTable())
                    .displayOrder(1)
                    .enabledByDefault(true)
                    .build());
        }
        if (def.getObsvdata() != null) {
            defs.add(StepDefinition.builder()
                    .stepId("obsvdata-extract")
                    .stepName("관측데이터 추출")
                    .description(def.getObsvdata().getSourceTable() + " → " + def.getObsvdata().getTargetTable())
                    .displayOrder(2)
                    .enabledByDefault(true)
                    .build());
        }
        return defs;
    }
}
