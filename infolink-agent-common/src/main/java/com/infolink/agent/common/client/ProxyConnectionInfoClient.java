package com.infolink.agent.common.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Proxy 경유 connection-info 조회 클라이언트 (시스템 간 호출 통일 진실원).
 *
 * <p>Agent / API 모듈이 Orchestrator 의 datasource 자격증명을 얻기 위해 사용한다.
 * Proxy({@code /api/datasources/{id}/connection-info}) 가 backend 의 응답을 패스스루하며,
 * 호출 측은 X-API-Key 헤더로 인증 (Proxy → backend 흐름에서 ApiKeyFilter soft-mode 가
 * ROLE_SYSTEM 박아 통과).
 *
 * <h3>응답 양식</h3>
 * username/password 는 ENC 암호문 그대로 반환된다. 호출 측이 {@link com.infolink.agent.common.datasource.PasswordEncryptor}
 * 로 직접 복호화한다 (Orchestrator → Proxy → Agent 경로에 평문이 노출되지 않도록).
 *
 * <h3>도입 배경 (2026-05-18)</h3>
 * 7 모듈이 동일 connection-info 호출 코드를 중복 구현해왔음
 * (api-collector / api-provider / 4 agent / api-provider 의 자체 메서드).
 * 본 클래스가 단일 진실원이 되고, 신규 모듈은 본 클래스 import 만 하면 된다.
 *
 * <h3>스레드 안전</h3>
 * 인스턴스 필드 (proxyUrl / apiKey / restTemplate) 만 사용. 호출은 동기 RestTemplate.
 * 따라서 스케줄러 thread / web nio thread 무관하게 안전.
 *
 * @see com.infolink.agent.common.datasource.PasswordEncryptor
 */
@Slf4j
public class ProxyConnectionInfoClient {

    private static final String API_KEY_HEADER = "X-API-Key";

    private final RestTemplate restTemplate;
    private final String proxyUrl;
    private final String apiKey;

    public ProxyConnectionInfoClient(RestTemplate restTemplate, String proxyUrl, String apiKey) {
        this.restTemplate = restTemplate;
        this.proxyUrl = proxyUrl;
        this.apiKey = apiKey;
    }

    /**
     * Proxy 경유로 datasource 연결 정보 조회 (암호문 그대로 반환).
     * 응답 키: datasourceId / dbType / host / port / databaseName / username(ENC) / password(ENC).
     *
     * @throws IllegalStateException 빈 응답
     * @throws org.springframework.web.client.HttpClientErrorException 인증 실패 등 HTTP 에러
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> fetchEncrypted(String datasourceId) {
        String url = proxyUrl + "/api/datasources/" + datasourceId + "/connection-info";
        log.info("[ProxyConnectionInfoClient] fetch: datasourceId={}", datasourceId);

        HttpHeaders headers = new HttpHeaders();
        if (apiKey != null && !apiKey.isEmpty()) {
            headers.set(API_KEY_HEADER, apiKey);
        }

        ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        Map<String, Object> body = response.getBody();

        if (body == null || body.isEmpty()) {
            throw new IllegalStateException("Empty connection-info response: " + datasourceId);
        }
        return body;
    }
}
