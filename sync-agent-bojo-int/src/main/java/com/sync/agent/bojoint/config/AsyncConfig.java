package com.sync.agent.bojoint.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 내부망 Agent 파이프라인 비동기 실행을 위한 ThreadPool 설정.
 *
 * <p>{@code pipelineExecutor} 빈을 등록하며, PipelineService에서
 * {@code @Async("pipelineExecutor")}로 파이프라인을 병렬 실행할 때 사용된다.</p>
 *
 * <ul>
 *   <li>core=4, max=8, queue=20</li>
 *   <li>스레드 접두사: {@code BojoInt-Pipeline-}</li>
 * </ul>
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "pipelineExecutor")
    public Executor pipelineExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("BojoInt-Pipeline-");
        executor.initialize();
        return executor;
    }
}
