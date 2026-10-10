package com.practice.flashsale.common.constant;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @Author: 沃淇淋
 * @Date: 2026/10/9 13:35
 * @Version: v1.0.0
 * @Description: Redis 缓存 Key 常量类
 **/
public class RedisKeyConstants {

    /**
     * 商品列表缓存 Key 前缀
     *
     * 完整格式：seckill:goods:list:{activityId}
     */
    public static final String GOODS_LIST_PREFIX = "seckill:goods:list:";

    /**
     * 商品详情缓存 Key 前缀
     *
     * 完整格式：seckill:goods:detail:{activityId}:{goodsId}
     */
    public static final String GOODS_DETAIL_PREFIX = "seckill:goods:detail:";

    /**
     * 活动结束后，缓存保留的短过期时间（单位：分钟）
     * 防止活动结束后仍有余温流量，每次都打到 DB
     */
    public static final long ENDED_ACTIVITY_TTL_MINUTES = 5;

    /**
     * 安全缓冲时间（单位：秒）
     */
    public static final long SAFETY_BUFFER_SECONDS = 30 * 60; // 30 分钟

    /**
     * 缓存 TTL 的随机抖动上限（单位：秒）
     */
    public static final long TTL_JITTER_SECONDS = 300;

    /**
     * 缓存空值，用于防止缓存穿透
     */
    public static final String NULL_CACHE_VALUE = "NULL";

    /**
     * 缓存空值的过期时间（单位：分钟）
     */
    public static final long NULL_CACHE_TTL_MINUTES = 5;

    /**
     * 活动布隆过滤器 Key
     * 存放所有合法的活动 ID（Long 类型）
     */
    public static final String SECKILL_ACTIVITY_BLOOM_KEY = "seckill:bloom:activity";

    /**
     * 商品布隆过滤器 Key
     * 存放 activityId:goodsId 组合（String 类型）
     */
    public static final String SECKILL_GOODS_BLOOM_KEY = "seckill:bloom:goods";

    /**
     * 根据活动结束时间动态计算缓存 TTL（秒）
     *
     * 公式：TTL = (活动结束时间 - 当前时间) + 安全缓冲时间
     *
     * @param endTime
     * @return
     */
    public static Long calculateTtlSeconds(LocalDateTime endTime) {
        if (Objects.isNull(endTime)) {
            return null;
        }
        long ttlSeconds = Duration.between(LocalDateTime.now(), endTime).getSeconds()
                + SAFETY_BUFFER_SECONDS;
        return ttlSeconds > 0 ? ttlSeconds : null;
    }

    /**
     * 计算最终写入 Redis 的 TTL（秒）
     *
     * 规则：
     * 1. 活动未结束（含结束后 30 分钟缓冲期）：活动剩余时间 + 安全缓冲时间
     * 2. 活动已结束且超过缓冲期：退化为短 TTL 兜底，避免余温流量每次都打到 DB
     * 3. 叠加 0~300 秒随机抖动，避免大量 Key 同一时刻失效
     *
     * @param endTime 活动结束时间
     * @return 最终 TTL（秒），恒为正数
     */
    public static long resolveTtlSeconds(LocalDateTime endTime) {
        Long ttlSeconds = calculateTtlSeconds(endTime);
        long baseSeconds = Objects.isNull(ttlSeconds)
                ? ENDED_ACTIVITY_TTL_MINUTES * 60
                : ttlSeconds;
        return baseSeconds + ThreadLocalRandom.current().nextLong(0, TTL_JITTER_SECONDS);
    }
}
