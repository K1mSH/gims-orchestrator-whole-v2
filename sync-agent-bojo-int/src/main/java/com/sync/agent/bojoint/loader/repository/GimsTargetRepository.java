package com.sync.agent.bojoint.loader.repository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

/**
 * GIMS Target DB JDBC 쿼리 모음 (순수 JDBC, JPA 엔티티 불필요)
 *
 * Target 테이블 (환경부 표준 컬럼명):
 * - tm_gd970001: ODM관측소 (제원) — READ ONLY (GIMS 자체 마스터)
 * - pm_gd970201: ODM관측자료 (EAV) — INSERT
 * - tm_gd980002: 보조수위측정망 연계현황 (증분 추적) — UPSERT
 * - tm_gd970101: ODM결과 (참조) — READ / 자동 INSERT
 *
 * Oracle/Tibero와 PostgreSQL 모두 지원 (dbType 분기)
 */
@Slf4j
public class GimsTargetRepository {

    private static final int BATCH_SIZE = 1000;

    private final JdbcTemplate targetJdbc;
    private final boolean isOracle;

    public GimsTargetRepository(JdbcTemplate targetJdbc, String dbType) {
        this.targetJdbc = targetJdbc;
        this.isOracle = "ORACLE".equalsIgnoreCase(dbType) || "TIBERO".equalsIgnoreCase(dbType);
    }

    // ==================== 제원 (tm_gd970001) — READ ONLY ====================

    /**
     * obsvtr_id → brnch_id 매핑 로드 (보조지하수관측망)
     * Loader는 제원을 쓰지 않고, 매핑용으로 읽기만 함
     */
    public Map<String, Long> loadSpotIdMap(String tableName) {
        String sql = String.format(
                "SELECT brnch_id, obsvtr_id FROM %s WHERE brnch_type_mng_trm_nm = '보조지하수관측망'",
                tableName);

        Map<String, Long> map = new HashMap<>();
        targetJdbc.query(sql, rs -> {
            map.put(rs.getString("obsvtr_id"), rs.getLong("brnch_id"));
        });

        log.info("[GimsTarget] {}에서 brnch_id 매핑 {} 건 로드", tableName, map.size());
        return map;
    }

    // ==================== 결과 매핑 (tm_gd970101) ====================

    /**
     * (brnch_id, obsrvn_artcl_id) → rslt_id 매핑 로드
     */
    public Map<String, Long> loadResultIdMap(String tableName) {
        String sql = String.format(
                "SELECT rslt_id, brnch_id, obsrvn_artcl_id FROM %s",
                tableName);

        Map<String, Long> map = new HashMap<>();
        targetJdbc.query(sql, rs -> {
            String key = rs.getLong("brnch_id") + ":" + rs.getInt("obsrvn_artcl_id");
            map.put(key, rs.getLong("rslt_id"));
        });

        log.info("[GimsTarget] {}에서 rslt_id 매핑 {} 건 로드", tableName, map.size());
        return map;
    }

    /**
     * rslt_id가 없으면 자동 INSERT하고 매핑에 추가
     * hr_unit_id = 3 (현행 GIMS 기준값)
     *
     * PG: INSERT ... ON CONFLICT DO NOTHING RETURNING rslt_id
     * Oracle: SELECT 먼저 → 없으면 INSERT → SELECT
     */
    public long ensureResultId(String resultTable, Map<String, Long> resultIdMap,
                                long brnchId, int artclId) {
        String key = brnchId + ":" + artclId;
        Long existing = resultIdMap.get(key);
        if (existing != null) return existing;

        long rsltId;

        if (isOracle) {
            // Oracle: SELECT → INSERT → SELECT 패턴
            String selectSql = String.format(
                    "SELECT rslt_id FROM %s WHERE hr_unit_id = 3 AND obsrvn_artcl_id = ? AND brnch_id = ?",
                    resultTable);

            List<Long> ids = targetJdbc.query(selectSql, (rs, rowNum) -> rs.getLong("rslt_id"), artclId, brnchId);
            if (!ids.isEmpty()) {
                rsltId = ids.get(0);
            } else {
                String insertSql = String.format(
                        "INSERT INTO %s (hr_unit_id, obsrvn_artcl_id, brnch_id, unit_id, mthd_id) VALUES (3, ?, ?, 1, 1)",
                        resultTable);
                targetJdbc.update(insertSql, artclId, brnchId);

                // INSERT 후 생성된 IDENTITY 조회
                rsltId = targetJdbc.queryForObject(selectSql, Long.class, artclId, brnchId);
            }
        } else {
            // PostgreSQL: INSERT ... ON CONFLICT DO NOTHING RETURNING rslt_id
            String sql = String.format(
                    "INSERT INTO %s (hr_unit_id, obsrvn_artcl_id, brnch_id, unit_id, mthd_id) VALUES (3, ?, ?, 1, 1) " +
                    "ON CONFLICT (hr_unit_id, obsrvn_artcl_id, brnch_id) DO NOTHING " +
                    "RETURNING rslt_id",
                    resultTable);

            List<Long> ids = targetJdbc.query(sql, (rs, rowNum) -> rs.getLong("rslt_id"), artclId, brnchId);

            if (!ids.isEmpty()) {
                rsltId = ids.get(0);
            } else {
                // ON CONFLICT DO NOTHING → SELECT
                String selectSql = String.format(
                        "SELECT rslt_id FROM %s WHERE hr_unit_id = 3 AND obsrvn_artcl_id = ? AND brnch_id = ?",
                        resultTable);
                rsltId = targetJdbc.queryForObject(selectSql, Long.class, artclId, brnchId);
            }
        }

        resultIdMap.put(key, rsltId);
        log.debug("[GimsTarget] rslt_id={} 확보 완료 (brnch_id={}, artcl_id={})", rsltId, brnchId, artclId);
        return rsltId;
    }

