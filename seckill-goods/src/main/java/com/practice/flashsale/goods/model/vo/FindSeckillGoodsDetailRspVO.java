package com.practice.flashsale.goods.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 查询秒杀商品详情出参
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FindSeckillGoodsDetailRspVO {

    /**
     * 秒杀商品关联表 ID
     */
    private Long id;

    /**
     * 商品 ID
     */
    private Long goodsId;

    /**
     * 活动 ID
     */
    private Long activityId;

    /**
     * 商品名称
     */
    private String goodsName;

    /**
     * 商品原价
     */
    private BigDecimal goodsPrice;

    /**
     * 商品轮播图列表
     */
    private List<String> goodsImgs;

    /**
     * 商品详情（HTML 内容）
     */
    private String goodsDetail;

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
