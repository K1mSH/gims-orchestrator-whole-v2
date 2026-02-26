package com.sync.agent.common.step;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 파이프라인 실행 옵션 (구조화된 파라미터)
 *
 * 공통 레이어는 파라미터를 전달만 하고, 해석은 각 Agent의 Step/Fetcher에서 수행.
 * - TimeRange: 시간 범위 (기간 지정 실행)
 * - params: Agent가 자유롭게 해석하는 key-value 파라미터
 */
@Getter
@Builder
public class ExecutionOptions {

    /** 실행 모드 ID (Agent 소스코드에 정의된 모드) */
    private String modeId;

    /** 시간 범위 (기간 지정 실행) */
    private TimeRange timeRange;

    /** 실행 파라미터 (Agent가 자유롭게 해석) */
    @Builder.Default
    private List<ExecutionParam> params = new ArrayList<>();

    /**
     * 모드 ID 반환 (미지정 시 "default")
     */
    public String getModeIdOrDefault() {
        return modeId != null ? modeId : "default";
    }

    // === 시간 범위 ===

    public boolean isTimeRangeExecution() {
        return timeRange != null && timeRange.getStartTime() != null;
    }

    // === 파라미터 접근 ===

    /**
     * paramId로 파라미터 조회
     */
    public ExecutionParam getParam(String paramId) {
        return params.stream()
                .filter(p -> paramId.equals(p.getParamId()))
                .findFirst().orElse(null);
    }

    /**
     * paramId로 값 조회 (없으면 null)
     */
    public String getParamValue(String paramId) {
        ExecutionParam p = getParam(paramId);
        return p != null && p.getValue() != null ? String.valueOf(p.getValue()) : null;
    }

    /**
     * 파라미터가 하나라도 있는지 확인
     */
    public boolean hasParams() {
        return params != null && !params.isEmpty();
    }

    @Getter
    @Builder
    public static class TimeRange {
        private LocalDateTime startTime;
        private LocalDateTime endTime;
    }

    @Getter
    @Builder
    public static class ExecutionParam {
        private String paramId;    // "sido", "obsv-code"
        private Object value;      // 값
    }
}
