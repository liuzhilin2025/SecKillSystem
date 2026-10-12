package com.practice.flashsale.order.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @Author: 沃淇淋
 * @Date: 2026/10/12 11:27
 * @Version: v1.0.0
 * @Description: 秒杀下单 MQ 消息体
 **/
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SeckillOrderMqDTO {

    /**
     * 下单用户 ID
     */
    private Long userId;

    /**
     * 秒杀活动 ID
     */
    private Long activityId;

    /**
     * 秒杀商品 ID
     */
    private Long goodsId;

    /**
     * 秒杀商品主键 ID
     */
    private Long seckillGoodsId;

    /**
     * 秒杀价格
     */
    private BigDecimal seckillPrice;

    /**
     * 订单号
     */
    private String orderNo;

    /**
     * 用户发起请求的时间（可用于追踪消息延迟）
     */
    private LocalDateTime requestTime;
}
