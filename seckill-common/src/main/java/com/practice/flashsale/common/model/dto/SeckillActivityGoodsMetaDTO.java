package com.practice.flashsale.common.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @Author: 沃淇淋
 * @Date: 2026/10/13 16:39
 * @Version: v1.0.0
 * @Description: 秒杀活动商品元数据
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SeckillActivityGoodsMetaDTO {

    private Long seckillGoodsId;

    private Long activityId;

    private Long goodsId;

    private String goodsName;

    private String goodsImg;

    private BigDecimal seckillPrice;

    private LocalDateTime beginTime;

    private LocalDateTime endTime;
}
