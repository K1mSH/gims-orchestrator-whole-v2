package com.infolink.agent.common.pipeline;

import com.infolink.agent.common.controller.DataSourceProvider;
import com.infolink.agent.common.repository.SyncLogRepository;
import com.infolink.agent.common.step.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Source → Target 복사 Factory — 모든 Agent의 기본 카피 Step 생성기
 *
 * RCV / SND / Internal RCV / Provide 공통. YAML 파라미터로 SourceToTargetStep 을 생성한다.
 * factory-key="source-to-if" 는 기존 20여 개 YAML 호환을 위해 유지 (별도 이슈에서 source-to-target 으로 마이그레이션 예정).
 *
 * Link 테이블 기반 증분 추출 (RCV obsvdata 등) 은 bojo 전용 LinkSourceToIfStepFactory 가 별도 처리.
 *
 * 지원하는 YAML 파라미터:
 *  - source-table (필수): 소스 테이블명
 *  - target-table (필수): 타겟 테이블명 (IF 여부 무관)
 *  - primary-key: PK 컬럼 (단일 or 콤마 구분 복합)
 *  - conflict-key: ON CONFLICT 기준 컬럼 (provide: source_refs 고정)
 *  - full-copy: 전체 복사 모드 (true/false)
 *  - skip-source-status-update: 소스 link_status 갱신 스킵 여부
 *  - date-column / time-column: 시간범위 실행용
 *  - target-meta-columns: 타겟 메타 컬럼 리스트 (provide 등 IF 표준과 다른 경우)
 */
@Component
@RequiredArgsConstructor
public class SourceToTargetStepFactory implements StepFactory {

    private final DataSourceProvider dataSourceProvider;
    private final SyncLogRepository syncLogRepository;

    @Override
    public String getFactoryKey() {
        return "source-to-if";
    }

    @Override
    @SuppressWarnings("unchecked")
    public StepExecutor create(Map<String, Object> config) {
        ExtractStepConfig extractConfig = ExtractStepConfig.builder()
                .stepId((String) config.get("id"))
                .stepName((String) config.get("name"))
                .extractType(ExtractType.SIMPLE_COPY)
                .sourceTable(toSingleString(config.get("source-table")))
                .targetIfTable(toSingleString(config.get("target-table")))
                .primaryKeyColumn((String) config.get("primary-key"))
                .conflictKey((String) config.get("conflict-key"))
                .fullCopy(Boolean.TRUE.equals(config.get("full-copy")))
                .skipSourceStatusUpdate(Boolean.TRUE.equals(config.get("skip-source-status-update")))
                .dateColumn((String) config.get("date-column"))
                .timeColumn((String) config.get("time-column"))
                .targetMetaColumns(config.get("target-meta-columns") instanceof List
                        ? (List<String>) config.get("target-meta-columns") : null)
                .excludeInsertColumns(config.get("exclude-insert-columns") instanceof List
                        ? (List<String>) config.get("exclude-insert-columns") : null)
                .build();

        SourceToTargetStep step = new SourceToTargetStep(extractConfig, dataSourceProvider, syncLogRepository);
        step.setMappingName(deriveMappingName((String) config.get("id")));
        return step;
    }

    /**
     * List 또는 String → 단일 String 변환 (AgentConfigLoader가 normalizeToList로 감싸는 경우 대응)
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
