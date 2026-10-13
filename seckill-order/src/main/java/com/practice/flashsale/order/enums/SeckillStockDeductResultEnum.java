package com.practice.flashsale.order.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @Author: 沃淇淋
 * @Date: 2026/10/13 15:22
 * @Version: v1.0.0
 * @Description: 秒杀库存预扣结果
 **/
@Getter
@AllArgsConstructor
public enum SeckillStockDeductResultEnum {
    ACTIVITY_ENDED(4L, "秒杀活动已结束"),
    ACTIVITY_NOT_STARTED(3L, "秒杀活动未开始"),
    REPEATED_ORDER(2L, "请勿重复参与秒杀"),
    SUCCESS(1L, "库存预扣成功"),
    SOLD_OUT(0L, "秒杀商品已售罄"),
    STOCK_NOT_PREHEATED(-1L, "秒杀库存未预热");

    /**
     * Lua 脚本返回码
     */
    private final Long code;

    /**
     * 返回码说明
     */
    private final String description;

    /**
     * 根据 Lua 返回码获取预扣结果
     */
    public static SeckillStockDeductResultEnum getByCode(Long code) {
        for (SeckillStockDeductResultEnum value : values()) {
            if (value.getCode().equals(code)) {
                return value;
            }
        }
        return null;
    }
}
