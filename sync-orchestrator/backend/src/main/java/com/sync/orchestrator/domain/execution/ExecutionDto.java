package com.sync.orchestrator.domain.execution;

import com.sync.orchestrator.domain.agent.AgentStatus;
import lombok.*;

import java.time.LocalDateTime;

public class ExecutionDto {

    /**
     * Agent 상태 요약 (대시보드용)
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AgentExecutionSummary {
        private Long agentId;
        private String agentCode;
        private String agentName;
        private String zone;
        private ExecutionStatus lastExecutionStatus;
        private LocalDateTime lastRunAt;
        private AgentStatus agentStatus;

        public static AgentExecutionSummary of(Long agentId, String agentCode, String agentName, String zone,
                                               ExecutionStatus lastExecutionStatus, LocalDateTime lastRunAt,
                                               AgentStatus agentStatus) {
            return AgentExecutionSummary.builder()
                    .agentId(agentId)
                    .agentCode(agentCode)
                    .agentName(agentName)
                    .zone(zone)
                    .lastExecutionStatus(lastExecutionStatus)
                    .lastRunAt(lastRunAt)
                    .agentStatus(agentStatus)
                    .build();
        }
    }

    /**
     * 테이블 데이터 조회용 검색/페이징/정렬 파라미터
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TableDataSearchParams {
        @Builder.Default
        private int page = 0;
        @Builder.Default
        private int size = 20;
        private String search;
        private String searchColumn;
        private String status;
        private String tableName;  // 테이블명 (여러 테이블 지원용)
        @Builder.Default
        private String sortColumn = "id";  // 정렬 컬럼 (기본값: id)
        @Builder.Default
        private String sortDirection = "asc";  // 정렬 방향 (기본값: asc)

        public String toQueryString() {
            StringBuilder sb = new StringBuilder();
            sb.append("page=").append(page);
            sb.append("&size=").append(size);
            if (search != null && !search.isBlank()) {
                sb.append("&search=").append(search);
            }
            if (searchColumn != null && !searchColumn.isBlank()) {
                sb.append("&searchColumn=").append(searchColumn);
            }
            if (status != null && !status.isBlank()) {
                sb.append("&status=").append(status);
            }
            if (tableName != null && !tableName.isBlank()) {
                sb.append("&tableName=").append(tableName);
            }
            if (sortColumn != null && !sortColumn.isBlank()) {
                sb.append("&sortColumn=").append(sortColumn);
            }
            if (sortDirection != null && !sortDirection.isBlank()) {
                sb.append("&sortDirection=").append(sortDirection);
            }
            return sb.toString();
        }
    }

    /**
     * 실행 트리거 요청
     * - startTime/endTime: 지정 시 해당 범위 데이터 동기화 (재동기화용)
     * - 미지정 시: 증분 동기화 (link_status 기반)
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TriggerRequest {
        @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime startTime;  // 동기화 시작 시간 (optional)

        @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        private LocalDateTime endTime;    // 동기화 종료 시간 (optional)

        /**
         * 실행 모드 ID (optional)
         * Agent에 정의된 실행 방식 (예: "incremental", "full-reload")
         */
        private String executionModeId;

        /**
         * 실행 필터 목록 (optional)
         * 각 필터: {"paramId":"sido","category":"FILTER","column":"sido","operator":"EQ","value":"경기도"}
         */
        private java.util.List<java.util.Map<String, Object>> filters;

        /**
         * 선택적 Step 실행 (optional)
         * 지정 시 해당 stepId만 실행, 미지정 시 전체 실행
         */
        private java.util.List<String> selectedStepIds;
    }

    /**
     * 실행 트리거 응답
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TriggerResponse {
        private String executionId;
        private Long agentId;
        private String agentCode;
        private String status;
        private LocalDateTime startTime;  // 사용된 시작 시간
        private LocalDateTime endTime;    // 사용된 종료 시간
    }

    /**
     * 실행 이력 응답 (대시보드 모니터링용)
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class HistoryResponse {
        private String executionId;
        private String agentCode;
        private String agentName;
        private String agentType;
        private ExecutionStatus status;
        private Long totalReadCount;
        private Long totalWriteCount;
        private Long totalSkipCount;
        private Long durationMs;
        private String errorMessage;
        private LocalDateTime startedAt;
        private LocalDateTime finishedAt;
        private String triggeredBy;

        public static HistoryResponse from(ExecutionHistory history) {
            return HistoryResponse.builder()
                    .executionId(history.getExecutionId())
                    .agentCode(history.getAgentCode())
                    .agentName(history.getAgentName())
                    .agentType(history.getAgentType())
                    .status(history.getStatus())
                    .totalReadCount(history.getTotalReadCount())
                    .totalWriteCount(history.getTotalWriteCount())
                    .totalSkipCount(history.getTotalSkipCount())
                    .durationMs(history.getDurationMs())
                    .errorMessage(history.getErrorMessage())
                    .startedAt(history.getStartedAt())
                    .finishedAt(history.getFinishedAt())
                    .triggeredBy(history.getTriggeredBy())
                    .build();
        }
    }

    /**
     * 대시보드 통계
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DashboardStats {
        private long todayExecutions;
        private long todayFailed;
        private long currentlyRunning;
        private long totalAgents;
        private long onlineAgents;
    }
}
