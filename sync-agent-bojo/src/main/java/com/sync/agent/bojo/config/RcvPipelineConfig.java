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
                pipelineRegistry.register(def.getAgentCode(), "RCV", runner);
                log.info("Registered RCV pipeline: {}", def.getAgentCode());
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
                .fullCopy(jewonCfg.isFullCopy())
                .skipSourceStatusUpdate(jewonCfg.isSkipSourceStatusUpdate())
                .build();
        SourceToIfStep jewonStep = new SourceToIfStep(
                jewonConfig, dataSourceProvider, syncLogRepository);

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
                    .build();
        } else {
            obsvConfig = ExtractStepConfig.builder()
                    .stepId("obsvdata-extract")
                    .stepName("관측데이터 추출")
                    .extractType(ExtractType.SIMPLE_COPY)
                    .sourceTable(obsvCfg.getSourceTable())
                    .targetIfTable(obsvCfg.getTargetTable())
                    .primaryKeyColumn(obsvCfg.getPrimaryKey())
                    .dateColumn(obsvCfg.getDateColumn())
                    .timeColumn(obsvCfg.getTimeColumn())
                    .build();
        }
        SourceToIfStep obsvStep = new SourceToIfStep(
                obsvConfig, dataSourceProvider, syncLogRepository);

        // Steps
        List<StepExecutor> steps;
        if (useLinkTable) {
            LinkTableUpdateStep linkStep = new LinkTableUpdateStep(
                    dataSourceProvider, obsvCfg.getTargetTable(), linkTableName);
            steps = List.of(jewonStep, obsvStep, linkStep);
        } else {
            steps = List.of(jewonStep, obsvStep);
        }

        return new PipelineRunner(def.getAgentCode(), steps);
    }
}
