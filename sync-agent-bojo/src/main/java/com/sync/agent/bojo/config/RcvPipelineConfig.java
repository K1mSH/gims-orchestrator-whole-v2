package com.sync.agent.bojo.config;

import com.sync.agent.bojo.rcv.fetcher.LinkTableObsvDataFetcher;
import com.sync.agent.bojo.rcv.step.LinkTableUpdateStep;
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
 * RCV 파이프라인 설정
 *
 * AgentConfigLoader에서 type=RCV인 정의를 읽어서
 * 각각 PipelineRunner를 생성하고 PipelineRegistry에 등록
 *
 * DataSourceProvider는 SyncDataSourceService (싱글턴)
 * ThreadLocal로 실행별 datasource 격리
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class RcvPipelineConfig {

    private final AgentConfigLoader agentConfigLoader;
    private final PipelineRegistry pipelineRegistry;
    private final DataSourceProvider dataSourceProvider;  // = SyncDataSourceService
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
        boolean useLinkTable = def.getLink() != null && def.getLink().isUseLinkTable();
        String linkTableName = def.getLink() != null ? def.getLink().getTableName() : "link_ngwis";

        // Step 1: 제원 추출
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

        // Step 2: 관측데이터 추출
        ExtractStepConfig obsvConfig;
        if (useLinkTable) {
            LinkTableObsvDataFetcher fetcher = new LinkTableObsvDataFetcher(
                    dataSourceProvider,
                    jewonCfg.getSourceTable(),
                    obsvCfg.getSourceTable(),
                    linkTableName
            );
            obsvConfig = ExtractStepConfig.builder()
                    .stepId("obsvdata-extract")
                    .stepName("관측데이터 추출 (Link 기반)")
                    .extractType(ExtractType.CUSTOM_STAGING)
                    .customDataFetcher(fetcher)
                    .sourceTable(obsvCfg.getSourceTable())
                    .targetIfTable(obsvCfg.getTargetTable())
                    .primaryKeyColumn(obsvCfg.getPrimaryKey())
                    .conflictKey(obsvCfg.getConflictKey())
                    .build();
        } else {
            obsvConfig = ExtractStepConfig.builder()
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
        }
        SourceToIfStep obsvStep = new SourceToIfStep(
                obsvConfig, dataSourceProvider, syncLogRepository);
        obsvStep.setMappingName("obsvdata");

        // Steps
        List<StepExecutor> steps;
        if (useLinkTable) {
            LinkTableUpdateStep linkStep = new LinkTableUpdateStep(
                    dataSourceProvider, obsvCfg.getTargetTable(), linkTableName, syncLogRepository);
            steps = List.of(jewonStep, obsvStep, linkStep);
        } else {
            steps = List.of(jewonStep, obsvStep);
        }

        return new PipelineRunner(def.getAgentCode(), steps);
    }

    /**
     * RCV Agent의 Step 메타데이터 생성
     */
    private List<StepDefinition> buildStepDefinitions(AgentDefinition def) {
        List<StepDefinition> defs = new ArrayList<>();
        boolean useLinkTable = def.getLink() != null && def.getLink().isUseLinkTable();

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
                    .stepName(useLinkTable ? "관측데이터 추출 (Link 기반)" : "관측데이터 추출")
                    .description(def.getObsvdata().getSourceTable() + " → " + def.getObsvdata().getTargetTable())
                    .displayOrder(2)
                    .enabledByDefault(true)
                    .build());
        }
        if (useLinkTable) {
            defs.add(StepDefinition.builder()
                    .stepId("link-table-update")
                    .stepName("Link 테이블 갱신")
                    .description("IF 데이터로 Link 테이블 상태 업데이트")
                    .displayOrder(3)
                    .enabledByDefault(true)
                    .build());
        }
        return defs;
    }
}
