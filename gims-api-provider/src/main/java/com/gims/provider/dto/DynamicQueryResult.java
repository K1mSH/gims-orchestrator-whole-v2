package com.gims.provider.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class DynamicQueryResult {

    private List<Map<String, Object>> data;
    private PaginationInfo pagination;
    private String executedSql;
    private long durationMs;

    @Data
    @Builder
    public static class PaginationInfo {
        private int page;
        private int pageSize;
        private long totalCount;
        private int totalPages;
    }
}
