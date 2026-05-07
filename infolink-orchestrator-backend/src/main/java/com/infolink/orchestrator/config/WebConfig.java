package com.infolink.orchestrator.config;

import com.infolink.agent.common.datasource.PasswordEncryptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${proxy.api-key:}")
    private String proxyApiKey;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:3000", "http://localhost:5173")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    @Bean
    public PasswordEncryptor passwordEncryptor(@Value("${jasypt.encryptor.password}") String secretKey) {
        return new PasswordEncryptor(secretKey);
    }

    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);   // 5초 연결 타임아웃
        factory.setReadTimeout(30000);    // 30초 읽기 타임아웃

        RestTemplate restTemplate = new RestTemplate(factory);

        // API Key 인터셉터 — 모든 요청에 X-API-Key 헤더 추가
        // Agent/Proxy 모두 common ApiKeyFilter로 검증
        if (proxyApiKey != null && !proxyApiKey.isEmpty()) {
            ClientHttpRequestInterceptor apiKeyInterceptor = (request, body, execution) -> {
                request.getHeaders().set("X-API-Key", proxyApiKey);
                return execution.execute(request, body);
            };
            restTemplate.setInterceptors(List.of(apiKeyInterceptor));
        }

        return restTemplate;
    }
}
