package com.sync.agent.bojoint.loader.step;

import com.sync.agent.common.controller.DataSourceProvider;
import com.sync.agent.common.entity.SyncLog;
import com.sync.agent.common.repository.SyncLogRepository;
import com.sync.agent.common.service.IfTableService;
import com.sync.agent.common.step.ConditionBuilder;
import com.sync.agent.common.step.StepContext;
import com.sync.agent.common.step.StepExecutor;
import com.sync.agent.common.step.StepResult;
import com.sync.agent.common.step.Status;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 제주 제원 Loader Step (I1)
 *
 * IF_RSV_TB_JEJU_JEWON → 5개 GIMS Target 테이블 순차 MERGE
 * 레거시 JewonDB.java + target.xml 로직 이식
 *
 * 실행 순서 (건별):
 *   1. TM_GD970001 (ODM관측소) MERGE → BRNCH_ID 확보
 *   2. TM_GD120001 (관정) MERGE → GWEL_NO 확보
 *   3. TM_GD970130 (ODM관정사양) MERGE
 *   4. TM_GD970002 (ODM관측소사양) MERGE
 *   5. TM_GD970101 (ODM결과) MERGE × 3 (GL/WTEMP/EC)
 */
@Slf4j
public class JejuJewonLoadStep implements StepExecutor {

    // 고정값 (레거시 JewonDB.class 디컴파일 확인)
    private static final String V1_BRNCH_TYPE = "보조지하수관측망";
    private static final String V2_VTCL_CTRL = "제주도평균해수면";
    private static final String V3_CTPV = "제주특별자치도";
    private static final String V4_RMRK = "제주도연계데이터";
    private static final String V5_GNRL_CTGRY = "연계";
    private static final String V6_OBSRVN_ARTCL = "수위, 온도, 전기전도도";
    private static final String V7_OBSRVN_CYCL = "1시간 간격";
    private static final String V8_CNST_INST = "제주도지자체";

    // EAV 항목 ID / 단위 ID
    private static final int IEM_GL = 5;         // 수위
    private static final int IEM_WTEMP = 163;    // 수온
    private static final int IEM_EC = 52;        // 전기전도도
    private static final int UNIT_GL = 5;
    private static final int UNIT_WTEMP = 58;
    private static final int UNIT_EC = 52;

    private final String stepId;
    private final String stepName;
    private final String ifTable;
    private final List<String> configSourceTables;
    private final List<String> configTargetTables;
    private final DataSourceProvider dataSourceProvider;
    private final SyncLogRepository syncLogRepository;
    private final IfTableService ifTableService;
    private final com.sync.agent.bojoint.config.DynamicEntityManagerService dynamicEmService;

    public JejuJewonLoadStep(String stepId, String stepName,
                              String ifTable,
                              List<String> configSourceTables, List<String> configTargetTables,
                              DataSourceProvider dataSourceProvider,
                              SyncLogRepository syncLogRepository,
                              IfTableService ifTableService,
                              com.sync.agent.bojoint.config.DynamicEntityManagerService dynamicEmService) {
        this.stepId = stepId;
        this.stepName = stepName;
        this.ifTable = ifTable;
        this.configSourceTables = configSourceTables;
        this.configTargetTables = configTargetTables;
        this.dataSourceProvider = dataSourceProvider;
        this.syncLogRepository = syncLogRepository;
        this.ifTableService = ifTableService;
        this.dynamicEmService = dynamicEmService;
    }

    @Override
    public String getStepId() { return stepId; }

    @Override
    public String getStepName() { return stepName; }

