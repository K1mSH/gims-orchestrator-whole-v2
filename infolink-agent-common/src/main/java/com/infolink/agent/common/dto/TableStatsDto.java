package com.infolink.agent.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

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
    /**
     * Per-target 실 적재 카운트 (선택). multi-target mapping 에서 각 target 의 실 INSERT/UPSERT 행수.
     * sync_log.target_tables JSON 이 [{"name":"...","count":N}] 형식일 때 채워짐.
     * 단일 target 또는 count 메타 없는 sync_log 에서는 null.
     */
    private Map<String, Long> targetCounts;
}
