package com.infolink.agent.bojo.rcv.factory;

import com.infolink.agent.bojo.rcv.fetcher.LinkTableObsvDataFetcher;
import com.infolink.agent.common.controller.DataSourceProvider;
import com.infolink.agent.common.pipeline.SourceToTargetStepFactory;
import com.infolink.agent.common.pipeline.StepFactory;
import com.infolink.agent.common.repository.SyncLogRepository;
import com.infolink.agent.common.step.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
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
                toSingleString(config.get("source-table")),
                (String) config.get("link-table")
        );

        ExtractStepConfig extractConfig = ExtractStepConfig.builder()
                .stepId((String) config.get("id"))
                .stepName((String) config.get("name"))
                .extractType(ExtractType.CUSTOM_STAGING)
                .customDataFetcher(fetcher)
                .sourceTable(toSingleString(config.get("source-table")))
                .targetIfTable(toSingleString(config.get("target-table")))
                .primaryKeyColumn((String) config.get("primary-key"))
                .conflictKey((String) config.get("conflict-key"))
                .dateColumn((String) config.get("date-column"))
                .timeColumn((String) config.get("time-column"))
                .excludeInsertColumns(config.get("exclude-insert-columns") instanceof List
                        ? (List<String>) config.get("exclude-insert-columns") : null)
                .build();

        SourceToTargetStep step = new SourceToTargetStep(extractConfig, dataSourceProvider, syncLogRepository);
        step.setMappingName(SourceToTargetStepFactory.deriveMappingName((String) config.get("id")));
        return step;
    }

    /**
     * source-table / target-table 만 AgentConfigLoader 가 List 로 정규화 — 단일 문자열 추출.
     * (4/14 SourceToTargetStepFactory 와 동일 패턴 — 본 Factory 누락 보강)
     */
    @SuppressWarnings("unchecked")
    private static String toSingleString(Object value) {
        if (value instanceof String) return (String) value;
        if (value instanceof List) {
            List<String> list = (List<String>) value;
            return list.isEmpty() ? null : list.get(0);
        }
        return value != null ? value.toString() : null;
    }
}
