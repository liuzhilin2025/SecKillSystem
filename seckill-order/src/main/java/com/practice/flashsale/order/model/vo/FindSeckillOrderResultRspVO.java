package com.practice.flashsale.order.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * @Author: 沃淇淋
 * @Date: 2026/10/13 09:09
 * @Version: v1.0.0
 * @Description: 查询秒杀订单结果
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FindSeckillOrderResultRspVO {

    /**
     * 订单 ID，订单未创建时为空
     */
    private Long orderId;

    /**
     * 订单号
     */
    private String orderNo;

    /**
     * 订单状态
     */
    private Integer status;

    /**
     * 订单状态描述
     */
    private String statusDesc;

    /**
     * 商品 ID
     */
    private Long goodsId;

    /**
     * 商品名称
     */
    private String goodsName;

    /**
     * 商品图片
     */
    private String goodsImg;

    /**
     * 秒杀价
     */
    private BigDecimal seckillPrice;
}
