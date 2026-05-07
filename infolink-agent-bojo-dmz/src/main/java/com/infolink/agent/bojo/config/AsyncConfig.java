package com.infolink.agent.bojo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * DMZ Agent 파이프라인 비동기 실행을 위한 ThreadPool 설정.
 *
 * <p>{@code pipelineExecutor} 빈을 등록하며, PipelineService에서
 * {@code @Async("pipelineExecutor")}로 파이프라인을 병렬 실행할 때 사용된다.</p>
 *
 * <ul>
 *   <li>core=6, max=15, queue=50</li>
 *   <li>스레드 접두사: {@code Bojo-Pipeline-}</li>
 * </ul>
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "pipelineExecutor")
    public Executor pipelineExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(6);
        executor.setMaxPoolSize(15);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("Bojo-Pipeline-");
        executor.initialize();
        return executor;
    }
}