    @Override
    public StepResult execute(StepContext context) {
        long startTime = System.currentTimeMillis();
        int readCount = 0;
        int writeCount = 0;
        int failedCount = 0;
        List<String> failedKeys = new ArrayList<>();
        String firstError = null;

        try {
            String sourceDsId = context.getSourceDatasourceId();
            String targetDsId = context.getTargetDatasourceId();
            JdbcTemplate sourceJdbc = dataSourceProvider.getJdbcTemplate(sourceDsId);
            JdbcTemplate targetJdbc = dataSourceProvider.getJdbcTemplate(targetDsId);
            String executionId = context.getExecutionId();

            // 1. 조건실행 판별 + IF 테이블 조회
            String sourceDbType = dataSourceProvider.getDbType(sourceDsId);
            boolean isResync = ConditionBuilder.isResyncExecution(context.getExecutionOptions());
            ConditionBuilder.WhereClause where = ConditionBuilder.buildIfTableQuery(
                    context.getExecutionOptions(), ifTable, sourceDbType);
            String sql = "SELECT * FROM " + ifTable + where.toWhereSql();
            List<Map<String, Object>> pendingRows = sourceJdbc.queryForList(sql, where.getParamsArray());
            readCount = pendingRows.size();
            log.info("[{}] IF_RSV에서 {} 건의 {} 제원 데이터 조회", stepId, readCount,
                    isResync ? "재동기화 (조건)" : "대기중");

            if (pendingRows.isEmpty()) {
                return StepResult.builder()
                        .stepId(stepId).status(Status.SUCCESS)
                        .readCount(0).writeCount(0).skipCount(0)
                        .durationMs(System.currentTimeMillis() - startTime)
                        .build();
            }

            List<Object> successIds = new ArrayList<>();
            List<Object> failedIds = new ArrayList<>();

            // 2. 건별 순차 처리
            for (Map<String, Object> row : pendingRows) {
                String obsvtrId = getString(row, "OBSRVT_ID");
                try {
                    // source_refs 생성
                    Object ifId = row.get("ID");
                    String sourceRef = String.format("[\"I:%s:%s:%s\"]", sourceDsId, ifTable, ifId);

                    // (1) TM_GD970001 MERGE → BRNCH_ID 확보
                    long brnchId = mergeGd970001(targetJdbc, row, sourceRef, executionId);

                    // (2) TM_GD120001 MERGE → GWEL_NO 확보
                    long gwelNo = mergeGd120001(targetJdbc, row, brnchId, sourceRef, executionId);

                    // (3) TM_GD970130 MERGE
                    mergeGd970130(targetJdbc, row, gwelNo, brnchId, sourceRef, executionId);

                    // (4) TM_GD970002 MERGE
                    mergeGd970002(targetJdbc, brnchId, sourceRef, executionId);

                    // (5) TM_GD970101 MERGE × 3
                    mergeGd970101(targetJdbc, brnchId, gwelNo, IEM_GL, UNIT_GL, sourceRef, executionId);
                    mergeGd970101(targetJdbc, brnchId, gwelNo, IEM_WTEMP, UNIT_WTEMP, sourceRef, executionId);
                    mergeGd970101(targetJdbc, brnchId, gwelNo, IEM_EC, UNIT_EC, sourceRef, executionId);

                    successIds.add(ifId);
                    writeCount++;
                } catch (Exception e) {
                    log.error("[{}] 제원 처리 실패: obsvtr_id={}", stepId, obsvtrId, e);
                    failedCount++;
                    failedIds.add(row.get("ID"));
                    failedKeys.add(obsvtrId);
                    if (firstError == null) firstError = e.getMessage();
                }
            }

            // 3. IF 상태 업데이트
            if (!successIds.isEmpty()) {
                ifTableService.batchMarkAsProcessed(ifTable, "ID", successIds, "SUCCESS", executionId);
            }
            if (!failedIds.isEmpty()) {
                ifTableService.batchMarkAsProcessed(ifTable, "ID", failedIds, "FAILED", executionId);
            }

            long durationMs = System.currentTimeMillis() - startTime;
            log.info("[{}] 완료: read={}, write={}, failed={}, duration={}ms",
                    stepId, readCount, writeCount, failedCount, durationMs);

            // 4. SyncLog 기록
            saveSyncLog(executionId, readCount, writeCount, failedCount, failedKeys, firstError);

            return StepResult.builder()
                    .stepId(stepId)
                    .status(failedCount > 0 && writeCount == 0 ? Status.FAILED : Status.SUCCESS)
                    .readCount(readCount)
                    .writeCount(writeCount)
                    .skipCount(0)
                    .durationMs(durationMs)
                    .errorMessage(firstError)
                    .build();

        } catch (Exception e) {
            log.error("[{}] Step 실행 실패", stepId, e);
            saveSyncLog(context.getExecutionId(), readCount, writeCount, failedCount, failedKeys, e.getMessage());
            return StepResult.failed(stepId, e.getMessage(), System.currentTimeMillis() - startTime);
        }
    }

    // ==================== MERGE 메서드 ====================

