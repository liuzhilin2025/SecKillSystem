package com.practice.flashsale.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        // 设置连接工厂
        redisTemplate.setConnectionFactory(connectionFactory);

        // Key 使用 String 序列化
        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();
        redisTemplate.setKeySerializer(stringRedisSerializer);
        redisTemplate.setHashKeySerializer(stringRedisSerializer);

        // Value 使用 JSON 序列化
        GenericJackson2JsonRedisSerializer jsonRedisSerializer = new GenericJackson2JsonRedisSerializer();
        redisTemplate.setValueSerializer(jsonRedisSerializer);
        redisTemplate.setHashValueSerializer(jsonRedisSerializer);

        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }

    /**
     * 验证码校验并删除的 Lua 脚本
     * 注意：RedisTemplate 的泛型在运行时会被擦除，所以这个 Bean 靠方法名区分
     */
    @Bean
    public DefaultRedisScript<Long> checkAndDeleteVerifyCodeScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        // 从 classpath 的 lua 目录下加载脚本文件
        script.setLocation(new ClassPathResource("lua/check_and_delete_verify_code.lua"));
        // 指定脚本返回值类型
        script.setResultType(Long.class);
        return script;
    }

    /**
     * 登录失败计数的 Lua 脚本
     */
    @Bean
    public DefaultRedisScript<Long> checkAndIncrementLoginFailScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/check_and_increment_login_fail_count.lua"));
        script.setResultType(Long.class);
        return script;
    }
}
