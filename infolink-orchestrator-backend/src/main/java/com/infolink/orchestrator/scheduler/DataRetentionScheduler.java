package com.infolink.orchestrator.scheduler;

import com.infolink.orchestrator.entity.Agent;
import com.infolink.orchestrator.entity.AgentStatus;
import com.infolink.orchestrator.repository.AgentRepository;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * 타겟 테이블 자동삭제(Retention) 스케줄러
 * - DB의 retention_config 설정을 읽어 Agent cleanup 엔드포인트에 전달
 * - retention_config가 없거나 비활성화면 스킵
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataRetentionScheduler {

    private final AgentRepository agentRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(cron = "${retention.cron:0 0 2 * * *}")
    @SuppressWarnings("unchecked")
    public void executeRetentionCleanup() {
        List<Agent> activeAgents = agentRepository.findByIsActiveTrue();
        if (activeAgents.isEmpty()) return;

        // OFFLINE 제외 + retention 설정이 있는 Agent만
        List<Agent> targets = activeAgents.stream()
                .filter(a -> a.getStatus() != AgentStatus.OFFLINE)
                .filter(a -> a.getRetentionConfig() != null && !a.getRetentionConfig().isBlank())
                .filter(a -> {
                    // 룰: targets 가 있으면 적용 (enabled 필드 deprecate — dev_plan/2026_05/08/retention-candidates-safety.md)
                    try {
                        Map<String, Object> config = objectMapper.readValue(a.getRetentionConfig(), Map.class);
                        Object t = config.get("targets");
                        return t instanceof List && !((List<?>) t).isEmpty();
                    } catch (Exception e) {
                        return false;
                    }
                })
                .toList();

        if (targets.isEmpty()) {
            log.debug("[Retention] retention 설정된 온라인 Agent 없음, 스킵");
            return;
        }

        log.info("[Retention] cleanup 시작: 대상 Agent {}개", targets.size());
        int successCount = 0;
        int failCount = 0;

        for (Agent agent : targets) {
            try {
                String url = agent.getEndpointUrl() + "/api/cleanup/" + agent.getAgentCode();

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                // agent.targetDatasourceId 를 body 에 자동 inject (운영자 입력 누락 방어).
                // bojo-internal 같은 multi-agent 모듈은 module-default fallback 이 잘못된 datasource 잡을 수 있어
                // 항상 명시적으로 박아 보냄. dev_plan/2026_05/08/retention-candidates-safety.md
                String enrichedConfig = enrichTargetDatasourceId(agent.getRetentionConfig(), agent.getTargetDatasourceId());
                HttpEntity<String> request = new HttpEntity<>(enrichedConfig, headers);

                ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    Object totalDeleted = response.getBody().get("totalDeleted");
                    log.info("[Retention] {} cleanup 완료: {}건 삭제", agent.getAgentCode(), totalDeleted);
                    successCount++;
                }
            } catch (Exception e) {
                log.warn("[Retention] {} cleanup 실패: {}", agent.getAgentCode(), e.getMessage());
                failCount++;
            }
        }

        log.info("[Retention] cleanup 완료: 성공={}, 실패={}", successCount, failCount);
    }

    @SuppressWarnings("unchecked")
    private String enrichTargetDatasourceId(String json, String agentTargetDsId) {
        if (json == null || json.isBlank() || agentTargetDsId == null || agentTargetDsId.isBlank()) {
            return json;
        }
        try {
            Map<String, Object> config = objectMapper.readValue(json, Map.class);
            Object existing = config.get("targetDatasourceId");
            if (existing == null || (existing instanceof String && ((String) existing).isBlank())) {
                config.put("targetDatasourceId", agentTargetDsId);
                return objectMapper.writeValueAsString(config);
            }
            return json;
        } catch (Exception e) {
            log.warn("[Retention] targetDatasourceId inject 실패, 원본 그대로 전송: {}", e.getMessage());
            return json;
        }
    }
}