    /**
     * TM_GD970001 (ODM관측소) MERGE, BRNCH_ID 반환
     */
    private long mergeGd970001(JdbcTemplate jdbc, Map<String, Object> row,
                                String sourceRef, String executionId) {
        String obsvtrId = getString(row, "OBSRVT_ID");
        String obsvtrNm = getString(row, "OBSRVT_NM");
        String lot = getString(row, "LO_VALUE");
        String lat = getString(row, "LA_VALUE");
        String stdgCd = getString(row, "LEGALDONG_CODE");
        String sigunNm = getString(row, "SIGUN_NM");
        String emdNm = getString(row, "EMD_NM");
        String liNm = getString(row, "LI_NM");
        String bunji = getString(row, "BUNJI");
        String ho = getString(row, "HO");
        String addr = V3_CTPV + " " + nvl(sigunNm) + " " + nvl(emdNm) + " " + nvl(liNm) + " " + nvl(bunji) + " " + nvl(ho);

        jdbc.update(
            "MERGE INTO TM_GD970001 t USING (SELECT ? AS OBSVTR_ID FROM DUAL) s " +
            "ON (t.OBSVTR_ID = s.OBSVTR_ID) " +
            "WHEN MATCHED THEN UPDATE SET " +
            "  OBSVTR_NM = ?, LOT = ?, LAT = ?, STDG_CD = ?, " +
            "  BRNCH_TYPE_MNG_TRM_NM = ?, VTCL_CRLPT_MNG_TRM_NM = ?, " +
            "  SPCEDATA_TYPE_MNG_TRM_NM = 'Point', ADDR = ?, RMRK_CN = ?, " +
            "  EXECUTION_ID = ?, SOURCE_REFS = ? " +
            "WHEN NOT MATCHED THEN INSERT " +
            "  (OBSVTR_ID, OBSVTR_NM, LOT, LAT, STDG_CD, " +
            "   BRNCH_TYPE_MNG_TRM_NM, VTCL_CRLPT_MNG_TRM_NM, " +
            "   SPCEDATA_TYPE_MNG_TRM_NM, ADDR, RMRK_CN, EXECUTION_ID, SOURCE_REFS) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, 'Point', ?, ?, ?, ?)",
            // ON 절
            obsvtrId,
            // UPDATE 절
            obsvtrNm, lot, lat, stdgCd, V1_BRNCH_TYPE, V2_VTCL_CTRL, addr, V4_RMRK, executionId, sourceRef,
            // INSERT 절
            obsvtrId, obsvtrNm, lot, lat, stdgCd, V1_BRNCH_TYPE, V2_VTCL_CTRL, addr, V4_RMRK, executionId, sourceRef
        );

        // BRNCH_ID 조회
        return jdbc.queryForObject(
                "SELECT BRNCH_ID FROM TM_GD970001 WHERE OBSVTR_ID = ?", Long.class, obsvtrId);
    }

    /**
     * TM_GD120001 (관정) MERGE, GWEL_NO 반환
     */
    private long mergeGd120001(JdbcTemplate jdbc, Map<String, Object> row, long brnchId,
                                String sourceRef, String executionId) {
        String obsvtrId = getString(row, "OBSRVT_ID");
        String obsvtrNm = getString(row, "OBSRVT_NM");
        String sigunNm = getString(row, "SIGUN_NM");
        String emdNm = getString(row, "EMD_NM");
        String liNm = getString(row, "LI_NM");
        String bunji = getString(row, "BUNJI");
        String ho = getString(row, "HO");
        String lot = getString(row, "LO_VALUE");
        String lat = getString(row, "LA_VALUE");
        Number tmx = getNumber(row, "TMX_VALUE");
        Number tmy = getNumber(row, "TMY_VALUE");
        Number altd = getNumber(row, "AL_VALUE");
        String useYn = getString(row, "USE_AT");
        String stdgCd = getString(row, "LEGALDONG_CODE");
        String addr = nvl(bunji) + " " + nvl(ho);

        jdbc.update(
            "MERGE INTO TM_GD120001 t USING (SELECT ? AS GRNDS_GWEL_NO, ? AS UGWTR_EXMN_CD FROM DUAL) s " +
            "ON (t.GRNDS_GWEL_NO = s.GRNDS_GWEL_NO AND t.UGWTR_EXMN_CD = s.UGWTR_EXMN_CD) " +
            "WHEN MATCHED THEN UPDATE SET " +
            "  BRNCH_NM = ?, CTPV_NM = ?, SGG_NM = ?, EMD_NM = ?, LI_NM = ?, ADDR = ?, " +
            "  LOT = ?, LAT = ?, XCRD = ?, YCRD = ?, ALTD_VL = ?, " +
            "  USE_YN = ?, PRMTV_DATA_NM = ?, PRMTV_DATA_INST_NM = ?, " +
            "  STDG_CD = ?, RMRK = ?, EXECUTION_ID = ?, SOURCE_REFS = ? " +
            "WHEN NOT MATCHED THEN INSERT " +
            "  (GRNDS_GWEL_NO, UGWTR_EXMN_CD, BRNCH_NM, CTPV_NM, SGG_NM, EMD_NM, LI_NM, ADDR, " +
            "   LOT, LAT, XCRD, YCRD, ALTD_VL, USE_YN, PRMTV_DATA_NM, PRMTV_DATA_INST_NM, " +
            "   STDG_CD, RMRK, EXECUTION_ID, SOURCE_REFS) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            // ON 절
            obsvtrId, "105",
            // UPDATE 절
            obsvtrNm, V3_CTPV, sigunNm, emdNm, liNm, addr,
            lot, lat, tmx, tmy, altd,
            useYn, V1_BRNCH_TYPE, V4_RMRK,
            stdgCd, V5_GNRL_CTGRY, executionId, sourceRef,
            // INSERT 절
            obsvtrId, "105", obsvtrNm, V3_CTPV, sigunNm, emdNm, liNm, addr,
            lot, lat, tmx, tmy, altd, useYn, V1_BRNCH_TYPE, V4_RMRK,
            stdgCd, V5_GNRL_CTGRY, executionId, sourceRef
        );

        // GWEL_NO 조회
        return jdbc.queryForObject(
                "SELECT GWEL_NO FROM TM_GD120001 WHERE GRNDS_GWEL_NO = ? AND UGWTR_EXMN_CD = '105'",
                Long.class, obsvtrId);
    }

