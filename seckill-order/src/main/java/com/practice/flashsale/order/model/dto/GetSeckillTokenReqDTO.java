package com.practice.flashsale.order.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author: 沃淇淋
 * @Date: 2026/10/16 10:07
 * @Version: v1.0.0
 * @Description: 获取秒杀令牌
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GetSeckillTokenReqDTO {

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

    /**
     * 行为验证码二次校验 ID
     */
    private String captchaId;
}
