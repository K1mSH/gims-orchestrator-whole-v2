package com.sync.agent.bojoint.loader.step;

import com.sync.agent.bojoint.config.DynamicEntityManagerService;
import com.sync.agent.bojoint.entity.iftable.saeol.IfRsvRgetstgms01;
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
import org.springframework.jdbc.core.RowCallbackHandler;

import javax.persistence.Column;
import javax.persistence.EntityManager;
import javax.persistence.Query;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 제주 이용시설 Loader Step (I3)
 *
 * IF_RSV_RGETSTGMS01 → 2개 GIMS Target 테이블 MERGE
 * 레거시 JejuInToDB.java + target.xml (insertTmGd31010Gms / insertPmGd31022) 이식
 *
 * 실행 순서 (건별):
 *   1. TM_GD111010 (이용량시설) MERGE ON PRMSN_DCLR_NO → BRNCH_ID 확보
 *   2. PM_GD111022 (이용량일자료) MERGE ON (BRNCH_ID + OBSRVN_YMD)  — 일자 없으면 skip
 */
@Slf4j
public class JejuFacilityLoadStep implements StepExecutor {

    private final String stepId;
    private final String stepName;
    private final String ifTable;
    private final List<String> configSourceTables;
    private final List<String> configTargetTables;
    private final DataSourceProvider dataSourceProvider;
    private final SyncLogRepository syncLogRepository;
    private final IfTableService ifTableService;
    private final DynamicEntityManagerService dynamicEmService;

