package com.practice.flashsale.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @Author: 沃淇淋
 * @Date: 2026/10/8 14:30
 * @Version: v1.0.0
 * @Description: 秒杀订单表 DO
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@TableName("t_seckill_order")
public class SeckillOrder {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long activityId;

    private Long goodsId;

    private String orderNo;

    private BigDecimal seckillPrice;

    private String goodsName;

    private String goodsImg;

    private Integer status;

    private LocalDateTime expireTime;

    private Integer isDeleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
