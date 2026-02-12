package com.sync.agent.common.service;

import com.sync.agent.common.entity.SyncRecordHistory;
import com.sync.agent.common.repository.SyncRecordHistoryRepository;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SyncRecordHistoryService {

    private final SyncRecordHistoryRepository repository;

    /**
     * 배치 이력 저장.
     * Step 처리 완료 후 한 번에 저장 (성능).
     */
    @Transactional
    public void saveBatch(String executionId, String stepId, String tableName,
                          List<HistoryEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        List<SyncRecordHistory> histories = entries.stream()
                .map(entry -> SyncRecordHistory.builder()
                        .executionId(executionId)
                        .stepId(stepId)
                        .tableName(tableName)
                        .recordKey(entry.getRecordKey())
                        .action(entry.getAction())
                        .sourceRefs(entry.getSourceRefs())
                        .processedAt(now)
                        .build())
                .collect(Collectors.toList());

        repository.saveAll(histories);
        log.info("Saved {} record history entries for execution={}, step={}, table={}",
                histories.size(), executionId, stepId, tableName);
    }

    /**
     * 이력 항목 DTO
     */
    @Getter
    @Builder
    public static class HistoryEntry {
        private final String recordKey;    // "GPM-3050-001" 또는 "GPM-3050-001|2026-01-15|093000"
        private final String action;       // "UPSERT", "INSERT", "UPDATE"
        private final String sourceRefs;   // source_refs JSON 스냅샷
    }
}
