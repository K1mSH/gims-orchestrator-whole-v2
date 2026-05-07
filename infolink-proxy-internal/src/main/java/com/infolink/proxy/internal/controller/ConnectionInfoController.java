package com.infolink.proxy.internal.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Agent용 connection-info 프록시 엔드포인트.
 *
 * Agent가 파이프라인 실행 시 DB 자격증명을 얻기 위해 호출한다.
 * Orchestrator의 connection-info API를 그대로 패스스루하며, 복호화하지 않는다.
 * Agent가 직접 PasswordEncryptor로 복호화하여 DataSource를 생성한다.
 *
 * -- 동기화 대상 --
 * 이 클래스는 sync-proxy-dmz / sync-proxy-internal 양쪽에 동일하게 존재한다.
 * 한쪽을 수정하면 반드시 다른 쪽도 동일하게 수정할 것.
 * common 모듈에 두지 않는 이유: Agent 앱에 불필요하게 로딩되기 때문.
 *
 * -- 관련 코드 --
 * - Orchestrator: DatasourceController.getConnectionInfo() — 암호문 응답 원본
 * - Agent: SyncDataSourceService.fetchConnectionInfo() — 이 엔드포인트를 호출하는 쪽
 * - Proxy 자체 DataSource 생성: ProxyDataSourceService.fetchFromOrchestrator() — 별도 흐름 (복호화 O)
 */
@Slf4j
@RestController
@RequestMapping("/api/datasources")
public class ConnectionInfoController {

    @Value("${agent.orchestrator-url}")
    private String orchestratorUrl;

    @Value("${agent.api-key:}")
    private String apiKey;

    @SuppressWarnings("unchecked")
    @GetMapping("/{datasourceId}/connection-info")
    public ResponseEntity<Map<String, Object>> getConnectionInfo(
            @PathVariable String datasourceId) {
        String url = orchestratorUrl + "/api/datasources/" + datasourceId + "/connection-info";
        log.info("[Proxy] Passthrough connection-info request: {} -> {}", datasourceId, url);

        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            if (apiKey != null && !apiKey.isEmpty()) {
                headers.set("X-API-Key", apiKey);   // Backend ApiKeyFilter 통과용
            }
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            Map<String, Object> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class).getBody();

            if (response == null || response.isEmpty()) {
                log.warn("[Proxy] Empty response from Orchestrator for datasource: {}", datasourceId);
                return ResponseEntity.notFound().build();
            }

            log.info("[Proxy] Connection-info passthrough success: {} ({}:{})",
                    datasourceId, response.get("host"), response.get("port"));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("[Proxy] Failed to passthrough connection-info for {}: {}", datasourceId, e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}
