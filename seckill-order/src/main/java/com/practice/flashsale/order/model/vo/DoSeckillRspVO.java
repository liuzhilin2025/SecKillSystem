package com.practice.flashsale.order.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @Author: 沃淇淋
 * @Date: 2026/10/8 14:50
 * @Version: v1.0.0
 * @Description: 秒杀下单出参
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DoSeckillRspVO {

    /**
     *订单 ID
     */
    private Long orderId;

    /**
     * 订单号
     */
    private String orderNo;

    /**
     * 商品名称
     */
    private String goodsName;

    /**
     * 商品图片
     */
    private String goodsImg;

    /**
     * 秒杀价格
     */
    private BigDecimal seckillPrice;

    /**
     * 订单状态：0=待支付
     */
    private Integer status;

    /**
     * 订单过期时间
     */
    private LocalDateTime expireTime;
}
