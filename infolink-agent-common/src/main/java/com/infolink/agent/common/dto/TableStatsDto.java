package com.infolink.agent.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TableStatsDto {
    private String mappingName;
    private List<String> sourceTables;
    private List<String> targetTables;
    private long readCount;
    private long writeCount;
    private long failedCount;
    private long skipCount;
}
