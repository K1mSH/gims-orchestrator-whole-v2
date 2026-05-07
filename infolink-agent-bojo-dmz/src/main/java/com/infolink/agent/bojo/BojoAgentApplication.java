package com.infolink.agent.bojo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import com.infolink.agent.common.controller.ExecutionDataController;
import com.infolink.agent.common.controller.DatasourceController;

/**
 * Bojo Agent - 통합 Agent (RCV + Loader + SND)
 *
 * 12개 논리적 Agent가 하나의 물리적 앱에서 동작
 * - RCV 10개: Source DB → IF_RSV 추출
 * - Loader 1개: IF_RSV → Target 적재
 * - SND 1개: Target → IF_SND 추출
 *
 * DB 프록시 엔드포인트는 전용 프록시 Agent(infolink-proxy-dmz)로 분리됨
 */
@SpringBootApplication
@ComponentScan(
        basePackages = {"com.infolink.agent.bojo", "com.infolink.agent.common"},
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {ExecutionDataController.class, DatasourceController.class}
        )
)
@EntityScan(basePackages = {
        "com.infolink.agent.bojo.entity",       // 모든 엔티티 (local, source, iftable, target)
        "com.infolink.agent.common.entity"      // Execution, SyncLog
})
@EnableJpaRepositories(basePackages = {
        "com.infolink.agent.bojo.entity.repository",
        "com.infolink.agent.common.repository"
})
public class BojoAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(BojoAgentApplication.class, args);
    }
}
