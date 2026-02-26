package com.sync.agent.bojoint.loader.repository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

/**
 * GIMS Target DB JDBC 쿼리 모음 (순수 JDBC, JPA 엔티티 불필요)
 *
 * Target 테이블:
 * - tm_gd970001: 관측소 (제원) — READ ONLY (GIMS 자체 마스터)
 * - pm_gd970201: 관측자료 (EAV) — INSERT
 * - tm_gd980002: Link (증분 추적) — UPSERT
 * - tm_gd970101: 결과 매핑 (참조) — READ / 자동 INSERT
 */
@Slf4j
public class GimsTargetRepository {

    private static final int BATCH_SIZE = 1000;

    private final JdbcTemplate targetJdbc;

    public GimsTargetRepository(JdbcTemplate targetJdbc) {
        this.targetJdbc = targetJdbc;
    }

    // ==================== 제원 (tm_gd970001) — READ ONLY ====================

    /**
     * obsrvt_id → spot_id 매핑 로드 (보조지하수관측망)
     * Loader는 제원을 쓰지 않고, 매핑용으로 읽기만 함
     */
    public Map<String, Long> loadSpotIdMap(String tableName) {
        String sql = String.format(
                "SELECT spot_id, obsrvt_id FROM %s WHERE spot_ty_mng_word_nm = '보조지하수관측망'",
                tableName);

        Map<String, Long> map = new HashMap<>();
        targetJdbc.query(sql, rs -> {
            map.put(rs.getString("obsrvt_id"), rs.getLong("spot_id"));
        });

        log.info("[GimsTarget] Loaded {} spot_id mappings from {}", map.size(), tableName);
        return map;
    }

    // ==================== 결과 매핑 (tm_gd970101) ====================

    /**
     * (spot_id, obsrvn_iem_id) → result_id 매핑 로드
     */
    public Map<String, Long> loadResultIdMap(String tableName) {
        String sql = String.format(
                "SELECT result_id, spot_id, obsrvn_iem_id FROM %s",
                tableName);

        Map<String, Long> map = new HashMap<>();
        targetJdbc.query(sql, rs -> {
            String key = rs.getLong("spot_id") + ":" + rs.getInt("obsrvn_iem_id");
            map.put(key, rs.getLong("result_id"));
        });

        log.info("[GimsTarget] Loaded {} result_id mappings from {}", map.size(), tableName);
        return map;
    }

    /**
     * result_id가 없으면 자동 INSERT하고 매핑에 추가
     * time_unit_id = 3 (현행 GIMS 기준값)
     */
    public long ensureResultId(String resultTable, Map<String, Long> resultIdMap,
                                long spotId, int iemId) {
        String key = spotId + ":" + iemId;
        Long existing = resultIdMap.get(key);
        if (existing != null) return existing;

        // INSERT and get generated key
        String sql = String.format(
                "INSERT INTO %s (time_unit_id, obsrvn_iem_id, spot_id) VALUES (3, ?, ?) " +
                "ON CONFLICT (time_unit_id, obsrvn_iem_id, spot_id) DO NOTHING " +
                "RETURNING result_id",
                resultTable);

        List<Long> ids = targetJdbc.query(sql, (rs, rowNum) -> rs.getLong("result_id"), iemId, spotId);

        long resultId;
        if (!ids.isEmpty()) {
            resultId = ids.get(0);
        } else {
            // ON CONFLICT DO NOTHING → SELECT
            String selectSql = String.format(
                    "SELECT result_id FROM %s WHERE time_unit_id = 3 AND obsrvn_iem_id = ? AND spot_id = ?",
                    resultTable);
            resultId = targetJdbc.queryForObject(selectSql, Long.class, iemId, spotId);
        }

        resultIdMap.put(key, resultId);
        log.debug("[GimsTarget] Ensured result_id={} for spot_id={}, iem_id={}", resultId, spotId, iemId);
        return resultId;
    }

    // ==================== 관측데이터 (pm_gd970201) ====================

    /**
     * 관측데이터 배치 INSERT (EAV 확장 후)
     * 현행 시스템과 동일하게 UK 없이 단순 INSERT
     */
    public int batchInsertObsvdata(String tableName, List<Object[]> rows) {
        if (rows.isEmpty()) return 0;

        String sql = String.format(
                "INSERT INTO %s (result_id, obsrvn_dta_value, obsrvn_dt, qlt_id) " +
                "VALUES (?, ?, ?, ?)",
                tableName);

        int totalInserted = 0;
        for (int i = 0; i < rows.size(); i += BATCH_SIZE) {
            List<Object[]> batch = rows.subList(i, Math.min(i + BATCH_SIZE, rows.size()));
            int[] results = targetJdbc.batchUpdate(sql, batch);
            totalInserted += Arrays.stream(results).sum();
        }

        log.info("[GimsTarget] Batch INSERT {} obsvdata rows into {}", totalInserted, tableName);
        return totalInserted;
    }

    // ==================== Link (tm_gd980002) ====================

    /**
     * Link 테이블 배치 UPSERT
     *
     * 현행(레거시)과의 차이:
     *   현행: 관측소별 개별 SELECT(getLinkData) + INSERT/UPDATE 분기 = 2N회 DB 호출
     *   개선: COALESCE로 frst_date/frst_time 보존 로직을 SQL에 위임 → SELECT 제거,
     *         batch UPSERT로 N회 → ceil(N/1000)회로 축소
     *
     * COALESCE(기존값, 신규값) 동작:
     *   - 기존 Link 존재(UPDATE): frst_date/frst_time = 기존값 유지
     *   - 신규 INSERT: frst_date/frst_time = VALUES의 값 사용
     *   → Java에서 기존 Link를 SELECT할 필요 없음
     *
     * @param rows 각 행: [obsrvt_id, spot_id, last_obsrvn_de, last_obsrvn_time, change_dt, frst_date, frst_time]
     */
    public int batchUpsertLink(String tableName, List<Object[]> rows) {
        if (rows.isEmpty()) return 0;

        String sql = String.format(
                "INSERT INTO %s (obsrvt_id, spot_id, last_obsrvn_de, last_obsrvn_time, " +
                "change_dt, frst_date, frst_time) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT (obsrvt_id) DO UPDATE SET " +
                "last_obsrvn_de = EXCLUDED.last_obsrvn_de, " +
                "last_obsrvn_time = EXCLUDED.last_obsrvn_time, " +
                "change_dt = EXCLUDED.change_dt, " +
                "frst_date = COALESCE(%s.frst_date, EXCLUDED.frst_date), " +
                "frst_time = COALESCE(%s.frst_time, EXCLUDED.frst_time)",
                tableName, tableName, tableName);

        int totalUpserted = 0;
        for (int i = 0; i < rows.size(); i += BATCH_SIZE) {
            List<Object[]> batch = rows.subList(i, Math.min(i + BATCH_SIZE, rows.size()));
            int[] results = targetJdbc.batchUpdate(sql, batch);
            totalUpserted += results.length;
        }

        log.info("[GimsTarget] Batch UPSERT {} link rows into {}", totalUpserted, tableName);
        return totalUpserted;
    }
}
