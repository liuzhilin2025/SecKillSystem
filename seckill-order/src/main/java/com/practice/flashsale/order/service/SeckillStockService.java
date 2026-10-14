package com.practice.flashsale.order.service;

import com.practice.flashsale.order.enums.SeckillStockCompensationResultEnum;
import com.practice.flashsale.order.enums.SeckillStockDeductResultEnum;
import com.practice.flashsale.order.model.dto.SeckillOrderMqDTO;

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
    SeckillStockDeductResultEnum preDeductStock(SeckillOrderMqDTO seckillOrderMqDTO,
                                                Long userOrderTtlSeconds,
                                                Long activityBeginTimeMillis,
                                                Long activityEndTimeMillis);

    /**
     * Redis Lua 原子回补秒杀库存，并删除用户购买标记
     */
    SeckillStockCompensationResultEnum compensatePreDeductStock(Long activityId,
                                                                Long goodsId,
                                                                Long userId,
                                                                String orderNo);
}
