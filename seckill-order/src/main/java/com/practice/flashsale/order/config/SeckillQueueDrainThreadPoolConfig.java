package com.practice.flashsale.order.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * @Author: 沃淇淋
 * @Date: 2026/10/16 16:01
 * @Version: v1.0.0
 * @Description: 秒杀下单队列泄洪线程池配置
 **/
@Configuration
public class SeckillQueueDrainThreadPoolConfig {

    /**
     * 秒杀下单队列泄洪线程池
     *
     * @return
     */
    @Bean("seckillOrderQueueDrainExecutor")
    public ThreadPoolTaskExecutor seckillOrderQueueDrainExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 核心线程数：线程池会按需创建核心线程，用来执行秒杀下单任务
        executor.setCorePoolSize(20);
        // 最大线程数：和核心线程数保持一致，形成固定大小的本地拥塞窗口，最多并发执行 20 个任务
        executor.setMaxPoolSize(20);
        // 等待队列长度：20 个工作线程都忙时，最多再缓冲 100 个下单任务
        executor.setQueueCapacity(100);
        // 线程名前缀
        executor.setThreadNamePrefix("seckill-order-drain-");
        // 拒绝策略：工作线程和等待队列都满时，直接抛 RejectedExecutionException
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        // 初始化线程池
        executor.initialize();

        return executor;
    }
}
