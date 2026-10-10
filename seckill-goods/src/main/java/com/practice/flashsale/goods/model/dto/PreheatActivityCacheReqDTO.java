package com.practice.flashsale.goods.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Author: 沃淇淋
 * @Date: 2026/10/9
 * @Version: v1.0.0
 * @Description: 预热活动商品缓存入参
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PreheatActivityCacheReqDTO {

    /**
     * 活动 ID
     */
    @NotNull(message = "活动 ID 不能为空")
    @Positive(message = "活动 ID 不合法")
    private Long activityId;
}
