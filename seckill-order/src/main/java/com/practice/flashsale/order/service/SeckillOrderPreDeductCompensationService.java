package com.practice.flashsale.order.service;

/**
 * @Author: 沃淇淋
 * @Date: 2026/10/14 13:42
 * @Version: v1.0.0
 * @Description: 秒杀订单预扣回补
 **/
public interface SeckillOrderPreDeductCompensationService {

    /**
     * MQ 发布明确失败时，回补 Redis 预扣库存和用户购买标记
     */
    void compensateWhenPublishFailed(String orderNo, String reason);
}
