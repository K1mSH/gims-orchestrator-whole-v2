package com.sync.agent.common.pipeline;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 파이프라인 Controller 응답 DTO
 */
public class PipelineDto {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ExecuteResponse {
        private boolean accepted;
        private String executionId;
        private String agentCode;
        private String agentType;
        private String message;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ResyncResponse {
        private String message;
        private String executionId;
        private String agentCode;
        private String resyncPeriod;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StatusResponse {
        private String status;
        private long totalReadCount;
        private long totalWriteCount;
        private long totalSkipCount;
        private long durationMs;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ExecutionResultResponse {
        private String executionId;
        private String status;
        private long readCount;
        private long writeCount;
        private long skipCount;
        private long durationMs;
        private String errorMessage;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TablesResponse {
        private String agentCode;
        private List<TableInfo> tables;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TableInfo {
        private String tableName;
        private String type;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ErrorResponse {
        private String error;
    }
}
