package com.practice.flashsale.order.service.impl;

import com.practice.flashsale.order.service.SeckillRateLimitService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * @Author: 沃淇淋
 * @Date: 2026/10/15 15:42
 * @Version: v1.0.0
 * @Description: TODO
 **/
@Service
@Slf4j
public class SeckillRateLimitServiceImpl implements SeckillRateLimitService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 秒杀接口限流 Lua 脚本
     */
    private final DefaultRedisScript<Long> seckillRateLimitScript;

    public SeckillRateLimitServiceImpl() {
        this.seckillRateLimitScript = new DefaultRedisScript<>();
        this.seckillRateLimitScript.setLocation(new ClassPathResource("lua/seckill_rate_limit.lua"));
        this.seckillRateLimitScript.setResultType(Long.class);
    }

    /**
     * 尝试获取一次请求资格
     */
    @Override
    public boolean tryAcquire(String limitKey, long maxCount, long windowSeconds) {
        // 执行限流 Lua 脚本
        Long resultCode = stringRedisTemplate.execute(seckillRateLimitScript,
                List.of(limitKey), String.valueOf(maxCount), String.valueOf(windowSeconds));

        if (Objects.isNull(resultCode)) {
            throw new IllegalStateException("执行秒杀接口限流 Lua 脚本失败");
        }

        // 是否允许
        boolean allowed = Objects.equals(resultCode, 1L);

        // 若不允许，记录日志
        if (!allowed) {
            log.warn("==> 秒杀接口触发限流, limitKey：{}, maxCount：{}, windowSeconds：{}",
                    limitKey, maxCount, windowSeconds);
        }

        return allowed;
    }
}
