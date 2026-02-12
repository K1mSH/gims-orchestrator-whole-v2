package com.sync.agent.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TableStatsDto {
    private String tableName;
    private String tableType;  // SOURCE, TARGET_IF, TARGET
    private long totalCount;
    private long successCount;
    private long failedCount;
    private long skipCount;
}
