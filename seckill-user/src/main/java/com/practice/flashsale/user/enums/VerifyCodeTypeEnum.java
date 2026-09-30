package com.practice.flashsale.user.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum VerifyCodeTypeEnum {
    REGISTER(1, "register", "注册"),
    LOGIN(2, "login", "登录"),
    ;

    // 类型值
    private final Integer code;
    // 场景标识（用于拼接 Redis Key）
    private final String purpose;
    // 类型描述
    private final String description;

    /**
     * 根据 code 获取枚举
     *
     * @param code
     * @return
     */
    public static VerifyCodeTypeEnum of(Integer code) {
        for (VerifyCodeTypeEnum typeEnum : values()) {
            if (typeEnum.getCode().equals(code)) {
                return typeEnum;
            }
        }
        return null;
    }
}
