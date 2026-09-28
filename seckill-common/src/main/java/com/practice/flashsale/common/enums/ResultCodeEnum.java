package com.practice.flashsale.common.enums;

import com.practice.flashsale.common.exception.BaseExceptionInterface;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ResultCodeEnum implements BaseExceptionInterface {

    // 通用状态异常码
    SYSTEM_ERROR("10000", "出错啦，后台小哥正在努力修复中..."),
    PARAM_NOT_VALID("10001", "参数错误"),

    // 业务异常状态码
    ;

    // 异常码
    private String errorCode;
    // 错误信息
    private String errorMessage;
}
