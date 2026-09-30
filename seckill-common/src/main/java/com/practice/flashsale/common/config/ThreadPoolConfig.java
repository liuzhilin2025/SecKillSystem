package com.practice.flashsale.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class ThreadPoolConfig {

    @Value("${thread.pool.core-pool-size:2}")
    private int corePoolSize;

    @Value("${thread.pool.max-pool-size:5}")
    private int maxPoolSize;

    @Value("${thread.pool.queue-capacity:100}")
    private int queueCapacity;

    @Value("${thread.pool.keep-alive-seconds:60}")
    private int keepAliveSeconds;

    @Bean("bizExecutor")
    public Executor bizExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数
        executor.setCorePoolSize(corePoolSize);
        // 最大线程数
        executor.setMaxPoolSize(maxPoolSize);
        // 队列容量
        executor.setQueueCapacity(queueCapacity);
        // 空闲线程存活时间（超过核心线程数的线程，空闲超过该时间会被回收）
        executor.setKeepAliveSeconds(keepAliveSeconds);
        // 线程名前缀（方便在日志中识别）
        executor.setThreadNamePrefix("biz-task-");
        // 拒绝策略：由调用线程处理（即同步执行，不会丢弃任务）
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 等待所有任务结束后再关闭线程池
        executor.setWaitForTasksToCompleteOnShutdown(true);
        // 优雅关闭等待时间（秒）：应用关闭时最多等待 30 秒，超时会强制关闭
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
