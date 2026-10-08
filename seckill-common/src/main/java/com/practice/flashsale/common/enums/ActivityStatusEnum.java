package com.practice.flashsale.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @Author: 沃淇淋
 * @Date: 2026/10/8 09:38
 * @Version: v1.0.0
 * @Description: 秒杀活动状态枚举
 **/
@Getter
@AllArgsConstructor
public enum ActivityStatusEnum {

    NOT_STARTED(0, "未开始"),
    ING(1, "进行中"),
    ENDED(2, "已结束");

    /**
     * 状态值
     */
    private final Integer status;

    /**
     * 状态描述
     */
    private final String description;
}