    // ==================== 관측데이터 (pm_gd970201) ====================

    /**
     * 관측데이터 배치 INSERT (EAV 확장 후)
     * 현행 시스템과 동일하게 UK 없이 단순 INSERT
     * 각 행: [rslt_id, obsrvn_data_vl, obsrvn_dt, qlt_id, execution_id, source_refs]
     */
    public int batchInsertObsvdata(String tableName, List<Object[]> rows) {
        if (rows.isEmpty()) return 0;

        String sql = String.format(
                "INSERT INTO %s (rslt_id, obsrvn_data_vl, obsrvn_dt, qlt_id, execution_id, source_refs) " +
                "VALUES (?, ?, ?, ?, ?, ?)",
                tableName);

        int totalInserted = 0;
        for (int i = 0; i < rows.size(); i += BATCH_SIZE) {
            List<Object[]> batch = rows.subList(i, Math.min(i + BATCH_SIZE, rows.size()));
            int[] results = targetJdbc.batchUpdate(sql, batch);
            totalInserted += Arrays.stream(results).sum();
        }

        log.info("[GimsTarget] {}에 관측데이터 {} 행 배치 INSERT", tableName, totalInserted);
        return totalInserted;
    }

    // ==================== Link (tm_gd980002) ====================

    /**
     * Link 테이블 배치 UPSERT
     *
     * PG: INSERT ... ON CONFLICT (obsvtr_id) DO UPDATE SET ...
     * Oracle: MERGE INTO ... USING (SELECT ... FROM DUAL) ON (...) WHEN MATCHED/NOT MATCHED
     *
     * COALESCE(기존값, 신규값) 동작:
     *   - 기존 Link 존재(UPDATE): frst_obsrvn_ymd/frst_obsrvn_hr = 기존값 유지
     *   - 신규 INSERT: frst_obsrvn_ymd/frst_obsrvn_hr = VALUES의 값 사용
     *
     * @param rows 각 행: [obsvtr_id, brnch_id, last_obsrvn_ymd, last_obsrvn_hr, chg_dt, frst_obsrvn_ymd, frst_obsrvn_hr]
     */
    public int batchUpsertLink(String tableName, List<Object[]> rows) {
        if (rows.isEmpty()) return 0;

        String sql;
        if (isOracle) {
            sql = String.format(
                    "MERGE INTO %s t " +
                    "USING (SELECT ? AS obsvtr_id, ? AS brnch_id, ? AS last_obsrvn_ymd, " +
                    "? AS last_obsrvn_hr, ? AS chg_dt, ? AS frst_obsrvn_ymd, ? AS frst_obsrvn_hr FROM DUAL) s " +
                    "ON (t.obsvtr_id = s.obsvtr_id) " +
                    "WHEN MATCHED THEN UPDATE SET " +
                    "t.last_obsrvn_ymd = s.last_obsrvn_ymd, " +
                    "t.last_obsrvn_hr = s.last_obsrvn_hr, " +
                    "t.chg_dt = s.chg_dt, " +
                    "t.frst_obsrvn_ymd = COALESCE(t.frst_obsrvn_ymd, s.frst_obsrvn_ymd), " +
                    "t.frst_obsrvn_hr = COALESCE(t.frst_obsrvn_hr, s.frst_obsrvn_hr) " +
                    "WHEN NOT MATCHED THEN INSERT " +
                    "(obsvtr_id, brnch_id, last_obsrvn_ymd, last_obsrvn_hr, chg_dt, frst_obsrvn_ymd, frst_obsrvn_hr) " +
                    "VALUES (s.obsvtr_id, s.brnch_id, s.last_obsrvn_ymd, s.last_obsrvn_hr, " +
                    "s.chg_dt, s.frst_obsrvn_ymd, s.frst_obsrvn_hr)",
                    tableName);
        } else {
            sql = String.format(
                    "INSERT INTO %s (obsvtr_id, brnch_id, last_obsrvn_ymd, last_obsrvn_hr, " +
                    "chg_dt, frst_obsrvn_ymd, frst_obsrvn_hr) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                    "ON CONFLICT (obsvtr_id) DO UPDATE SET " +
                    "last_obsrvn_ymd = EXCLUDED.last_obsrvn_ymd, " +
                    "last_obsrvn_hr = EXCLUDED.last_obsrvn_hr, " +
                    "chg_dt = EXCLUDED.chg_dt, " +
                    "frst_obsrvn_ymd = COALESCE(%s.frst_obsrvn_ymd, EXCLUDED.frst_obsrvn_ymd), " +
                    "frst_obsrvn_hr = COALESCE(%s.frst_obsrvn_hr, EXCLUDED.frst_obsrvn_hr)",
                    tableName, tableName, tableName);
        }

        int totalUpserted = 0;
        for (int i = 0; i < rows.size(); i += BATCH_SIZE) {
            List<Object[]> batch = rows.subList(i, Math.min(i + BATCH_SIZE, rows.size()));
            int[] results = targetJdbc.batchUpdate(sql, batch);
            totalUpserted += results.length;
        }

        log.info("[GimsTarget] {}에 Link {} 행 배치 UPSERT", tableName, totalUpserted);
        return totalUpserted;
    }
}
