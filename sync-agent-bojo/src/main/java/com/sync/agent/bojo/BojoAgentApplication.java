package com.sync.agent.bojo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Bojo Agent - 통합 Agent (RCV + Loader + SND)
 *
 * 12개 논리적 Agent가 하나의 물리적 앱에서 동작
 * - RCV 10개: Source DB → IF_RSV 추출
 * - Loader 1개: IF_RSV → Target 적재
 * - SND 1개: Target → IF_SND 추출
 */
@SpringBootApplication(scanBasePackages = {"com.sync.agent.bojo", "com.sync.agent.common"})
@EntityScan(basePackages = {
        "com.sync.agent.bojo.entity",       // 모든 엔티티 (local, source, iftable, target)
        "com.sync.agent.common.entity"      // Execution, SyncLog
})
@EnableJpaRepositories(basePackages = {
        "com.sync.agent.bojo.entity.repository",
        "com.sync.agent.common.repository"
})
public class BojoAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(BojoAgentApplication.class, args);
    }
}
