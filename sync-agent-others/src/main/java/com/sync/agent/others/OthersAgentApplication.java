package com.sync.agent.others;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import com.sync.agent.common.controller.ExecutionDataController;
import com.sync.agent.common.controller.DatasourceController;

/**
 * Others Agent — SND 전용 Agent (제주/이용량 담당)
 *
 * 기존 bojo(보조관측)와 동일 구조, 담당 데이터만 다름.
 * 운영 안정성 위해 별도 서비스로 분리.
 *
 * - SND: DMZ DB → IF_SND 추출 (제주 3 + 이용량 3 = 6테이블)
 */
@SpringBootApplication
@ComponentScan(
        basePackages = {"com.sync.agent.others", "com.sync.agent.common"},
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {ExecutionDataController.class, DatasourceController.class}
        )
)
@EntityScan(basePackages = {
        "com.sync.agent.others.entity",
        "com.sync.agent.common.entity"
})
@EnableJpaRepositories(basePackages = {
        "com.sync.agent.common.repository"
})
public class OthersAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(OthersAgentApplication.class, args);
    }
}
