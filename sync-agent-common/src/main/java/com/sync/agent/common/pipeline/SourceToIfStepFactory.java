package com.sync.agent.common.pipeline;

import com.sync.agent.common.controller.DataSourceProvider;
import com.sync.agent.common.repository.SyncLogRepository;
import com.sync.agent.common.step.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * source-to-if Factory (simple-copy 전용)
 *
 * RCV/SND/Internal RCV 공통. YAML 파라미터로 SourceToIfStep을 생성한다.
 * link 기반 추출은 bojo 전용 LinkSourceToIfStepFactory가 처리.
 */
@Component
@RequiredArgsConstructor
public class SourceToIfStepFactory implements StepFactory {

    private final DataSourceProvider dataSourceProvider;
    private final SyncLogRepository syncLogRepository;

    @Override
    public String getFactoryKey() {
        return "source-to-if";
    }

    @Override
    public StepExecutor create(Map<String, Object> config) {
        ExtractStepConfig extractConfig = ExtractStepConfig.builder()
                .stepId((String) config.get("id"))
                .stepName((String) config.get("name"))
                .extractType(ExtractType.SIMPLE_COPY)
                .sourceTable((String) config.get("source-table"))
                .targetIfTable((String) config.get("target-table"))
                .primaryKeyColumn((String) config.get("primary-key"))
                .conflictKey((String) config.get("conflict-key"))
                .fullCopy(Boolean.TRUE.equals(config.get("full-copy")))
                .skipSourceStatusUpdate(Boolean.TRUE.equals(config.get("skip-source-status-update")))
                .dateColumn((String) config.get("date-column"))
                .timeColumn((String) config.get("time-column"))
                .build();

        SourceToIfStep step = new SourceToIfStep(extractConfig, dataSourceProvider, syncLogRepository);
        step.setMappingName(deriveMappingName((String) config.get("id")));
        return step;
    }

    /**
     * step id에서 mapping name 추출
     * "jewon-extract" → "jewon", "obsvdata-snd-extract" → "obsvdata"
     */
    public static String deriveMappingName(String stepId) {
        if (stepId == null) return "unknown";
        // 첫 번째 '-' 이전 부분을 mapping name으로 사용
        int idx = stepId.indexOf('-');
        return idx > 0 ? stepId.substring(0, idx) : stepId;
    }
}
