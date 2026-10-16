package com.practice.flashsale.common.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Objects;

/**
 * @Author: 沃淇淋
 * @Date: 2026/10/15 13:40
 * @Version: v1.0.0
 * @Description:  Redisson 配置类
 **/
@Configuration
@EnableConfigurationProperties(RedisProperties.class)
public class RedissonConfig {

    /**
     * 手动创建 RedissonClient，只用于布隆过滤器、分布式锁等高级 Redis 能力
     */
    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(RedisProperties redisProperties) {
        // 创建 Redisson 配置对象
        Config config = new Config();

        // Redisson 单机模式地址，格式必须是 redis://host:port
        String address = "redis://" + redisProperties.getHost() + ":" + redisProperties.getPort();

        // 使用单机 Redis 配置，并复用 spring.data.redis 中的 database 配置
        SingleServerConfig singleServerConfig = config.useSingleServer()
                .setAddress(address)
                .setDatabase(redisProperties.getDatabase());

        // 如果 Redis 配置子密码，则同步设置到 Redisson 中
        if (StringUtils.hasText(redisProperties.getPassword())) {
            singleServerConfig.setPassword(redisProperties.getPassword());
        }

        // 复用 spring.data.redis.timeout 超时时间
        Duration timeout = redisProperties.getTimeout();
        if (Objects.nonNull(timeout)) {
            int timeoutMillis = Math.toIntExact(timeout.toMillis());
            singleServerConfig.setConnectTimeout(timeoutMillis);
            singleServerConfig.setTimeout(timeoutMillis);
        }

        // 根据配置创建 RedissonClient
        return Redisson.create(config);
    }
}
