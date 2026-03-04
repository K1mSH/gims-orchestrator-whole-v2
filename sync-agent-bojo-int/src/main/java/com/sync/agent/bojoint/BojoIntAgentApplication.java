package com.sync.agent.bojoint;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import com.sync.agent.common.controller.ExecutionDataController;
import com.sync.agent.common.controller.DatasourceController;

/**
 * BojoInt Agent - 내부망 동기화 Agent
 *
 * DMZ SND IF → 내부 RSV IF 동기화
 * - RCV: if_snd_sec_jewon/obsvdata → if_rsv_sec_jewon/obsvdata
 *
 * DB 프록시 엔드포인트는 전용 프록시 Agent(sync-proxy-internal)로 분리됨
 */
@SpringBootApplication
@ComponentScan(
        basePackages = {"com.sync.agent.bojoint", "com.sync.agent.common"},
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {ExecutionDataController.class, DatasourceController.class}
        )
)
@EntityScan(basePackages = {
        "com.sync.agent.common.entity"
})
@EnableJpaRepositories(basePackages = {
        "com.sync.agent.common.repository"
})
public class BojoIntAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(BojoIntAgentApplication.class, args);
    }
}
