package com.sync.agent.common.service;

import com.sync.agent.common.config.RetentionConfig;
import com.sync.agent.common.controller.DataSourceProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 타겟 테이블 자동삭제(Retention) 실행 서비스
 * - RetentionConfig에 따라 오래된 데이터를 DELETE
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataRetentionService {

    private final DataSourceProvider dataSourceProvider;

    /**
     * retention 설정에 따라 오래된 데이터 삭제 실행
     *
     * @param config retention 설정
     * @param targetDatasourceId target datasource ID
     * @return 테이블별 삭제 결과 목록
     */
    public CleanupResult executeCleanup(RetentionConfig config, String targetDatasourceId) {
        JdbcTemplate jdbc = dataSourceProvider.getJdbcTemplate(targetDatasourceId);
        List<TableResult> results = new ArrayList<>();
        int totalDeleted = 0;

        for (RetentionConfig.TableRetention target : config.getTargets()) {
            try {
                LocalDate cutoffDate = LocalDate.now().minusDays(target.getRetentionDays());
                LocalDateTime cutoffDateTime = cutoffDate.atStartOfDay();
                String sql = String.format("DELETE FROM %s WHERE %s < ?",
                        target.getTable(), target.getDateColumn());

                int deleted = jdbc.update(sql, Timestamp.valueOf(cutoffDateTime));
                results.add(new TableResult(target.getTable(), deleted, cutoffDate.toString(), null));
                totalDeleted += deleted;

                log.info("[Retention] {} : {} 이전 데이터 {}건 삭제 (dateColumn={})",
                        target.getTable(), cutoffDate, deleted, target.getDateColumn());
            } catch (Exception e) {
                log.error("[Retention] {} 삭제 실패: {}", target.getTable(), e.getMessage(), e);
                results.add(new TableResult(target.getTable(), 0, null, e.getMessage()));
            }
        }

        return new CleanupResult(results, totalDeleted);
    }

    /**
     * 테이블별 삭제 결과
     */
    public record TableResult(String table, int deletedCount, String cutoffDate, String error) {}

    /**
     * 전체 삭제 결과
     */
    public record CleanupResult(List<TableResult> results, int totalDeleted) {}
}
