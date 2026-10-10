package com.practice.flashsale.order.utils;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * @Author: 沃淇淋
 * @Date: 2026/10/9 11:15
 * @Version: v1.0.0
 * @Description: 基于 ConcurrentHashMap 实现单机环境下的锁
 **/
@Component
public class OrderLockUtils {

    /**
     * 锁容器：Key 为 "userId:activityId:goodsId"，Value 为 true
     * 使用 ConcurrentHashMap 保证并发安全
     */
    private final ConcurrentHashMap<String, Boolean> lockMap = new ConcurrentHashMap<>();

    /**
     * 尝试获取锁（非阻塞）
     *
     * 利用 ConcurrentHashMap#putIfAbsent 的原子性：
     * - 如果 Key 不存在，插入成功，返回 null → 表示获取锁成功
     * - 如果 Key 已存在，插入失败，返回旧值 → 表示锁已被占用
     *
     * @param lockKey
     * @return true 获取锁成功；false 锁已被占用
     */
    public boolean tryLock(String lockKey) {
        return lockMap.putIfAbsent(lockKey, Boolean.TRUE) == null;
    }

    /**
     * 释放锁
     */
    public void unlock(String lockKey) {
        lockMap.remove(lockKey);
    }
}
