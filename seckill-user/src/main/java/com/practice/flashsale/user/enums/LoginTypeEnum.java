package com.practice.flashsale.user.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum LoginTypeEnum {

    PASSWORD(1, "密码登录"),
    VERIFY_CODE(2, "验证码登录"),
    ;

    // 类型值
    private final Integer code;
    // 类型描述
    private final String description;
}
