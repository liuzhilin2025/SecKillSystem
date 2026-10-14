package com.practice.flashsale.order.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * @Author: 沃淇淋
 * @Date: 2026/10/14 11:29
 * @Version: v1.0.0
 * @Description: 秒杀库存回补结果枚举
 **/
@Getter
@AllArgsConstructor
public enum SeckillStockCompensationResultEnum {

    SUCESS(1L, "库存回补成功"),
    USER_ORDER_MARK_NOT_EXIST(0L, "用户购买标记不存在或不属于当前订单"),
    STOCK_NOT_PREHEATED(-1L, "秒杀库存未预热");

    private final Long code;
    private final String description;

    /**
     * 根据 Code 值获取对应枚举
     * @param code
     * @return
     */
    public static SeckillStockCompensationResultEnum getByCode(Long code) {
        return Arrays.stream(values())
                .filter(result -> result.getCode().equals(code))
                .findFirst()
                .orElse(null);
    }
}