    /**
     * TM_GD970130 (ODM관정사양) MERGE
     */
    private void mergeGd970130(JdbcTemplate jdbc, Map<String, Object> row,
                                long gwelNo, long brnchId,
                                String sourceRef, String executionId) {
        String calbr = getString(row, "EXTN_CSNG_CALBR");
        String dkppYn = getString(row, "DRNK_AT");
        Number wtlv = getNumber(row, "WAL");
        String usgCd = getString(row, "UGRWTR_PRPOS_CODE");

        jdbc.update(
            "MERGE INTO TM_GD970130 t USING (SELECT ? AS GWEL_NO FROM DUAL) s " +
            "ON (t.GWEL_NO = s.GWEL_NO) " +
            "WHEN MATCHED THEN UPDATE SET " +
            "  BRNCH_ID = ?, OTSD_CSNG_CALBR = ?, DKPP_YN = ?, WTLV = ?, " +
            "  UGWTR_DTL_USG_CD = ?, OBSRVN_ARTCL_NM = ?, OBSRVN_CYCL_CN = ?, " +
            "  EXECUTION_ID = ?, SOURCE_REFS = ? " +
            "WHEN NOT MATCHED THEN INSERT " +
            "  (GWEL_NO, BRNCH_ID, OTSD_CSNG_CALBR, DKPP_YN, WTLV, " +
            "   UGWTR_DTL_USG_CD, OBSRVN_ARTCL_NM, OBSRVN_CYCL_CN, EXECUTION_ID, SOURCE_REFS) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            // ON 절
            gwelNo,
            // UPDATE 절
            brnchId, calbr, dkppYn, wtlv, usgCd, V6_OBSRVN_ARTCL, V7_OBSRVN_CYCL, executionId, sourceRef,
            // INSERT 절
            gwelNo, brnchId, calbr, dkppYn, wtlv, usgCd, V6_OBSRVN_ARTCL, V7_OBSRVN_CYCL, executionId, sourceRef
        );
    }

    /**
     * TM_GD970002 (ODM관측소사양) MERGE
     */
    private void mergeGd970002(JdbcTemplate jdbc, long brnchId,
                                String sourceRef, String executionId) {
        jdbc.update(
            "MERGE INTO TM_GD970002 t USING (SELECT ? AS BRNCH_ID FROM DUAL) s " +
            "ON (t.BRNCH_ID = s.BRNCH_ID) " +
            "WHEN MATCHED THEN UPDATE SET " +
            "  USE_YN = 'O', CNST_INST_CN = ?, EXECUTION_ID = ?, SOURCE_REFS = ? " +
            "WHEN NOT MATCHED THEN INSERT " +
            "  (BRNCH_ID, USE_YN, CNST_INST_CN, EXECUTION_ID, SOURCE_REFS) " +
            "VALUES (?, 'O', ?, ?, ?)",
            // ON 절
            brnchId,
            // UPDATE 절
            V8_CNST_INST, executionId, sourceRef,
            // INSERT 절
            brnchId, V8_CNST_INST, executionId, sourceRef
        );
    }

