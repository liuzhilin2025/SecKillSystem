package com.practice.flashsale.order.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author: 沃淇淋
 * @Date: 2026/10/8 14:46
 * @Version: v1.0.0
 * @Description: 秒杀下单入参
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DoSeckillReqDTO {

    /**
     * 活动 ID
     */
    @NotNull(message = "活动 ID 不能为空")
    @Positive(message = "活动 ID 不合法")
    private Long activityId;

    /**
     * 商品 ID
     */
    @NotNull(message = "商品 ID 不能为空")
    @Positive(message = "商品 ID 不合法")
    private Long goodsId;
}
