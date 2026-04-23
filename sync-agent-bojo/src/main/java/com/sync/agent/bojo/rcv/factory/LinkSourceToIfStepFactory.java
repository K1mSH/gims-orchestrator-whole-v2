package com.sync.agent.bojo.rcv.factory;

import com.sync.agent.bojo.rcv.fetcher.LinkTableObsvDataFetcher;
import com.sync.agent.common.controller.DataSourceProvider;
import com.sync.agent.common.pipeline.SourceToTargetStepFactory;
import com.sync.agent.common.pipeline.StepFactory;
import com.sync.agent.common.repository.SyncLogRepository;
import com.sync.agent.common.step.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * source-to-if-link Factory (bojo 전용)
 *
 * Link 테이블 기반 증분 추출을 사용하는 SourceToTargetStep 생성.
 * LinkTableObsvDataFetcher를 customDataFetcher로 주입한다.
 */
@Component
@RequiredArgsConstructor
public class LinkSourceToIfStepFactory implements StepFactory {

    private final DataSourceProvider dataSourceProvider;
    private final SyncLogRepository syncLogRepository;

    @Override
    public String getFactoryKey() {
        return "source-to-if-link";
    }

    @Override
    @SuppressWarnings("unchecked")
    public StepExecutor create(Map<String, Object> config) {
        LinkTableObsvDataFetcher fetcher = new LinkTableObsvDataFetcher(
                dataSourceProvider,
                (String) config.get("link-jewon-source"),
                (String) config.get("source-table"),
                (String) config.get("link-table")
        );

        ExtractStepConfig extractConfig = ExtractStepConfig.builder()
                .stepId((String) config.get("id"))
                .stepName((String) config.get("name"))
                .extractType(ExtractType.CUSTOM_STAGING)
                .customDataFetcher(fetcher)
                .sourceTable((String) config.get("source-table"))
                .targetIfTable((String) config.get("target-table"))
                .primaryKeyColumn((String) config.get("primary-key"))
                .conflictKey((String) config.get("conflict-key"))
                .dateColumn((String) config.get("date-column"))
                .timeColumn((String) config.get("time-column"))
                .excludeInsertColumns(config.get("exclude-insert-columns") instanceof java.util.List
                        ? (java.util.List<String>) config.get("exclude-insert-columns") : null)
                .build();

        SourceToTargetStep step = new SourceToTargetStep(extractConfig, dataSourceProvider, syncLogRepository);
        step.setMappingName(SourceToTargetStepFactory.deriveMappingName((String) config.get("id")));
        return step;
    }
}
