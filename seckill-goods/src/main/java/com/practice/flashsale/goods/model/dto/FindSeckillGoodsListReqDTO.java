package com.practice.flashsale.goods.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author: 沃淇淋
 * @Date: 2026/10/8 09:43
 * @Version: v1.0.0
 * @Description: 查询秒杀商品列表入参
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FindSeckillGoodsListReqDTO {
    @NotNull(message = "活动 ID 不能为空")
    @Positive(message = "活动 ID 不合法")
    private Long activityId;
}
