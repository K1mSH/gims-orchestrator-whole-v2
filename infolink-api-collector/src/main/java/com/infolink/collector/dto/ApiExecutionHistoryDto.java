package com.infolink.collector.dto;

import com.infolink.collector.entity.ApiExecutionHistory;
import lombok.*;

import java.time.LocalDateTime;

public class ApiExecutionHistoryDto {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Response {
        private Long id;
        private String executionId;
        private ApiExecutionHistory.Status status;
        private Integer httpStatusCode;
        private Integer responseCount;
        private Integer insertCount;
        private Integer updateCount;
        private Integer skipCount;
        private String errorMessage;
        private LocalDateTime startedAt;
        private LocalDateTime finishedAt;
        private Long durationMs;
        private ApiExecutionHistory.TriggeredBy triggeredBy;

        public static Response from(ApiExecutionHistory h) {
            return Response.builder()
                    .id(h.getId())
                    .executionId(h.getExecutionId())
                    .status(h.getStatus())
                    .httpStatusCode(h.getHttpStatusCode())
                    .responseCount(h.getResponseCount())
                    .insertCount(h.getInsertCount())
                    .updateCount(h.getUpdateCount())
                    .skipCount(h.getSkipCount())
                    .errorMessage(h.getErrorMessage())
                    .startedAt(h.getStartedAt())
                    .finishedAt(h.getFinishedAt())
                    .durationMs(h.getDurationMs())
                    .triggeredBy(h.getTriggeredBy())
                    .build();
        }
    }
}