    public JejuFacilityLoadStep(String stepId, String stepName,
                                 String ifTable,
                                 List<String> configSourceTables, List<String> configTargetTables,
                                 DataSourceProvider dataSourceProvider,
                                 SyncLogRepository syncLogRepository,
                                 IfTableService ifTableService,
                                 DynamicEntityManagerService dynamicEmService) {
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
            JdbcTemplate targetJdbc = dataSourceProvider.getJdbcTemplate(targetDsId);
            String executionId = context.getExecutionId();

            // 0. 룩업 맵 로딩 (TC_GD00002, TC_GD00100)
            Map<String, String> usageMap = loadUsageMap(targetJdbc);           // NGW_0003: 용도코드→명칭
            Map<String, String> detailUsageMap = loadDetailUsageMap(targetJdbc); // NGW_0013: 상세용도코드→명칭
            Map<String, Map<String, String>> addrMap = loadAddrMap(targetJdbc);  // 법정동코드→주소
            log.info("[{}] 룩업 맵 로딩: 용도={}, 상세용도={}, 법정동={}",
                    stepId, usageMap.size(), detailUsageMap.size(), addrMap.size());

            // 1. IF 테이블 조회 (JPA native query)
            String sourceDbType = dataSourceProvider.getDbType(sourceDsId);
            boolean isResync = ConditionBuilder.isResyncExecution(context.getExecutionOptions());
            ConditionBuilder.WhereClause where = ConditionBuilder.buildIfTableQuery(
                    context.getExecutionOptions(), ifTable, sourceDbType);
            String sql = "SELECT * FROM " + ifTable + where.toWhereSql();

            List<Map<String, Object>> pendingRows;
            EntityManager sourceEm = dynamicEmService.getSourceEntityManager();
            try {
                Query query = sourceEm.createNativeQuery(sql, IfRsvRgetstgms01.class);
                Object[] params = where.getParamsArray();
                for (int i = 0; i < params.length; i++) {
                    query.setParameter(i + 1, params[i]);
                }
                List<IfRsvRgetstgms01> entities = query.getResultList();
                pendingRows = new ArrayList<>();
                for (IfRsvRgetstgms01 e : entities) {
                    pendingRows.add(entityToMap(e));
                }
            } finally {
                sourceEm.close();
            }
            readCount = pendingRows.size();
            log.info("[{}] IF_RSV에서 {} 건의 {} 이용시설 데이터 조회", stepId, readCount,
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
                String permNtNo = getString(row, "PERM_NT_NO");
                try {
                    Object ifId = row.get("ID");
                    String sourceRef = String.format("[\"I:%s:%s:%s\"]", sourceDsId, ifTable, ifId);

                    // TM_GD111010 MERGE ON PRMSN_DCLR_NO
                    // PM_GD111022는 I5(UseLoadStep)가 USE_JEJU_DAY 소스로 별도 적재
                    mergeGd111010(targetJdbc, row, sourceRef, executionId,
                            usageMap, detailUsageMap, addrMap);

                    successIds.add(ifId);
                    writeCount++;
                } catch (Exception e) {
                    log.error("[{}] 이용시설 처리 실패: perm_nt_no={}", stepId, permNtNo, e);
                    failedCount++;
                    failedIds.add(row.get("ID"));
                    failedKeys.add(permNtNo);
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
     * TM_GD111010 (이용량시설) MERGE, BRNCH_ID 반환
     * 레거시 insertTmGd31010Gms 이식 — PRMSN_DCLR_NO 기준 MERGE
     *
     * 룩업 변환 (레거시 select_rgetstgms01 JOIN 재현):
     *   - UWATER_SRV_CODE → TC_GD00002(NGW_0003) → 용도명
     *   - UWATER_DTL_SRV_CODE → TC_GD00002(NGW_0013) → 상세용도명
     *   - REGN_CODE → TC_GD00100 → 시도/시군구/읍면동/리
     *   - USE_AT: LNHO_RAISE_YN='0' AND END_NT_YN='0' → 'Y', else 'N'
     */
    private void mergeGd111010(JdbcTemplate jdbc, Map<String, Object> row,
                                String sourceRef, String executionId,
                                Map<String, String> usageMap,
                                Map<String, String> detailUsageMap,
                                Map<String, Map<String, String>> addrMap) {
        String permNtNo = getString(row, "PERM_NT_NO");
        String regnCode = trim(getString(row, "REGN_CODE"));
        String sfTeamCode = getString(row, "SF_TEAM_CODE");
        Number digDiam = getNumber(row, "DIG_DIAM");
        Number dph = getNumber(row, "DPH");
        String potaYn = getString(row, "POTA_YN");
        String lnhoRaiseYn = getString(row, "LNHO_RAISE_YN");
        String endNtYn = getString(row, "END_NT_YN");
        String telno = getString(row, "TELNO");

        // 룩업 변환
        String uwaterSrvCode = trim(getString(row, "UWATER_SRV_CODE"));
        String usageName = usageMap.getOrDefault(uwaterSrvCode, uwaterSrvCode);

        String uwaterDtlSrvCode = trim(getString(row, "UWATER_DTL_SRV_CODE"));
        String detailUsageName = detailUsageMap.getOrDefault(uwaterDtlSrvCode, uwaterDtlSrvCode);

        // 법정동 룩업 → 시도/시군구/읍면동/리
        Map<String, String> addr = addrMap.get(regnCode);
        String ctpvNm = addr != null ? addr.get("CTPV_NM") : null;
        String sggNm = addr != null ? addr.get("SGG_NM") : null;
        String emdNm = addr != null ? addr.get("EMD_NM") : null;
        String liNm = addr != null ? addr.get("LI_NM") : null;

        // USE_AT: 레거시 CASE WHEN LNHO_RAISE_YN = 0 AND END_NT_YN = 0 THEN 'Y' ELSE 'N'
        String useYn = resolveUseYn(lnhoRaiseYn, endNtYn);

        jdbc.update(
            "MERGE INTO TM_GD111010 t USING (SELECT ? AS PRMSN_DCLR_NO FROM DUAL) s " +
            "ON (t.PRMSN_DCLR_NO = s.PRMSN_DCLR_NO) " +
            "WHEN MATCHED THEN UPDATE SET " +
            "  STDG_CD = ?, CTPV_NM = ?, SGG_NM = ?, EMD_NM = ?, LI_NM = ?, " +
            "  LCLGV_CD = ?, UGWTR_USG_CN = ?, UGWTR_DTL_USG_CN = ?, " +
            "  DGG_CALBR = ?, DGG_DPTH = ?, DKPP_YN = ?, USE_YN = ?, TELNO = ?, " +
            "  EXECUTION_ID = ?, SOURCE_REFS = ? " +
            "WHEN NOT MATCHED THEN INSERT " +
            "  (PRMSN_DCLR_NO, STDG_CD, CTPV_NM, SGG_NM, EMD_NM, LI_NM, " +
            "   LCLGV_CD, UGWTR_USG_CN, UGWTR_DTL_USG_CN, " +
            "   DGG_CALBR, DGG_DPTH, DKPP_YN, USE_YN, TELNO, " +
            "   FRST_REG_DT, EXECUTION_ID, SOURCE_REFS) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, SYSDATE, ?, ?)",
            // ON 절
            permNtNo,
            // UPDATE 절
            regnCode, ctpvNm, sggNm, emdNm, liNm,
            sfTeamCode, usageName, detailUsageName,
            digDiam, dph, potaYn, useYn, telno,
            executionId, sourceRef,
            // INSERT 절
            permNtNo, regnCode, ctpvNm, sggNm, emdNm, liNm,
            sfTeamCode, usageName, detailUsageName,
            digDiam, dph, potaYn, useYn, telno,
            executionId, sourceRef
        );
    }

    // ==================== 룩업 맵 로딩 ====================

    /**
     * NGW_0003 (지하수용도) 룩업 맵
     * 레거시: UWATER_SRV_CODE = SUBSTR(UGWTR_COM_CD, 2, 2)
     * '01'→SUBSTR='1', '02'→'2' ... → code='1' maps to '생활용' 등
     */
    private Map<String, String> loadUsageMap(JdbcTemplate jdbc) {
        Map<String, String> map = new HashMap<>();
        jdbc.query(
            "SELECT TRIM(UGWTR_COM_CD) AS CD, CD_CN FROM TC_GD00002 WHERE GROUP_CD_SN = 'NGW_0003'",
            (RowCallbackHandler) rs -> {
                String code = rs.getString("CD");
                // SUBSTR(code, 2) → '01'의 두번째 문자부터 = '1'
                String key = code.length() > 1 ? code.substring(1) : code;
                map.put(key, rs.getString("CD_CN"));
            });
        return map;
    }

    /**
     * NGW_0013 (지하수상세용도) 룩업 맵
     * 레거시: UWATER_DTL_SRV_CODE = UGWTR_COM_CD (직접매칭)
     */
    private Map<String, String> loadDetailUsageMap(JdbcTemplate jdbc) {
        Map<String, String> map = new HashMap<>();
        jdbc.query(
            "SELECT TRIM(UGWTR_COM_CD) AS CD, CD_CN FROM TC_GD00002 WHERE GROUP_CD_SN = 'NGW_0013'",
            (RowCallbackHandler) rs -> map.put(rs.getString("CD"), rs.getString("CD_CN")));
        return map;
    }

    /**
     * TC_GD00100 (법정동코드) 룩업 맵
     * 레거시: REGN_CODE = LEGALDONG_CODE → BRTC_NM, SIGUN_NM, EMD_NM, LI_NM
     */
    private Map<String, Map<String, String>> loadAddrMap(JdbcTemplate jdbc) {
        Map<String, Map<String, String>> map = new HashMap<>();
        jdbc.query(
            "SELECT STDG_CD, CTPV_NM, SGG_NM, EMD_NM, LI_NM FROM TC_GD00100",
            (RowCallbackHandler) rs -> {
                Map<String, String> addr = new HashMap<>();
                addr.put("CTPV_NM", rs.getString("CTPV_NM"));
                addr.put("SGG_NM", rs.getString("SGG_NM"));
                addr.put("EMD_NM", rs.getString("EMD_NM"));
                addr.put("LI_NM", rs.getString("LI_NM"));
                map.put(rs.getString("STDG_CD"), addr);
            });
        return map;
    }

    // ==================== 헬퍼 ====================

    /**
     * USE_YN 판별 (레거시 로직 재현)
     * CASE WHEN LNHO_RAISE_YN = 0 AND END_NT_YN = 0 THEN 'Y' ELSE 'N' END
     * 둘 다 0(또는 '0')이면 사용중, 하나라도 아니면 폐공
     */
    private String resolveUseYn(String lnhoRaiseYn, String endNtYn) {
        boolean lnhoOk = "0".equals(trim(lnhoRaiseYn));
        boolean endOk = "0".equals(trim(endNtYn));
        return (lnhoOk && endOk) ? "Y" : "N";
    }

    private String trim(String val) {
        return val != null ? val.trim() : null;
    }

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

    /**
     * Entity → Map 변환 (MERGE 메서드 호환용)
     */
    private Map<String, Object> entityToMap(IfRsvRgetstgms01 e) {
        Map<String, Object> map = new HashMap<>();
        Field[] fields = e.getClass().getDeclaredFields();
        for (Field f : fields) {
            f.setAccessible(true);
            Column col = f.getAnnotation(Column.class);
            String key = col != null ? col.name() : f.getName().toUpperCase();
            try {
                map.put(key, f.get(e));
            } catch (IllegalAccessException ex) {
                // skip
            }
        }
        return map;
    }

    private void saveSyncLog(String executionId, int readCount, int writeCount,
                              int failedCount, List<String> failedKeys, String errorSummary) {
        try {
            String sourceJson = "[" + configSourceTables.stream()
                    .map(t -> "\"" + t + "\"").reduce((a, b) -> a + "," + b).orElse("") + "]";
            String targetJson = "[" + configTargetTables.stream()
                    .map(t -> "\"" + t + "\"").reduce((a, b) -> a + "," + b).orElse("") + "]";
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
