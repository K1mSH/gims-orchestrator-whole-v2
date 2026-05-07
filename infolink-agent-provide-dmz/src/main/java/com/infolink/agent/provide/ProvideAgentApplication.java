package com.infolink.agent.provide;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import com.infolink.agent.common.controller.DatasourceController;

/**
 * Provide Agent - 내부망 제공 데이터 적재 Agent
 *
 * Oracle 원본 테이블 → PG api_provider 제공 테이블 적재
 * - Type A: 단순 복사 (simple-load)
 * - Type B: 전처리 (복잡 SQL → 정제 테이블)
 */
@SpringBootApplication
@ComponentScan(
        basePackages = {"com.infolink.agent.provide", "com.infolink.agent.common"},
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {DatasourceController.class}
        )
)
@EntityScan(basePackages = {
        "com.infolink.agent.common.entity",
        "com.infolink.agent.provide.entity"
})
@EnableJpaRepositories(basePackages = {
        "com.infolink.agent.common.repository"
})
public class ProvideAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProvideAgentApplication.class, args);
    }
}
