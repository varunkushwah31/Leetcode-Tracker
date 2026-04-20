package com.tracker.leetcode.tracker.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Minimum number of worker threads kept alive
        executor.setCorePoolSize(4);

        // Maximum number of threads allowed
        executor.setMaxPoolSize(10);

        // How many tasks can wait in line before the server rejects them
        executor.setQueueCapacity(50);

        // Prefix for your logs so you know it's a background task running
        executor.setThreadNamePrefix("AsyncWorker-");

        executor.initialize();
        return executor;
    }
}