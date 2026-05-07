package com.infolink.proxy.internal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 내부망 DB 프록시 전용 Agent
 *
 * 파이프라인 실행 없이 DB 조회 전용:
 * - /api/execution-data/** : 실행 데이터 조회, Trace
 * - /api/datasource/**     : 연결 테스트, 테이블/컬럼 스키마 조회
 */
@SpringBootApplication(scanBasePackages = {"com.infolink.proxy.internal", "com.infolink.agent.common"})
@EntityScan(basePackages = {
        "com.infolink.agent.common.entity"
})
@EnableJpaRepositories(basePackages = {
        "com.infolink.agent.common.repository"
})
public class ProxyInternalApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProxyInternalApplication.class, args);
    }
}
