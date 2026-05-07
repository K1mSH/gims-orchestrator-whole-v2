package com.infolink.agent.common.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.infolink.agent.common.config.RetentionConfig;
import com.infolink.agent.common.service.DataRetentionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Target 테이블의 오래된 데이터를 자동 삭제하는 Retention REST API
 *
 * Orchestrator가 Agent별 retention 설정을 DB에서 읽어 POST body로 전달하면,
 * 이 컨트롤러가 DataRetentionService를 호출하여 실제 DELETE를 수행한다.
 *
 * ── 엔드포인트 ──
 * POST /api/cleanup/{agentCode}
 *   Body: RetentionConfig JSON
 *   { "enabled": true, "targetDatasourceId": "internal",
 *     "targets": [{"table":"pm_gd970201", "dateColumn":"obsrvn_dt", "retentionDays":90}] }
 *   Response: 테이블별 삭제 건수
 *
 * ── retentionDays 음수 방어 (4계층) ──
 * 1. 프론트: input min=1 제한
 * 2. Orchestrator API: 저장 시 검증
 * 3. 이 Controller: retentionDays < 1이면 예외
 * 4. DataRetentionService: retentionDays < 1이면 skip
 *
 * ── 사용처 ──
 * - Orchestrator의 스케줄 또는 수동 트리거로 호출
 * - infolink-agent-bojo-dmz, infolink-agent-bojo-internal 모두 대상
 */
@Slf4j
@RestController
@RequestMapping("/api/cleanup")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "common.controller.cleanup.enabled", havingValue = "true")
public class DataRetentionController {

    private final DataRetentionService dataRetentionService;
    private final DataSourceProvider dataSourceProvider;
    private final ObjectMapper objectMapper;

    @PostMapping("/{agentCode}")
    @SuppressWarnings("unchecked")
    public ResponseEntity<?> cleanup(
            @PathVariable String agentCode,
            @RequestBody(required = false) String requestBody) {
        log.info("[Retention] cleanup 요청: agentCode={}", agentCode);

        try {
            RetentionConfig config = parseFromBody(requestBody);

            if (config == null || !config.isEnabled() || config.getTargets().isEmpty()) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("agentCode", agentCode);
                body.put("message", "retention 설정이 없거나 비활성화 상태입니다.");
                return ResponseEntity.status(404).body(body);
            }

            // datasource: body의 targetDatasourceId → DataSourceProvider fallback
            String targetDatasourceId = config.getTargetDatasourceId();
            if (targetDatasourceId == null) {
                try {
                    targetDatasourceId = dataSourceProvider.getTargetDatasourceId();
                } catch (Exception e) {
                    log.warn("[Retention] target datasource fallback 실패: {}", e.getMessage());
                }
            }
            if (targetDatasourceId == null) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("agentCode", agentCode);
                body.put("message", "target datasource를 찾을 수 없습니다.");
                return ResponseEntity.status(500).body(body);
            }

            DataRetentionService.CleanupResult result = dataRetentionService.executeCleanup(config, targetDatasourceId);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("agentCode", agentCode);
            body.put("results", result.results());
            body.put("totalDeleted", result.totalDeleted());

            log.info("[Retention] cleanup 완료: agentCode={}, totalDeleted={}", agentCode, result.totalDeleted());
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            log.error("[Retention] cleanup 실패: agentCode={}, error={}", agentCode, e.getMessage(), e);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("agentCode", agentCode);
            body.put("error", e.getMessage());
            return ResponseEntity.status(500).body(body);
        }
    }

    @SuppressWarnings("unchecked")
    private RetentionConfig parseFromBody(String body) {
        if (body == null || body.isBlank()) return null;
        try {
            Map<String, Object> map = objectMapper.readValue(body, Map.class);
            RetentionConfig config = new RetentionConfig();
            config.setEnabled(Boolean.TRUE.equals(map.get("enabled")));
            config.setTargetDatasourceId((String) map.get("targetDatasourceId"));

            List<Map<String, Object>> targetsList = (List<Map<String, Object>>) map.get("targets");
            if (targetsList != null) {
                for (Map<String, Object> t : targetsList) {
                    RetentionConfig.TableRetention tr = new RetentionConfig.TableRetention();
                    tr.setTable((String) t.get("table"));
                    tr.setDateColumn((String) t.get("dateColumn"));
                    int days = t.get("retentionDays") != null ? ((Number) t.get("retentionDays")).intValue() : 365;
                    if (days < 1) {
                        throw new IllegalArgumentException("retentionDays는 1 이상이어야 합니다. (입력값: " + days + ", 테이블: " + tr.getTable() + ")");
                    }
                    tr.setRetentionDays(days);
                    config.getTargets().add(tr);
                }
            }
            return config;
        } catch (Exception e) {
            log.warn("[Retention] body 파싱 실패: {}", e.getMessage());
            return null;
        }
    }
}
