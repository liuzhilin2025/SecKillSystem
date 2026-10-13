package com.practice.flashsale.order.service;

import com.practice.flashsale.order.enums.SeckillStockDeductResultEnum;

/**
 * @Author: 沃淇淋
 * @Date: 2026/10/13 15:25
 * @Version: v1.0.0
 * @Description: 秒杀库存服务
 **/
public interface SeckillStockService {

    /**
     * Redis Lua 原子预扣库存
     */
    SeckillStockDeductResultEnum preDeductStock(Long activityId, Long goodsId,
                                                Long userId, Long userOrderTtlSeconds,
                                                Long activityBeginTimeMillis, Long activityEndTimeMillis);
}
