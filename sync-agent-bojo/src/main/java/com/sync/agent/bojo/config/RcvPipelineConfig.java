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

    /**
     * RCV Runner 생성 — YAML 설정 기반으로 Step 객체를 조립하여 PipelineRunner에 담는다.
     *
     * 여기서 new SourceToIfStep(...), new LinkTableUpdateStep(...)으로 생성한 구체적 객체가
     * Runner의 steps 리스트에 들어간다. 런타임에 runner.run()이 호출되면
     * for (step : steps) { step.execute(context) }로 순차 실행되며,
     * Java 다형성에 의해 각 객체의 execute()가 호출된다.
     * Runner는 Step의 구체적 타입을 모르고, StepExecutor 인터페이스만 알고 있다.
     */
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

        // Step 리스트 조립 — 이 순서대로 런타임에 순차 실행됨
        // 각 객체는 StepExecutor 구현체이며, Runner는 구체적 타입을 구분하지 않음
        List<StepExecutor> steps;
        if (useLinkTable) {
            LinkTableUpdateStep linkStep = new LinkTableUpdateStep(
                    dataSourceProvider, obsvCfg.getTargetTable(), linkTableName, syncLogRepository);
            // [SourceToIfStep(jewon), SourceToIfStep(obsvdata), LinkTableUpdateStep]
            steps = List.of(jewonStep, obsvStep, linkStep);
        } else {
            // [SourceToIfStep(jewon), SourceToIfStep(obsvdata)]
            steps = List.of(jewonStep, obsvStep);
        }

        // ★ 이 Runner가 PipelineRegistry에 등록되어, 실행 요청 시 getRunner(agentCode)로 꺼내 실행
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
