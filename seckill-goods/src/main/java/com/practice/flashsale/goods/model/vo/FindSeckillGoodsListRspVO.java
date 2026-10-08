package com.practice.flashsale.goods.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @Author: 沃淇淋
 * @Date: 2026/10/8 09:46
 * @Version: v1.0.0
 * @Description: 查询秒杀商品列表出参
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FindSeckillGoodsListRspVO {

    private Long id;

    /**
     * 商品 ID
     */
    private Long goodsId;

    /**
     * 商品原价
     */
    private BigDecimal goodsPrice;

    /**
     * 活动 ID
     */
    private Long activityId;

    /**
     * 秒杀商品名称
     */
    private String seckillTitle;

    /**
     * 秒杀商品图片
     */
    private String seckillImg;

    /**
     * 秒杀价
     */
    private BigDecimal seckillPrice;

    /**
     * 秒杀商品库存总量
     */
    private Integer seckillTotal;

    /**
     * 秒杀商品剩余库存
     */
    private Integer seckillStock;

    /**
     * 活动状态：0=未开始，1=进行中，2=已结束
     */
    private Integer activityStatus;

    /**
     * 活动开始时间
     */
    private LocalDateTime beginTime;

    /**
     * 活动结束时间
     */
    private LocalDateTime endTime;
}
