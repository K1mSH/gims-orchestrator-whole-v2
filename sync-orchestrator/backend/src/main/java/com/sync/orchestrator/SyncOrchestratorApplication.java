package com.sync.orchestrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Backend Orchestrator (port 8080).
 *
 * <p>com.sync.agent.common.config 패키지만 추가 스캔 — ApiKeyFilter 자동 등록용.
 * Agent 전용 controller / service / repository 가 있는 다른 common 하위 패키지는 스캔 X
 * (ExecutionService 등 Backend 자체 빈과 이름 충돌 회피).
 */
@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = {"com.sync.orchestrator", "com.sync.agent.common.config"})
public class SyncOrchestratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(SyncOrchestratorApplication.class, args);
    }
}