    /**
     * TM_GD970101 (ODM결과) MERGE — 항목별 호출
     */
    private void mergeGd970101(JdbcTemplate jdbc, long brnchId, long gwelNo,
                                int artclId, int unitId,
                                String sourceRef, String executionId) {
        // SELECT → 존재하면 UPDATE, 없으면 INSERT
        List<Long> existing = jdbc.query(
                "SELECT RSLT_ID FROM TM_GD970101 WHERE BRNCH_ID = ? AND HR_UNIT_ID = 3 AND OBSRVN_ARTCL_ID = ?",
                (rs, rowNum) -> rs.getLong("RSLT_ID"), brnchId, artclId);

        if (!existing.isEmpty()) {
            jdbc.update(
                "UPDATE TM_GD970101 SET " +
                "  RSLT_DT = SYSDATE, OBSRVN_ARTCL_ID = ?, UNIT_ID = ?, MTHD_ID = 0, " +
                "  BRNCH_ID = ?, HR_GAP_VL = 1, HR_UNIT_ID = 3, " +
                "  GNRL_CTGRY_MNG_TRM_NM = ?, VL_TYPE_MNG_TRM_NM = 'POINTTIMESERIESRESULTVALUES', " +
                "  DATA_TYPE_MNG_TRM_NM = ?, FRST_REG_DT = SYSDATE, LAST_CHG_DT = SYSDATE, " +
                "  TAG_CN = ?, EXECUTION_ID = ?, SOURCE_REFS = ? " +
                "WHERE RSLT_ID = ?",
                artclId, unitId, brnchId,
                V5_GNRL_CTGRY, V6_OBSRVN_ARTCL,
                String.valueOf(gwelNo), executionId, sourceRef,
                existing.get(0));
        } else {
            jdbc.update(
                "INSERT INTO TM_GD970101 " +
                "  (RSLT_DT, OBSRVN_ARTCL_ID, UNIT_ID, MTHD_ID, BRNCH_ID, " +
                "   HR_GAP_VL, HR_UNIT_ID, " +
                "   GNRL_CTGRY_MNG_TRM_NM, VL_TYPE_MNG_TRM_NM, DATA_TYPE_MNG_TRM_NM, " +
                "   FRST_REG_DT, LAST_CHG_DT, TAG_CN, EXECUTION_ID, SOURCE_REFS) " +
                "VALUES (SYSDATE, ?, ?, 0, ?, 1, 3, ?, 'POINTTIMESERIESRESULTVALUES', ?, SYSDATE, SYSDATE, ?, ?, ?)",
                artclId, unitId, brnchId,
                V5_GNRL_CTGRY, V6_OBSRVN_ARTCL,
                String.valueOf(gwelNo), executionId, sourceRef);
        }
    }

    // ==================== 헬퍼 ====================

    private String getString(Map<String, Object> row, String key) {
        Object val = row.get(key);
        return val != null ? val.toString() : null;
    }

    private Number getNumber(Map<String, Object> row, String key) {
        Object val = row.get(key);
        if (val instanceof Number) return (Number) val;
        if (val != null) {
            try { return Double.parseDouble(val.toString()); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    private String nvl(String val) {
        return val != null ? val : "";
    }

    private void saveSyncLog(String executionId, int readCount, int writeCount,
                              int failedCount, List<String> failedKeys, String errorSummary) {
        try {
            String sourceJson = "[" + configSourceTables.stream().map(t -> "\"" + t + "\"").reduce((a, b) -> a + "," + b).orElse("") + "]";
            String targetJson = "[" + configTargetTables.stream().map(t -> "\"" + t + "\"").reduce((a, b) -> a + "," + b).orElse("") + "]";
            SyncLog logEntry = SyncLog.builder()
                    .executionId(executionId)
                    .stepId(stepId)
                    .mappingName(stepId)
                    .sourceTables(sourceJson)
                    .targetTables(targetJson)
                    .readCount((long) readCount)
                    .writeCount((long) writeCount)
                    .failedCount((long) failedCount)
                    .skipCount(0L)
                    .failedKeys(failedKeys.isEmpty() ? null : String.join(",", failedKeys))
                    .errorSummary(errorSummary)
                    .build();
            syncLogRepository.save(logEntry);
        } catch (Exception e) {
            log.warn("[{}] SyncLog 저장 실패: {}", stepId, e.getMessage());
        }
    }
}
