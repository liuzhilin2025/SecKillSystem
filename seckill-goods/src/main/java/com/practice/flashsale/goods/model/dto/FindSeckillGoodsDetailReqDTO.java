package com.practice.flashsale.goods.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 查询秒杀商品详情入参
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FindSeckillGoodsDetailReqDTO {

    /**
     * 活动 ID
     */
    @NotNull(message = "活动 ID 不能为空")
    @Positive(message = "活动 ID 不合法")
    private Long activityId;

    /**
     * 商品 ID（t_goods.id）
     */
    @NotNull(message = "商品 ID 不能为空")
    @Positive(message = "商品 ID 不合法")
    private Long goodsId;
}
