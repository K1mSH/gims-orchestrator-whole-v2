package com.sync.agent.common.step;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@Getter
@Builder
public class StepContext {

    private String executionId;
    private String pipelineId;

    /**
     * 파이프라인별 Source DataSource ID
     * 파이프라인 실행 시 설정됨
     */
    @Setter
    private String sourceDatasourceId;

    /**
     * 파이프라인별 Target DataSource ID
     * 파이프라인 실행 시 설정됨
     */
    @Setter
    private String targetDatasourceId;

    /**
     * Source DB가 위치한 네트워크 Zone
     * EXTERNAL, DMZ, INTERNAL_COMMON, INTERNAL_SERVICE
     * sourceRefs 생성 시 사용
     */
    @Setter
    private String sourceZone;

    /**
     * Source Zone의 약어 코드 (sourceRef용)
     * E, D, IC, IS
     */
    @Setter
    private String sourceZoneShortCode;

    /**
     * Source Datasource의 DB PK (sourceRef용)
     * Orchestrator에서 전달받음
     */
    @Setter
    private Long sourceDatasourceDbId;

    /**
     * Source 테이블 ID 목록 (테이블명 -> tableId 매핑)
     * Orchestrator에서 전달받음
     */
    @Setter
    private java.util.Map<String, Long> sourceTableIds;

    /**
     * 현재 Agent가 위치한 네트워크 Zone
     * Agent Chain 추적 시 다음 단계에서 이 값이 sourceZone이 됨
     */
    @Setter
    private String agentZone;

    @Builder.Default
    private Map<String, Object> params = new HashMap<>();

    /**
     * 구조화된 실행 옵션 (filters, timeRange 등)
     * 기존 params Map과 동기화됨 (하위 호환)
     */
    @Setter
    @Builder.Default
    private ExecutionOptions executionOptions = ExecutionOptions.builder().build();

    @Builder.Default
    private Map<String, Object> sharedData = new HashMap<>();

    public void put(String key, Object value) {
        this.sharedData.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) this.sharedData.get(key);
    }

    public void putSharedData(String key, Object value) {
        this.sharedData.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getSharedData(String key) {
        return (T) this.sharedData.get(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T getParam(String key) {
        return (T) this.params.get(key);
    }
}
