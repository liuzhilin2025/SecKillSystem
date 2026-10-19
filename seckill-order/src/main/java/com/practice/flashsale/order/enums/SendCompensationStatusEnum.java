package com.practice.flashsale.order.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Objects;

/**
 * @Author: 沃淇淋
 * @Date: 2026/10/19 14:38
 * @Version: v1.0.0
 * @Description: 发送失败补偿状态枚举
 **/
@Getter
@AllArgsConstructor
public enum SendCompensationStatusEnum {

    /**
     * 待重试：等待定时任务捞起重发
     */
    RETRY_PENDING(0, "待重试"),
    /**
     * 已补偿：消息已重新发送成功
     */
    COMPENSATED(1, "已补偿"),
    /**
     * 待人工处理：重发达到上限仍失败，不再自动重发
     */
    MANUAL_PENDING(2, "待人工处理"),
    ;

    private final Integer status;

    private final String description;

    /**
     * 根据状态值获取枚举
     */
    public static SendCompensationStatusEnum getByStatus(Integer status) {
        for (SendCompensationStatusEnum value : values()) {
            if (Objects.equals(value.getStatus(), status)) {
                return value;
            }
        }
        return null;
    }
}
