package com.sync.agent.common.datasource;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

/**
 * 대표 Agent(Zone Master)로부터 DataSource 정보를 가져오는 클라이언트
 */
@Slf4j
public class ZoneMasterClient {

    private final String zoneMasterUrl;
    private final RestTemplate restTemplate;

    public ZoneMasterClient(String zoneMasterUrl) {
        this.zoneMasterUrl = zoneMasterUrl;
        this.restTemplate = new RestTemplate();
    }

    public ZoneMasterClient(String zoneMasterUrl, RestTemplate restTemplate) {
        this.zoneMasterUrl = zoneMasterUrl;
        this.restTemplate = restTemplate;
    }

    /**
     * 대표 Agent로부터 DataSource 정보 조회
     * @param datasourceId DataSource ID
     * @return DataSource 정보 (비밀번호 암호화 상태)
     */
    public DataSourceInfo getDataSourceInfo(String datasourceId) {
        try {
            String url = zoneMasterUrl + "/api/datasource/" + datasourceId;
            ResponseEntity<DataSourceInfo> response = restTemplate.getForEntity(url, DataSourceInfo.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.info("Retrieved datasource info: {}", datasourceId);
                return response.getBody();
            }
            throw new RuntimeException("Failed to get datasource info: " + datasourceId);
        } catch (Exception e) {
            log.error("Failed to get datasource info from zone master: {}", datasourceId, e);
            throw new RuntimeException("Failed to get datasource info: " + e.getMessage(), e);
        }
    }
}
