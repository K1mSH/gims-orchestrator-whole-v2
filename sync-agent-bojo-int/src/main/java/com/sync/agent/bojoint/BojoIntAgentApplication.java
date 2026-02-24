package com.sync.agent.bojoint;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * BojoInt Agent - 내부망 동기화 Agent
 *
 * DMZ SND IF → 내부 RSV IF 동기화
 * - RCV: if_snd_sec_jewon/obsvdata → if_rsv_sec_jewon/obsvdata
 */
@SpringBootApplication(scanBasePackages = {"com.sync.agent.bojoint", "com.sync.agent.common"})
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
